package com.example.nettools.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.example.nettools.core.Hop
import com.example.nettools.core.Traceroute
import com.example.nettools.core.rememberPrefString
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

@Composable
fun TracerouteScreen() {
    var host by rememberPrefString("trace_host", "www.google.com")
    var maxHops by rememberPrefString("trace_max", "30")
    val hops = remember { mutableStateListOf<Hop>() }
    var job by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()
    val running = job?.isActive == true

    Column(Modifier.padding(16.dp).fillMaxSize()) {
        OutlinedTextField(host, { host = it }, label = { Text("主机 / IP") },
            singleLine = true, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(maxHops, { maxHops = it.filter(Char::isDigit) },
            label = { Text("最大跳数") }, singleLine = true,
            modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        Row {
            Button(
                enabled = !running && host.isNotBlank(),
                onClick = {
                    hops.clear()
                    val mh = maxHops.toIntOrNull()?.coerceIn(1, 64) ?: 30
                    job = scope.launch {
                        Traceroute.stream(host.trim(), mh).collect { hops.add(it) }
                    }
                }
            ) { Text("开始") }
            Spacer(Modifier.width(8.dp))
            OutlinedButton(enabled = running, onClick = { job?.cancel() }) { Text("停止") }
            Spacer(Modifier.width(8.dp))
            if (running) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
        }
        Spacer(Modifier.height(8.dp))
        Divider()
        LazyColumn(Modifier.weight(1f)) {
            items(hops) { h ->
                val addr = h.address ?: "*"
                val rtt = h.rttMs?.let { "${"%.1f".format(it)} ms" } ?: "—"
                val tag = if (h.reachedTarget) " ✓" else ""
                Text(
                    text = "%2d  %-32s  %s%s".format(h.ttl, addr, rtt, tag),
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(vertical = 2.dp),
                )
            }
        }
    }
}
