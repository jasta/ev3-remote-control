package org.devtcg.robotrc.robotview.ui

import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.get
import org.devtcg.robotrc.robotview.ui.viewmodel.RobotViewViewModel

class RobotViewAgent(
  activity: FragmentActivity
) {
  private val viewModel = ViewModelProvider(activity).get<RobotViewViewModel>()

  fun onViewCreated() {
    // Nothing yet...
  }
}