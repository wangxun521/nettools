package com.example.nettools.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.nettools.core.LanHost
import com.example.nettools.core.LanScanner
import com.example.nettools.core.LanSubnet
import com.example.nettools.core.rememberPrefString
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

@Composable
fun LanScanScreen() {
    val ctx = LocalContext.current
    val detected = remember { LanScanner.detectSubnet(ctx) }
    var subnetCidr by rememberPrefString("lan_subnet", detected?.cidr ?: "192.168.1.0/24")
    var concurrency by rememberPrefString("lan_concurrency", "64")

    val hosts = remember { mutableStateListOf<LanHost>() }
    var job by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()
    val running = job?.isActive == true

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        if (detected != null) {
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text("当前网络", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(detected.cidr, fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold)
                    Row {
                        detected.self?.let {
                            Text("本机 $it",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.width(8.dp))
                        }
                        detected.gateway?.let {
                            Text("网关 $it",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Text("共 ${detected.totalHosts} 个可能地址",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(subnetCidr, { subnetCidr = it },
                label = { Text("子网 CIDR") }, singleLine = true,
                modifier = Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            OutlinedTextField(concurrency,
                { concurrency = it.filter(Char::isDigit) },
                label = { Text("并发") }, singleLine = true,
                modifier = Modifier.width(96.dp))
        }
        Spacer(Modifier.height(8.dp))
        Row {
            FilledTonalButton(
                enabled = !running && subnetCidr.contains('/'),
                onClick = {
                    hosts.clear()
                    val cidrParts = subnetCidr.split('/')
                    val target = LanSubnet(
                        cidr = subnetCidr,
                        gateway = detected?.gateway,
                        self = detected?.self,
                        totalHosts = (1 shl (32 - (cidrParts.getOrNull(1)?.toIntOrNull() ?: 24))) - 2,
                    )
                    val c = concurrency.toIntOrNull()?.coerceIn(1, 256) ?: 64
                    job = scope.launch {
                        LanScanner.scan(target, c).collect { hosts.add(it) }
                    }
                },
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Default.PlayArrow, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (running) "扫描中…" else "开始扫描")
            }
            Spacer(Modifier.width(8.dp))
            OutlinedButton(enabled = running, onClick = { job?.cancel() },
                modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.Stop, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("停止")
            }
        }
        if (running) {
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        Spacer(Modifier.height(8.dp))
        Text("发现 ${hosts.size} 台在线设备",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))

        val sorted = hosts.sortedBy { ipSortKey(it.ip) }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(sorted) { HostCard(it) }
        }
    }
}

@Composable
private fun HostCard(h: LanHost) {
    val (icon, color, tag) = when {
        h.isSelf -> Triple(Icons.Default.Person, Color(0xFF1976D2), "本机")
        h.isGateway -> Triple(Icons.Default.Router, Color(0xFFE64A19), "网关")
        else -> Triple(Icons.Default.Computer, Color(0xFF455A64), null)
    }
    ElevatedCard(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(40.dp).clip(RoundedCornerShape(10.dp))
                    .background(color.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) { Icon(icon, null, tint = color) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(h.ip, fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold)
                    if (tag != null) {
                        Spacer(Modifier.width(6.dp))
                        TagChip(tag, color)
                    }
                }
                h.hostname?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                h.mac?.let {
                    Text(it.uppercase(), fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            h.rttMs?.let {
                Text("${it}ms",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontFamily = FontFamily.Monospace)
            }
        }
    }
}

@Composable
private fun TagChip(text: String, color: Color) {
    Box(Modifier.clip(RoundedCornerShape(6.dp))
        .background(color.copy(alpha = 0.15f))
        .padding(horizontal = 6.dp, vertical = 2.dp)) {
        Text(text, style = MaterialTheme.typography.labelSmall,
            color = color, fontWeight = FontWeight.SemiBold)
    }
}

private fun ipSortKey(ip: String): Long =
    ip.split('.').fold(0L) { acc, s -> (acc shl 8) or ((s.toIntOrNull() ?: 0).toLong()) }
