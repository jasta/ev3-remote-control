package org.devtcg.robotrc.appcontext

import android.content.Context

object AppContext {
  private lateinit var context: Context

  fun get() = context

  fun set(context: Context) {
    this.context = context
  }
}