package org.devtcg.robotrc.networkservice.network

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.SystemClock
import android.util.Log
import androidx.annotation.GuardedBy
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

class RemoteControlDiscovery(
  context: Context,
  private val discoveryExecutor: ScheduledExecutorService,
) {
  companion object {
    private const val TAG = "RemoteControlDiscovery"

    private const val DISCOVERY_FAILED_RETRY_DELAY_MS: Long = 10_000

    private val MAX_DISCOVERED_PEER_TTL: Long = TimeUnit.MINUTES.toMillis(10)
    private val MAX_LOST_PEER_TTL: Long = TimeUnit.MINUTES.toMillis(1)

    const val TIME_NOT_SPECIFIED: Long = -1
  }

  private val nsdManager by lazy {
    context.getSystemService(Context.NSD_SERVICE) as NsdManager
  }

  @GuardedBy("this")
  private var isDiscovering = false

  @GuardedBy("this")
  private val peersByName = mutableMapOf<String, DiscoveredPeer>()

  private val listeners = CopyOnWriteArrayList<DiscoveryListener>()

  fun addDiscoveryListener(listener: DiscoveryListener) {
    listeners.add(listener)
  }

  fun removeDiscoveryListener(listener: DiscoveryListener) {
    listeners.remove(listener)
  }

  @Synchronized
  fun openDiscovery() {
    if (!isDiscovering) {
      scheduleKickDiscovery(0)
    }
  }

  @Synchronized
  fun closeDiscovery() {
    if (isDiscovering) {
      isDiscovering = false
      nsdManager.stopServiceDiscovery(discoveryListener)
    }
  }

  private fun pruneAndDispatchPeers() {
    val peersList = synchronized (this) {
      prunePeersLocked()
      peersByName.values.toList()
    }
    for (listener in listeners) {
      listener.onPeersChanged(peersList)
    }
  }

  @GuardedBy("this")
  private fun prunePeersLocked() {
    val now = realtimeNow()
    val peersIter = peersByName.values.iterator()
    while (peersIter.hasNext()) {
      val peer = peersIter.next()
      val elapsedSinceDiscovery = now - peer.discoveryTimeMs
      val expiredDiscovery = (elapsedSinceDiscovery > MAX_DISCOVERED_PEER_TTL)
      val expiredLost = if (peer.lostTimeMs != TIME_NOT_SPECIFIED) {
        val elapsedSinceLost = now - peer.lostTimeMs
        (elapsedSinceLost > MAX_LOST_PEER_TTL)
      } else {
        false
      }

      if (expiredDiscovery || expiredLost) {
        peersIter.remove()
      }
    }
  }

  private fun scheduleKickDiscovery(delayMs: Long) {
    discoveryExecutor.schedule(::kickDiscovery, delayMs, TimeUnit.MILLISECONDS)
  }

  private fun kickDiscovery() {
    nsdManager.discoverServices("_coap._udp", NsdManager.PROTOCOL_DNS_SD, discoveryListener)
  }

  private val discoveryListener = object: NsdManager.DiscoveryListener {
    override fun onDiscoveryStarted(serviceType: String?) {
      Log.d(TAG, "onDiscoveryStarted: serviceType=$serviceType")
    }

    override fun onDiscoveryStopped(serviceType: String?) {
      Log.d(TAG, "onDiscoveryStopped: serviceType=$serviceType")
    }

    override fun onStartDiscoveryFailed(serviceType: String?, errorCode: Int) {
      Log.e(TAG, "onStartDiscoveryFailed: serviceType=$serviceType, errorCode=$errorCode")
      scheduleKickDiscovery(DISCOVERY_FAILED_RETRY_DELAY_MS)
    }

    override fun onStopDiscoveryFailed(serviceType: String?, errorCode: Int) {
      Log.w(TAG, "onStopDiscoveryFailed: serviceType=$serviceType, errorCode=$errorCode")
    }

    override fun onServiceFound(serviceInfo: NsdServiceInfo) {
      nsdManager.resolveService(serviceInfo, object: NsdManager.ResolveListener {
        override fun onResolveFailed(serviceInfo: NsdServiceInfo?, errorCode: Int) {
          Log.w(TAG, "onResolveFailed: serviceInfo=$serviceInfo, errorCode=$errorCode")
        }

        override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
          Log.d(TAG, "onServiceResolved: serviceInfo=$serviceInfo")

          if (!isValidRobotTarget(serviceInfo)) {
            Log.d(TAG, "...not a match")
          }

          val peer = peersByName.getOrPut(serviceInfo.serviceName) {
            DiscoveredPeer(
              serviceInfo.host?.hostAddress + ":" + serviceInfo.port,
              serviceInfo.serviceName,
              serviceInfo.attributes)
          }

          peer.discoveryTimeMs = realtimeNow()
          peer.lostTimeMs = TIME_NOT_SPECIFIED

          pruneAndDispatchPeers()
        }
      })
    }

    override fun onServiceLost(serviceInfo: NsdServiceInfo) {
      Log.i(TAG, "onServiceLost: serviceInfo=$serviceInfo")
      peersByName[serviceInfo.serviceName]?.lostTimeMs = realtimeNow()

      pruneAndDispatchPeers()
    }
  }

  private fun realtimeNow() = SystemClock.elapsedRealtime()

  private fun isValidRobotTarget(serviceInfo: NsdServiceInfo): Boolean {
    if (!serviceInfo.attributes["rt"].contentEquals("\"devices\"".toByteArray())) {
      return false
    }
    return true
  }

  data class DiscoveredPeer(
    val targetSocketAddr: String,
    val targetName: String,
    val textRecords: Map<String, ByteArray>,
    var discoveryTimeMs: Long = TIME_NOT_SPECIFIED,
    var lostTimeMs: Long = TIME_NOT_SPECIFIED)

  fun interface DiscoveryListener {
    fun onPeersChanged(peers: List<DiscoveredPeer>)
  }
}
