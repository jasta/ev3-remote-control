package org.devtcg.robotrc.ev3.layout

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import org.devtcg.robotrc.databinding.Ev3MotorBinding
import org.devtcg.robotrc.robotdata.api.AttributeSpec
import org.devtcg.robotrc.robotdata.api.DeviceModelApi
import org.devtcg.robotrc.robotdata.model.AttributeValueLocal
import org.devtcg.robotrc.robotdata.model.DeviceAttributesSnapshot
import kotlin.math.max
import kotlin.math.min

class LegoMotorWidget: DeviceWidget {
  private lateinit var binding: Ev3MotorBinding

  private lateinit var model: DeviceModelApi

  override fun onDeviceModelUpdated(model: DeviceModelApi) {
    model.updateAttributeSpec(listOf(
      AttributeSpec("position"),
      AttributeSpec("duty_cycle"),
    ))
    this.model = model
  }

  override fun onBindView(view: View, snapshot: DeviceAttributesSnapshot) {
    val position = snapshot.attributeValues["position"]?.asNumber()
    val duty_cycle = snapshot.attributeValues["duty_cycle"]?.asNumber()
    binding.motorState.text = "$position @ $duty_cycle%"

    if (duty_cycle != null) {
      binding.motorDutyCycle.value = duty_cycle.toFloat()
    }

    binding.motorReset.setOnClickListener { motorReset() }
    binding.dutyUp.setOnClickListener { dutyChange(10) }
    binding.dutyDown.setOnClickListener { dutyChange(-10) }
  }

  private fun dutyChange(step: Int) {
    val bindingValue = binding.motorDutyCycle.value.toInt()
    val newValue = max(min(bindingValue + step, 100), -100)
    if (newValue != bindingValue) {
      model.sendAttributeWrites(
        mapOf(
          "duty_cycle_sp" to AttributeValueLocal("int8", newValue.toString()),
          "command" to AttributeValueLocal("string", "run-direct")))
    }
  }

  private fun motorReset() {
    model.sendAttributeWrites(
      mapOf("command" to AttributeValueLocal("string", "reset")))
  }

  override fun onCreateView(
    inflater: LayoutInflater,
    parent: ViewGroup?,
  ): View {
    binding = Ev3MotorBinding.inflate(inflater, parent, false)
    return binding.root
  }
}