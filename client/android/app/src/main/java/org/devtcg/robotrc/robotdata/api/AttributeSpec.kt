package org.devtcg.robotrc.robotdata.api

import org.devtcg.robotrc.robotdata.model.ReadWriteSpec

data class AttributeSpec(
  val name: String,
  val readwrite: ReadWriteSpec = ReadWriteSpec.READ,
  val isArray: Boolean = false,
  val optional: Boolean = false,
)