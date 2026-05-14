package com.example.nettools.core

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.os.Build
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.Dispatchers

data class WifiAp(
    val ssid: String,
    val bssid: String,
    val rssiDbm: Int,
    val frequencyMhz: Int,
    val channel: Int,
    val band: String,
    val capabilities: String,
    val security: String,
    val isHidden: Boolean,
)

object WifiScanner {

    /** 一次性读取系统缓存的扫描结果（不主动触发扫描） */
    @Suppress("MissingPermission")
    fun snapshot(ctx: Context): List<WifiAp> {
        val wm = ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        return wm.scanResults.map(::toAp).sortedByDescending { it.rssiDbm }
    }

    /**
     * 触发一次主动扫描并通过 broadcast 回调结果。
     * Android 9+ 后台限速：前台 4 次/2 分钟，超出会用最近一次缓存。
     */
    @Suppress("MissingPermission", "DEPRECATION")
    fun scanOnce(ctx: Context): Flow<List<WifiAp>> = callbackFlow {
        val wm = ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, i: Intent?) {
                val updated = i?.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, true) ?: true
                if (updated) trySend(wm.scanResults.map(::toAp).sortedByDescending { it.rssiDbm })
            }
        }
        val filter = IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
        if (Build.VERSION.SDK_INT >= 33) {
            ctx.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            ctx.registerReceiver(receiver, filter)
        }

        // 先发一次缓存，避免空白
        trySend(wm.scanResults.map(::toAp).sortedByDescending { it.rssiDbm })
        // 触发主动扫描
        wm.startScan()

        awaitClose { runCatching { ctx.unregisterReceiver(receiver) } }
    }.flowOn(Dispatchers.IO)

    private fun toAp(s: ScanResult): WifiAp {
        val rawSsid = if (Build.VERSION.SDK_INT >= 33) {
            s.wifiSsid?.toString()?.removeSurrounding("\"") ?: s.SSID
        } else {
            @Suppress("DEPRECATION") s.SSID
        }
        val hidden = rawSsid.isNullOrBlank()
        val ch = freqToChannel(s.frequency)
        return WifiAp(
            ssid = if (hidden) "<隐藏 SSID>" else rawSsid!!,
            bssid = s.BSSID ?: "",
            rssiDbm = s.level,
            frequencyMhz = s.frequency,
            channel = ch,
            band = bandOf(s.frequency),
            capabilities = s.capabilities ?: "",
            security = parseSecurity(s.capabilities ?: ""),
            isHidden = hidden,
        )
    }

    private fun freqToChannel(mhz: Int): Int = when {
        mhz == 2484 -> 14
        mhz in 2412..2472 -> (mhz - 2412) / 5 + 1
        mhz in 5170..5825 -> (mhz - 5000) / 5
        mhz in 5955..7115 -> (mhz - 5950) / 5   // 6 GHz (Wi-Fi 6E)
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

    /** 用于排 2.4G / 5G 信道冲突；同信道台数越多越拥挤 */
    fun channelCongestion(aps: List<WifiAp>): Map<Pair<String, Int>, Int> =
        aps.groupingBy { it.band to it.channel }.eachCount()
}
