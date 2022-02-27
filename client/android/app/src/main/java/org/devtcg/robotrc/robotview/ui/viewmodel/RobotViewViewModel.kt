package org.devtcg.robotrc.robotview.ui.viewmodel

import androidx.lifecycle.ViewModel

class RobotViewViewModel: ViewModel() {
  init {
    DeviceFetcherBridge.instance.refresh()
  }
}