package com.example.nettools.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.xbill.DNS.Cache
import org.xbill.DNS.DClass
import org.xbill.DNS.ExtendedResolver
import org.xbill.DNS.Lookup
import org.xbill.DNS.Name
import org.xbill.DNS.Record
import org.xbill.DNS.SimpleResolver
import org.xbill.DNS.Type
import java.net.InetSocketAddress
import java.time.Duration

data class DnsRecord(
    val type: String,
    val name: String,
    val ttl: Long,
    val data: String,
)

data class DnsResult(
    val type: String,
    val records: List<DnsRecord>,
    val error: String? = null,
    val tookMs: Long,
)

object DnsScanner {

    private val DEFAULT_TYPES = listOf(
        "A" to Type.A,
        "AAAA" to Type.AAAA,
        "CNAME" to Type.CNAME,
        "MX" to Type.MX,
        "TXT" to Type.TXT,
        "NS" to Type.NS,
        "SOA" to Type.SOA,
        "SRV" to Type.SRV,
        "CAA" to Type.CAA,
    )

    /**
     * @param domain 要查询的域名
     * @param dnsServer 自定义 DNS 服务器（如 "8.8.8.8"、"1.1.1.1:53"），传 null 走系统
     * @param types null 表示用默认全集
     */
    suspend fun scan(
        domain: String,
        dnsServer: String? = null,
        types: List<String>? = null,
        timeoutMs: Long = 4000,
    ): List<DnsResult> = coroutineScope {
        val target = types?.mapNotNull { t ->
            DEFAULT_TYPES.firstOrNull { it.first.equals(t, ignoreCase = true) }
        } ?: DEFAULT_TYPES

        target.map { (label, code) ->
            async(Dispatchers.IO) { queryOne(domain, label, code, dnsServer, timeoutMs) }
        }.awaitAll()
    }

    private fun queryOne(
        domain: String,
        label: String,
        code: Int,
        dnsServer: String?,
        timeoutMs: Long,
    ): DnsResult {
        val start = System.currentTimeMillis()
        return try {
            val lookup = Lookup(Name.fromString(domain.ensureDot()), code, DClass.IN)
            // 每次查询用独立 Cache，避免全局缓存把上一次的 NXDOMAIN/SERVFAIL 串到当前类型
            lookup.setCache(Cache(DClass.IN))
            if (!dnsServer.isNullOrBlank()) {
                lookup.setResolver(buildResolver(dnsServer, timeoutMs))
            }
            val records: Array<Record>? = lookup.run()
            val took = System.currentTimeMillis() - start
            when (lookup.result) {
                Lookup.SUCCESSFUL -> DnsResult(
                    type = label,
                    tookMs = took,
                    records = (records ?: emptyArray()).map {
                        DnsRecord(
                            type = Type.string(it.type),
                            name = it.name.toString(),
                            ttl = it.ttl,
                            data = it.rdataToString(),
                        )
                    },
                )
                Lookup.TYPE_NOT_FOUND -> DnsResult(label, emptyList(), "无此类型记录", took)
                Lookup.HOST_NOT_FOUND -> DnsResult(label, emptyList(), "域名不存在 (NXDOMAIN)", took)
                Lookup.TRY_AGAIN -> DnsResult(label, emptyList(), "服务器无响应 / 超时", took)
                Lookup.UNRECOVERABLE -> DnsResult(label, emptyList(), "解析失败 (SERVFAIL)", took)
                else -> DnsResult(label, emptyList(), lookup.errorString, took)
            }
        } catch (t: Throwable) {
            DnsResult(label, emptyList(), t.message ?: t.javaClass.simpleName,
                System.currentTimeMillis() - start)
        }
    }

    private fun buildResolver(server: String, timeoutMs: Long): SimpleResolver {
        val (host, port) = if (server.contains(":") && !server.contains("::")) {
            val idx = server.lastIndexOf(':')
            server.substring(0, idx) to server.substring(idx + 1).toIntOrNull().let { it ?: 53 }
        } else server to 53
        val r = SimpleResolver(host)
        r.port = port
        r.timeout = Duration.ofMillis(timeoutMs)
        return r
    }

    private fun String.ensureDot(): String =
        if (endsWith(".")) this else "$this."
}
