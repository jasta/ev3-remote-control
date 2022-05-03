package org.devtcg.robotrc.networkservice.network

import com.squareup.moshi.Moshi
import com.squareup.moshi.adapter
import org.devtcg.robotrc.networkservice.model.Attribute
import org.devtcg.robotrc.networkservice.model.AttributeValue
import org.devtcg.robotrc.networkservice.model.Device
import org.eclipse.californium.core.CoapClient
import org.eclipse.californium.core.CoapHandler
import org.eclipse.californium.core.CoapResponse
import org.eclipse.californium.core.coap.CoAP
import org.eclipse.californium.core.coap.MediaTypeRegistry
import java.io.IOException

class RemoteControlService(
  private val moshi: Moshi,
  private val baseUrl: String,
) {
  fun listDevices() =
    execGet<List<Device>>("devices") ?: throw noNull()

  fun observeDevices(handler: ObserveHandler<List<Device>>) =
    execObserve("devices", handler)

  fun listDevices(driver: String) =
    execGet<List<Device>>("devices/$driver") ?: throw noNull()

  fun lookupDevice(address: String) =
    execGet<Device>("device/$address")

  fun getAttributes(address: String) =
    execGet<List<Attribute>>("device/$address/attributes")

  fun getAttributes(address: String, names: List<String>): List<AttributeValue> {
    return if (names.size == 1) {
      listOf(getAttribute(address, names[0]) ?: throw noNull())
    } else {
      execGet<List<AttributeValue>>("device/$address/attributes/" + names.joinToString(separator = ","))
        ?: throw noNull()
    }
  }

  fun getAttribute(address: String, name: String) =
    execGet<AttributeValue>("device/$address/attributes/$name")

  fun observeAttributes(
      address: String,
      names: List<String>,
      handler: ObserveHandler<List<AttributeValue>>) =
    execObserve("device/$address/attributes/" + names.joinToString(separator = ","), handler)

  fun putAttributes(address: String, values: List<AttributeValue>) =
    execPut("device/$address/attributes", values)

  @OptIn(ExperimentalStdlibApi::class)
  private inline fun <reified T> execGet(stem: String): T? {
    val response = CoapClient("$baseUrl/$stem").get()
    return when (val code = response?.code) {
      CoAP.ResponseCode.CONTENT -> moshi.adapter<T>().fromJson(response.responseText)
      CoAP.ResponseCode.NOT_FOUND -> null
      null -> throw IOException("Missing response")
      else -> throw CoapException(code)
    }
  }

  @OptIn(ExperimentalStdlibApi::class)
  private inline fun <reified T> execPut(stem: String, payload: T) {
    val payloadStr = moshi.adapter<T>().toJson(payload) ?: throw noNull()
    val response = CoapClient("$baseUrl/$stem").put(payloadStr, MediaTypeRegistry.APPLICATION_JSON)
    return when (val code = response?.code) {
      CoAP.ResponseCode.CHANGED -> {}
      null -> throw IOException("Missing response")
      else -> throw CoapException(code)
    }
  }

  @OptIn(ExperimentalStdlibApi::class)
  private inline fun <reified T> execObserve(stem: String, handler: ObserveHandler<T>): CancelTrigger {
    val handle = CoapClient("$baseUrl/$stem").observe(object: CoapHandler {
      override fun onLoad(response: CoapResponse) {
        val result = try {
          val obj = when (val code = response.code) {
            CoAP.ResponseCode.CONTENT -> moshi.adapter<T>().fromJson(response.responseText)
              ?: throw noNull()
            else -> throw CoapException(code)
          }
          Result.success(obj)
        } catch (e: IOException) {
          Result.failure(e)
        }
        handler.onResult(result)
      }

      override fun onError() {
        handler.onResult(Result.failure(IOException("generic observe error")))
      }
    })
    return CancelTrigger(handle)
  }

  private fun noNull(): IOException {
    return IOException("missing response object")
  }
}

