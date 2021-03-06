package org.devtcg.robotrc.robotview.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment

class RobotViewFragment: Fragment() {
  private lateinit var layoutAgent: RobotLayoutAgent

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?
  ): View? {
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

