package org.devtcg.robotrc.ev3.layout

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.PopupMenu
import org.devtcg.robotrc.databinding.Ev3ColorSensorBinding
import org.devtcg.robotrc.robotdata.api.AttributeSpec
import org.devtcg.robotrc.robotdata.api.DeviceModelApi
import org.devtcg.robotrc.robotdata.model.AttributeValueLocal
import org.devtcg.robotrc.robotdata.model.DeviceAttributesSnapshot

class ColorSensorWidget: DeviceWidget {
  private lateinit var model: DeviceModelApi
  private lateinit var binding: Ev3ColorSensorBinding

  private val availableModes = mutableListOf<String>()
  private var confirmedMode: String? = null

  override val driverLabel = "color"

  override fun onDeviceModelUpdated(model: DeviceModelApi) {
    this.model = model
    updateAttributeSpec(confirmedMode)
  }

  private fun updateAttributeSpec(forMode: String?) {
    val baseSpec = listOf(
      AttributeSpec("mode"),
      AttributeSpec("modes"),
      AttributeSpec("value0"),
    )

    val extraSpec = when (forMode) {
      "RGB-RAW" -> listOf(
        AttributeSpec("value1"),
        AttributeSpec("value2"))
      else -> listOf()
    }

    model.updateAttributeSpec(baseSpec + extraSpec)
  }

  override fun onBindView(view: View, snapshot: DeviceAttributesSnapshot) {
    val mode = snapshot.lookupRemote("mode")?.asString()
    val value0 = snapshot.lookupRemote("value0")?.asNumber()
    val value1 = snapshot.lookupRemote("value1")?.asNumber()
    val value2 = snapshot.lookupRemote("value2")?.asNumber()
    val modes = snapshot.lookupRemote("modes")?.asStringList()

    maybeHandleConfirmedMode(mode)
    updateAvailableModes(modes)

    binding.colorModeButton.text = mode ?: "<no mode>"

    applyColorReading(mode, listOf(value0, value1, value2))
  }

  private fun maybeHandleConfirmedMode(mode: String?) {
    if (confirmedMode != mode) {
      confirmedMode = mode
      updateAttributeSpec(mode)
    }
  }

  private fun updateAvailableModes(receivedModes: List<String>?) {
    if (availableModes != receivedModes) {
      availableModes.clear()
      if (receivedModes != null) {
        availableModes.addAll(receivedModes)
      }
    }
  }

  private fun applyColorReading(mode: String?, values: List<Number?>) {
    println("mode=$mode, values=$values")
    val colorToShow = when (mode) {
      "COL-REFLECT" -> Color.valueOf(1F, 1F, 1F, values.first()?.toFloat() ?: 0F)
      "COL-AMBIENT" -> Color.valueOf(1F, 1F, 1F, values.first()?.toFloat() ?: 0F)
      "COL-COLOR" -> {
        when (values.first()?.toInt()) {
          0 -> Color.valueOf(0F, 0F, 0F, 0F) // none
          1 -> Color.valueOf(Color.BLACK)
          2 -> Color.valueOf(Color.BLUE)
          3 -> Color.valueOf(Color.GREEN)
          4 -> Color.valueOf(Color.YELLOW)
          5 -> Color.valueOf(Color.RED)
          6 -> Color.valueOf(Color.WHITE)
          7 -> Color.valueOf(0x9b673c) // brown
          else -> Color.valueOf(0F, 0F, 0F, 0F)
        }
      }
      "RGB-RAW" -> {
        Color.valueOf(
          values[0]?.toFloat() ?: 0F,
          values[1]?.toFloat() ?: 0F,
          values[2]?.toFloat() ?: 0F)
      }
      else -> Color.valueOf(0F, 0F, 0F, 0F)
    }
    binding.colorReading.setBackgroundColor(colorToShow.toArgb())
  }

  override fun onCreateView(inflater: LayoutInflater, parent: ViewGroup?): View {
    binding = Ev3ColorSensorBinding.inflate(inflater, parent, false)
    binding.colorModeButton.setOnClickListener { anchor ->
      val popup = PopupMenu(anchor.context, anchor)
      for (availableMode in availableModes) {
        popup.menu.add(availableMode)
      }

      popup.setOnMenuItemClickListener { clicked ->
        model.sendAttributeWrites(mapOf(
          "mode" to AttributeValueLocal("string", clicked.title.toString()),
        ))
        true
      }

      popup.show()
    }
    return binding.root
  }
}