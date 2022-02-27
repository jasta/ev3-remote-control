package org.devtcg.robotrc.devicewidget.api

data class DeviceWidgetSpec(
  val hardwarePlatform: String?,
  val type: String?,
  val driver: String?,
  val attributes: List<AttributeSpec>,
)