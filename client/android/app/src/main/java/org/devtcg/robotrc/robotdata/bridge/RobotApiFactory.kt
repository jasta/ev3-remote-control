package org.devtcg.robotrc.robotdata.bridge

import androidx.lifecycle.MutableLiveData
import org.devtcg.robotrc.networkservice.bridge.RemoteControlServiceFactory
import org.devtcg.robotrc.robotdata.api.DeviceModelApi
import org.devtcg.robotrc.robotdata.api.RobotApi
import org.devtcg.robotrc.robotdata.model.RobotTarget
import org.devtcg.robotrc.robotdata.model.RobotModel
import org.devtcg.robotrc.robotdata.network.DeviceDataFetcher
import org.devtcg.robotrc.robotlayout.api.DeviceAttributesSnapshot
import java.util.concurrent.Executors

internal class RobotApiFactory {
  fun create(target: RobotTarget): RobotApi {
    val allDevices = MutableLiveData<List<DeviceModelApi>>()
    val relevantAttributes = MutableLiveData<Map<String, DeviceAttributesSnapshot>>()

    return RobotApi(
      target,
      allDevices,
      relevantAttributes,
      DeviceDataFetcher(
        Executors.newSingleThreadScheduledExecutor(),
        target,
        RemoteControlServiceFactory.create(target.host),
        allDevices,
        relevantAttributes))
  }
}