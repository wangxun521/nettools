package com.example.nettools.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.net.InetSocketAddress
import java.net.Socket

data class PortResult(
    val port: Int,
    val open: Boolean,
    val service: String?,
    val tookMs: Long,
)

object PortScanner {

    // 常见端口/服务对照（够日常用）
    private val WELL_KNOWN = mapOf(
        20 to "FTP-data", 21 to "FTP", 22 to "SSH", 23 to "Telnet",
        25 to "SMTP", 53 to "DNS", 80 to "HTTP", 110 to "POP3",
        111 to "RPC", 135 to "MS-RPC", 139 to "NetBIOS", 143 to "IMAP",
        443 to "HTTPS", 445 to "SMB", 465 to "SMTPS", 587 to "SMTP-Sub",
        993 to "IMAPS", 995 to "POP3S", 1080 to "SOCKS", 1433 to "MSSQL",
        1521 to "Oracle", 2049 to "NFS", 3306 to "MySQL", 3389 to "RDP",
        5432 to "PostgreSQL", 5900 to "VNC", 6379 to "Redis",
        8080 to "HTTP-Alt", 8443 to "HTTPS-Alt", 9200 to "Elasticsearch",
        11211 to "Memcached", 27017 to "MongoDB",
    )

    /** 解析端口表达式："22,80,443" 或 "1-1024" 或 "22,80,8000-8100" */
    fun parsePorts(expr: String): List<Int> {
        val out = sortedSetOf<Int>()
        expr.split(',').forEach { token ->
            val t = token.trim()
            if (t.isEmpty()) return@forEach
            if ('-' in t) {
                val (a, b) = t.split('-', limit = 2)
                val start = a.trim().toIntOrNull() ?: return@forEach
                val end = b.trim().toIntOrNull() ?: return@forEach
                (start.coerceIn(1, 65535)..end.coerceIn(1, 65535)).forEach { out.add(it) }
            } else {
                t.toIntOrNull()?.takeIf { it in 1..65535 }?.let { out.add(it) }
            }
        }
        return out.toList()
    }

    /**
     * 并发扫描，结果按完成顺序流式返回。
     * @param concurrency 并发数（默认 64，太高反而慢）
     * @param timeoutMs 单端口连接超时
     */
    fun scan(
        host: String,
        ports: List<Int>,
        concurrency: Int = 64,
        timeoutMs: Int = 800,
    ): Flow<PortResult> = channelFlow {
        val sem = Semaphore(concurrency.coerceIn(1, 256))
        coroutineScope {
            ports.map { port ->
                async(Dispatchers.IO) {
                    sem.withPermit {
                        val start = System.currentTimeMillis()
                        val open = try {
                            Socket().use {
                                it.connect(InetSocketAddress(host, port), timeoutMs)
                                true
                            }
                        } catch (_: Throwable) {
                            false
                        }
                        send(PortResult(port, open, WELL_KNOWN[port],
                            System.currentTimeMillis() - start))
                    }
                }
            }.awaitAll()
        }
    }.flowOn(Dispatchers.IO)
}
