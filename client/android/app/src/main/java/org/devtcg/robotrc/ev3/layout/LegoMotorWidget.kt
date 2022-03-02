package org.devtcg.robotrc.ev3.layout

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import org.devtcg.robotrc.databinding.Ev3MotorBinding
import org.devtcg.robotrc.robotdata.api.AttributeSpec
import org.devtcg.robotrc.robotdata.api.DeviceModelApi
import org.devtcg.robotrc.robotdata.model.AttributeValueLocal
import org.devtcg.robotrc.robotdata.model.AttributeValueSource
import org.devtcg.robotrc.robotdata.model.DeviceAttributesSnapshot

class LegoMotorWidget: DeviceWidget {
  private lateinit var binding: Ev3MotorBinding

  private lateinit var model: DeviceModelApi

  override val driverLabel: String
  get() {
    return when (model.intrinsics.driver) {
      "lego-ev3-m-motor" -> "motor (M)"
      "lego-ev3-l-motor" -> "motor (L)"
      else -> "motor"
    }
  }

  override fun onDeviceModelUpdated(model: DeviceModelApi) {
    model.updateAttributeSpec(listOf(
      AttributeSpec("position"),
      AttributeSpec("duty_cycle"),
    ))
    this.model = model
  }

  override fun onBindView(view: View, snapshot: DeviceAttributesSnapshot) {
    val position = snapshot.lookupLocalOrRemote("position")?.asNumber()
    val duty_cycle = snapshot.lookupLocalOrRemote("duty_cycle_sp", "duty_cycle")?.asNumber()

    if (position != null) {
      binding.motorPosition.text = (position.toInt() % 360 * 4).toString()
    } else {
      binding.motorPosition.text = "???"
    }

    if (duty_cycle != null) {
      binding.motorDutyCycleLabel.text = "$duty_cycle%"
      binding.motorDutyCycleSlider.value = duty_cycle.toFloat()
    } else {
      binding.motorDutyCycleLabel.text = "???"
      binding.motorDutyCycleSlider.value = 0F
    }
  }

  private fun stepDutyCycle(step: Int) {
    val bindingValue = binding.motorDutyCycleSlider.value
    val clamped = binding.motorDutyCycleSlider.clampValue(bindingValue + step)
    setRemoteDutyCycle(clamped.toInt())
  }

  private fun setRemoteDutyCycle(newValue: Int) {
    model.sendAttributeWrites(
      mapOf(
        "duty_cycle_sp" to AttributeValueLocal("int8", newValue.toString()),
        "command" to AttributeValueLocal("string", "run-direct")))
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
    binding.motorReset.setOnClickListener { motorReset() }
    binding.dutyUp.setOnClickListener { stepDutyCycle(10) }
    binding.dutyDown.setOnClickListener { stepDutyCycle(-10) }
    binding.motorDutyCycleSlider.also {
      it.setValueRange(-100F, 100F, 0F)
      it.touchEnabled = true
      it.sticky = true
      it.addChangeListener { _, value, _ ->
        setRemoteDutyCycle(value.toInt())
      }
    }
    return binding.root
  }
}