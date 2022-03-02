package org.devtcg.robotrc.concurrency

import android.util.Log
import java.io.IOException
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.ThreadFactory

object RobotExecutors {
  fun newSingleThreadScheduledExecutor(name: String): ScheduledThreadPoolExecutor {
    return DecoratedScheduledThreadPoolExecutor(corePoolSize = 1, threadFactory = { runnable ->
      Thread(runnable, name)
    })
  }

  class DecoratedScheduledThreadPoolExecutor(
    corePoolSize: Int,
    threadFactory: ThreadFactory,
  ): ScheduledThreadPoolExecutor(corePoolSize, threadFactory) {
    companion object {
      private const val TAG = "DecoratedScheduledThreadPoolExecutor"
    }

    override fun afterExecute(r: Runnable?, t: Throwable?) {
      super.afterExecute(r, t)

      when (t) {
        null -> {}
        is IOException -> {
          Log.w(TAG, "Probably harmless uncaught IOException: $t")
        }
        else -> {
          Log.e(TAG, "Uncaught exception would leak ($t), trying to crash...")
          Thread.getDefaultUncaughtExceptionHandler()?.uncaughtException(Thread.currentThread(), t)
        }
      }
    }
  }
}