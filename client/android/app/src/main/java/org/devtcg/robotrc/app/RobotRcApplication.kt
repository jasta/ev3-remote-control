package org.devtcg.robotrc.app

import android.app.Application
import android.content.Context
import org.devtcg.robotrc.appcontext.AppContext

class RobotRcApplication: Application() {
  override fun attachBaseContext(base: Context) {
    super.attachBaseContext(base)
    AppContext.set(this)
  }
}