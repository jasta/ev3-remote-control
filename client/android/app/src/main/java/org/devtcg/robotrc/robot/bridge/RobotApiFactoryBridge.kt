package org.devtcg.robotrc.robot.bridge

import org.devtcg.robotrc.robot.impl.RobotApiFactory
import org.devtcg.robotrc.robotview.bridge.DeviceWidgetRegistryBridge

internal object RobotApiFactoryBridge {
  val instance = RobotApiFactory(DeviceWidgetRegistryBridge.instance)
}