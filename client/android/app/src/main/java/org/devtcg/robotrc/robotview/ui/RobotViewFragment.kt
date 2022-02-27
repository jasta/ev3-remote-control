package org.devtcg.robotrc.robotview.ui

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment

class RobotViewFragment: Fragment() {
  private lateinit var agent: RobotViewAgent

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    agent = RobotViewAgent(requireActivity())
    agent.onViewCreated()
  }
}