package org.devtcg.robotrc.networkservice.bridge

import org.devtcg.robotrc.moshi.MoshiBridge
import org.devtcg.robotrc.networkservice.network.RemoteControlService
import org.eclipse.californium.core.config.CoapConfig
import org.eclipse.californium.elements.config.UdpConfig

object RemoteControlServiceFactory {
  init {
    CoapConfig.register()
    UdpConfig.register()
  }

  fun create(robotHost: String): RemoteControlService {
    return RemoteControlService(MoshiBridge.instance, "coap://$robotHost")
  }
}