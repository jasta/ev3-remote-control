package org.devtcg.robotrc.robotview.ui.bridge

import androidx.lifecycle.MutableLiveData
import org.devtcg.robotrc.networkservice.model.Device

object DevicesLiveDataBridge {
  val instance = MutableLiveData<List<Device>>()
}