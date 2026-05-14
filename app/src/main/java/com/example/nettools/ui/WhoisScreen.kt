package com.example.nettools.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.example.nettools.core.Whois
import com.example.nettools.core.WhoisResponse
import com.example.nettools.core.rememberPrefString
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@Composable
fun WhoisScreen() {
    var domain by rememberPrefString("whois_domain", "example.com")
    val responses = remember { mutableStateListOf<WhoisResponse>() }
    var error by remember { mutableStateOf<String?>(null) }
    var job by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()
    val running = job?.isActive == true

    Column(Modifier.padding(16.dp).fillMaxSize()) {
        OutlinedTextField(domain, { domain = it }, label = { Text("域名") },
            singleLine = true, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        Row {
            Button(
                enabled = !running && domain.isNotBlank(),
                onClick = {
                    responses.clear(); error = null
                    job = scope.launch {
                        runCatching { Whois.lookup(domain.trim()) }
                            .onSuccess { responses.addAll(it) }
                            .onFailure { error = it.message ?: it.javaClass.simpleName }
                    }
                }
            ) { Text("查询") }
            Spacer(Modifier.width(8.dp))
            if (running) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
        }
        Spacer(Modifier.height(8.dp))
        Divider()
        if (error != null) {
            Text("✗ $error", color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(vertical = 8.dp))
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            responses.forEach { r ->
                Text("@ ${r.server}", style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
                Text(r.raw, fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
