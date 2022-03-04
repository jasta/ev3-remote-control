package org.devtcg.robotrc.robotdata.bridge

import androidx.lifecycle.MutableLiveData
import org.devtcg.robotrc.robotdata.network.DiscoveredPeer

object RobotDiscoveryViewModelBridge {
  val peers = MutableLiveData<List<DiscoveredPeer>>(emptyList())
}