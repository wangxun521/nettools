package com.example.nettools.core

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Build

data class WifiSnapshot(
    val ssid: String?,
    val bssid: String?,
    val rssiDbm: Int?,
    val linkSpeedMbps: Int?,
    val frequencyMhz: Int?,
    val ipv4: String?,
    val ipv6: List<String>,
    val gateway: String?,
    val dns: List<String>,
    val domainSuffix: String?,
    val mtu: Int?,
    val isMetered: Boolean?,
    val transport: String,
)

object WifiInfoReader {

    fun read(ctx: Context): WifiSnapshot {
        val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val wm = ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

        val active = cm.activeNetwork
        val caps: NetworkCapabilities? = active?.let { cm.getNetworkCapabilities(it) }
        val link: LinkProperties? = active?.let { cm.getLinkProperties(it) }

        // Wi-Fi 详情（SSID/BSSID 需要 ACCESS_FINE_LOCATION，未授权时返回 <unknown ssid>）
        @Suppress("DEPRECATION")
        val info = wm.connectionInfo
        val ssidRaw = info?.ssid
        val ssid = ssidRaw?.removeSurrounding("\"")?.takeIf { it.isNotBlank() && it != "<unknown ssid>" }

        val ipv4 = link?.linkAddresses?.firstOrNull {
            it.address.hostAddress?.contains('.') == true
        }?.address?.hostAddress
        val ipv6 = link?.linkAddresses
            ?.mapNotNull { it.address.hostAddress }
            ?.filter { it.contains(':') }
            ?: emptyList()
        val gateway = link?.routes?.firstOrNull { it.isDefaultRoute }?.gateway?.hostAddress
        val dns = link?.dnsServers?.mapNotNull { it.hostAddress } ?: emptyList()

        val transport = when {
            caps == null -> "未连接"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "蜂窝"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "以太网"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN"
            else -> "其他"
        }

        return WifiSnapshot(
            ssid = ssid,
            bssid = info?.bssid?.takeIf { it != "02:00:00:00:00:00" },
            rssiDbm = info?.rssi?.takeIf { it != -127 },
            linkSpeedMbps = info?.linkSpeed?.takeIf { it > 0 },
            frequencyMhz = if (Build.VERSION.SDK_INT >= 21) info?.frequency?.takeIf { it > 0 } else null,
            ipv4 = ipv4,
            ipv6 = ipv6,
            gateway = gateway,
            dns = dns,
            domainSuffix = if (Build.VERSION.SDK_INT >= 21) link?.domains else null,
            mtu = if (Build.VERSION.SDK_INT >= 23) link?.mtu?.takeIf { it > 0 } else null,
            isMetered = if (caps != null && Build.VERSION.SDK_INT >= 28)
                !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) else null,
            transport = transport,
        )
    }
}
