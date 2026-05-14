package com.example.nettools.core

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

data class IperfConfig(
    val host: String,
    val port: Int = 5201,
    val durationSec: Int = 10,
    val parallel: Int = 1,
    val reverse: Boolean = false,   // -R: 下行测试（服务器发给客户端）
    val udp: Boolean = false,        // -u
    val bitrateMbps: Int? = null,    // -b (UDP 一般要给个上限)
    val omitSec: Int = 0,            // -O 忽略前 N 秒
)

/** 单次区间统计 */
data class IperfInterval(
    val streamId: Int?,        // null = SUM 行（多流汇总）
    val startSec: Double,
    val endSec: Double,
    val transferBytes: Long,
    val bitsPerSec: Double,
    val retransmits: Int?,     // 仅 TCP sender 有
)

sealed class IperfEvent {
    data class Line(val raw: String) : IperfEvent()
    data class Interval(val data: IperfInterval) : IperfEvent()
    data class Summary(val sent: IperfInterval?, val recv: IperfInterval?) : IperfEvent()
    data class Error(val message: String) : IperfEvent()
    object Done : IperfEvent()
}

object Iperf3Runner {

    fun binaryPath(ctx: Context): String =
        ctx.applicationInfo.nativeLibraryDir + "/libiperf3.so"

    fun isAvailable(ctx: Context): Boolean {
        val f = File(binaryPath(ctx))
        return f.exists() && f.canExecute()
    }

    fun version(ctx: Context): String? = runCatching {
        val p = ProcessBuilder(listOf(binaryPath(ctx), "-v"))
            .redirectErrorStream(true).start()
        val out = p.inputStream.bufferedReader().use { it.readText() }
        p.waitFor()
        out.lineSequence().firstOrNull()?.trim()
    }.getOrNull()

    fun run(ctx: Context, cfg: IperfConfig): Flow<IperfEvent> = flow {
        if (!isAvailable(ctx)) {
            emit(IperfEvent.Error("iperf3 二进制不存在或不可执行：${binaryPath(ctx)}"))
            return@flow
        }
        val args = buildArgs(cfg)
        val proc = ProcessBuilder(listOf(binaryPath(ctx)) + args)
            .redirectErrorStream(true).start()
        try {
            BufferedReader(InputStreamReader(proc.inputStream)).use { br ->
                var inSummary = false
                var sent: IperfInterval? = null
                var recv: IperfInterval? = null
                while (true) {
                    val line = br.readLine() ?: break
                    emit(IperfEvent.Line(line))

                    if (line.startsWith("iperf3:") || line.startsWith("error")) {
                        emit(IperfEvent.Error(line))
                        continue
                    }

                    // 区间行 / 汇总行
                    val iv = parseIntervalLine(line) ?: continue
                    if (line.contains("sender")) { sent = iv; inSummary = true; continue }
                    if (line.contains("receiver")) { recv = iv; inSummary = true; continue }
                    if (!inSummary) emit(IperfEvent.Interval(iv))
                }
                if (sent != null || recv != null) emit(IperfEvent.Summary(sent, recv))
            }
            proc.waitFor()
            emit(IperfEvent.Done)
        } finally {
            runCatching { proc.destroy() }
        }
    }.flowOn(Dispatchers.IO)

    private fun buildArgs(cfg: IperfConfig): List<String> = buildList {
        add("-c"); add(cfg.host)
        add("-p"); add(cfg.port.toString())
        add("-t"); add(cfg.durationSec.toString())
        if (cfg.parallel > 1) { add("-P"); add(cfg.parallel.toString()) }
        if (cfg.reverse) add("-R")
        if (cfg.udp) add("-u")
        cfg.bitrateMbps?.let { add("-b"); add("${it}M") }
        if (cfg.omitSec > 0) { add("-O"); add(cfg.omitSec.toString()) }
        // 强制 1 秒一个区间，方便实时刷新
        add("-i"); add("1")
        add("--forceflush")
    }

    // 例：
    // [  5]   0.00-1.00   sec   118 MBytes   990 Mbits/sec    0   1.23 MBytes
    // [SUM]   0.00-10.00  sec  1.18 GBytes  1.01 Gbits/sec   12             sender
    private val intervalRegex = Regex(
        """^\[\s*(\d+|SUM)\]\s+([\d.]+)-([\d.]+)\s+sec\s+([\d.]+)\s+(\w+ytes)\s+([\d.]+)\s+(\w+bits/sec)(?:\s+(\d+))?"""
    )

    fun parseIntervalLine(line: String): IperfInterval? {
        val m = intervalRegex.find(line) ?: return null
        val id = m.groupValues[1].let { if (it == "SUM") null else it.toIntOrNull() }
        val start = m.groupValues[2].toDoubleOrNull() ?: return null
        val end = m.groupValues[3].toDoubleOrNull() ?: return null
        val xfer = m.groupValues[4].toDoubleOrNull() ?: return null
        val xferUnit = m.groupValues[5]
        val rate = m.groupValues[6].toDoubleOrNull() ?: return null
        val rateUnit = m.groupValues[7]
        val retr = m.groupValues.getOrNull(8)?.toIntOrNull()

        return IperfInterval(
            streamId = id,
            startSec = start, endSec = end,
            transferBytes = (xfer * unitToBytes(xferUnit)).toLong(),
            bitsPerSec = rate * unitToBits(rateUnit),
            retransmits = retr,
        )
    }

    private fun unitToBytes(u: String): Double = when (u) {
        "Bytes" -> 1.0
        "KBytes" -> 1024.0
        "MBytes" -> 1024.0 * 1024
        "GBytes" -> 1024.0 * 1024 * 1024
        "TBytes" -> 1024.0 * 1024 * 1024 * 1024
        else -> 1.0
    }

    private fun unitToBits(u: String): Double = when (u) {
        "bits/sec" -> 1.0
        "Kbits/sec" -> 1e3
        "Mbits/sec" -> 1e6
        "Gbits/sec" -> 1e9
        "Tbits/sec" -> 1e12
        else -> 1.0
    }
}

fun Double.humanBits(): String = when {
    this >= 1e9 -> "%.2f Gbps".format(this / 1e9)
    this >= 1e6 -> "%.2f Mbps".format(this / 1e6)
    this >= 1e3 -> "%.1f Kbps".format(this / 1e3)
    else -> "%.0f bps".format(this)
}

fun Long.humanBytes(): String = when {
    this >= 1L shl 30 -> "%.2f GiB".format(this.toDouble() / (1L shl 30))
    this >= 1L shl 20 -> "%.2f MiB".format(this.toDouble() / (1L shl 20))
    this >= 1L shl 10 -> "%.1f KiB".format(this.toDouble() / (1L shl 10))
    else -> "$this B"
}
