package org.devtcg.robotrc.networkservice.network

import com.squareup.moshi.Moshi
import com.squareup.moshi.adapter
import org.devtcg.robotrc.networkservice.model.Attribute
import org.devtcg.robotrc.networkservice.model.AttributeValue
import org.devtcg.robotrc.networkservice.model.Device
import org.eclipse.californium.core.CoapClient
import org.eclipse.californium.core.coap.CoAP
import org.eclipse.californium.core.coap.MediaTypeRegistry
import org.w3c.dom.Attr
import java.io.IOException

class RemoteControlService(
  private val moshi: Moshi,
  private val baseUrl: String,
) {
  fun listDevices() =
    execGet<List<Device>>("devices") ?: throw noNull()

  fun listDevices(driver: String) =
    execGet<List<Device>>("devices/$driver") ?: throw noNull()

  fun lookupDevice(address: String) =
    execGet<Device>("device/$address")

  fun getAttributes(address: String) =
    execGet<List<Attribute>>("device/$address/attributes")

  fun getAttributes(address: String, names: List<String>) =
    execGet<List<AttributeValue>>("device/$address/attributes/" + names.joinToString(separator = ",")) ?: throw noNull()

  fun getAttribute(address: String, name: String) =
    execGet<AttributeValue>("device/$address/attributes/$name")

  fun putAttributes(address: String, values: List<AttributeValue>) =
    execPut("device/$address/attributes", values)

  @OptIn(ExperimentalStdlibApi::class)
  private inline fun <reified T> execGet(stem: String): T? {
    val response = CoapClient("$baseUrl/$stem").get()
    return when (response.code) {
      CoAP.ResponseCode.CONTENT -> moshi.adapter<T>().fromJson(response.responseText)
      CoAP.ResponseCode.NOT_FOUND -> null
      else -> throw IOException("unexpected CoAP response: $response")
    }
  }

  @OptIn(ExperimentalStdlibApi::class)
  private inline fun <reified T> execPut(stem: String, payload: T) {
    val payloadStr = moshi.adapter<T>().toJson(payload) ?: throw noNull()
    val response = CoapClient("$baseUrl/$stem").put(payloadStr, MediaTypeRegistry.APPLICATION_JSON)
    return when (response.code) {
      CoAP.ResponseCode.CHANGED -> {}
      else -> throw IOException("unexpected CoAP response: $response")
    }
  }

  private fun noNull(): IOException {
    return IOException("missing response object")
  }
}