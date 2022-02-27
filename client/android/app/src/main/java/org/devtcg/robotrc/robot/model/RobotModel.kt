package org.devtcg.robotrc.robot.model

data class RobotModel(
  val target: RobotTarget,
  val relevantDevices: List<RelevantDevice>,
)