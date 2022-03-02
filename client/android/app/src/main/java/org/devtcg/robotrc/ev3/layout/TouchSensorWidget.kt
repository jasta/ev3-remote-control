package org.devtcg.robotrc.ev3.layout

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import org.devtcg.robotrc.databinding.Ev3TouchSensorBinding
import org.devtcg.robotrc.robotdata.api.AttributeSpec
import org.devtcg.robotrc.robotdata.api.DeviceModelApi
import org.devtcg.robotrc.robotdata.model.DeviceAttributesSnapshot

class TouchSensorWidget: DeviceWidget {
  private lateinit var binding: Ev3TouchSensorBinding

  override val driverLabel = "touch"

  override fun onDeviceModelUpdated(model: DeviceModelApi) {
    model.updateAttributeSpec(listOf(
      AttributeSpec("value0"),
    ))
  }

  override fun onBindView(view: View, snapshot: DeviceAttributesSnapshot) {
    val value0 = snapshot.lookupLocalOrRemote("value0")?.asNumber()
    println("value0=$value0")
    binding.touchPressed.isChecked = (value0?.toInt() == 1)
  }

  override fun onCreateView(inflater: LayoutInflater, parent: ViewGroup?): View {
    binding = Ev3TouchSensorBinding.inflate(inflater, parent, false)
    return binding.root
  }
}