package org.devtcg.robotrc.robotdata.api

import org.devtcg.robotrc.robotlayout.api.AttributeValueLocal
import org.devtcg.robotrc.robotlayout.api.DeviceIntrinsics

interface DeviceModelApi {
  val intrinsics: DeviceIntrinsics
  fun updateAttributeSpec(spec: List<AttributeSpec>)
  fun sendAttributeWrite(key: String, value: AttributeValueLocal)
}
