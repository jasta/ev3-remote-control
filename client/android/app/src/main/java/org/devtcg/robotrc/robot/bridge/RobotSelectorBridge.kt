package org.devtcg.robotrc.robot.bridge

import androidx.lifecycle.MutableLiveData
import org.devtcg.robotrc.robot.model.RobotConfiguration
import org.devtcg.robotrc.robot.model.RobotTarget

object RobotSelectorBridge {
  // TODO: Don't hardcode the selection :P
  val instance = MutableLiveData(RobotTarget("ev3dev", "ev3", RobotConfiguration.RAW_DEVICES))
}
