package com.example.nettools.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Stop
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
import com.example.nettools.core.MDNS_TYPES
import com.example.nettools.core.MdnsDiscover
import com.example.nettools.core.MdnsService
import com.example.nettools.core.SsdpDevice
import com.example.nettools.core.SsdpDiscover
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

@Composable
fun DiscoverScreen() {
    var tab by remember { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = tab) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("mDNS / Bonjour") })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("SSDP / UPnP") })
        }
        if (tab == 0) MdnsTab() else SsdpTab()
    }
}

// ---------- mDNS Tab ----------

@Composable
private fun MdnsTab() {
    val ctx = LocalContext.current
    val services = remember { mutableStateMapOf<String, MdnsService>() }
    var running by remember { mutableStateOf(false) }
    var jobs by remember { mutableStateOf<List<Job>>(emptyList()) }
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row {
            FilledTonalButton(
                enabled = !running,
                onClick = {
                    services.clear()
                    running = true
                    jobs = MDNS_TYPES.map { (t, _) ->
                        scope.launch {
                            try {
                                MdnsDiscover.discover(ctx, t).collect { svc ->
                                    val key = "$t|${svc.name}"
                                    val existing = services[key]
                                    // 后续 resolve 完的 host/port 会覆盖前面占位
                                    if (existing == null || existing.host == null) services[key] = svc
                                }
                            } catch (_: Throwable) { /* swallow */ }
                        }
                    }
                },
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Default.Search, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (running) "扫描中…" else "开始扫描")
            }
            Spacer(Modifier.width(8.dp))
            OutlinedButton(
                enabled = running,
                onClick = {
                    jobs.forEach { it.cancel() }
                    jobs = emptyList()
                    running = false
                },
                modifier = Modifier.weight(1f),
            ) {
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
        Text("已发现 ${services.size} 个服务",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))

        val grouped = services.values.groupBy { it.type }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            grouped.forEach { (type, list) ->
                item(key = "h-$type") {
                    Text(
                        text = MDNS_TYPES.firstOrNull { type.startsWith(it.first) }?.second ?: type,
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                items(list, key = { "${type}|${it.name}" }) { svc -> MdnsCard(svc) }
            }
        }
    }
}

@Composable
private fun MdnsCard(svc: MdnsService) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text(svc.name, style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(2.dp))
            Text(
                buildString {
                    append(svc.host ?: "解析中…")
                    svc.port?.let { append(" : $it") }
                },
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (svc.attributes.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                svc.attributes.entries.take(5).forEach { (k, v) ->
                    Text("$k = $v",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

// ---------- SSDP Tab ----------

@Composable
private fun SsdpTab() {
    val devices = remember { mutableStateListOf<SsdpDevice>() }
    var running by remember { mutableStateOf(false) }
    var job by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row {
            FilledTonalButton(
                enabled = !running,
                onClick = {
                    devices.clear(); running = true
                    job = scope.launch {
                        try {
                            SsdpDiscover.discover(durationMs = 6000).collect { devices.add(it) }
                        } finally { running = false }
                    }
                },
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Default.Search, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (running) "搜索中…" else "搜索 UPnP")
            }
            Spacer(Modifier.width(8.dp))
            OutlinedButton(
                enabled = running, onClick = { job?.cancel() },
                modifier = Modifier.weight(1f),
            ) {
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
        Text("已发现 ${devices.size} 个设备",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(devices) { dev -> SsdpCard(dev) }
        }
    }
}

@Composable
private fun SsdpCard(d: SsdpDevice) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text(d.ip, style = MaterialTheme.typography.titleSmall,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold)
            d.server?.let {
                Spacer(Modifier.height(2.dp))
                Text(it, style = MaterialTheme.typography.bodySmall)
            }
            d.st?.let {
                Spacer(Modifier.height(2.dp))
                Text("ST: $it", style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            d.location?.let {
                Text("LOCATION: $it", style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2)
            }
        }
    }
}
