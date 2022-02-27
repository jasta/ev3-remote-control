package org.devtcg.robotrc.robotview.bridge

import org.devtcg.robotrc.devicewidget.api.DeviceWidgetRegistry
import org.devtcg.robotrc.ev3.widgets.Ev3Widgets

object DeviceWidgetRegistryBridge {
  val instance = DeviceWidgetRegistry(Ev3Widgets.instance)
}