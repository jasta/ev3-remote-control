package org.devtcg.robotrc.robotview.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment

class RobotViewFragment: Fragment() {
  private lateinit var layoutAgent: RobotLayoutAgent
  private lateinit var dataFetcherAgent: DataFetcherAgent

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?
  ): View? {
    return layoutAgent.onCreateView(inflater, container)
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    dataFetcherAgent = DataFetcherAgent(requireActivity())
    dataFetcherAgent.onViewCreated()
  }
}

