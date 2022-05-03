package org.devtcg.robotrc.networkservice.network

import org.eclipse.californium.core.coap.ClientObserveRelation

class CancelTrigger internal constructor(private val handle: ClientObserveRelation) {
  fun cancel() {
    handle.proactiveCancel()
  }
}