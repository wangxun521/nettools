package com.example.nettools.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.nettools.core.IperfConfig
import com.example.nettools.core.IperfEvent
import com.example.nettools.core.IperfInterval
import com.example.nettools.core.Iperf3Runner
import com.example.nettools.core.humanBits
import com.example.nettools.core.humanBytes
import com.example.nettools.core.rememberPrefBool
import com.example.nettools.core.rememberPrefString
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

@Composable
fun Iperf3Screen() {
    val ctx = LocalContext.current
    val available = remember { Iperf3Runner.isAvailable(ctx) }
    val version = remember { Iperf3Runner.version(ctx) }

    var host by rememberPrefString("iperf_host", "")
    var port by rememberPrefString("iperf_port", "5201")
    var duration by rememberPrefString("iperf_duration", "10")
    var parallel by rememberPrefString("iperf_parallel", "1")
    var reverse by rememberPrefBool("iperf_reverse", false)
    var udp by rememberPrefBool("iperf_udp", false)
    var bitrate by rememberPrefString("iperf_bitrate", "")

    val intervals = remember { mutableStateListOf<IperfInterval>() }
    var summarySent by remember { mutableStateOf<IperfInterval?>(null) }
    var summaryRecv by remember { mutableStateOf<IperfInterval?>(null) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var currentBps by remember { mutableDoubleStateOf(0.0) }

    var job by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()
    val running = job?.isActive == true

    Column(
        Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())
    ) {
        if (!available) {
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("⚠ iperf3 二进制未打包到 APK 中",
                        style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "去 GitHub Actions → 跑一次 \"Build iperf3 native binaries (one-shot)\"，" +
                                "生成 4 个 ABI 的 .so 并自动提交回仓库，然后重新构建 APK。",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            return@Column
        }

        ElevatedCard {
            Column(Modifier.padding(16.dp)) {
                OutlinedTextField(host, { host = it },
                    label = { Text("iperf3 服务器") },
                    placeholder = { Text("vps.example.com 或 IP") },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                Row {
                    OutlinedTextField(port,
                        { port = it.filter(Char::isDigit) },
                        label = { Text("端口") }, singleLine = true,
                        modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(8.dp))
                    OutlinedTextField(duration,
                        { duration = it.filter(Char::isDigit) },
                        label = { Text("时长(s)") }, singleLine = true,
                        modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(8.dp))
                    OutlinedTextField(parallel,
                        { parallel = it.filter(Char::isDigit) },
                        label = { Text("并发流") }, singleLine = true,
                        modifier = Modifier.weight(1f))
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FilterChip(selected = reverse, onClick = { reverse = !reverse },
                        label = { Text("下行 (-R)") })
                    Spacer(Modifier.width(6.dp))
                    FilterChip(selected = udp, onClick = { udp = !udp },
                        label = { Text("UDP") })
                    Spacer(Modifier.width(6.dp))
                    if (udp) {
                        OutlinedTextField(bitrate,
                            { bitrate = it.filter(Char::isDigit) },
                            label = { Text("Mbps 上限") }, singleLine = true,
                            modifier = Modifier.width(140.dp))
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row {
                    FilledTonalButton(
                        enabled = !running && host.isNotBlank(),
                        onClick = {
                            intervals.clear()
                            summarySent = null; summaryRecv = null
                            errorMsg = null; currentBps = 0.0
                            val cfg = IperfConfig(
                                host = host.trim(),
                                port = port.toIntOrNull() ?: 5201,
                                durationSec = duration.toIntOrNull() ?: 10,
                                parallel = parallel.toIntOrNull()?.coerceAtLeast(1) ?: 1,
                                reverse = reverse,
                                udp = udp,
                                bitrateMbps = bitrate.toIntOrNull().takeIf { udp },
                            )
                            job = scope.launch {
                                Iperf3Runner.run(ctx, cfg).collect { ev ->
                                    when (ev) {
                                        is IperfEvent.Interval -> {
                                            intervals.add(ev.data)
                                            currentBps = ev.data.bitsPerSec
                                        }
                                        is IperfEvent.Summary -> {
                                            summarySent = ev.sent; summaryRecv = ev.recv
                                        }
                                        is IperfEvent.Error -> errorMsg = ev.message
                                        is IperfEvent.Line, IperfEvent.Done -> Unit
                                    }
                                }
                            }
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Default.PlayArrow, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("开始测试")
                    }
                    Spacer(Modifier.width(8.dp))
                    OutlinedButton(enabled = running, onClick = { job?.cancel() },
                        modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Stop, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("停止")
                    }
                }
                if (version != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(version, style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        // 大数字仪表
        Spacer(Modifier.height(12.dp))
        ElevatedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally) {
                Text(if (running) (if (reverse) "下行速率" else "上行速率") else "结果",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                val display = when {
                    running -> currentBps.humanBits()
                    summaryRecv != null -> summaryRecv!!.bitsPerSec.humanBits()
                    summarySent != null -> summarySent!!.bitsPerSec.humanBits()
                    else -> "—"
                }
                Text(display, style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary)
                if (running) {
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
            }
        }

        if (summarySent != null || summaryRecv != null) {
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                summarySent?.let { StatPill("发送", it.bitsPerSec.humanBits(), Modifier.weight(1f)) }
                summaryRecv?.let { StatPill("接收", it.bitsPerSec.humanBits(), Modifier.weight(1f)) }
                summarySent?.transferBytes?.let {
                    StatPill("总流量", it.humanBytes(), Modifier.weight(1f))
                }
            }
            summarySent?.retransmits?.let {
                Text("重传: $it", style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 6.dp))
            }
        }

        if (errorMsg != null) {
            Spacer(Modifier.height(12.dp))
            Card(colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer)) {
                Text(errorMsg!!, modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer)
            }
        }

        if (intervals.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text("逐秒区间", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(6.dp))
            intervals.forEach { iv ->
                Text(
                    "[%5.1f-%5.1f] %s   %s%s".format(
                        iv.startSec, iv.endSec,
                        iv.transferBytes.humanBytes(),
                        iv.bitsPerSec.humanBits(),
                        iv.retransmits?.let { "   retr=$it" } ?: "",
                    ),
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}
