package org.devtcg.robotrc.mainui

import androidx.fragment.app.FragmentManager
import androidx.lifecycle.LiveData
import org.devtcg.robotrc.robotdata.model.RobotTarget
import org.devtcg.robotrc.robotview.ui.RobotViewFragment
import org.devtcg.robotrc.robotselection.ui.RobotSelectionFragment

class MainUiDeciderAgent(
  private val fragmentManager: FragmentManager,
  private val robotSelector: LiveData<RobotTarget>
) {
  fun onCreate() {
    if (robotSelector.value != null) {
      fragmentManager.beginTransaction()
        .add(RobotViewFragment(), "robot-view")
    } else {
      fragmentManager.beginTransaction()
        .add(RobotSelectionFragment(), "robot-selection")
    }
  }
}