package com.example.nettools.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.example.nettools.core.Ping
import com.example.nettools.core.PingLine
import com.example.nettools.core.rememberPrefString
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@Composable
fun PingScreen() {
    var host by rememberPrefString("ping_host", "8.8.8.8")
    var count by rememberPrefString("ping_count", "10")
    val lines = remember { mutableStateListOf<PingLine>() }
    var job by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()
    val running = job?.isActive == true

    val rtts = lines.mapNotNull { it.rttMs }
    val avg = if (rtts.isNotEmpty()) rtts.average() else null
    val min = rtts.minOrNull()
    val max = rtts.maxOrNull()

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        ElevatedCard {
            Column(Modifier.padding(16.dp)) {
                OutlinedTextField(host, { host = it }, label = { Text("主机 / IP") },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(count, { count = it.filter(Char::isDigit) },
                    label = { Text("次数 (0 = 持续)") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(12.dp))
                Row {
                    FilledTonalButton(
                        enabled = !running && host.isNotBlank(),
                        onClick = {
                            lines.clear()
                            val n = count.toIntOrNull() ?: 4
                            job = scope.launch {
                                Ping.stream(host.trim(), n).collectLatest { lines.add(it) }
                            }
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Default.PlayArrow, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("开始")
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
            }
        }

        if (rtts.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatPill("最小", "%.1f ms".format(min), Modifier.weight(1f))
                StatPill("平均", "%.1f ms".format(avg), Modifier.weight(1f))
                StatPill("最大", "%.1f ms".format(max), Modifier.weight(1f))
            }
        }

        Spacer(Modifier.height(12.dp))
        ElevatedCard(Modifier.weight(1f).fillMaxWidth()) {
            LazyColumn(Modifier.padding(12.dp)) {
                items(lines) {
                    Text(
                        text = it.raw,
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(vertical = 2.dp),
                    )
                }
            }
        }
    }
}

@Composable
internal fun StatPill(label: String, value: String, modifier: Modifier = Modifier) {
    ElevatedCard(modifier) {
        Column(Modifier.padding(vertical = 10.dp, horizontal = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleSmall)
        }
    }
}
