package org.devtcg.robotrc.robotlayout.api

import org.devtcg.robotrc.robotdata.api.AttributeSpec

data class DeviceWidgetSpec(
  val hardwarePlatform: String?,
  val type: String?,
  val driver: String?,
  val attributes: List<AttributeSpec>,
)