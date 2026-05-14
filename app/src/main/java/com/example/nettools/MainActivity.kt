package com.example.nettools

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.nettools.ui.CellScreen
import com.example.nettools.ui.DnsScreen
import com.example.nettools.ui.FEATURES
import com.example.nettools.ui.HomeScreen
import com.example.nettools.ui.Iperf3Screen
import com.example.nettools.ui.IpGeoScreen
import com.example.nettools.ui.LanScanScreen
import com.example.nettools.ui.PingScreen
import com.example.nettools.ui.PortScanScreen
import com.example.nettools.ui.TracerouteScreen
import com.example.nettools.ui.WhoisScreen
import com.example.nettools.ui.WifiScanScreen
import com.example.nettools.ui.WifiScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { AppRoot() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppRoot() {
    val ctx = LocalContext.current
    val dark = isSystemInDarkTheme()
    val scheme = when {
        Build.VERSION.SDK_INT >= 31 ->
            if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> darkColorScheme()
        else -> lightColorScheme()
    }

    MaterialTheme(colorScheme = scheme) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            val nav = rememberNavController()
            val backStack by nav.currentBackStackEntryAsState()
            val currentRoute = backStack?.destination?.route
            val currentFeature = FEATURES.firstOrNull { it.route == currentRoute }
            val isHome = currentRoute == null || currentRoute == "home"

            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text(currentFeature?.title ?: "NetTools") },
                        navigationIcon = {
                            if (!isHome) {
                                IconButton(onClick = { nav.popBackStack() }) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                                }
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                        ),
                    )
                }
            ) { pad ->
                Box(Modifier.padding(pad)) {
                    NavHost(navController = nav, startDestination = "home") {
                        composable("home") { HomeScreen(onSelect = { nav.navigate(it) }) }
                        composable("ping") { PingScreen() }
                        composable("traceroute") { TracerouteScreen() }
                        composable("dns") { DnsScreen() }
                        composable("portscan") { PortScanScreen() }
                        composable("wifi_info") { WifiScreen() }
                        composable("wifi_scan") { WifiScanScreen() }
                        composable("whois") { WhoisScreen() }
                        composable("ipgeo") { IpGeoScreen() }
                        composable("iperf3") { Iperf3Screen() }
                        composable("cell") { CellScreen() }
                        composable("lan") { LanScanScreen() }
                    }
                }
            }
        }
    }
}
