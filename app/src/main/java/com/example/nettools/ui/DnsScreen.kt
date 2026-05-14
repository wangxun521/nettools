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
import com.example.nettools.core.DnsResult
import com.example.nettools.core.DnsScanner
import com.example.nettools.core.rememberPrefString
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private val ALL_TYPES = listOf("A", "AAAA", "CNAME", "MX", "TXT", "NS", "SOA", "SRV", "CAA")

@Composable
fun DnsScreen() {
    var domain by rememberPrefString("dns_domain", "example.com")
    var dnsServer by rememberPrefString("dns_server", "")
    val selected = remember { mutableStateMapOf<String, Boolean>().apply {
        ALL_TYPES.forEach { put(it, it == "A" || it == "AAAA" || it == "MX" || it == "TXT" || it == "NS") }
    } }
    val results = remember { mutableStateListOf<DnsResult>() }
    var job by remember { mutableStateOf<Job?>(null) }
    var running by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(Modifier.padding(16.dp).fillMaxSize()) {
        OutlinedTextField(domain, { domain = it }, label = { Text("域名") },
            singleLine = true, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(dnsServer, { dnsServer = it },
            label = { Text("DNS 服务器 (留空 = 系统)") },
            placeholder = { Text("8.8.8.8 / 1.1.1.1:53") },
            singleLine = true, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        FlowRowCompat {
            ALL_TYPES.forEach { t ->
                FilterChip(
                    selected = selected[t] == true,
                    onClick = { selected[t] = !(selected[t] ?: false) },
                    label = { Text(t) },
                    modifier = Modifier.padding(end = 6.dp, bottom = 4.dp),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Row {
            Button(
                enabled = !running && domain.isNotBlank(),
                onClick = {
                    results.clear()
                    val types = ALL_TYPES.filter { selected[it] == true }.ifEmpty { ALL_TYPES }
                    running = true
                    job = scope.launch {
                        try {
                            val r = DnsScanner.scan(domain.trim(),
                                dnsServer.trim().ifBlank { null }, types)
                            results.addAll(r)
                        } finally { running = false }
                    }
                }
            ) { Text("扫描") }
            Spacer(Modifier.width(8.dp))
            if (running) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
        }
        Spacer(Modifier.height(8.dp))
        Divider()
        LazyColumn(Modifier.weight(1f)) {
            items(results) { res ->
                Column(Modifier.padding(vertical = 6.dp)) {
                    Text(
                        "${res.type}  (${res.tookMs} ms)",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    if (res.error != null && res.records.isEmpty()) {
                        Text(
                            "  ✗ ${res.error}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    } else if (res.records.isEmpty()) {
                        Text("  (无记录)", style = MaterialTheme.typography.bodySmall)
                    } else {
                        res.records.forEach { rec ->
                            Text(
                                "  ${rec.name} ${rec.ttl}s  ${rec.data}",
                                fontFamily = FontFamily.Monospace,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
                Divider()
            }
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun FlowRowCompat(content: @Composable () -> Unit) {
    androidx.compose.foundation.layout.FlowRow { content() }
}
