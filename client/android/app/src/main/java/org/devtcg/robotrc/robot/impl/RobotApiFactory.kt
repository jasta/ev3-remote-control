package org.devtcg.robotrc.robot.impl

import androidx.lifecycle.MutableLiveData
import org.devtcg.robotrc.devicewidget.api.DeviceWidgetRegistry
import org.devtcg.robotrc.networkservice.bridge.RemoteControlServiceFactory
import org.devtcg.robotrc.robot.api.RobotApi
import org.devtcg.robotrc.robot.model.RobotTarget
import org.devtcg.robotrc.robot.model.RobotModel
import org.devtcg.robotrc.robotview.network.DeviceDataFetcher
import java.util.concurrent.Executors

class RobotApiFactory(
  private val widgetRegistry: DeviceWidgetRegistry,
) {
  fun create(target: RobotTarget): RobotApi {
    val applicableSpecs = widgetRegistry.supportedDrivers
      .map { it.first }
      .filter { it.hardwarePlatform == target.hardwarePlatform }
    val modelLiveData = MutableLiveData<RobotModel>()
    return RobotApi(
      modelLiveData,
      DeviceDataFetcher(
        Executors.newSingleThreadExecutor(),
        RemoteControlServiceFactory.create(target.host),
        applicableSpecs,
        modelLiveData
      )
    )
  }
}