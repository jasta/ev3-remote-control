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

    /**
     * Debugging feature to arbitrarily force a write delay to the remote peer to verify that
     * optimistic writes are working smoothly even when the network is very slow.
     */
    private const val FORCE_WRITE_DELAY_MS: Long = 0
  }

  @GuardedBy("this")
  private var started = false

  @GuardedBy("this")
  private var deviceListFuture: Future<*>? = null

  @GuardedBy("this")
  private var relevantUpdateFuture: Future<*>? = null

  @GuardedBy("this")
  private var pendingWritesFuture: Future<*>? = null

  @GuardedBy("this")
  private var localDeviceStates = HashMap<Device, LocalDeviceState>()

  private data class LocalDeviceState(
    var recentFetch: Map<String, AttributeValueLocal> = mapOf(),

    /**
     * Writes that we're attempting to transact with the peer.  These values will be presumed to be
     * the values at the remote site optimistically until we can confirm otherwise.
     */
    val optimisticWrites: MutableMap<String, AttributeValueLocal> = mutableMapOf(),

    /**
     * Attributes we're currently refreshing (can be changed by user interactions)
     */
    val relevantAttributes: MutableList<RelevantAttribute> = mutableListOf(),
  )

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
      relevantUpdateFuture?.cancel(false)
      relevantUpdateFuture = null
    }
  }

  @Synchronized
  private fun applyPendingWrites(device: Device, writes: Map<String, AttributeValueLocal>) {
    val state = localDeviceState(device)
    state.optimisticWrites.putAll(writes)

    // Post the new optimistic values so the UI can update right away...
    val snapshots = localDeviceStates.entries.associate {
      it.key.address to createSnapshotLocked(it.value)
    }
    relevantAttributesDestination.postValue(snapshots)

    pendingWritesFuture?.cancel(false)
    pendingWritesFuture = fetchingExecutor.schedule({
      performAttributeWrites(device)
    }, FORCE_WRITE_DELAY_MS, TimeUnit.MILLISECONDS)
  }

  @Synchronized
  private fun createSnapshotLocked(state: LocalDeviceState): DeviceAttributesSnapshot {
    return DeviceAttributesSnapshot(
      state.recentFetch,
      state.optimisticWrites.toMap(),
      SystemClock.elapsedRealtime())
  }

  private fun performAttributeWrites(device: Device) {
    // Only use the most recent values, not the ones provided to each call.  This means if we
    // stack writes for the same attribute we'll save some network latency and drop the previous
    // values automatically.
    val state = localDeviceState(device)
    if (state.optimisticWrites.isEmpty()) {
      Log.d(TAG, "Dropping extraneous pending write, already achieved desired result")
      return
    }
    val attributes = state.optimisticWrites.map { (key, value) ->
      AttributeValue(key, value.coerceToString())
    }
    try {
      service.putAttributes(device.address, attributes)
      refreshAttributeValues()
    } catch (e: IOException) {
      Log.e(TAG, "Error writing to ${device.address} with $attributes: $e")
    } finally {
      // Values are removed even if there is an error as we want to reflect that these pending
      // writes can no longer be seen as valid by the client and the server's values will
      // snap back as the authority.
      synchronized(this) {
        for ((key, value) in state.optimisticWrites) {
          // Note that we remove by value too so that we ensure the most recent pending write
          // we sent is the one that actually clears the pending state.  It's possible new values
          // came in while we were stuck in `putAttributes`, and those values need to be applied
          // next time this method is invoked.
          state.optimisticWrites.remove(key, value)
        }
      }
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
    val stateAttributes = localDeviceState(device).relevantAttributes
    stateAttributes.clear()
    stateAttributes.addAll(attributes)

    // TODO: We can technically do better and have multiple scheduled futures in flight.  Might not
    // be worth it though as the msot efficient solution is to actually use the observe feature
    // in CoAP and only receive updates on change from the server.
    val lowestUpdateFrequencyMs = localDeviceStates.values
      .map { it.relevantAttributes }
      .flatten()
      .minOf { it.updateFrequencyMs }

    if (started) {
      Log.i(TAG, "Scheduling refresh attributes every $lowestUpdateFrequencyMs ms...")

      relevantUpdateFuture?.cancel(false)
      relevantUpdateFuture = fetchingExecutor.scheduleAtFixedRate(
        ::refreshAttributeValues,
        0 /* initialDelay */,
        lowestUpdateFrequencyMs,
        TimeUnit.MILLISECONDS)
    }
  }

  private fun refreshAttributeValues() {
    Log.i(TAG, "Refreshing attribute values for $target...")

    val fetchedAttributes = mutableMapOf<String, DeviceAttributesSnapshot>()
    val deviceStatesCopy = synchronized(this) {
      localDeviceStates.toMap()
    }.entries
    for ((device, state) in deviceStatesCopy) {
      val attributes = state.relevantAttributes
      try {
        val valuesFromPeer = service.getAttributes(device.address, attributes.map { it.attributeName })
        val receivedValues = valuesFromPeer.associate {
          val value = AttributeValueLocal(
            device.attributes.find { attr -> attr.name == it.name }!!.type_name,
            anyTypeFixupHack(it.value).toString(),
            AttributeValueSource.REMOTE_READ
          )
          it.name to value
        }
        synchronized (this) {
          state.recentFetch = receivedValues
          fetchedAttributes[device.address] = createSnapshotLocked(state)
        }
      } catch (e: IOException) {
        Log.e(TAG, "Error refreshing ${device.address}: $e, moving on...")
      }
    }

    synchronized(this) {
      Log.d(TAG, "Updated ${fetchedAttributes.values.size} attributes!")
      relevantAttributesDestination.postValue(fetchedAttributes)
    }
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
      is List<*> -> {
        value.joinToString(separator = " ")
      }
      is Array<*> -> {
        value.joinToString(separator = " ")
      }
      else -> value
    }
  }

  @Synchronized
  fun scheduleRefreshAttributeValues() {
    check(started)
    fetchingExecutor.submit(::refreshAttributeValues)
  }

  private fun localDeviceState(device: Device): LocalDeviceState {
    return localDeviceStates.getOrPut(device) { LocalDeviceState() }
  }

  private data class RelevantAttribute(
    val attributeName: String,
    val updateFrequencyMs: Long)

  private inner class DeviceModelApiImpl(private val device: Device) : DeviceModelApi {
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
      writes.entries.find { it.value.source != AttributeValueSource.LOCAL_WRITE }?.let {
        throw IllegalArgumentException("sending non-local write for: ${it.key}")
      }
      applyPendingWrites(device, writes)
    }
  }
}
