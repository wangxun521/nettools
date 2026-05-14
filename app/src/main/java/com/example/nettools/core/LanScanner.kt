package com.example.nettools.core

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkAddress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.net.Inet4Address
import java.net.InetAddress

data class LanHost(
    val ip: String,
    val hostname: String? = null,
    val mac: String? = null,
    val rttMs: Long? = null,
    val isSelf: Boolean = false,
    val isGateway: Boolean = false,
)

data class LanSubnet(
    val cidr: String,         // 例如 192.168.1.0/24
    val gateway: String?,
    val self: String?,
    val totalHosts: Int,
)

object LanScanner {

    /** 通过 ConnectivityManager 推断当前网络的 IPv4 子网 */
    fun detectSubnet(ctx: Context): LanSubnet? {
        val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val active = cm.activeNetwork ?: return null
        val link = cm.getLinkProperties(active) ?: return null

        val v4: LinkAddress = link.linkAddresses.firstOrNull {
            it.address is Inet4Address && !it.address.isLoopbackAddress
        } ?: return null

        val prefix = v4.prefixLength.coerceIn(8, 30)
        val self = v4.address.hostAddress ?: return null
        val gateway = link.routes.firstOrNull { it.isDefaultRoute }?.gateway?.hostAddress

        val network = networkAddress(self, prefix) ?: return null
        val total = (1 shl (32 - prefix)) - 2  // 减去网络号 + 广播

        return LanSubnet("$network/$prefix", gateway, self, total.coerceAtLeast(0))
    }

    /**
     * 扫描子网。先读一次 ARP 缓存（已知设备秒出），再并发 ping 整个网段补充未知设备。
     * @param maxHosts 防止 /16 等大网段炸：超过这个数只扫前 N 个
     */
    fun scan(
        subnet: LanSubnet,
        concurrency: Int = 64,
        pingTimeoutMs: Int = 600,
        maxHosts: Int = 1024,
    ): Flow<LanHost> = channelFlow {
        val (network, prefix) = subnet.cidr.split('/').let { it[0] to it[1].toInt() }
        val ips = enumerateHosts(network, prefix).take(maxHosts)

        // ARP 表（一次性出已知设备的 MAC）
        val arp = readArpTable()

        val sem = Semaphore(concurrency.coerceIn(1, 256))
        coroutineScope {
            ips.map { ip ->
                async(Dispatchers.IO) {
                    sem.withPermit {
                        val start = System.currentTimeMillis()
                        val reachable = runCatching {
                            InetAddress.getByName(ip).isReachable(pingTimeoutMs)
                        }.getOrDefault(false)
                        val mac = arp[ip]
                        if (reachable || mac != null) {
                            val rtt = System.currentTimeMillis() - start
                            val host = runCatching {
                                InetAddress.getByName(ip).canonicalHostName
                                    .takeIf { it != ip }
                            }.getOrNull()
                            send(LanHost(
                                ip = ip,
                                hostname = host,
                                mac = mac,
                                rttMs = if (reachable) rtt else null,
                                isSelf = ip == subnet.self,
                                isGateway = ip == subnet.gateway,
                            ))
                        }
                    }
                }
            }.toList().awaitAll()
        }
    }.flowOn(Dispatchers.IO)

    /** /proc/net/arp 解析。格式：IP  HW类型  Flags  HW地址  Mask  Device */
    private suspend fun readArpTable(): Map<String, String> = withContext(Dispatchers.IO) {
        runCatching {
            val out = mutableMapOf<String, String>()
            File("/proc/net/arp").useLines { lines ->
                lines.drop(1).forEach { line ->
                    val parts = line.split(Regex("\\s+"))
                    if (parts.size >= 4) {
                        val ip = parts[0]
                        val mac = parts[3]
                        if (mac != "00:00:00:00:00:00" && mac.contains(':')) {
                            out[ip] = mac
                        }
                    }
                }
            }
            out
        }.getOrDefault(emptyMap())
    }

    private fun networkAddress(ip: String, prefix: Int): String? {
        val ipInt = ipToInt(ip) ?: return null
        val mask = if (prefix == 0) 0 else -1 shl (32 - prefix)
        return intToIp(ipInt and mask)
    }

    private fun enumerateHosts(network: String, prefix: Int): Sequence<String> = sequence {
        val net = ipToInt(network) ?: return@sequence
        val size = 1 shl (32 - prefix)
        // 跳过网络号 (net) 和广播 (net+size-1)
        for (i in 1 until size - 1) yield(intToIp(net + i))
    }

    private fun ipToInt(ip: String): Int? {
        val parts = ip.split('.')
        if (parts.size != 4) return null
        return parts.fold(0) { acc, s ->
            val n = s.toIntOrNull() ?: return null
            if (n !in 0..255) return null
            (acc shl 8) or n
        }
    }

    private fun intToIp(v: Int): String =
        "${(v ushr 24) and 0xff}.${(v ushr 16) and 0xff}.${(v ushr 8) and 0xff}.${v and 0xff}"
}
