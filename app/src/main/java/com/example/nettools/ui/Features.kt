package com.example.nettools.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.NetworkPing
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SettingsEthernet
import androidx.compose.material.icons.filled.SignalWifi4Bar
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiFind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

data class Feature(
    val route: String,
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val tint: Color,
)

val FEATURES = listOf(
    Feature("ping", "Ping", "测试主机连通性 / RTT",
        Icons.Default.NetworkPing, Color(0xFF1976D2)),
    Feature("traceroute", "Traceroute", "追踪到目标的逐跳路径",
        Icons.Default.Route, Color(0xFF7B1FA2)),
    Feature("dns", "DNS 扫描", "多记录类型并发查询",
        Icons.Default.Public, Color(0xFF00838F)),
    Feature("portscan", "端口扫描", "扫描 TCP 端口开放情况",
        Icons.Default.SettingsEthernet, Color(0xFFE64A19)),
    Feature("wifi_info", "Wi-Fi 信息", "当前网络详情",
        Icons.Default.Wifi, Color(0xFF2E7D32)),
    Feature("wifi_scan", "Wi-Fi 扫描", "周边热点 / 信号 / 信道",
        Icons.Default.WifiFind, Color(0xFFC2185B)),
    Feature("whois", "Whois", "查询域名注册信息",
        Icons.Default.Search, Color(0xFF5D4037)),
    Feature("ipgeo", "IP 定位", "IP 归属地 / ISP / AS",
        Icons.Default.LocationOn, Color(0xFFF57C00)),
)
