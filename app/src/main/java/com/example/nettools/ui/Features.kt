package com.example.nettools.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.Traffic
import androidx.compose.material.icons.filled.NetworkPing
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SettingsEthernet
import androidx.compose.material.icons.filled.SignalWifi4Bar
import androidx.compose.material.icons.filled.Speed
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
    Feature("iperf3", "iperf3 测速", "TCP/UDP 带宽实测",
        Icons.Default.Speed, Color(0xFF455A64)),
    Feature("cell", "基站扫描", "周边 2G/3G/4G/5G 小区",
        Icons.Default.CellTower, Color(0xFF512DA8)),
    Feature("lan", "局域网扫描", "发现同网段在线设备",
        Icons.Default.Hub, Color(0xFF00695C)),
    Feature("discover", "服务发现", "mDNS / SSDP / UPnP",
        Icons.Default.Devices, Color(0xFF00838F)),
    Feature("wol", "Wake-on-LAN", "魔法包远程开机",
        Icons.Default.PowerSettingsNew, Color(0xFFE65100)),
    Feature("latency", "延迟监控", "连续 ping + 折线图",
        Icons.Default.Timeline, Color(0xFF1976D2)),
    Feature("traffic", "流量监控", "实时上下行速率",
        Icons.Default.Traffic, Color(0xFF455A64)),
    Feature("dnsmon", "DNS 监控", "解析时间持续跟踪",
        Icons.Default.GraphicEq, Color(0xFF6A1B9A)),
)
