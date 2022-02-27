package org.devtcg.robotrc.robot.model

data class RobotTarget(
  val host: String,
  val hardwarePlatform: String,
  val configuration: RobotConfiguration,
)