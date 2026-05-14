package com.example.nettools.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class GeoResult(
    val query: String,
    val status: String,
    val country: String?,
    val region: String?,
    val city: String?,
    val isp: String?,
    val org: String?,
    val asn: String?,
    val lat: Double?,
    val lon: Double?,
    val timezone: String?,
    val reverse: String?,
    val message: String?,
)

object IpGeo {

    /** 空 ip 表示查"我自己"的出口 IP。ip-api.com 免费版限速 45 次/分钟，仅 HTTP */
    suspend fun lookup(ip: String? = null): GeoResult = withContext(Dispatchers.IO) {
        val q = ip?.trim().orEmpty()
        val fields = "status,message,country,regionName,city,isp,org,as,lat,lon,timezone,reverse,query"
        val url = URL("http://ip-api.com/json/$q?fields=$fields&lang=zh-CN")

        val conn = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 5000; readTimeout = 5000
            setRequestProperty("User-Agent", "NetTools/1.0")
        }
        try {
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val j = JSONObject(body)
            GeoResult(
                query = j.optString("query"),
                status = j.optString("status"),
                country = j.optString("country").nullIfBlank(),
                region = j.optString("regionName").nullIfBlank(),
                city = j.optString("city").nullIfBlank(),
                isp = j.optString("isp").nullIfBlank(),
                org = j.optString("org").nullIfBlank(),
                asn = j.optString("as").nullIfBlank(),
                lat = j.opt("lat") as? Double,
                lon = j.opt("lon") as? Double,
                timezone = j.optString("timezone").nullIfBlank(),
                reverse = j.optString("reverse").nullIfBlank(),
                message = j.optString("message").nullIfBlank(),
            )
        } finally {
            conn.disconnect()
        }
    }

    private fun String?.nullIfBlank(): String? =
        if (this.isNullOrBlank()) null else this
}
