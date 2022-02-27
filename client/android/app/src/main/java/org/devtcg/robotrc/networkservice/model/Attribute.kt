package org.devtcg.robotrc.networkservice.model

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class Attribute(
  val type_name: String,
  val name: String,
  val is_readable: Boolean,
  val is_writable: Boolean,
)