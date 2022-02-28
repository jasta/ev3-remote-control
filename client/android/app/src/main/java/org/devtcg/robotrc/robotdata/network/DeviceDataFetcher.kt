package org.devtcg.robotrc.robotdata.network

import android.os.SystemClock
import android.util.Log
import androidx.annotation.GuardedBy
import androidx.lifecycle.MutableLiveData
import org.devtcg.robotrc.networkservice.model.Attribute
import org.devtcg.robotrc.networkservice.model.AttributeValue
import org.devtcg.robotrc.networkservice.model.Device
import org.devtcg.robotrc.networkservice.network.RemoteControlService
import org.devtcg.robotrc.robotdata.api.AttributeSpec
import org.devtcg.robotrc.robotdata.api.DeviceModelApi
import org.devtcg.robotrc.robotdata.model.*
import org.devtcg.robotrc.robotselection.model.RobotTarget
import java.io.IOException
import java.util.concurrent.Future
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

class DeviceDataFetcher(
  private val fetchingExecutor: ScheduledExecutorService,
  private val target: RobotTarget,
  private val service: RemoteControlService,
  private val allDevicesDestination: MutableLiveData<List<DeviceModelApi>>,
  private val relevantAttributesDestination: MutableLiveData<Map<String, DeviceAttributesSnapshot>>,
) {
  companion object {
    private const val TAG = "DeviceFetcher"

    private const val DEVICE_LIST_UPDATE_FREQUENCY_MS: Long = 10_000
  }

  @GuardedBy("this")
  private var started = false

  @GuardedBy("this")
  private var deviceListFuture: Future<*>? = null

  @GuardedBy("this")
  private var relevantUpdateFuture: Future<*>? = null

  @GuardedBy("this")
  private var relevantAttributesByDevice = HashMap<Device, List<RelevantAttribute>>()

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
      val asApis = devices.map { DeviceModelApiImpl(it) }
      allDevicesDestination.postValue(asApis)
    } catch (e: IOException) {
      Log.e(TAG, "Failed to refresh device list: $e")
    }
  }

  private fun updateRelevantAttributesFromSpec(device: Device, specs: List<AttributeSpec>) {
    val output = mutableListOf<RelevantAttribute>()
    for (deviceAttr in device.attributes) {
      val attrSpec = specs.find { specMatchesAttr(it, deviceAttr) } ?: continue
      output.add(RelevantAttribute(deviceAttr.name, attrSpec.updateFrequencyMs))
    }

    updateRelevantAttributesForDevice(device, output)
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
  private fun updateRelevantAttributesForDevice(device: Device, attributes: List<RelevantAttribute>) {
    relevantAttributesByDevice[device] = attributes

    val updateFrequencyMs = relevantAttributesByDevice.values.flatten().minOf { it.updateFrequencyMs }

    if (started) {
      relevantUpdateFuture?.cancel(false)
      relevantUpdateFuture = fetchingExecutor.scheduleAtFixedRate(
        ::refreshAttributeValues,
        0 /* initialDelay */,
        updateFrequencyMs,
        TimeUnit.MILLISECONDS)
    }
  }

  private fun refreshAttributeValues() {
    Log.i(TAG, "Refreshing attribute values for $target...")

    val fetchedAttributes = mutableMapOf<String, DeviceAttributesSnapshot>()
    for ((device, attributes) in relevantAttributesByDevice.entries) {
      try {
        val valuesFromPeer = service.getAttributes(device.address, attributes.map { it.attributeName })
        val values = valuesFromPeer.associate {
          val value = AttributeValueLocal(
            device.attributes.find { attr -> attr.name == it.name }!!.type_name,
            anyTypeFixupHack(it.value).toString()
          )
          it.name to value
        }
        fetchedAttributes[device.address] =
          DeviceAttributesSnapshot(values, SystemClock.elapsedRealtime())
      } catch (e: IOException) {
        Log.e(TAG, "Error refreshing ${device.address}: $e, moving on...")
      }
    }

    Log.d(TAG, "Updated ${fetchedAttributes.values.size} attributes!")
    relevantAttributesDestination.postValue(fetchedAttributes)
  }

  /**
   * Moshi by default treats all JSON numbers as doubles, which confuses our parser downstream for
   * good reason.  Let's try to fit them into more accurate types.
   */
  private fun anyTypeFixupHack(value: Any): Any {
    return when (value) {
      is Double -> {
        val asLong = value.toLong()
        if (asLong.toDouble() == value) {
          asLong
        } else {
          value
        }
      }
      else -> value
    }
  }

  @Synchronized
  fun refreshAttributesNow() {
    check(started)
    fetchingExecutor.submit(::refreshAttributeValues)
  }

  data class RelevantAttribute(
    val attributeName: String,
    val updateFrequencyMs: Long)

  inner class DeviceModelApiImpl(private val device: Device) : DeviceModelApi {
    override val intrinsics = DeviceIntrinsics(
      when (device.type_name) {
        "sensor" -> DeviceType.SENSOR
        "actuator" -> DeviceType.ACTUATOR
        else -> throw IOException("type=${device.type_name}")
      },
      device.driver_name,
      device.address)

    override fun updateAttributeSpec(spec: List<AttributeSpec>) {
      updateRelevantAttributesFromSpec(device, spec)
    }

    override fun sendAttributeWrites(writes: Map<String, AttributeValueLocal>) {
      fetchingExecutor.submit {
        val attributes = writes.map { (key, value) ->
          AttributeValue(key, value.coerceToString())
        }
        try {
          service.putAttributes(device.address, attributes)
          refreshAttributesNow()
        } catch (e: IOException) {
          Log.e(TAG, "Error writing to ${device.address} with $attributes: $e")
        }
      }
    }
  }
}