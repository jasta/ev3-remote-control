package org.devtcg.robotrc.robotdata.bridge

import androidx.lifecycle.MutableLiveData
import org.devtcg.robotrc.robotselection.model.RobotTarget

object RobotSelectorBridge {
  // TODO: Don't hardcode the selection :P
  val instance = MutableLiveData(RobotTarget("ev3dev", "ev3"))
}
