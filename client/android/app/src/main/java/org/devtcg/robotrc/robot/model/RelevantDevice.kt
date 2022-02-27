package org.devtcg.robotrc.robot.model

import org.devtcg.robotrc.devicewidget.api.AttributeValueLocal
import org.devtcg.robotrc.networkservice.model.Device

data class RelevantDevice(
  val device: Device,
  val relevantValues: Map<String, AttributeValueLocal>,
)