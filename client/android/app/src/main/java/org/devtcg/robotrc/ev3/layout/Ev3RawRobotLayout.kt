package org.devtcg.robotrc.ev3.layout

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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

    for (slotAddress in rootBinding.ev3RawRobotParent.getPortAddresses()) {
      val actualDevice = devices.find { matchAddress(it.intrinsics.address, slotAddress) }
      if (actualDevice != null) {
        devicesCopy.remove(actualDevice)
      }
      val holder = deviceViewHolders.getOrPut(slotAddress) {
        DeviceViewHolder(
          slotAddress,
          rootBinding.ev3RawRobotParent.getPortBinding(slotAddress)!!)
      }
      createOrRemoveWidgetAsNecessary(actualDevice, holder)
    }

    for (unmatchedDevice in devicesCopy) {
      Log.w(TAG, "Unsupported device address: ${unmatchedDevice.intrinsics.address}")
    }
  }

  private fun matchAddress(remoteAddress: String, localPortAddress: String): Boolean {
    // We use starts with so we can match the local address slot "ev3-ports:in1" to a remote
    // generic i2c device like "ev3-ports:in1:i2c12".
    return remoteAddress.startsWith(localPortAddress)
  }

  override fun onDeviceAttributesUpdated(deviceAddress: String, attributes: DeviceAttributesSnapshot) {
    deviceViewHolders.values.find { matchAddress(deviceAddress, it.address) }?.let { holder ->
      holder.widget?.onBindView(holder.view!!, attributes)
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
          "lego-ev3-touch" -> TouchSensorWidget::class.java
          "lego-ev3-color" -> ColorSensorWidget::class.java
          else -> DefaultSensorWidget::class.java
        }
      }
      DeviceType.ACTUATOR -> LegoMotorWidget::class.java
    }

    viewHolder.model = model

    val currentWidget = viewHolder.widget
    val activeWidget = if (currentWidget?.javaClass != targetWidgetClass) {
      val context = rootBinding.root.context

      viewHolder.model?.updateAttributeSpec(emptyList())
      viewHolder.clearBinding()

      val newWidget = targetWidgetClass.newInstance()
      val view = newWidget.onCreateView(LayoutInflater.from(context), viewHolder.portBinding.portView)
      viewHolder.assignBinding(model, newWidget, view)
      newWidget
    } else {
      currentWidget
    }

    activeWidget?.onDeviceModelUpdated(model)
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
