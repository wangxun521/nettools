package com.example.nettools.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.BufferedReader
import java.io.InputStreamReader

data class PingLine(
    val raw: String,
    val seq: Int? = null,
    val ttl: Int? = null,
    val rttMs: Double? = null,
    val fromHost: String? = null,
)

object Ping {

    private val seqRegex = Regex("""icmp_seq=(\d+)""")
    private val ttlRegex = Regex("""ttl=(\d+)""", RegexOption.IGNORE_CASE)
    private val rttRegex = Regex("""time[=<]([\d.]+)\s*ms""")
    private val fromRegex = Regex("""from\s+([^\s:]+)""", RegexOption.IGNORE_CASE)

    /**
     * 流式输出 ping 结果。host 支持 IPv4/IPv6/域名。
     * count<=0 表示持续 ping，直到 Flow 被取消。
     */
    fun stream(host: String, count: Int = 4, intervalSec: Double = 1.0): Flow<PingLine> = flow {
        val cmd = mutableListOf("/system/bin/ping", "-W", "2")
        if (count > 0) cmd += listOf("-c", count.toString())
        if (intervalSec > 0) cmd += listOf("-i", intervalSec.toString())
        cmd += host

        val proc = ProcessBuilder(cmd).redirectErrorStream(true).start()
        try {
            BufferedReader(InputStreamReader(proc.inputStream)).use { br ->
                while (true) {
                    val line = br.readLine() ?: break
                    emit(parse(line))
                }
            }
            proc.waitFor()
        } finally {
            runCatching { proc.destroy() }
        }
    }.flowOn(Dispatchers.IO)

    fun parse(line: String): PingLine = PingLine(
        raw = line,
        seq = seqRegex.find(line)?.groupValues?.get(1)?.toIntOrNull(),
        ttl = ttlRegex.find(line)?.groupValues?.get(1)?.toIntOrNull(),
        rttMs = rttRegex.find(line)?.groupValues?.get(1)?.toDoubleOrNull(),
        fromHost = fromRegex.find(line)?.groupValues?.get(1),
    )
}
