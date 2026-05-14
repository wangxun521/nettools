# NetTools — Android 13 网络工具包

一个运行在 Android 13（targetSdk 33，最低 24）上的网络小工具，包含三个功能：

| 功能 | 实现 |
|---|---|
| **Ping** | 调用系统 `/system/bin/ping`，流式解析 RTT/TTL/丢包 |
| **Traceroute** | 用 `ping -t <TTL>` 递增 TTL 实现（Android 无内置 traceroute） |
| **DNS 扫描** | [dnsjava](https://github.com/dnsjava/dnsjava) 异步并发查询 A/AAAA/CNAME/MX/TXT/NS/SOA/SRV/CAA，支持自定义 DNS 服务器 |

## 技术栈

- Kotlin 1.9 + Jetpack Compose（Material 3，动态色）
- Android Gradle Plugin 8.2，Gradle 8.5
- Coroutines + Flow，单 Activity 三标签页

## 目录结构

```
app/src/main/java/com/example/nettools/
├── MainActivity.kt              # 单 Activity + 三标签
├── core/
│   ├── Ping.kt                  # 流式 ping
│   ├── Traceroute.kt            # 基于 ping TTL 的 traceroute
│   └── DnsScanner.kt            # dnsjava 并发查询
└── ui/
    ├── PingScreen.kt
    ├── TracerouteScreen.kt
    └── DnsScreen.kt
```

## 构建

### 方法 A：Android Studio

1. Android Studio Hedgehog (2023.1) 或更高
2. `File → Open` 选择本目录
3. 等 Gradle 同步完成
4. 接上 Android 13 设备 / 模拟器，点 Run

### 方法 B：命令行（需先放置 gradle wrapper jar）

```bash
# 首次构建：在项目根目录下生成 wrapper jar
gradle wrapper

# 然后
./gradlew :app:assembleDebug
# 输出在 app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## 权限

只需要 `INTERNET` 和 `ACCESS_NETWORK_STATE`，全是 normal 权限，**无需运行时申请**。
应用不申请定位、存储等隐私权限。

## 使用提示

- **Ping**：次数填 `0` = 持续 ping，按"停止"中断。
- **Traceroute**：每跳一次 ICMP，超时 2s 显示 `*`，到达目标自动停止。慢是正常的，等几秒。
- **DNS**：
  - DNS 服务器留空 = 走当前网络默认 DNS（dnsjava 走系统配置）
  - 可填 `8.8.8.8` / `1.1.1.1` / `223.5.5.5` / `8.8.8.8:53` 等
  - 用复选 chip 选要查的记录类型，默认 A/AAAA/MX/TXT/NS

## 已知限制

- Android 上的 `ping` 是 setuid 程序，少数定制 ROM（特别是去 root 化的厂商系统）可能无法直接执行；这种情况下 ping/traceroute 会立刻报 IOException。
- Traceroute 用 ICMP（不是 UDP/TCP traceroute），如果中间节点过滤 ICMP 会显示 `*`。
- IPv6 traceroute 暂未单独优化，依赖系统 ping6（一般通过同一个 `ping` 二进制识别地址族）。

## 后续可以加

- DoH / DoT（dnsjava 已支持 `DohResolver`、`DoTResolver`，只需在 `DnsScanner.buildResolver` 里加一个分支）
- 结果导出 / 历史（Room）
- 子域名字典扫描
