package org.devtcg.robotrc.networkservice.network

fun interface ObserveHandler<T> {
  fun onResult(result: Result<T>)
}