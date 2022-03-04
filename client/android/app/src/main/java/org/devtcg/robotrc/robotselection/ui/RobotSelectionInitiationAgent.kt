package org.devtcg.robotrc.robotselection.ui

import android.view.View
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.commit
import androidx.lifecycle.LiveData
import org.devtcg.robotrc.R
import org.devtcg.robotrc.robotselection.model.RobotTarget
import org.devtcg.robotrc.robotview.ui.RobotViewFragment

class RobotSelectionInitiationAgent(
  private val activity: AppCompatActivity,
  private val fragmentManager: FragmentManager,
  private val targetButton: Button,
  private val robotSelector: LiveData<RobotTarget?>
) {
  fun onCreate() {
    robotSelector.observe(activity) {
      onTargetChange(it)
    }

    targetButton.setOnClickListener(::targetButtonClicked)
  }

  private fun targetButtonClicked(view: View) {
    RobotChooserFragment().show(fragmentManager, "robot-chooser")
  }

  private fun onTargetChange(selection: RobotTarget?) {
    if (selection != null) {
      targetButton.text = selection.displayName
      fragmentManager.commit {
        replace(R.id.main_container, RobotViewFragment::class.java, null, "robot-view")
      }
    } else {
      targetButton.text = activity.getString(R.string.select_a_robot)
      fragmentManager.findFragmentById(R.id.main_container)?.let {
        fragmentManager.commit { remove(it) }
      }
    }
  }
}