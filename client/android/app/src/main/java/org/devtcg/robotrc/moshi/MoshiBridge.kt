package org.devtcg.robotrc.moshi

import com.squareup.moshi.Moshi

object MoshiBridge {
  val instance: Moshi = Moshi.Builder().build()
}