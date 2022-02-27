package org.devtcg.robotrc.robotdata.model

import org.devtcg.robotrc.robotlayout.api.AttributeValueLocal
import org.devtcg.robotrc.networkservice.model.Device

data class RelevantDevice(
  val device: Device,
  val relevantValues: Map<String, AttributeValueLocal>,
)