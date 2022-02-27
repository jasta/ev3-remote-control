package org.devtcg.robotrc.robotview.ui

import androidx.lifecycle.ViewModel
import org.devtcg.robotrc.robotdata.bridge.RobotApiBridge

class RobotDataViewModel: ViewModel() {
  val robotApi = RobotApiBridge.currentSelection!!

  init {
    robotApi.deviceDataFetcher.ensureStarted()
  }
}