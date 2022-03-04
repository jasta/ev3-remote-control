package org.devtcg.robotrc.robotselection.ui

import android.util.Log
import androidx.fragment.app.Fragment
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import org.devtcg.robotrc.databinding.RobotChooserFragmentBinding
import org.devtcg.robotrc.robotdata.network.DiscoveredPeer
import org.devtcg.robotrc.robotdata.network.RobotDiscovery
import org.devtcg.robotrc.robotselection.model.RobotTarget

class RobotChooserAgent(
  private val fragment: BottomSheetDialogFragment,
  private val binding: RobotChooserFragmentBinding,
  private val robotDiscovery: RobotDiscovery,
  private val selectorTarget: MutableLiveData<RobotTarget?>
) {
  companion object {
    private const val TAG = "RobotChoiceAgent"
  }

  private val adapter = DiscoveredPeerAdapter(fragment, selectorTarget)

  fun onViewCreated() {
    binding.availableRobotTargets.layoutManager = LinearLayoutManager(fragment.context)
    binding.availableRobotTargets.adapter = adapter
    robotDiscovery.peers.observe(fragment) {
      adapter.submitList(it)
    }
  }

  fun onStart() {
    Log.i(TAG, "Discovery active...")
    robotDiscovery.openDiscovery()
  }

  fun onStop() {
    robotDiscovery.closeDiscovery()
    Log.i(TAG, "Discovery stopped")
  }
}