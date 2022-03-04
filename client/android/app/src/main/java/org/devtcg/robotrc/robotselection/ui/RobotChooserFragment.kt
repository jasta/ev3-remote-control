package org.devtcg.robotrc.robotselection.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import org.devtcg.robotrc.databinding.RobotChooserFragmentBinding
import org.devtcg.robotrc.robotdata.bridge.RobotDiscoveryBridge
import org.devtcg.robotrc.robotdata.bridge.RobotDiscoveryViewModelBridge
import org.devtcg.robotrc.robotdata.bridge.RobotSelectorBridge

class RobotChooserFragment: BottomSheetDialogFragment() {
  private lateinit var chooserAgent: RobotChooserAgent

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?
  ): View {
    val binding = RobotChooserFragmentBinding.inflate(inflater, container, false)
    chooserAgent = RobotChooserAgent(
      this,
      binding,
      RobotDiscoveryBridge.instance,
      RobotSelectorBridge.instance)
    chooserAgent.onViewCreated()
    return binding.root
  }

  override fun onStart() {
    super.onStart()
    chooserAgent.onStart()
  }

  override fun onStop() {
    super.onStop()
    chooserAgent.onStop()
  }
}