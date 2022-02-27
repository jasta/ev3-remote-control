package org.devtcg.robotrc.devicewidget.api

data class AttributeSpec(
  val name: String,
  val updateFrequencyMs: Long = 1000,
  val readwrite: ReadWriteSpec = ReadWriteSpec.READ,
  val isArray: Boolean = false,
  val optional: Boolean = false,
)