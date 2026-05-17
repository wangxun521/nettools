package com.example.nettools.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

data class SsdpDevice(
    val ip: String,
    val server: String?,      // Server header (品牌型号)
    val location: String?,    // 描述 XML URL
    val st: String?,          // 搜索目标
    val usn: String?,         // 唯一标识
    val raw: String,
)

object SsdpDiscover {

    private const val MULTICAST_ADDR = "239.255.255.250"
    private const val MULTICAST_PORT = 1900

    /**
     * 发 M-SEARCH 然后听响应若干秒。
     * @param searchTarget "ssdp:all" 发现所有；可填 "upnp:rootdevice" 等精确目标
     */
    fun discover(durationMs: Long = 4000, searchTarget: String = "ssdp:all"): Flow<SsdpDevice> = flow {
        val msearch = buildString {
            append("M-SEARCH * HTTP/1.1\r\n")
            append("HOST: $MULTICAST_ADDR:$MULTICAST_PORT\r\n")
            append("MAN: \"ssdp:discover\"\r\n")
            append("MX: 3\r\n")
            append("ST: $searchTarget\r\n")
            append("USER-AGENT: NetTools/1.0 Android UPnP/1.1\r\n")
            append("\r\n")
        }.toByteArray()

        val sock = DatagramSocket().apply {
            broadcast = true
            soTimeout = 1000
        }
        try {
            val target = InetAddress.getByName(MULTICAST_ADDR)
            sock.send(DatagramPacket(msearch, msearch.size, target, MULTICAST_PORT))
            // 再发一次防丢
            sock.send(DatagramPacket(msearch, msearch.size, target, MULTICAST_PORT))

            val seen = mutableSetOf<String>()
            val buf = ByteArray(2048)
            val deadline = System.currentTimeMillis() + durationMs
            while (System.currentTimeMillis() < deadline) {
                val pkt = DatagramPacket(buf, buf.size)
                try {
                    sock.receive(pkt)
                } catch (e: Exception) {
                    continue
                }
                val ip = pkt.address.hostAddress ?: continue
                val raw = String(pkt.data, 0, pkt.length, Charsets.US_ASCII)
                val server = header(raw, "SERVER")
                val location = header(raw, "LOCATION")
                val st = header(raw, "ST")
                val usn = header(raw, "USN")
                val dedupKey = "$ip|$usn"
                if (seen.add(dedupKey)) {
                    emit(SsdpDevice(ip, server, location, st, usn, raw))
                }
            }
        } finally {
            sock.close()
        }
    }.flowOn(Dispatchers.IO)

    private fun header(raw: String, key: String): String? {
        val lower = "$key:"
        return raw.lineSequence().firstOrNull {
            it.length > lower.length && it.substring(0, lower.length).equals(lower, ignoreCase = true)
        }?.substring(lower.length)?.trim()
    }
}
