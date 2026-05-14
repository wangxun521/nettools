package com.example.nettools

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.example.nettools.ui.DnsScreen
import com.example.nettools.ui.PingScreen
import com.example.nettools.ui.TracerouteScreen
import android.os.Build

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
    val scheme = if (Build.VERSION.SDK_INT >= 31) dynamicLightColorScheme(ctx) else lightColorScheme()
    MaterialTheme(colorScheme = scheme) {
        Surface(Modifier.fillMaxSize()) {
            var tab by remember { mutableIntStateOf(0) }
            val tabs = listOf("Ping", "Traceroute", "DNS")
            Scaffold(
                topBar = { TopAppBar(title = { Text("NetTools") }) }
            ) { pad ->
                androidx.compose.foundation.layout.Column(Modifier.padding(pad)) {
                    TabRow(selectedTabIndex = tab) {
                        tabs.forEachIndexed { i, t ->
                            Tab(selected = tab == i, onClick = { tab = i }, text = { Text(t) })
                        }
                    }
                    when (tab) {
                        0 -> PingScreen()
                        1 -> TracerouteScreen()
                        else -> DnsScreen()
                    }
                }
            }
        }
    }
}
