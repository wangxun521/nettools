package com.example.nettools.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.nettools.core.WifiAp
import com.example.nettools.core.WifiScanner
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

@Composable
fun WifiScanScreen() {
    val ctx = LocalContext.current
    val requiredPerm = if (Build.VERSION.SDK_INT >= 33)
        Manifest.permission.NEARBY_WIFI_DEVICES else Manifest.permission.ACCESS_FINE_LOCATION
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(ctx, requiredPerm)
                == PackageManager.PERMISSION_GRANTED)
    }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted = it }

    val aps = remember { mutableStateListOf<WifiAp>() }
    var scanJob by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()
    val scanning = scanJob?.isActive == true

    val startScan = {
        scanJob?.cancel()
        aps.clear()
        scanJob = scope.launch {
            WifiScanner.scanOnce(ctx).collect {
                aps.clear(); aps.addAll(it)
            }
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        if (!granted) {
            ElevatedCard(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text("需要权限才能扫描周围 Wi-Fi",
                        style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { launcher.launch(requiredPerm) }) {
                        Text(if (Build.VERSION.SDK_INT >= 33) "授予附近设备权限" else "授予定位权限")
                    }
                }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            FilledTonalButton(enabled = granted, onClick = { startScan() }) {
                Icon(Icons.Default.Refresh, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (scanning) "扫描中…" else "开始扫描")
            }
            Spacer(Modifier.width(12.dp))
            Text("发现 ${aps.size} 个网络",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Spacer(Modifier.height(8.dp))
        if (aps.isNotEmpty()) {
            val congestion = remember(aps.toList()) { WifiScanner.channelCongestion(aps) }
            Text(buildBandSummary(aps),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(aps) { ap ->
                    ApCard(ap, sameChannel = congestion[ap.band to ap.channel] ?: 1)
                }
            }
        }
    }
}

@Composable
private fun ApCard(ap: WifiAp, sameChannel: Int) {
    val strength = signalLevel(ap.rssiDbm)
    val barColor = when (strength) {
        4 -> Color(0xFF2E7D32)
        3 -> Color(0xFF689F38)
        2 -> Color(0xFFF9A825)
        1 -> Color(0xFFEF6C00)
        else -> Color(0xFFD32F2F)
    }
    ElevatedCard(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(40.dp).clip(RoundedCornerShape(10.dp))
                    .background(barColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.Wifi, null, tint = barColor)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(ap.ssid, style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold, maxLines = 1)
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(ap.bssid, fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Chip(ap.band, barColor)
                    Spacer(Modifier.width(4.dp))
                    Chip("Ch ${ap.channel}",
                        if (sameChannel > 1) Color(0xFFD32F2F)
                        else MaterialTheme.colorScheme.outline)
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        if (ap.security == "开放") Icons.Default.LockOpen else Icons.Default.Lock,
                        null, modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(2.dp))
                    Text(ap.security,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("${ap.rssiDbm} dBm",
                    style = MaterialTheme.typography.titleSmall,
                    color = barColor, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(2.dp))
                SignalBars(strength, barColor)
            }
        }
    }
}

@Composable
private fun SignalBars(level: Int, color: Color) {
    Row(verticalAlignment = Alignment.Bottom) {
        for (i in 1..4) {
            Box(
                Modifier.padding(horizontal = 1.dp)
                    .width(4.dp).height((i * 4).dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(if (i <= level) color else color.copy(alpha = 0.2f))
            )
        }
    }
}

@Composable
private fun Chip(text: String, color: Color) {
    Box(
        Modifier.clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelSmall, color = color)
    }
}

private fun signalLevel(rssi: Int): Int = when {
    rssi >= -50 -> 4
    rssi >= -60 -> 3
    rssi >= -70 -> 2
    rssi >= -80 -> 1
    else -> 0
}

private fun buildBandSummary(aps: List<WifiAp>): String {
    val by = aps.groupingBy { it.band }.eachCount()
    return "2.4G ${by["2.4G"] ?: 0}  ·  5G ${by["5G"] ?: 0}  ·  6G ${by["6G"] ?: 0}"
}
