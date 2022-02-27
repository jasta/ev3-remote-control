package org.devtcg.robotrc.networkservice.model

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class AttributeValue(
  val name: String,
  val value: Any,
)