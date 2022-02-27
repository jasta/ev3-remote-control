package org.devtcg.robotrc.robot.bridge

import org.devtcg.robotrc.robot.api.RobotApi

object RobotApiBridge {
  private val robots = HashMap<String, RobotApi>()

  val currentSelection: RobotApi?
  get() {
    val robotHost = RobotSelectorBridge.instance.value ?: return null
    return robots.getOrPut(robotHost.host) {
      RobotApiFactoryBridge.instance.create(robotHost)
    }
  }
}

