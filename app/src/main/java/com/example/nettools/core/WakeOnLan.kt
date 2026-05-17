package com.example.nettools.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

object WakeOnLan {

    /**
     * 发送魔法包到目标 MAC。
     * @param mac 形如 "AA:BB:CC:DD:EE:FF" 或 "aa-bb-cc-dd-ee-ff"
     * @param broadcast 通常是子网广播地址，如 "192.168.1.255"；公网无法 WoL
     * @param ports 通常是 9 或 7；同时发到这两个端口最稳
     */
    suspend fun send(
        mac: String,
        broadcast: String = "255.255.255.255",
        ports: List<Int> = listOf(9, 7),
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val macBytes = parseMac(mac) ?: error("MAC 格式不对")
            val packet = ByteArray(6 + 16 * 6).apply {
                for (i in 0..5) this[i] = 0xFF.toByte()
                for (i in 0..15) System.arraycopy(macBytes, 0, this, 6 + i * 6, 6)
            }
            val addr = InetAddress.getByName(broadcast)
            DatagramSocket().use { sock ->
                sock.broadcast = true
                for (p in ports) {
                    sock.send(DatagramPacket(packet, packet.size, addr, p))
                }
            }
        }
    }

    private fun parseMac(s: String): ByteArray? {
        val cleaned = s.trim().replace(":", "").replace("-", "").replace(".", "")
        if (cleaned.length != 12) return null
        return ByteArray(6) { i ->
            cleaned.substring(i * 2, i * 2 + 2).toIntOrNull(16)?.toByte() ?: return null
        }
    }
}
