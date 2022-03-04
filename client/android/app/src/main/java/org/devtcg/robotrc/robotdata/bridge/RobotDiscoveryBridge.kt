package org.devtcg.robotrc.robotdata.bridge

import org.devtcg.robotrc.appcontext.AppContext
import org.devtcg.robotrc.concurrency.RobotExecutors
import org.devtcg.robotrc.robotdata.network.RobotDiscovery

object RobotDiscoveryBridge {
  val instance = RobotDiscovery(
    AppContext.get(),
    RobotExecutors.newSingleThreadScheduledExecutor("discovery"),
    RobotDiscoveryViewModelBridge.peers)
}
