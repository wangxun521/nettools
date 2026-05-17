package com.example.nettools.core

import android.net.TrafficStats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

data class TrafficSample(
    val rxBytesPerSec: Long,
    val txBytesPerSec: Long,
    val totalRxBytes: Long,
    val totalTxBytes: Long,
)

object TrafficMonitor {

    fun stream(intervalMs: Long = 1000): Flow<TrafficSample> = flow {
        var lastRx = TrafficStats.getTotalRxBytes().coerceAtLeast(0)
        var lastTx = TrafficStats.getTotalTxBytes().coerceAtLeast(0)
        var lastTs = System.currentTimeMillis()
        while (true) {
            delay(intervalMs)
            val rx = TrafficStats.getTotalRxBytes().coerceAtLeast(0)
            val tx = TrafficStats.getTotalTxBytes().coerceAtLeast(0)
            val now = System.currentTimeMillis()
            val dtSec = (now - lastTs).coerceAtLeast(1) / 1000.0
            val rxRate = ((rx - lastRx) / dtSec).toLong().coerceAtLeast(0)
            val txRate = ((tx - lastTx) / dtSec).toLong().coerceAtLeast(0)
            emit(TrafficSample(rxRate, txRate, rx, tx))
            lastRx = rx; lastTx = tx; lastTs = now
        }
    }.flowOn(Dispatchers.IO)
}

fun Long.humanRate(): String = when {
    this >= 1L shl 30 -> "%.2f GiB/s".format(this.toDouble() / (1L shl 30))
    this >= 1L shl 20 -> "%.2f MiB/s".format(this.toDouble() / (1L shl 20))
    this >= 1L shl 10 -> "%.1f KiB/s".format(this.toDouble() / (1L shl 10))
    else -> "$this B/s"
}
