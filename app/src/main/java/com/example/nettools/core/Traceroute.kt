package com.example.nettools.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetAddress

data class Hop(
    val ttl: Int,
    val address: String?,   // null = timeout / unreachable
    val rttMs: Double?,
    val reachedTarget: Boolean,
)

/**
 * Android 上没有 traceroute 可执行文件，这里用系统 ping 加递增 TTL 实现。
 * 每跳发 1 个 ICMP echo，解析返回的 "From x.x.x.x ... Time exceeded" 或正常 "bytes from".
 */
object Traceroute {

    private val fromRegex = Regex("""[Ff]rom\s+([^\s:]+)""")
    private val rttRegex = Regex("""time[=<]([\d.]+)\s*ms""")

    fun stream(host: String, maxHops: Int = 30, timeoutSec: Int = 2): Flow<Hop> = flow {
        val targetIp = runCatching { InetAddress.getByName(host).hostAddress }.getOrNull()

        for (ttl in 1..maxHops) {
            val cmd = listOf(
                "/system/bin/ping", "-c", "1",
                "-W", timeoutSec.toString(),
                "-t", ttl.toString(),
                host,
            )
            val proc = ProcessBuilder(cmd).redirectErrorStream(true).start()
            val output = BufferedReader(InputStreamReader(proc.inputStream)).use { it.readText() }
            proc.waitFor()

            val from = fromRegex.find(output)?.groupValues?.get(1)
            val rtt = rttRegex.find(output)?.groupValues?.get(1)?.toDoubleOrNull()
            val reached = from != null && targetIp != null && from == targetIp
                    || (output.contains("bytes from") && rtt != null)

            emit(Hop(ttl, from, rtt, reached))
            if (reached) break
        }
    }.flowOn(Dispatchers.IO)
}
