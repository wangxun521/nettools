package com.example.nettools.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.example.nettools.core.PortResult
import com.example.nettools.core.PortScanner
import com.example.nettools.core.rememberPrefString
import com.example.nettools.core.rememberPrefBool
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

@Composable
fun PortScanScreen() {
    var host by rememberPrefString("port_host", "scanme.nmap.org")
    var portsExpr by rememberPrefString("port_ports", "21,22,80,443,3306,3389,8080,8443")
    var concurrency by rememberPrefString("port_concurrency", "64")
    var showOpenOnly by rememberPrefBool("port_open_only", true)
    val results = remember { mutableStateListOf<PortResult>() }
    var job by remember { mutableStateOf<Job?>(null) }
    var totalCount by remember { mutableIntStateOf(0) }
    var running by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(Modifier.padding(16.dp).fillMaxSize()) {
        OutlinedTextField(host, { host = it }, label = { Text("主机 / IP") },
            singleLine = true, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(portsExpr, { portsExpr = it },
            label = { Text("端口（如 22,80,443 或 1-1024）") },
            singleLine = true, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            OutlinedTextField(concurrency,
                { concurrency = it.filter(Char::isDigit) },
                label = { Text("并发") }, singleLine = true,
                modifier = Modifier.width(110.dp))
            Spacer(Modifier.width(12.dp))
            Switch(checked = showOpenOnly, onCheckedChange = { showOpenOnly = it })
            Text("只看开放", style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(8.dp))
        Row {
            Button(
                enabled = !running && host.isNotBlank(),
                onClick = {
                    results.clear()
                    val ports = PortScanner.parsePorts(portsExpr)
                    totalCount = ports.size
                    val c = concurrency.toIntOrNull()?.coerceIn(1, 256) ?: 64
                    running = true
                    job = scope.launch {
                        try {
                            PortScanner.scan(host.trim(), ports, c).collect { results.add(it) }
                        } finally { running = false }
                    }
                }
            ) { Text("扫描") }
            Spacer(Modifier.width(8.dp))
            OutlinedButton(enabled = running, onClick = { job?.cancel() }) { Text("停止") }
            Spacer(Modifier.width(8.dp))
            if (running) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
        }
        Spacer(Modifier.height(8.dp))
        val openCount = results.count { it.open }
        Text("进度 ${results.size}/$totalCount，开放 $openCount",
            style = MaterialTheme.typography.bodySmall)
        Divider()
        val shown = if (showOpenOnly) results.filter { it.open } else results
        val sorted = shown.sortedBy { it.port }
        LazyColumn(Modifier.weight(1f)) {
            items(sorted) { r ->
                val color = if (r.open) Color(0xFF2E7D32) else Color.Gray
                Text(
                    text = "%5d  %-12s  %s  (%dms)".format(
                        r.port, r.service ?: "-",
                        if (r.open) "OPEN" else "closed", r.tookMs),
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                    color = color,
                    modifier = Modifier.padding(vertical = 2.dp),
                )
            }
        }
    }
}
