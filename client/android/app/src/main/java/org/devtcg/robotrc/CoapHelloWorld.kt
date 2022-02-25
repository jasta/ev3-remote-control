package org.devtcg.robotrc

import org.eclipse.californium.core.CoapClient
import org.eclipse.californium.core.config.CoapConfig
import org.eclipse.californium.elements.config.UdpConfig

class CoapHelloWorld {
  init {
    CoapConfig.register()
    UdpConfig.register()
  }

  fun hello() {
    val client = CoapClient("coap://localhost/devices")
    val response = client.get()
    println("...=$response")
    println("code=${response.code}")
    println("options=${response.options}")
    println("payload=${response.responseText}")
  }
}