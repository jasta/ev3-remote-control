package org.devtcg.robotrc.networkservice.network

import org.eclipse.californium.core.coap.CoAP
import java.io.IOException

class CoapException : IOException {
  val code: CoAP.ResponseCode

  constructor(code: CoAP.ResponseCode) : super("Unexpected response code: $code") {
    this.code = code
  }
}