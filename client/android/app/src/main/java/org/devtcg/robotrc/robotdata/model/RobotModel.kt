package org.devtcg.robotrc.robotdata.model

import androidx.lifecycle.LiveData
import org.devtcg.robotrc.robotdata.api.DeviceModelApi
import org.devtcg.robotrc.robotlayout.api.DeviceAttributesSnapshot

data class RobotModel(
  val target: RobotTarget,
  val allDevices: List<DeviceModelApi>,
  val allAttributes: Map<String, LiveData<DeviceAttributesSnapshot>>
)