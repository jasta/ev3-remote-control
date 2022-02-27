package org.devtcg.robotrc.robotdata.bridge

import org.devtcg.robotrc.robotdata.api.RobotApi
import org.devtcg.robotrc.robotview.bridge.DeviceWidgetRegistryBridge

object RobotApiBridge {
  private val robots = HashMap<String, RobotApi>()

  private val apiFactory = RobotApiFactory()

  val currentSelection: RobotApi?
  get() {
    val robotHost = RobotSelectorBridge.instance.value ?: return null
    return robots.getOrPut(robotHost.host) {
      apiFactory.create(robotHost)
    }
  }
}

