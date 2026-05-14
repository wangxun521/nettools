package com.example.nettools.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.example.nettools.core.Ping
import com.example.nettools.core.PingLine
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope

@Composable
fun PingScreen() {
    var host by remember { mutableStateOf("8.8.8.8") }
    var count by remember { mutableStateOf("10") }
    val lines = remember { mutableStateListOf<PingLine>() }
    var job by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()
    val running = job?.isActive == true

    Column(Modifier.padding(16.dp).fillMaxSize()) {
        OutlinedTextField(host, { host = it }, label = { Text("主机 / IP") },
            singleLine = true, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(count, { count = it.filter(Char::isDigit) },
            label = { Text("次数 (0 = 持续)") }, singleLine = true,
            modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        Row {
            Button(
                enabled = !running && host.isNotBlank(),
                onClick = {
                    lines.clear()
                    val n = count.toIntOrNull() ?: 4
                    job = scope.launch {
                        Ping.stream(host.trim(), n).collectLatest { lines.add(it) }
                    }
                }
            ) { Text("开始") }
            Spacer(Modifier.width(8.dp))
            OutlinedButton(enabled = running, onClick = { job?.cancel() }) { Text("停止") }
        }
        Spacer(Modifier.height(8.dp))
        Divider()
        LazyColumn(Modifier.weight(1f)) {
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
