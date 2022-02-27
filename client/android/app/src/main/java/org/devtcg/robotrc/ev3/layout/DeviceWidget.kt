package org.devtcg.robotrc.ev3.layout

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import org.devtcg.robotrc.robotdata.model.DeviceAttributesSnapshot
import org.devtcg.robotrc.robotdata.api.DeviceModelApi

interface DeviceWidget {
  fun onDeviceModelUpdated(model: DeviceModelApi)
  fun onBindView(view: View, snapshot: DeviceAttributesSnapshot)
  fun onCreateView(inflater: LayoutInflater, parent: ViewGroup?): View
}
