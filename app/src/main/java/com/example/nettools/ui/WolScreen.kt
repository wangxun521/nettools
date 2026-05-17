package com.example.nettools.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.nettools.core.LanScanner
import com.example.nettools.core.WakeOnLan
import com.example.nettools.core.rememberPrefString
import kotlinx.coroutines.launch

@Composable
fun WolScreen() {
    val ctx = LocalContext.current
    val detected = remember { LanScanner.detectSubnet(ctx) }
    val defaultBroadcast = remember(detected) {
        detected?.cidr?.let { cidr ->
            // 把 CIDR 的网络地址末段改成 255 当广播地址（仅 /24 简化估算）
            val (network, prefix) = cidr.split('/')
            if (prefix == "24") network.substringBeforeLast('.') + ".255"
            else "255.255.255.255"
        } ?: "255.255.255.255"
    }

    var mac by rememberPrefString("wol_mac", "")
    var broadcast by rememberPrefString("wol_broadcast", defaultBroadcast)
    var ports by rememberPrefString("wol_ports", "9,7")
    var sending by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        ElevatedCard {
            Column(Modifier.padding(16.dp)) {
                OutlinedTextField(mac, { mac = it.uppercase() },
                    label = { Text("目标 MAC 地址") },
                    placeholder = { Text("AA:BB:CC:DD:EE:FF") },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(broadcast, { broadcast = it },
                    label = { Text("广播地址") },
                    placeholder = { Text("192.168.1.255") },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(ports, { ports = it },
                    label = { Text("端口（逗号分隔）") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(12.dp))
                Button(
                    enabled = !sending && mac.isNotBlank(),
                    onClick = {
                        sending = true; result = null
                        val portList = ports.split(',').mapNotNull { it.trim().toIntOrNull() }
                            .ifEmpty { listOf(9, 7) }
                        scope.launch {
                            val r = WakeOnLan.send(mac.trim(), broadcast.trim(), portList)
                            result = r.fold(
                                { "已发送魔法包到 $broadcast :${portList.joinToString("/")}" },
                                { "发送失败：${it.message ?: it.javaClass.simpleName}" },
                            )
                            sending = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.Send, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (sending) "发送中…" else "唤醒")
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        result?.let {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (it.startsWith("已发送"))
                        MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Text(it, Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodyMedium)
            }
        }
        Spacer(Modifier.height(16.dp))
        Text("使用提示", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(4.dp))
        Text(
            "• 目标设备 BIOS / 网卡设置里必须开启 \"Wake on LAN\"\n" +
            "• 手机和目标必须在同一局域网（广播包不能跨路由）\n" +
            "• 广播地址默认 /24 末段 255；自定子网请按实际填\n" +
            "• 端口 9 (Discard) 和 7 (Echo) 是 WoL 惯例",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
