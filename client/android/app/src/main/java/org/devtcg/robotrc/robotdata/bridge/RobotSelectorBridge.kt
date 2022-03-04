package org.devtcg.robotrc.robotdata.bridge

import androidx.lifecycle.MutableLiveData
import org.devtcg.robotrc.robotselection.model.RobotTarget

object RobotSelectorBridge {
  val instance = MutableLiveData<RobotTarget?>(null)
}
