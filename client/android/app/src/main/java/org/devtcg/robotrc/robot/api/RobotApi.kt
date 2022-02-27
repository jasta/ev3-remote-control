package org.devtcg.robotrc.robot.api

import androidx.lifecycle.MutableLiveData
import org.devtcg.robotrc.robot.model.RobotModel
import org.devtcg.robotrc.robotview.network.DeviceDataFetcher

/**
 * Main UI <-> network infrastructure gateway, but relevant only after a robot has been queried and
 * selected by the user.
 */
class RobotApi(
  val devicesLiveData: MutableLiveData<RobotModel>,
  val deviceDataFetcher: DeviceDataFetcher,
)
