package org.devtcg.robotrc.robotview.network

import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.annotation.GuardedBy
import androidx.lifecycle.MutableLiveData
import org.devtcg.robotrc.devicewidget.api.AttributeSpec
import org.devtcg.robotrc.devicewidget.api.AttributeValueLocal
import org.devtcg.robotrc.devicewidget.api.DeviceWidgetSpec
import org.devtcg.robotrc.devicewidget.api.ReadWriteSpec
import org.devtcg.robotrc.networkservice.model.Attribute
import org.devtcg.robotrc.networkservice.model.Device
import org.devtcg.robotrc.networkservice.network.RemoteControlService
import org.devtcg.robotrc.robot.model.RelevantDevice
import org.devtcg.robotrc.robot.model.RobotModel
import org.devtcg.robotrc.robot.model.RobotTarget
import java.io.IOException
import java.util.concurrent.Future
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

class DeviceDataFetcher(
  private val fetchingExecutor: ScheduledExecutorService,
  private val target: RobotTarget,
  private val service: RemoteControlService,
  private val applicableSpecs: List<DeviceWidgetSpec>,
  private val destination: MutableLiveData<RobotModel>
) {
  companion object {
    private const val TAG = "DeviceFetcher"

    private const val DEVICE_LIST_UPDATE_FREQUENCY_MS: Long = 10_000
  }

  private val mainHandler = Handler(Looper.getMainLooper())

  @GuardedBy("this")
  private var started = false

  @GuardedBy("this")
  private var deviceListFuture: Future<*>? = null

  @GuardedBy("this")
  private var relevantUpdateFuture: Future<*>? = null

  @GuardedBy("this")
  private var relevantAttributesByDevice: Map<Device, List<RelevantAttribute>>? = null

  @Synchronized
  fun ensureStarted() {
    if (!started) {
      Log.i(TAG, "Starting fetches for $target...")
      start()
    }
  }

  @Synchronized
  fun ensureStopped() {
    if (started) {
      Log.i(TAG, "Shutting down fetches for $target...")
      stop()
    }
  }

  @Synchronized
  private fun start() {
    check(!started)
    check(deviceListFuture == null)
    check(relevantUpdateFuture == null)
    check(relevantAttributesByDevice == null)

    started = true

    deviceListFuture = fetchingExecutor.scheduleAtFixedRate(
      ::refreshDeviceList,
      0 /* initialDelay */,
      DEVICE_LIST_UPDATE_FREQUENCY_MS,
      TimeUnit.MILLISECONDS)
  }

  @Synchronized
  private fun stop() {
    check(started)
    started = false

    try {
      deviceListFuture!!.cancel(false)
      deviceListFuture = null
    } finally {
      relevantUpdateFuture!!.cancel(false)
      relevantUpdateFuture = null
    }
  }

  private fun refreshDeviceList() {
    Log.i(TAG, "Refreshing device list for $target...")
    try {
      val devices = service.listDevices()
      Log.d(TAG, "Got ${devices.size} devices!")
      val attributes = filterAttributes(devices)
      determinedRelevantAttributes(attributes)
    } catch (e: IOException) {
      Log.e(TAG, "Failed to refresh device list: $e")
    }
  }

  private fun filterAttributes(devices: List<Device>): Map<Device, List<RelevantAttribute>> {
    val output = mutableMapOf<Device, MutableList<RelevantAttribute>>()

    for (device in devices) {
      val deviceSpec = applicableSpecs.find { specMatchesDevice(it, device) } ?: continue
      for (deviceAttr in device.attributes) {
        val attrSpec = deviceSpec.attributes.find { specMatchesAttr(it, deviceAttr) } ?: continue
        val outputAttrs = output.getOrPut(device) { mutableListOf() }
        outputAttrs.add(RelevantAttribute(deviceAttr.name, attrSpec.updateFrequencyMs))
      }
    }

    return output
  }

  private fun specMatchesDevice(spec: DeviceWidgetSpec, device: Device): Boolean {
    if (spec.type?.equals(device.type_name) == false) {
      return false
    }
    if (spec.driver?.equals(device.driver_name) == false) {
      return false
    }
    return true
  }

  private fun specMatchesAttr(spec: AttributeSpec, attr: Attribute): Boolean {
    if (spec.name != attr.name) {
      return false
    }
    if (spec.isArray && !attr.type_name.startsWith('[')) {
      return false
    }
    val readWriteMatches = when (spec.readwrite) {
      ReadWriteSpec.READ -> attr.is_readable
      ReadWriteSpec.WRITE -> attr.is_writable
      ReadWriteSpec.READWRITE -> attr.is_readable && attr.is_writable
    }
    if (!readWriteMatches) {
      return false
    }
    return true
  }

  @Synchronized
  private fun determinedRelevantAttributes(attributes: Map<Device, List<RelevantAttribute>>) {
    relevantAttributesByDevice = attributes
    val updateFrequencyMs = attributes.values.flatten().minOf { it.updateFrequencyMs }

    if (started) {
      relevantUpdateFuture?.cancel(false)
      relevantUpdateFuture = fetchingExecutor.scheduleAtFixedRate(
        ::refreshAttributeValues,
        0 /* initialDelay */,
        updateFrequencyMs,
        TimeUnit.MILLISECONDS
      )
    }
  }

  private fun refreshAttributeValues() {
    Log.i(TAG, "Refreshing attribute values for $target...")

    val relevantDevices = mutableListOf<RelevantDevice>()
    for ((device, attributes) in relevantAttributesByDevice!!.entries) {
      try {
        val valuesFromPeer = service.getAttributes(device.address, attributes.map { it.attributeName })
        val values = valuesFromPeer.associate {
          val value = AttributeValueLocal(
            device.attributes.find { attr -> attr.name == it.name }!!.type_name,
            it.value.toString()
          )
          it.name to value
        }
        relevantDevices.add(RelevantDevice(device, values))
      } catch (e: IOException) {
        Log.e(TAG, "Error refreshing ${device.address}: $e")
      }
    }

    mainHandler.post {
      destination.value = RobotModel(target, relevantDevices)
    }
  }

  @Synchronized
  fun refreshNow() {
    check(started)
    fetchingExecutor.submit(::refreshDeviceList)
  }

  data class RelevantAttribute(
    val attributeName: String,
    val updateFrequencyMs: Long)
}