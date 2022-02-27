package org.devtcg.robotrc.devicewidget.api

class DeviceWidgetRegistry(
  val supportedDrivers: List<Pair<DeviceWidgetSpec, Class<out DeviceWidget>>>,
)