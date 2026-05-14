package com.example.nettools.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.nettools.core.CellScanner
import com.example.nettools.core.CellTower
import kotlinx.coroutines.flow.collect

private val REQUIRED_PERMS = arrayOf(
    Manifest.permission.ACCESS_FINE_LOCATION,
    Manifest.permission.READ_PHONE_STATE,
)

@Composable
fun CellScreen() {
    val ctx = LocalContext.current
    var granted by remember {
        mutableStateOf(REQUIRED_PERMS.all {
            ContextCompat.checkSelfPermission(ctx, it) == PackageManager.PERMISSION_GRANTED
        })
    }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result -> granted = result.values.all { it } }

    val supported = remember { CellScanner.supported(ctx) }
    val operator = remember { CellScanner.operatorName(ctx) }
    val towers = remember { mutableStateListOf<CellTower>() }

    LaunchedEffect(granted) {
        if (!granted) return@LaunchedEffect
        CellScanner.stream(ctx).collect {
            towers.clear(); towers.addAll(it)
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        if (!supported) {
            ElevatedCard(Modifier.fillMaxWidth()) {
                Text("此设备不支持蜂窝网络",
                    Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium)
            }
            return@Column
        }

        if (!granted) {
            ElevatedCard(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text("需要定位 + 电话状态权限才能扫描基站",
                        style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { launcher.launch(REQUIRED_PERMS) }) {
                        Text("授予权限")
                    }
                }
            }
        }

        if (operator != null) {
            Text("当前运营商：$operator",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
        }
        if (towers.isNotEmpty()) {
            Text("发现 ${towers.size} 个小区  ·  ${countByTech(towers)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(towers, key = { it.tech + (it.cellId ?: 0) + (it.pci ?: 0) }) {
                    TowerCard(it)
                }
            }
        } else if (granted) {
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text("扫描中…")
            }
        }
    }
}

@Composable
private fun TowerCard(c: CellTower) {
    val techColor = when (c.tech) {
        "5G" -> Color(0xFF6A1B9A)
        "4G" -> Color(0xFF2E7D32)
        "3G" -> Color(0xFFEF6C00)
        else -> Color(0xFFC62828)
    }
    val sig = signalLevelDbm(c.dbm)
    val sigColor = when (sig) {
        4 -> Color(0xFF2E7D32); 3 -> Color(0xFF689F38)
        2 -> Color(0xFFF9A825); 1 -> Color(0xFFEF6C00)
        else -> Color(0xFFD32F2F)
    }
    ElevatedCard(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(40.dp).clip(RoundedCornerShape(10.dp))
                    .background(techColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Default.CellTower, null, tint = techColor) }

            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TechChip(c.tech, techColor)
                    Spacer(Modifier.width(6.dp))
                    if (c.isServing) {
                        TechChip("驻留", MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(6.dp))
                    }
                    Text("PLMN ${c.operatorPlmn.ifBlank { "—" }}",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(4.dp))
                Text(buildIdLine(c),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace)
                Spacer(Modifier.height(2.dp))
                Text(buildBandLine(c),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (c.rsrp != null || c.rsrq != null || c.sinr != null) {
                    Text(buildRfLine(c),
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("${c.dbm} dBm",
                    style = MaterialTheme.typography.titleSmall,
                    color = sigColor, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(2.dp))
                SignalBarsCell(sig, sigColor)
            }
        }
    }
}

@Composable
private fun TechChip(text: String, color: Color) {
    Box(Modifier.clip(RoundedCornerShape(6.dp))
        .background(color.copy(alpha = 0.15f))
        .padding(horizontal = 6.dp, vertical = 2.dp)) {
        Text(text, style = MaterialTheme.typography.labelSmall,
            color = color, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun SignalBarsCell(level: Int, color: Color) {
    Row(verticalAlignment = Alignment.Bottom) {
        for (i in 1..4) {
            Box(Modifier.padding(horizontal = 1.dp)
                .width(4.dp).height((i * 4).dp)
                .clip(RoundedCornerShape(2.dp))
                .background(if (i <= level) color else color.copy(alpha = 0.2f)))
        }
    }
}

private fun signalLevelDbm(dbm: Int): Int = when {
    dbm >= -75 -> 4
    dbm >= -85 -> 3
    dbm >= -95 -> 2
    dbm >= -110 -> 1
    else -> 0
}

private fun buildIdLine(c: CellTower): String = buildString {
    c.cellId?.let { append("CID $it") }
    c.pci?.let { if (isNotEmpty()) append("  ·  "); append("PCI $it") }
    c.tacLac?.let { if (isNotEmpty()) append("  ·  "); append("TAC $it") }
    if (isEmpty()) append("—")
}

private fun buildBandLine(c: CellTower): String = buildString {
    c.band?.let { append(it) }
    c.arfcn?.let { if (isNotEmpty()) append("  ·  "); append("ARFCN $it") }
}

private fun buildRfLine(c: CellTower): String = buildString {
    c.rsrp?.let { append("RSRP $it") }
    c.rsrq?.let { if (isNotEmpty()) append("  RSRQ $it") }
    c.sinr?.let { if (isNotEmpty()) append("  SINR $it") }
}

private fun countByTech(list: List<CellTower>): String {
    val g = list.groupingBy { it.tech }.eachCount()
    return listOf("5G", "4G", "3G", "2G").joinToString("  ·  ") { t -> "$t ${g[t] ?: 0}" }
}
