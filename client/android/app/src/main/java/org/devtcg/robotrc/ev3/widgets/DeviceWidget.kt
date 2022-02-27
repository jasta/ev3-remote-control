package org.devtcg.robotrc.ev3.widgets

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import org.devtcg.robotrc.robotlayout.api.DeviceAttributesSnapshot
import org.devtcg.robotrc.robotdata.api.DeviceModelApi

interface DeviceWidget {
  fun onBindView(view: View, snapshot: DeviceAttributesSnapshot)
  fun onCreateView(inflater: LayoutInflater, parent: ViewGroup?, model: DeviceModelApi): View
}
