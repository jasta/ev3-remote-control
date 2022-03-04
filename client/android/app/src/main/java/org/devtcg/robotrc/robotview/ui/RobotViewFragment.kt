package org.devtcg.robotrc.robotview.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import org.devtcg.robotrc.concurrency.RobotExecutors
import org.devtcg.robotrc.networkservice.network.RemoteControlDiscovery

class RobotViewFragment: Fragment() {
  private lateinit var layoutAgent: RobotLayoutAgent

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?
  ): View? {
    val discovery = RemoteControlDiscovery(inflater.context, RobotExecutors.newSingleThreadScheduledExecutor("TODOwhatever"))
    discovery.addDiscoveryListener {
      println("peers: $it")
    }
    discovery.openDiscovery()

    layoutAgent = RobotLayoutAgent(requireActivity())
    return layoutAgent.onCreateView(inflater, container)
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    layoutAgent.onViewCreated()
  }

  override fun onPause() {
    super.onPause()
    layoutAgent.onPause()
  }

  override fun onResume() {
    super.onResume()
    layoutAgent.onResume()
  }
}

