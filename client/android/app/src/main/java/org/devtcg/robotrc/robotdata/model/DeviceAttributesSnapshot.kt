package org.devtcg.robotrc.robotdata.model

import android.os.SystemClock

data class DeviceAttributesSnapshot(
  /**
   * Only contains attributes that are part of the spec!  To update, see
   * [DeviceApi#updateAttributeSpec].
   */
  private val receivedValues: Map<String, AttributeValueLocal>,

  /**
   * Optimistic values that are pending receipt by the remote peer but have not yet been
   * acknowledged.  These values should be used to prioritize user request, i.e. when the user is
   * stepping up the duty cycle gradually we don't want to wait for a network round trip.
   */
  private val optimisticValues: Map<String, AttributeValueLocal>,

  /**
   * Time (as per [SystemClock]) that the fetch occurred.  Can be used to determine age of the
   * snapshot and make determinations of drift.
   */
  private val fetchedRealtimeMs: Long,
) {
  /**
   * How much time has passed since the snapshot was received from the peer?
   */
  fun elapsedSinceFetchedMs() = SystemClock.elapsedRealtime() - fetchedRealtimeMs

  fun lookupLocalOrRemote(vararg attributeNames: String): AttributeValueLocal? {
    for (attributeName in attributeNames) {
      val value = optimisticValues[attributeName] ?: receivedValues[attributeName]
      if (value != null) {
        return value
      }
    }
    return null
  }
}