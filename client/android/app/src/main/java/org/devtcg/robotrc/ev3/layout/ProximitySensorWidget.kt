package org.devtcg.robotrc.ev3.layout

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import org.devtcg.robotrc.databinding.Ev3ProximitySensorBinding
import org.devtcg.robotrc.robotdata.api.AttributeSpec
import org.devtcg.robotrc.robotdata.api.DeviceModelApi
import org.devtcg.robotrc.robotdata.model.DeviceAttributesSnapshot

class ProximitySensorWidget: DeviceWidget {
  private lateinit var binding: Ev3ProximitySensorBinding

  override val driverLabel = "lego IR"

  override fun onDeviceModelUpdated(model: DeviceModelApi) {
    model.updateAttributeSpec(listOf(
      AttributeSpec("mode"),
      AttributeSpec("value0"),
    ))
  }

  override fun onBindView(view: View, snapshot: DeviceAttributesSnapshot) {
    val mode = snapshot.lookupLocalOrRemote("mode")?.asString()
    val percent = snapshot.lookupLocalOrRemote("value0")?.asNumber()
    if (mode == "IR-PROX" && percent != null) {
      binding.proximitySlider.value = percent.toFloat()
      binding.proximityValue.text = percent.toInt().toString() + "%"
    } else {
      binding.proximitySlider.visibility = View.GONE
    }
  }

  override fun onCreateView(inflater: LayoutInflater, parent: ViewGroup?): View {
    binding = Ev3ProximitySensorBinding.inflate(inflater, parent, false)
    binding.proximitySlider.setValueRange(0F, 100F, 0F)
    binding.proximitySlider.touchEnabled = false
    return binding.root
  }
}