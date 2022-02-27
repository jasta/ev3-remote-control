package org.devtcg.robotrc.devicewidget.api

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup

interface DeviceWidget {
  fun onCreate(inflater: LayoutInflater, parent: ViewGroup?, modelApi: DeviceModelApi): View
}
