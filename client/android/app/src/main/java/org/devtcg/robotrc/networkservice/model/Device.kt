package org.devtcg.robotrc.networkservice.model

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class Device(
  val type_name: String,
  val driver_name: String,
  val address: String,
  val attributes: List<Attribute>,
)