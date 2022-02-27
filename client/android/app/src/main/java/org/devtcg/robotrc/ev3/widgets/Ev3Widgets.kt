package org.devtcg.robotrc.ev3.widgets

import org.devtcg.robotrc.devicewidget.api.DeviceWidget
import org.devtcg.robotrc.devicewidget.api.DeviceWidgetSpec

object Ev3Widgets {
  val instance = listOf<Pair<DeviceWidgetSpec, Class<out DeviceWidget>>>(
    DefaultSensorWidget.Spec.instance to DefaultSensorWidget::class.java,
  )
}