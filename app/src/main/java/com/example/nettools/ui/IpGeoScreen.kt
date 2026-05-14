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
import com.example.nettools.core.GeoResult
import com.example.nettools.core.IpGeo
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@Composable
fun IpGeoScreen() {
    var ip by remember { mutableStateOf("") }
    var res by remember { mutableStateOf<GeoResult?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var job by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()
    val running = job?.isActive == true

    Column(Modifier.padding(16.dp).fillMaxSize().verticalScroll(rememberScrollState())) {
        OutlinedTextField(ip, { ip = it },
            label = { Text("IP（留空 = 查本机出口 IP）") },
            placeholder = { Text("8.8.8.8") },
            singleLine = true, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        Row {
            Button(
                enabled = !running,
                onClick = {
                    res = null; error = null
                    job = scope.launch {
                        runCatching { IpGeo.lookup(ip.ifBlank { null }) }
                            .onSuccess {
                                if (it.status == "success") res = it
                                else error = it.message ?: "查询失败"
                            }
                            .onFailure { error = it.message ?: it.javaClass.simpleName }
                    }
                }
            ) { Text("查询") }
            Spacer(Modifier.width(8.dp))
            if (running) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
        }
        Spacer(Modifier.height(12.dp))
        Divider()
        if (error != null) {
            Text("✗ $error", color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(vertical = 8.dp))
        }
        res?.let { r ->
            Spacer(Modifier.height(8.dp))
            InfoRow2("IP", r.query)
            InfoRow2("国家", r.country ?: "—")
            InfoRow2("省/州", r.region ?: "—")
            InfoRow2("城市", r.city ?: "—")
            InfoRow2("ISP", r.isp ?: "—")
            InfoRow2("组织", r.org ?: "—")
            InfoRow2("AS", r.asn ?: "—")
            InfoRow2("时区", r.timezone ?: "—")
            InfoRow2("经纬度", if (r.lat != null && r.lon != null)
                "${"%.4f".format(r.lat)}, ${"%.4f".format(r.lon)}" else "—")
            InfoRow2("反查域名", r.reverse ?: "—")
        }
        Spacer(Modifier.height(16.dp))
        Text("数据源: ip-api.com (免费, 45 次/分钟)",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun InfoRow2(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(label, modifier = Modifier.width(80.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.bodySmall)
    }
}
