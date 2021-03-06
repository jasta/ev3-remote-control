package org.devtcg.robotrc.robotview.api

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.MainThread
import org.devtcg.robotrc.robotdata.api.DeviceModelApi
import org.devtcg.robotrc.robotdata.model.DeviceAttributesSnapshot

@MainThread
interface RobotLayout {
  /**
   * Called to inform the robot layout that one or more devices have changed in some way.  Called
   * initially with the list of devices we got from the peer and then only called again if
   * a physical device change is detected (e.g. plugged in or removed).
   *
   * Note that you must invoke [DeviceModelApi.updateAttributeSpec] at least once to
   * inform the data fetcher what attributes you're interested in.
   */
  fun onDevicesUpdated(devices: List<DeviceModelApi>)

  /**
   * Called to inform that attribute values have been updated for one specific device.  Note
   * that not all values will have been changed so local diffing may still be required for
   * performance reasons.
   */
  fun onDeviceAttributesUpdated(deviceAddress: String, attributes: DeviceAttributesSnapshot)

  /**
   * Create the main robot layout, happens immediately even before we've transacted with the robot
   * to find out properties of connected devices.
   */
  fun onCreateView(inflater: LayoutInflater, parent: ViewGroup?): View
}