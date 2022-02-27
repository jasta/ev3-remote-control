package org.devtcg.robotrc.ev3.widgets

import org.devtcg.robotrc.robotlayout.api.DeviceWidgetSpec

object Ev3Widgets {
  val instance = listOf<Pair<DeviceWidgetSpec, Class<out DeviceWidget>>>(
    DefaultSensorWidget.Spec.instance to DefaultSensorWidget::class.java,
  )
}