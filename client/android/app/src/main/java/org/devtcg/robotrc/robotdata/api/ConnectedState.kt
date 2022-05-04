package org.devtcg.robotrc.robotdata.api

data class ConnectivityState(
  /**
   * Indicates that things are working as they should (e.g. CONNECTING when starting up,
   * and CONNECTED soon after).
   */
  val nominal: Boolean,

  /**
   * "Raw" connected state, showing what the underlying system is doing about the connection.
   */
  val raw_state: ConnectedState,
)

enum class ConnectedState {
  /**
   * Actively connected with recent messages exchanged to confirm the peer is active.
   */
  CONNECTED,

  /**
   * Disconnected but actively trying to reconnect.
   */
  CONNECTING,

  /**
   * No response after some time from recent requests, presumed shut off, out of range, or
   * malfunctioning.  Not trying to reconnect (but will soon).
   */
  DISCONNECTED_AND_IDLE,
}