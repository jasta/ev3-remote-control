package org.devtcg.robotrc.mainui

import androidx.fragment.app.FragmentManager
import androidx.lifecycle.LiveData
import org.devtcg.robotrc.robotview.ui.RobotViewFragment
import org.devtcg.robotrc.robotselection.ui.RobotSelectionFragment

class MainUiDeciderAgent(
  private val fragmentManager: FragmentManager,
  private val robotSelector: LiveData<String>
) {
  fun onCreate() {
    if (robotSelector.value != null) {
      fragmentManager.beginTransaction()
        .add(RobotViewFragment(), "devices")
    } else {
      fragmentManager.beginTransaction()
        .add(RobotSelectionFragment(), "robot-selection")
    }
  }
}