package com.example.nettools.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.nettools.core.WifiInfoReader
import com.example.nettools.core.WifiSnapshot

@Composable
fun WifiScreen() {
    val ctx = LocalContext.current
    var snap by remember { mutableStateOf<WifiSnapshot?>(null) }
    var hasLocation by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(
            ctx, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED)
    }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasLocation = granted
        snap = WifiInfoReader.read(ctx)
    }
    LaunchedEffect(Unit) { snap = WifiInfoReader.read(ctx) }

    Column(Modifier.padding(16.dp).fillMaxSize().verticalScroll(rememberScrollState())) {
        if (!hasLocation) {
            Card(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text("需要定位权限才能显示 SSID / BSSID",
                        style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = {
                        launcher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                    }) { Text("授予定位权限") }
                }
            }
        }
        Row {
            Button(onClick = { snap = WifiInfoReader.read(ctx) }) { Text("刷新") }
        }
        Spacer(Modifier.height(12.dp))
        val s = snap
        if (s == null) {
            Text("读取中…")
        } else {
            InfoRow("连接类型", s.transport)
            InfoRow("SSID", s.ssid ?: "(未知 / 无权限)")
            InfoRow("BSSID", s.bssid ?: "—")
            InfoRow("信号 RSSI", s.rssiDbm?.let { "$it dBm" } ?: "—")
            InfoRow("链路速率", s.linkSpeedMbps?.let { "$it Mbps" } ?: "—")
            InfoRow("频率", s.frequencyMhz?.let { freqHuman(it) } ?: "—")
            Divider(Modifier.padding(vertical = 6.dp))
            InfoRow("IPv4", s.ipv4 ?: "—")
            s.ipv6.forEachIndexed { i, ip -> InfoRow(if (i == 0) "IPv6" else "", ip) }
            InfoRow("网关", s.gateway ?: "—")
            s.dns.forEachIndexed { i, d -> InfoRow(if (i == 0) "DNS" else "", d) }
            InfoRow("MTU", s.mtu?.toString() ?: "—")
            InfoRow("域后缀", s.domainSuffix ?: "—")
            InfoRow("计费网络", when (s.isMetered) { true -> "是"; false -> "否"; null -> "—" })
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(label, modifier = Modifier.width(96.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.bodySmall)
    }
}

private fun freqHuman(mhz: Int): String {
    val band = when (mhz) {
        in 2400..2500 -> "2.4 GHz"
        in 4900..5900 -> "5 GHz"
        in 5925..7125 -> "6 GHz"
        else -> ""
    }
    return "$mhz MHz${if (band.isNotEmpty()) "  ($band)" else ""}"
}
