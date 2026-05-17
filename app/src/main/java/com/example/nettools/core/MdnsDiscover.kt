package com.example.nettools.core

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

data class MdnsService(
    val name: String,
    val type: String,
    val host: String?,     // IP 或主机名
    val port: Int?,
    val attributes: Map<String, String>,
)

/** 常见服务类型 */
val MDNS_TYPES = listOf(
    "_http._tcp." to "Web 服务",
    "_https._tcp." to "HTTPS 服务",
    "_smb._tcp." to "SMB 共享",
    "_afpovertcp._tcp." to "AFP (Mac 共享)",
    "_googlecast._tcp." to "Chromecast",
    "_airplay._tcp." to "AirPlay",
    "_raop._tcp." to "AirTunes",
    "_homekit._tcp." to "HomeKit",
    "_ipp._tcp." to "打印机 IPP",
    "_printer._tcp." to "打印机",
    "_ssh._tcp." to "SSH",
    "_sftp-ssh._tcp." to "SFTP",
    "_workstation._tcp." to "工作站",
    "_companion-link._tcp." to "Apple 设备",
    "_hap._tcp." to "HomeKit Accessory",
    "_spotify-connect._tcp." to "Spotify Connect",
)

object MdnsDiscover {

    fun discover(ctx: Context, serviceType: String): Flow<MdnsService> = callbackFlow {
        val nsd = ctx.applicationContext.getSystemService(Context.NSD_SERVICE) as NsdManager

        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(t: String) {}
            override fun onDiscoveryStopped(t: String) {}
            override fun onStartDiscoveryFailed(t: String, e: Int) {}
            override fun onStopDiscoveryFailed(t: String, e: Int) {}
            override fun onServiceLost(info: NsdServiceInfo) {}
            override fun onServiceFound(info: NsdServiceInfo) {
                resolveAndEmit(nsd, info) { trySend(it) }
            }
        }
        runCatching { nsd.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, listener) }
            .onFailure { close(it) }

        awaitClose {
            runCatching { nsd.stopServiceDiscovery(listener) }
        }
    }

    private fun resolveAndEmit(
        nsd: NsdManager,
        info: NsdServiceInfo,
        onResolved: (MdnsService) -> Unit,
    ) {
        // 先发一个未解析的占位（host/port 还没有）
        onResolved(MdnsService(info.serviceName, info.serviceType, null, null, emptyMap()))

        val resolveListener = object : NsdManager.ResolveListener {
            override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) {}
            override fun onServiceResolved(resolved: NsdServiceInfo) {
                val attrs = if (Build.VERSION.SDK_INT >= 21) {
                    runCatching {
                        resolved.attributes?.mapValues { it.value?.toString(Charsets.UTF_8) ?: "" }
                            ?: emptyMap()
                    }.getOrDefault(emptyMap())
                } else emptyMap()
                onResolved(MdnsService(
                    name = resolved.serviceName,
                    type = resolved.serviceType,
                    host = resolved.host?.hostAddress,
                    port = resolved.port.takeIf { it > 0 },
                    attributes = attrs,
                ))
            }
        }
        runCatching { nsd.resolveService(info, resolveListener) }
    }
}
