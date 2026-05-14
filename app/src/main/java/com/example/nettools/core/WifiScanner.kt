package com.example.nettools.core

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class WifiAp(
    val ssid: String,
    val bssid: String,
    val rssiDbm: Int,
    val frequencyMhz: Int,
    val channel: Int,
    val band: String,
    val widthMhz: String,         // 20 / 40 / 80 / 160 / 80+80 / 320
    val capabilities: String,
    val security: String,
    val isHidden: Boolean,
    val timestampUs: Long,
)

object WifiScanner {

    @Suppress("MissingPermission")
    fun snapshot(ctx: Context): List<WifiAp> {
        val wm = ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        return wm.scanResults.map(::toAp).sortedByDescending { it.rssiDbm }
    }

    /**
     * 持续扫描：监听系统广播 + 定时主动触发 + 定时重读缓存。
     * Android 9+ 主扫限速 4 次/2 分钟，超出后只读缓存（仍能反映背景扫描更新）。
     *
     * @param activeIntervalMs 触发 startScan 的间隔
     * @param pollIntervalMs   重读缓存的间隔（更高频，体感更快）
     */
    @Suppress("MissingPermission", "DEPRECATION")
    fun continuousScan(
        ctx: Context,
        activeIntervalMs: Long = 8_000,
        pollIntervalMs: Long = 1_200,
    ): Flow<List<WifiAp>> = callbackFlow {
        val wm = ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, i: Intent?) {
                trySend(wm.scanResults.map(::toAp).sortedByDescending { it.rssiDbm })
            }
        }
        val filter = IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
        if (Build.VERSION.SDK_INT >= 33) {
            ctx.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            ctx.registerReceiver(receiver, filter)
        }

        // 立即出一份缓存
        trySend(wm.scanResults.map(::toAp).sortedByDescending { it.rssiDbm })

        // 主动触发循环（被限速时静默失败，不影响读取）
        val activeJob = launch {
            wm.startScan()
            while (isActive) {
                delay(activeIntervalMs)
                runCatching { wm.startScan() }
            }
        }
        // 缓存重读循环
        val pollJob = launch {
            while (isActive) {
                delay(pollIntervalMs)
                trySend(wm.scanResults.map(::toAp).sortedByDescending { it.rssiDbm })
            }
        }

        awaitClose {
            activeJob.cancel(); pollJob.cancel()
            runCatching { ctx.unregisterReceiver(receiver) }
        }
    }.flowOn(Dispatchers.IO)

    private fun toAp(s: ScanResult): WifiAp {
        val rawSsid = if (Build.VERSION.SDK_INT >= 33) {
            s.wifiSsid?.toString()?.removeSurrounding("\"") ?: s.SSID
        } else {
            @Suppress("DEPRECATION") s.SSID
        }
        val hidden = rawSsid.isNullOrBlank()
        return WifiAp(
            ssid = if (hidden) "<隐藏 SSID>" else rawSsid!!,
            bssid = s.BSSID ?: "",
            rssiDbm = s.level,
            frequencyMhz = s.frequency,
            channel = freqToChannel(s.frequency),
            band = bandOf(s.frequency),
            widthMhz = widthString(s),
            capabilities = s.capabilities ?: "",
            security = parseSecurity(s.capabilities ?: ""),
            isHidden = hidden,
            timestampUs = s.timestamp,
        )
    }

    private fun widthString(s: ScanResult): String {
        if (Build.VERSION.SDK_INT < 23) return "?"
        return when (s.channelWidth) {
            ScanResult.CHANNEL_WIDTH_20MHZ -> "20"
            ScanResult.CHANNEL_WIDTH_40MHZ -> "40"
            ScanResult.CHANNEL_WIDTH_80MHZ -> "80"
            ScanResult.CHANNEL_WIDTH_160MHZ -> "160"
            ScanResult.CHANNEL_WIDTH_80MHZ_PLUS_MHZ -> "80+80"
            else -> if (Build.VERSION.SDK_INT >= 33 && s.channelWidth == 5) "320" else "?"
        }
    }

    private fun freqToChannel(mhz: Int): Int = when {
        mhz == 2484 -> 14
        mhz in 2412..2472 -> (mhz - 2412) / 5 + 1
        mhz in 5170..5825 -> (mhz - 5000) / 5
        mhz in 5955..7115 -> (mhz - 5950) / 5
        else -> 0
    }

    private fun bandOf(mhz: Int): String = when (mhz) {
        in 2400..2500 -> "2.4G"
        in 4900..5900 -> "5G"
        in 5925..7125 -> "6G"
        else -> "?"
    }

    private fun parseSecurity(cap: String): String {
        val s = StringBuilder()
        if ("WPA3" in cap) s.append("WPA3 ")
        if ("WPA2" in cap) s.append("WPA2 ")
        else if ("WPA" in cap) s.append("WPA ")
        if ("WEP" in cap) s.append("WEP ")
        if ("SAE" in cap) s.append("SAE ")
        if ("OWE" in cap) s.append("OWE ")
        if (s.isEmpty()) {
            if ("ESS" in cap && !cap.contains("WPA") && !cap.contains("WEP")) return "开放"
            return cap.ifBlank { "?" }
        }
        return s.trim().toString()
    }

    fun channelCongestion(aps: List<WifiAp>): Map<Pair<String, Int>, Int> =
        aps.groupingBy { it.band to it.channel }.eachCount()
}
