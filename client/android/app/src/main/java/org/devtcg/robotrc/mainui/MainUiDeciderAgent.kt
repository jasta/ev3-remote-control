package org.devtcg.robotrc.mainui

import androidx.fragment.app.FragmentManager
import androidx.fragment.app.commit
import androidx.lifecycle.LiveData
import org.devtcg.robotrc.R
import org.devtcg.robotrc.robotselection.model.RobotTarget
import org.devtcg.robotrc.robotview.ui.RobotViewFragment
import org.devtcg.robotrc.robotselection.ui.RobotSelectionFragment

class MainUiDeciderAgent(
  private val fragmentManager: FragmentManager,
  private val robotSelector: LiveData<RobotTarget>
) {
  fun onCreate() {
    fragmentManager.commit {
      if (robotSelector.value != null) {
        replace(R.id.main_container, RobotViewFragment::class.java, null, "robot-view")
      } else {
        replace(R.id.main_container, RobotSelectionFragment::class.java, null, "robot-selection")
      }
    }
  }
}