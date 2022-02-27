package org.devtcg.robotrc.robotview.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.get
import org.devtcg.robotrc.ev3.widgets.Ev3RawRobotLayout
import org.devtcg.robotrc.robotview.ui.viewmodel.RobotDataViewModel

class RobotLayoutAgent(private val activity: FragmentActivity) {
  private val viewModel = ViewModelProvider(activity).get<RobotDataViewModel>()
  private val layout = Ev3RawRobotLayout()

  fun onCreateView(inflater: LayoutInflater, container: ViewGroup?): View {
    return layout.onCreateView(inflater, container)
  }

  fun onViewCreated() {
    viewModel.robotApi.allDevices.observe(activity) {
      it.relevantDevices
    }
  }
}