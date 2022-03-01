package org.devtcg.robotrc.ev3.layout

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import org.devtcg.robotrc.databinding.Ev3PortBinding
import org.devtcg.robotrc.databinding.Ev3RawRobotLayoutBinding
import org.devtcg.robotrc.robotdata.model.DeviceAttributesSnapshot
import org.devtcg.robotrc.robotdata.api.DeviceModelApi
import org.devtcg.robotrc.robotdata.model.DeviceType
import org.devtcg.robotrc.robotview.api.RobotLayout

class Ev3RawRobotLayout: RobotLayout {
  companion object {
    private const val TAG = "Ev3RawRobotLayout"
  }

  private lateinit var rootBinding: Ev3RawRobotLayoutBinding

  private val deviceViewHolders = mutableMapOf<String, DeviceViewHolder>()

  override fun onDevicesUpdated(devices: List<DeviceModelApi>) {
    val devicesCopy = devices.toMutableList()

    for (deviceAddress in rootBinding.ev3RawRobotParent.getKnownAddresses()) {
      val actualDevice = devices.find { it.intrinsics.address == deviceAddress }
      if (actualDevice != null) {
        devicesCopy.remove(actualDevice)
      }
      val holder = deviceViewHolders.getOrPut(deviceAddress) {
        DeviceViewHolder(
          deviceAddress,
          rootBinding.ev3RawRobotParent.getPortBinding(deviceAddress)!!)
      }
      createOrRemoveWidgetAsNecessary(actualDevice, holder)
    }

    for (unmatchedDevice in devicesCopy) {
      Log.w(TAG, "Unsupported device address: ${unmatchedDevice.intrinsics.address}")
    }
  }

  override fun onAttributesUpdated(allAttributes: Map<String, DeviceAttributesSnapshot>) {
    for ((deviceAddress, attributes) in allAttributes) {
      deviceViewHolders[deviceAddress]?.let { holder ->
        holder.widget?.onBindView(holder.view!!, attributes)
      }
    }
  }

  private fun createOrRemoveWidgetAsNecessary(model: DeviceModelApi?, viewHolder: DeviceViewHolder) {
    if (model == null) {
      viewHolder.clearBinding()
      return
    }

    val targetWidgetClass = when (model.intrinsics.type) {
      DeviceType.SENSOR -> {
        when (model.intrinsics.driver) {
          "lego-ev3-ir" -> ProximitySensorWidget::class.java
          else -> DefaultSensorWidget::class.java
        }
      }
      DeviceType.ACTUATOR -> LegoMotorWidget::class.java
    }
    if (viewHolder.widget?.javaClass != targetWidgetClass) {
      val context = rootBinding.root.context

      viewHolder.model?.updateAttributeSpec(emptyList())
      viewHolder.clearBinding()

      if (targetWidgetClass == null) {
        Log.w(TAG, "Unsupported device type: ${model.intrinsics.type}")
        return
      } else {
        val widget = targetWidgetClass.newInstance()
        val view = widget.onCreateView(LayoutInflater.from(context), viewHolder.portBinding.portView)
        widget.onDeviceModelUpdated(model)
        viewHolder.assignBinding(model, widget, view)
      }
    }
  }

  override fun onCreateView(inflater: LayoutInflater, parent: ViewGroup?): View {
    rootBinding = Ev3RawRobotLayoutBinding.inflate(inflater, parent, false)
    return rootBinding.root
  }

  private inner class DeviceViewHolder(
    val address: String,
    val portBinding: Ev3PortBinding,
    var model: DeviceModelApi? = null,
    var widget: DeviceWidget? = null,
    var view: View? = null,
  ) {
    fun clearBinding() {
      portBinding.portView.removeAllViews()
      portBinding.driverName.text = ""
      model = null
      widget = null
      view = null
    }

    fun assignBinding(model: DeviceModelApi, widget: DeviceWidget, view: View) {
      portBinding.driverName.text = widget.driverLabel
      portBinding.portView.removeAllViews()
      portBinding.portView.addView(view)
      this.model = model
      this.widget = widget
      this.view = view
    }
  }
}
