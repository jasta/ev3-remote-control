package org.devtcg.robotrc.robotdata.model

data class DeviceIntrinsics(
  val type: DeviceType,
  val driver: String,
  val address: String,
)