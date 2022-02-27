package org.devtcg.robotrc.robotdata.api

import androidx.lifecycle.MutableLiveData
import org.devtcg.robotrc.robotselection.model.RobotTarget
import org.devtcg.robotrc.robotdata.network.DeviceDataFetcher
import org.devtcg.robotrc.robotdata.model.DeviceAttributesSnapshot

/**
 * Main UI <-> network infrastructure gateway, but relevant only after a robot has been queried and
 * selected by the user.
 */
class RobotModelApi(
  val target: RobotTarget,
  val allDevices: MutableLiveData<List<DeviceModelApi>>,
  val relevantAttributes: MutableLiveData<Map<String, DeviceAttributesSnapshot>>,
  val deviceDataFetcher: DeviceDataFetcher,
)
