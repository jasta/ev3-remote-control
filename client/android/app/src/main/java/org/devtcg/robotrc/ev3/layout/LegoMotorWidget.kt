package org.devtcg.robotrc.ev3.layout

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import org.devtcg.robotrc.databinding.Ev3DefaultSensorBinding
import org.devtcg.robotrc.robotdata.api.AttributeSpec
import org.devtcg.robotrc.robotdata.api.DeviceModelApi
import org.devtcg.robotrc.robotdata.model.DeviceAttributesSnapshot

class LegoMotorWidget: DeviceWidget {
  private lateinit var binding: Ev3DefaultSensorBinding

  override fun onDeviceModelUpdated(model: DeviceModelApi) {
    model.updateAttributeSpec(listOf(
      AttributeSpec("position"),
      AttributeSpec("duty_cycle"),
    ))
  }

  override fun onBindView(view: View, snapshot: DeviceAttributesSnapshot) {
    val position = snapshot.attributeValues["position"]?.asNumber()
    val duty_cycle = snapshot.attributeValues["duty_cycle"]?.asNumber()
    binding.root.text = "$position @ $duty_cycle%"
  }

  override fun onCreateView(
    inflater: LayoutInflater,
    parent: ViewGroup?,
  ): View {
    binding = Ev3DefaultSensorBinding.inflate(inflater, parent, false)
    return binding.root
  }
}