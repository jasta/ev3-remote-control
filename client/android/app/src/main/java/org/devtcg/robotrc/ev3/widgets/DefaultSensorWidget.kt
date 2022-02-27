package org.devtcg.robotrc.ev3.widgets

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import org.devtcg.robotrc.databinding.Ev3DefaultSensorBinding
import org.devtcg.robotrc.robotdata.api.AttributeSpec
import org.devtcg.robotrc.robotlayout.api.DeviceAttributesSnapshot
import org.devtcg.robotrc.robotdata.api.DeviceModelApi

class DefaultSensorWidget: DeviceWidget {
  private lateinit var binding: Ev3DefaultSensorBinding

  override fun onBindView(view: View, snapshot: DeviceAttributesSnapshot) {
    val mode = snapshot.attributeValues["mode"]
    val value0 = snapshot.attributeValues["value0"]
    binding.root.text = "Mode: $mode\nValue0: $value0"
  }

  override fun onCreateView(
    inflater: LayoutInflater,
    parent: ViewGroup?,
    model: DeviceModelApi
  ): View {
    binding = Ev3DefaultSensorBinding.inflate(inflater, parent, true)
    model.updateAttributeSpec(listOf(
      AttributeSpec("mode"),
      AttributeSpec("value0"),
    ))
    return binding.root
  }
}