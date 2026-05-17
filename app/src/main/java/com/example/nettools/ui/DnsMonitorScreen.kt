package com.example.nettools.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.nettools.core.DnsScanner
import com.example.nettools.core.rememberPrefString
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val MAX_POINTS = 60

@Composable
fun DnsMonitorScreen() {
    var domain by rememberPrefString("dnsmon_domain", "www.google.com")
    var dnsServer by rememberPrefString("dnsmon_server", "")
    var intervalSec by rememberPrefString("dnsmon_interval", "2")

    val points = remember { mutableStateListOf<Float?>() }
    var running by remember { mutableStateOf(false) }
    var job by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()
    var sent by remember { mutableIntStateOf(0) }
    var lastError by remember { mutableStateOf<String?>(null) }

    val times = points.filterNotNull()
    val received = times.size
    val lost = sent - received
    val lossRate = if (sent > 0) lost * 100.0 / sent else 0.0

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        ElevatedCard {
            Column(Modifier.padding(16.dp)) {
                OutlinedTextField(domain, { domain = it },
                    label = { Text("域名") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(dnsServer, { dnsServer = it },
                    label = { Text("DNS 服务器（留空 = 系统）") },
                    placeholder = { Text("8.8.8.8 / 1.1.1.1") },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(intervalSec,
                    { intervalSec = it.filter(Char::isDigit) },
                    label = { Text("间隔（秒）") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(12.dp))
                Row {
                    FilledTonalButton(
                        enabled = !running && domain.isNotBlank(),
                        onClick = {
                            points.clear(); sent = 0; lastError = null
                            running = true
                            val iv = (intervalSec.toLongOrNull() ?: 2).coerceAtLeast(1) * 1000
                            job = scope.launch {
                                try {
                                    while (true) {
                                        sent++
                                        val results = DnsScanner.scan(
                                            domain.trim(),
                                            dnsServer.trim().ifBlank { null },
                                            listOf("A"),
                                        )
                                        val r = results.firstOrNull()
                                        if (r != null && r.records.isNotEmpty()) {
                                            points.add(r.tookMs.toFloat())
                                        } else {
                                            points.add(null)
                                            lastError = r?.error
                                        }
                                        while (points.size > MAX_POINTS) points.removeAt(0)
                                        delay(iv)
                                    }
                                } finally { running = false }
                            }
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Default.PlayArrow, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("开始")
                    }
                    Spacer(Modifier.width(8.dp))
                    OutlinedButton(enabled = running, onClick = { job?.cancel() },
                        modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Stop, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("停止")
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        ElevatedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                val latest = times.lastOrNull()
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(latest?.let { "%.0f".format(it) } ?: "—",
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.tertiary)
                    Text(" ms", style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(8.dp))
                LineChart(values = points,
                    modifier = Modifier.fillMaxWidth().height(140.dp),
                    lineColor = MaterialTheme.colorScheme.tertiary)
            }
        }
        if (times.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatPill("最小", "%.0f ms".format(times.min()), Modifier.weight(1f))
                StatPill("平均", "%.0f ms".format(times.average()), Modifier.weight(1f))
                StatPill("最大", "%.0f ms".format(times.max()), Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatPill("查询", sent.toString(), Modifier.weight(1f))
                StatPill("成功", received.toString(), Modifier.weight(1f))
                StatPill("失败率", "%.1f%%".format(lossRate), Modifier.weight(1f))
            }
        }
        lastError?.let {
            Spacer(Modifier.height(8.dp))
            Text("最近错误：$it",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error)
        }
    }
}
