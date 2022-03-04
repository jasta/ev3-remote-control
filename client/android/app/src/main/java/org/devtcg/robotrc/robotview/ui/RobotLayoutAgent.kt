package org.devtcg.robotrc.robotview.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.get
import org.devtcg.robotrc.ev3.layout.Ev3RawRobotLayout

class RobotLayoutAgent(private val activity: FragmentActivity) {
  private val viewModel = ViewModelProvider(activity).get<RobotDataViewModel>()
  private val layout = Ev3RawRobotLayout()

  fun onCreateView(inflater: LayoutInflater, container: ViewGroup?): View {
    // TODO: Eventually we want to support other hardware "layouts" for controls
    return layout.onCreateView(inflater, container)
  }

  fun onViewCreated() {
    viewModel.robotApi.allDevices.observe(activity) {
      layout.onDevicesUpdated(it)
    }
    viewModel.robotApi.relevantAttributes.observe(activity) {
      layout.onAttributesUpdated(it)
    }
  }

  fun onPause() {
    viewModel.robotApi.deviceDataFetcher.ensureStopped()
  }

  fun onResume() {
    viewModel.robotApi.deviceDataFetcher.ensureStarted()
  }
}