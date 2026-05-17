package com.example.nettools.ui

import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.nettools.core.Ping
import com.example.nettools.core.rememberPrefString
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

private const val MAX_POINTS = 120

@Composable
fun LatencyMonitorScreen() {
    var host by rememberPrefString("latency_host", "8.8.8.8")
    var intervalSec by rememberPrefString("latency_interval", "1")

    val points = remember { mutableStateListOf<Float?>() }
    var running by remember { mutableStateOf(false) }
    var job by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()
    var sent by remember { mutableIntStateOf(0) }

    val rtts = points.filterNotNull()
    val received = rtts.size
    val lost = sent - received
    val lossRate = if (sent > 0) lost * 100.0 / sent else 0.0

    Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
        ElevatedCard {
            Column(Modifier.padding(16.dp)) {
                OutlinedTextField(host, { host = it }, label = { Text("主机 / IP") },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(intervalSec,
                    { intervalSec = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("间隔（秒）") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(12.dp))
                Row {
                    FilledTonalButton(
                        enabled = !running && host.isNotBlank(),
                        onClick = {
                            points.clear(); sent = 0
                            running = true
                            val iv = intervalSec.toDoubleOrNull()?.coerceAtLeast(0.2) ?: 1.0
                            job = scope.launch {
                                try {
                                    Ping.stream(host.trim(), count = 0, intervalSec = iv).collect { ln ->
                                        // 解析 RTT；没有 RTT 的行（DNS 解析等）忽略
                                        if (ln.seq != null) {
                                            sent++
                                            points.add(ln.rttMs?.toFloat())
                                            while (points.size > MAX_POINTS) points.removeAt(0)
                                        }
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

        // 大数字 + 统计
        ElevatedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                val latest = rtts.lastOrNull()
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(latest?.let { "%.1f".format(it) } ?: "—",
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary)
                    Text(" ms", style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(8.dp))
                LineChart(values = points,
                    modifier = Modifier.fillMaxWidth().height(160.dp),
                    lineColor = MaterialTheme.colorScheme.primary)
            }
        }

        if (rtts.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatPill("最小", "%.1f ms".format(rtts.min()), Modifier.weight(1f))
                StatPill("平均", "%.1f ms".format(rtts.average()), Modifier.weight(1f))
                StatPill("最大", "%.1f ms".format(rtts.max()), Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatPill("发送", sent.toString(), Modifier.weight(1f))
                StatPill("接收", received.toString(), Modifier.weight(1f))
                StatPill("丢包", "%.1f%%".format(lossRate), Modifier.weight(1f))
            }
        }
    }
}
