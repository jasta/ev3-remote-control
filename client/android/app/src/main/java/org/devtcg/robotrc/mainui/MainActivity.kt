package org.devtcg.robotrc.mainui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import org.devtcg.robotrc.databinding.MainActivityBinding
import org.devtcg.robotrc.robotdata.bridge.RobotSelectorBridge
import org.devtcg.robotrc.robotselection.ui.RobotSelectionInitiationAgent

/**
 * An example full-screen activity that shows and hides the system UI (i.e.
 * status bar and navigation/system bar) with user interaction.
 */
class MainActivity : AppCompatActivity() {
  private lateinit var binding: MainActivityBinding
  private lateinit var robotSelectionAgent: RobotSelectionInitiationAgent

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    binding = MainActivityBinding.inflate(layoutInflater)
    setContentView(binding.root)
    setSupportActionBar(binding.toolbar)
    supportActionBar!!.setDisplayShowTitleEnabled(false)

    robotSelectionAgent = RobotSelectionInitiationAgent(
      this,
      supportFragmentManager,
      binding.robotTargetButton,
      RobotSelectorBridge.instance)

    robotSelectionAgent.onCreate()
  }
}
