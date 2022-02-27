package org.devtcg.robotrc.mainui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import org.devtcg.robotrc.databinding.ActivityFullscreenBinding
import org.devtcg.robotrc.robot.bridge.RobotSelectorBridge

/**
 * An example full-screen activity that shows and hides the system UI (i.e.
 * status bar and navigation/system bar) with user interaction.
 */
class MainActivity : AppCompatActivity() {
  private lateinit var binding: ActivityFullscreenBinding
  private lateinit var windowTreatmentAgent: WindowTreatmentAgent
  private val robotSelectionDeciderAgent =
    MainUiDeciderAgent(
      supportFragmentManager,
      RobotSelectorBridge.instance)

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    binding = ActivityFullscreenBinding.inflate(layoutInflater)
    windowTreatmentAgent = WindowTreatmentAgent(this, binding)
    windowTreatmentAgent.onCreate()

    robotSelectionDeciderAgent.onCreate()
  }

  override fun onPostCreate(savedInstanceState: Bundle?) {
    super.onPostCreate(savedInstanceState)
    windowTreatmentAgent.onPostCreate()
  }
}
