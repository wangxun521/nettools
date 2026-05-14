package com.example.nettools.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.InetSocketAddress
import java.net.Socket

data class WhoisResponse(
    val server: String,
    val raw: String,
    val referral: String? = null,
)

object Whois {

    private const val IANA = "whois.iana.org"
    private const val PORT = 43

    /**
     * 查询域名 whois。先问 IANA 拿到权威 whois 服务器，再问那台拿详细信息。
     */
    suspend fun lookup(domain: String, timeoutMs: Int = 5000): List<WhoisResponse> =
        withContext(Dispatchers.IO) {
            val out = mutableListOf<WhoisResponse>()
            val first = query(IANA, domain, timeoutMs)
            val ref = parseReferral(first.raw)
            out += first.copy(referral = ref)
            if (ref != null && !ref.equals(IANA, ignoreCase = true)) {
                out += query(ref, domain, timeoutMs)
            }
            out
        }

    private fun query(server: String, q: String, timeoutMs: Int): WhoisResponse {
        Socket().use { sock ->
            sock.connect(InetSocketAddress(server, PORT), timeoutMs)
            sock.soTimeout = timeoutMs
            OutputStreamWriter(sock.getOutputStream(), Charsets.UTF_8).apply {
                write("$q\r\n"); flush()
            }
            val text = BufferedReader(InputStreamReader(sock.getInputStream(), Charsets.UTF_8))
                .use { it.readText() }
            return WhoisResponse(server, text)
        }
    }

    private val referralRegex = Regex(
        """(?:refer|whois server|registrar whois server):\s*([^\s]+)""",
        RegexOption.IGNORE_CASE,
    )

    private fun parseReferral(raw: String): String? =
        referralRegex.find(raw)?.groupValues?.get(1)?.trim()
}
