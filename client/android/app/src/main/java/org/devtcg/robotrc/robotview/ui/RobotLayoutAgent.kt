package org.devtcg.robotrc.robotview.ui

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Transformations
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.get
import org.devtcg.robotrc.ev3.layout.Ev3RawRobotLayout
import org.devtcg.robotrc.robotdata.model.DeviceAttributesSnapshot
import org.w3c.dom.Attr

class RobotLayoutAgent(private val activity: FragmentActivity) {
  companion object {
    private const val TAG = "RobotLayoutAgent"
  }

  private val viewModel = ViewModelProvider(activity).get<RobotDataViewModel>()
  private val layout = Ev3RawRobotLayout()

  private val attributesCache = HashMap<String, DeviceAttributesSnapshot>()

  fun onCreateView(inflater: LayoutInflater, container: ViewGroup?): View {
    // TODO: Eventually we want to support other hardware "layouts" for controls
    check(viewModel.robotApi.target.supportedLayouts.contains("ev3"))
    return layout.onCreateView(inflater, container)
  }

  fun onViewCreated() {
    Transformations.distinctUntilChanged(viewModel.robotApi.connectivity).observe(activity) {
      Log.i(TAG, "robot connectivity state change: $it")
    }
    viewModel.robotApi.allDevices.observe(activity) {
      layout.onDevicesUpdated(it)
    }
    viewModel.robotApi.relevantAttributes.observe(activity) {
      maybeEmitOnDeviceAttributesUpdated(it)
    }
  }

  fun onPause() {
    viewModel.robotApi.deviceDataFetcher.ensureStopped()
  }

  fun onResume() {
    viewModel.robotApi.deviceDataFetcher.ensureStarted()
  }

  private fun maybeEmitOnDeviceAttributesUpdated(allAttributes: Map<String, DeviceAttributesSnapshot>) {
    for ((deviceAddress, attributes) in allAttributes) {
      if (attributes.hasFetchedRemote()) {
        val cachedAttributes = attributesCache.put(deviceAddress, attributes)
        if (cachedAttributes != attributes) {
          layout.onDeviceAttributesUpdated(deviceAddress, attributes)
        }
      }
    }
    attributesCache.keys.retainAll { allAttributes.containsKey(it) }
  }
}