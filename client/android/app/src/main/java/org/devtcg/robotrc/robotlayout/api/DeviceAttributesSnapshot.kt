package org.devtcg.robotrc.robotlayout.api

import android.os.SystemClock

class DeviceAttributesSnapshot(
  /**
   * Only contains attributes that are part of the spec!  To update, see
   * [DeviceApi#updateAttributeSpec].
   */
  val attributeValues: Map<String, AttributeValueLocal>,

  /**
   * Time (as per [SystemClock]) that the fetch occurred.  Can be used to determine age of the
   * snapshot and make determinations of drift.
   */
  private val fetchedRealtimeMs: Long,
) {
  /**
   * How much time has passed since the snapshot was received from the peer?
   */
  fun elapsedMsSinceFetched() = fetchedRealtimeMs - SystemClock.elapsedRealtime()
}