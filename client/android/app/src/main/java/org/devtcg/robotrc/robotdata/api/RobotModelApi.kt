package org.devtcg.robotrc.robotdata.api

import androidx.lifecycle.LiveData
import org.devtcg.robotrc.robotdata.model.DeviceAttributesSnapshot
import org.devtcg.robotrc.robotdata.network.DeviceDataFetcher
import org.devtcg.robotrc.robotselection.model.RobotTarget

/**
 * Main UI <-> network infrastructure gateway, but relevant only after a robot has been queried and
 * selected by the user.
 */
class RobotModelApi(
  val target: RobotTarget,
  val connectivity: LiveData<ConnectivityState>,
  val allDevices: LiveData<List<DeviceModelApi>>,
  val relevantAttributes: LiveData<Map<String, DeviceAttributesSnapshot>>,
  val deviceDataFetcher: DeviceDataFetcher,
)

