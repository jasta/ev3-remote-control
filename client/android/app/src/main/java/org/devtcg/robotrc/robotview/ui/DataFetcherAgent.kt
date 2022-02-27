package org.devtcg.robotrc.robotview.ui

import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.get
import org.devtcg.robotrc.robotview.ui.viewmodel.RobotDataViewModel

class DataFetcherAgent(
  activity: FragmentActivity
) {
  private val viewModel = ViewModelProvider(activity).get<RobotDataViewModel>()

  fun onViewCreated() {
    // Nothing yet...
  }
}