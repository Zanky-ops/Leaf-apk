package com.example.nissanleafdiag

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

enum class Screen {
    Menu, Connect, Battery, Vehicle, Charge, Ecu, EcuDetail, Dtc, Logging, Monitor, Settings, About
}

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        LeafService.init(this)
        setContent {
            LeafTheme {
                AppRoot()
            }
        }
    }

    override fun onStop() {
        LeafService.saveSettings()
        super.onStop()
    }

    override fun onDestroy() {
        LeafService.stopMonitor()
        LeafService.saveSettings()
        super.onDestroy()
    }
}

@Composable
fun AppRoot() {
    var screen by remember { mutableStateOf(Screen.Menu) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 31) {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.BLUETOOTH_SCAN
                )
            )
        }
    }

    BackHandler(enabled = screen != Screen.Menu) {
        screen = if (screen == Screen.EcuDetail) Screen.Ecu else Screen.Menu
    }

    val back: () -> Unit = { screen = Screen.Menu }

    when (screen) {
        Screen.Menu -> MenuScreen(onOpen = { screen = it })
        Screen.Connect -> ConnectScreen(onBack = back)
        Screen.Battery -> BatteryScreen(onBack = back)
        Screen.Vehicle -> VehicleScreen(onBack = back)
        Screen.Charge -> ChargeScreen(onBack = back)
        Screen.Ecu -> EcuScreen(onBack = back, onOpenDetail = { screen = Screen.EcuDetail })
        Screen.EcuDetail -> EcuDetailScreen(onBack = { screen = Screen.Ecu })
        Screen.Dtc -> DtcScreen(onBack = back)
        Screen.Logging -> LoggingScreen(onBack = back)
        Screen.Monitor -> MonitorScreen(onBack = back)
        Screen.Settings -> SettingsScreen(onBack = back)
        Screen.About -> AboutScreen(onBack = back)
    }
}
