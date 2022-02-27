package org.devtcg.robotrc.ev3.widgets

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.LifecycleOwner
import org.devtcg.robotrc.databinding.Ev3UnknownDeviceBinding
import org.devtcg.robotrc.devicewidget.api.*

class DefaultSensorWidget: DeviceWidget {
  object Spec {
    val instance = DeviceWidgetSpec(
      hardwarePlatform = "ev3",
      type = "sensor",
      driver = null,
      attributes = listOf(
        AttributeSpec("mode"),
        AttributeSpec("value0"),
      )
    )
  }

  private lateinit var binding: Ev3UnknownDeviceBinding

  override fun onCreate(
    inflater: LayoutInflater,
    parent: ViewGroup?,
    modelApi: DeviceModelApi
  ): View {
    binding = Ev3UnknownDeviceBinding.inflate(inflater, parent, true)
    modelApi.attributesSnapshot.observe(inflater.context as LifecycleOwner) {
      updateFromSnapshot(it)
    }
    return binding.root
  }

  private fun updateFromSnapshot(snapshot: DeviceAttributesSnapshot) {
    val mode = snapshot.attributeValues["mode"]
    val value0 = snapshot.attributeValues["value0"]
    binding.root.text = "Mode: $mode\nValue0: $value0"
  }
}