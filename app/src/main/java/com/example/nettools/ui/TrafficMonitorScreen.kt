package com.example.nettools.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.nettools.core.TrafficMonitor
import com.example.nettools.core.TrafficSample
import com.example.nettools.core.humanBytes
import com.example.nettools.core.humanRate
import kotlinx.coroutines.flow.collect

private const val MAX_POINTS = 90

@Composable
fun TrafficMonitorScreen() {
    val rxSeries = remember { mutableStateListOf<Float?>() }
    val txSeries = remember { mutableStateListOf<Float?>() }
    var current by remember { mutableStateOf<TrafficSample?>(null) }

    LaunchedEffect(Unit) {
        TrafficMonitor.stream().collect { s ->
            current = s
            rxSeries.add(s.rxBytesPerSec.toFloat())
            txSeries.add(s.txBytesPerSec.toFloat())
            while (rxSeries.size > MAX_POINTS) rxSeries.removeAt(0)
            while (txSeries.size > MAX_POINTS) txSeries.removeAt(0)
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BigStat(
                label = "下行",
                value = current?.rxBytesPerSec?.humanRate() ?: "—",
                icon = Icons.Default.ArrowDownward,
                color = Color(0xFF1976D2),
                modifier = Modifier.weight(1f),
            )
            BigStat(
                label = "上行",
                value = current?.txBytesPerSec?.humanRate() ?: "—",
                icon = Icons.Default.ArrowUpward,
                color = Color(0xFFE64A19),
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(Modifier.height(12.dp))
        ElevatedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("下行速率", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                LineChart(values = rxSeries,
                    modifier = Modifier.fillMaxWidth().height(120.dp),
                    lineColor = Color(0xFF1976D2))
            }
        }
        Spacer(Modifier.height(8.dp))
        ElevatedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("上行速率", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                LineChart(values = txSeries,
                    modifier = Modifier.fillMaxWidth().height(120.dp),
                    lineColor = Color(0xFFE64A19))
            }
        }

        Spacer(Modifier.height(12.dp))
        current?.let { s ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatPill("总下行", s.totalRxBytes.humanBytes(), Modifier.weight(1f))
                StatPill("总上行", s.totalTxBytes.humanBytes(), Modifier.weight(1f))
            }
        }

        Spacer(Modifier.height(12.dp))
        Text("数据来源：TrafficStats（设备启动以来累计）。包含 Wi-Fi + 蜂窝 + VPN 全部接口。",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun BigStat(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    modifier: Modifier = Modifier,
) {
    ElevatedCard(modifier) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = color, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text(label, style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(4.dp))
            Text(value, style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold, color = color)
        }
    }
}
