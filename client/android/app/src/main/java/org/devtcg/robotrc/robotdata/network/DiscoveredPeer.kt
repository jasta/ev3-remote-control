package org.devtcg.robotrc.robotdata.network

data class DiscoveredPeer(
  val targetSocketAddr: String,
  val targetName: String,
  val textRecords: Map<String, ByteArray>,
  var discoveryTimeMs: Long = RobotDiscovery.TIME_NOT_SPECIFIED,
  var lostTimeMs: Long = RobotDiscovery.TIME_NOT_SPECIFIED
)