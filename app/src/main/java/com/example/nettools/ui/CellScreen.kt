package com.example.nettools.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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

private val REQUIRED_PERMS = arrayOf(
    Manifest.permission.ACCESS_FINE_LOCATION,
    Manifest.permission.READ_PHONE_STATE,
)

@OptIn(ExperimentalMaterial3Api::class)
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
    var selected by remember { mutableStateOf<CellTower?>(null) }

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
                items(towers) { tower ->
                    TowerCard(tower, onClick = { selected = tower })
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

    if (selected != null) {
        ModalBottomSheet(onDismissRequest = { selected = null }) {
            TowerDetail(selected!!)
        }
    }
}

@Composable
private fun TowerCard(c: CellTower, onClick: () -> Unit) {
    val techColor = techColor(c.tech)
    val sig = signalLevelDbm(c.dbm)
    val sigColor = signalColor(sig)
    ElevatedCard(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
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
                val band = buildBandLine(c)
                if (band.isNotEmpty()) {
                    Spacer(Modifier.height(2.dp))
                    Text(band,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(c.dbm?.let { "$it dBm" } ?: "—",
                    style = MaterialTheme.typography.titleSmall,
                    color = sigColor, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(2.dp))
                SignalBarsCell(sig, sigColor)
            }
        }
    }
}

@Composable
private fun TowerDetail(c: CellTower) {
    val techColor = techColor(c.tech)
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(52.dp).clip(RoundedCornerShape(14.dp))
                    .background(techColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Default.CellTower, null, tint = techColor,
                modifier = Modifier.size(28.dp)) }
            Spacer(Modifier.width(16.dp))
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(c.tech, style = MaterialTheme.typography.headlineSmall,
                        color = techColor, fontWeight = FontWeight.Bold)
                    if (c.isServing) {
                        Spacer(Modifier.width(8.dp))
                        TechChip("驻留中", MaterialTheme.colorScheme.primary)
                    }
                }
                Text("小区详情", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(20.dp))

        SectionTitle("信号")
        DetailRow("dBm", c.dbm?.let { "$it dBm" })
        DetailRow("信号等级", "${c.level} / 4")
        if (c.tech == "4G") {
            DetailRow("RSRP", c.rsrp?.let { "$it dBm" }, "参考信号接收功率")
            DetailRow("RSRQ", c.rsrq?.let { "$it dB" }, "参考信号接收质量")
            DetailRow("RSSNR", c.sinr?.let { "${it / 10.0} dB" }, "信噪比")
        } else if (c.tech == "5G") {
            DetailRow("RSRP", c.rsrp?.let { "$it dBm" }, "参考信号接收功率")
            DetailRow("RSRQ", c.rsrq?.let { "$it dB" }, "参考信号接收质量")
            DetailRow("SINR", c.sinr?.let { "$it dB" }, "信噪干扰比")
        }

        Spacer(Modifier.height(16.dp))
        SectionTitle("身份")
        DetailRow("PLMN", c.operatorPlmn.ifBlank { null }, "MCC + MNC")
        DetailRow("Cell ID", c.cellId?.toString(),
            if (c.tech == "5G") "NCI (36-bit)" else "CI / CID")
        DetailRow("PCI", c.pci?.toString(), "物理小区 ID (0..1007)")
        DetailRow(if (c.tech == "2G" || c.tech == "3G") "LAC" else "TAC",
            c.tacLac?.toString(),
            if (c.tech == "2G" || c.tech == "3G") "位置区码" else "跟踪区码")

        Spacer(Modifier.height(16.dp))
        SectionTitle("频率")
        DetailRow("频段", c.band)
        DetailRow("ARFCN", c.arfcn?.toString(),
            when (c.tech) {
                "4G" -> "E-UTRA 信道号"
                "5G" -> "NR 信道号"
                "3G" -> "UMTS 信道号"
                else -> "GSM 信道号"
            })

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(bottom = 4.dp))
}

@Composable
private fun DetailRow(label: String, value: String?, hint: String? = null) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(Modifier.width(96.dp)) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            if (hint != null) {
                Text(hint, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.width(12.dp))
        Text(value ?: "—",
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = FontFamily.Monospace,
            color = if (value == null) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.onSurface)
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

private fun techColor(tech: String): Color = when (tech) {
    "5G" -> Color(0xFF6A1B9A)
    "4G" -> Color(0xFF2E7D32)
    "3G" -> Color(0xFFEF6C00)
    else -> Color(0xFFC62828)
}

private fun signalColor(level: Int): Color = when (level) {
    4 -> Color(0xFF2E7D32); 3 -> Color(0xFF689F38)
    2 -> Color(0xFFF9A825); 1 -> Color(0xFFEF6C00)
    else -> Color(0xFFD32F2F)
}

private fun signalLevelDbm(dbm: Int?): Int = when {
    dbm == null -> 0
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

private fun countByTech(list: List<CellTower>): String {
    val g = list.groupingBy { it.tech }.eachCount()
    return listOf("5G", "4G", "3G", "2G").joinToString("  ·  ") { t -> "$t ${g[t] ?: 0}" }
}
