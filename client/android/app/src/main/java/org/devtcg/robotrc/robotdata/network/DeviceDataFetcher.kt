package org.devtcg.robotrc.robotdata.network

import android.os.SystemClock
import android.util.Log
import androidx.annotation.GuardedBy
import androidx.lifecycle.MutableLiveData
import org.devtcg.robotrc.networkservice.model.Attribute
import org.devtcg.robotrc.networkservice.model.AttributeValue
import org.devtcg.robotrc.networkservice.model.Device
import org.devtcg.robotrc.networkservice.network.CancelTrigger
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

    /**
     * Length of time we will wait with no response from the peer before re-issuing our
     * observe requests to make sure the server is still there and responding as it should.
     */
    private const val NO_PEER_RESPONSE_TIMEOUT: Long = 15_000

    /**
     * Enabled only to help debug Observe support issues in the server
     */
    private const val DISABLE_NO_RESPONSE_TIMEOUT = true

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
  private var deviceListCancel: CancelTrigger? = null

  @GuardedBy("this")
  private var pendingWritesFuture: Future<*>? = null

  @GuardedBy("this")
  private var localDeviceStates = HashMap<Device, LocalDeviceState>()

  private data class LocalDeviceState(
    /**
     * Remote observe request cancel trigger
     */
    var cancelTrigger: CancelTrigger? = null,

    var recentFetch: Map<String, AttributeValueLocal> = mapOf(),
    var recentFetchTimeMs: Long = 0,

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

    started = true

    performDeviceListObserve()
    forceScheduleNoResponseTimeout()
  }

  @Synchronized
  private fun forceScheduleNoResponseTimeout() {
    if (!DISABLE_NO_RESPONSE_TIMEOUT) {
      if (started) {
        deviceListFuture?.cancel(false)
        deviceListFuture = fetchingExecutor.schedule(
          ::performDeviceListObserve,
          NO_PEER_RESPONSE_TIMEOUT,
          TimeUnit.MILLISECONDS
        )
      }
    }
  }

  @Synchronized
  private fun performDeviceListObserve() {
    // Note that it's harmless to issue this call in an overlapping fashion since the
    // peer will simply cancel the old observer silently and use this new one as the main
    // handle.  That said, we do need to cancel our _local_ handle for this request or
    // our CoAP client will keep re-asserting the request.
    Log.i(TAG, "Issuing observeDevices...")
    deviceListCancel?.cancel()
    deviceListCancel = service.observeDevices { result ->
      result.fold(onSuccess = {
        if (started) {
          forceScheduleNoResponseTimeout()
          emitDeviceList(it)
        }
      }, onFailure = {
        // Rare edge case failure, but it's OK because we'll treat this the same as
        // disconnecting (i.e. walking away from the robot with your phone) and will retry
        // again automatically soon.
        Log.e(TAG, "observeDevices failure: $it, should automatically re-establish...")
      })
    }
  }

  @Synchronized
  private fun stop() {
    check(started)
    started = false

    pendingWritesFuture?.cancel(false)
    pendingWritesFuture = null

    deviceListCancel?.cancel()
    deviceListCancel = null

    for (deviceState in localDeviceStates.values) {
      try {
        deviceState.cancelTrigger?.cancel()
      } catch (e: Exception) {
        Log.w(TAG, "Swallowing exception during stop(): $e")
      }
    }
    localDeviceStates.clear()

    deviceListFuture?.cancel(false)
    deviceListFuture = null
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
      state.recentFetchTimeMs)
  }

  private fun performAttributeWrites(device: Device) {
    // Only use the most recent values, not the ones provided to each call.  This means if we
    // stack writes for the same attribute we'll save some network latency and drop the previous
    // values automatically.
    val state = localDeviceState(device)
    val writesLocalCopy = synchronized(this) {
      if (state.optimisticWrites.isEmpty()) {
        Log.d(TAG, "Dropping extraneous pending write, already achieved desired result")
        return
      }
      state.optimisticWrites.toMap()
    }
    val attributes = writesLocalCopy.map { (key, value) ->
      AttributeValue(key, value.coerceToString())
    }
    try {
      service.putAttributes(device.address, attributes)
      refreshAttributeValues(device, service.getAttributes(device.address, attributes.map { it.name }))
    } catch (e: IOException) {
      Log.e(TAG, "Error writing to ${device.address} with $attributes: $e")
    } finally {
      // Values are removed even if there is an error as we want to reflect that these pending
      // writes can no longer be seen as valid by the client and the server's values will
      // snap back as the authority.
      synchronized(this) {
        for ((key, value) in writesLocalCopy) {
          // Note that we remove by value too so that we ensure the most recent pending write
          // we sent is the one that actually clears the pending state.  It's possible new values
          // came in while we were stuck in `putAttributes`, and those values need to be applied
          // next time this method is invoked.
          state.optimisticWrites.remove(key, value)
        }
      }
    }
  }

  private fun emitDeviceList(devices: List<Device>) {
    Log.d(TAG, "Updated devices list: got ${devices.size} devices!")
    val asApis = devices.map { DeviceModelApiImpl(it) }
    allDevicesDestination.postValue(asApis)
  }

  private fun updateRelevantAttributesFromSpec(device: Device, specs: List<AttributeSpec>) {
    val output = mutableListOf<RelevantAttribute>()
    for (deviceAttr in device.attributes) {
      val attrSpec = specs.find { specMatchesAttr(it, deviceAttr) } ?: continue
      output.add(RelevantAttribute(deviceAttr.name))
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
    val deviceState = localDeviceState(device)
    val stateAttributes = deviceState.relevantAttributes
    stateAttributes.clear()
    stateAttributes.addAll(attributes)

    if (started) {
      val attributeNames = attributes.map { it.attributeName }
      Log.i(TAG, "Observing attribute changes for ${device.driver_name} @ ${device.address}: $attributeNames...")

      deviceState.cancelTrigger?.cancel()
      deviceState.cancelTrigger = service.observeAttributes(device.address, attributeNames) { result ->
        result.fold(onSuccess = {
          if (started) {
            forceScheduleNoResponseTimeout()
            refreshAttributeValues(device, it)
          }
        }, onFailure = {
          Log.e(TAG, "observeAttributes failure for ${device.address}: $it, should automatically re-establish...")
        })
      }
    }
  }

  private fun refreshAttributeValues(receivingDevice: Device, rawValuesFromPeer: List<AttributeValue>) {
    Log.i(TAG, "Refreshing ${receivingDevice.address}: got ${rawValuesFromPeer.size} attribute values...")

    val fetchedAttributes = mutableMapOf<String, DeviceAttributesSnapshot>()
    val deviceStatesCopy = synchronized(this) {
      localDeviceStates.toMap()
    }

    for ((device, state) in deviceStatesCopy) {
      if (receivingDevice != device) {
        fetchedAttributes[device.address] = createSnapshotLocked(state)
      } else {
        val attributes = state.relevantAttributes
        try {
          val refinedValues = refineValuesFromPeer(rawValuesFromPeer, attributes)
          val receivedValues = refinedValues.associate {
            val value = AttributeValueLocal(
              device.attributes.find { attr -> attr.name == it.name }!!.type_name,
              anyTypeFixupHack(it.value).toString(),
              AttributeValueSource.REMOTE_READ
            )
            it.name to value
          }
          synchronized(this) {
            state.recentFetch = receivedValues
            state.recentFetchTimeMs = SystemClock.elapsedRealtime()
            fetchedAttributes[device.address] = createSnapshotLocked(state)
          }
        } catch (e: IOException) {
          Log.e(TAG, "Error refreshing ${device.address}: $e, moving on...")
        }
      }
    }

    synchronized(this) {
      Log.d(TAG, "Emitting attribute changes for ${receivingDevice.address}...")
      relevantAttributesDestination.postValue(fetchedAttributes)
    }
  }

  /**
   * Refine the values provided from the peer to only those that we're interested in, throwing an
   * error if not all relevant attributes are found.
   */
  private fun refineValuesFromPeer(
    values: List<AttributeValue>,
    relevantAttributes: List<RelevantAttribute>
  ): List<AttributeValue> {
    val refinedValues = values.filter { relevantAttributes.contains(RelevantAttribute(it.name)) }
    if (refinedValues.size < relevantAttributes.size) {
      throw IOException("values=$values does not contain all required relevantAttributes=$relevantAttributes")
    }
    return refinedValues
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

  private fun localDeviceState(device: Device): LocalDeviceState {
    return localDeviceStates.getOrPut(device) { LocalDeviceState() }
  }

  private data class RelevantAttribute(val attributeName: String)

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
