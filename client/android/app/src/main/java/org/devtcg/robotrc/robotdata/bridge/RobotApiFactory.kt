package org.devtcg.robotrc.robotdata.bridge

import androidx.lifecycle.MutableLiveData
import org.devtcg.robotrc.concurrency.RobotExecutors
import org.devtcg.robotrc.networkservice.bridge.RemoteControlServiceFactory
import org.devtcg.robotrc.robotdata.api.DeviceModelApi
import org.devtcg.robotrc.robotdata.api.RobotModelApi
import org.devtcg.robotrc.robotselection.model.RobotTarget
import org.devtcg.robotrc.robotdata.network.DeviceDataFetcher
import org.devtcg.robotrc.robotdata.model.DeviceAttributesSnapshot
import java.util.concurrent.Executors
import java.util.concurrent.Executors.newSingleThreadScheduledExecutor

internal class RobotApiFactory {
  fun create(target: RobotTarget): RobotModelApi {
    val allDevices = MutableLiveData<List<DeviceModelApi>>()
    val relevantAttributes = MutableLiveData<Map<String, DeviceAttributesSnapshot>>()

    return RobotModelApi(
      target,
      allDevices,
      relevantAttributes,
      DeviceDataFetcher(
        RobotExecutors.newSingleThreadScheduledExecutor("data-fetcher-${target.host}"),
        target,
        RemoteControlServiceFactory.create(target.host),
        allDevices,
        relevantAttributes))
  }
}