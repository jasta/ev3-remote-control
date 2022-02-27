package org.devtcg.robotrc.devicewidget.api

import androidx.lifecycle.LiveData

interface DeviceModelApi {
  val attributesSnapshot: LiveData<DeviceAttributesSnapshot>
  fun updateAttributeSpec(spec: List<AttributeSpec>)
  fun sendAttributeWrite(key: String, value: AttributeValueLocal)
}
