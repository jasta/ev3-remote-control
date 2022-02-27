package org.devtcg.robotrc.robotdata.api

import org.devtcg.robotrc.robotdata.model.AttributeValueLocal
import org.devtcg.robotrc.robotdata.model.DeviceIntrinsics

interface DeviceModelApi {
  val intrinsics: DeviceIntrinsics
  fun updateAttributeSpec(spec: List<AttributeSpec>)
  fun sendAttributeWrite(key: String, value: AttributeValueLocal)
}
