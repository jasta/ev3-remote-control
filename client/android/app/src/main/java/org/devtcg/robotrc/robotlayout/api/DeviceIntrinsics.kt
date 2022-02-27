package org.devtcg.robotrc.robotlayout.api

data class DeviceIntrinsics(
  val type: DeviceType,
  val driver: String,
  val address: String,
)