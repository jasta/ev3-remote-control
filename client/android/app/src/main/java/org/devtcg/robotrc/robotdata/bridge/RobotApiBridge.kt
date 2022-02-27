package org.devtcg.robotrc.robotdata.bridge

import org.devtcg.robotrc.robotdata.api.RobotModelApi

object RobotApiBridge {
  private val robots = HashMap<String, RobotModelApi>()

  private val apiFactory = RobotApiFactory()

  val currentSelection: RobotModelApi?
  get() {
    val robotHost = RobotSelectorBridge.instance.value ?: return null
    return robots.getOrPut(robotHost.host) {
      apiFactory.create(robotHost)
    }
  }
}

