package com.hect0x7.proxy

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class MainActivity : ComponentActivity() {
    private lateinit var preferences: ProxyPreferences
    private var settings by mutableStateOf(ProxySettings())
    private var addresses by mutableStateOf(emptyList<String>())
    private var batteryOptimizationIgnored by mutableStateOf(true)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        preferences = ProxyPreferences(this)
        settings = preferences.read()
        addresses = localIpv4Addresses()
        refreshBatteryOptimizationState()
        requestRuntimePermissions()

        if (settings.httpEnabled || settings.socksEnabled) {
            ProxyService.applySettings(this)
        }

        setContent {
            val runtime by ProxyService.runtime.collectAsState()
            ProxyTheme {
                ProxyScreen(
                    settings = settings,
                    runtime = runtime,
                    addresses = addresses,
                    batteryOptimizationIgnored = batteryOptimizationIgnored,
                    onSettingsChanged = ::updateSettings,
                    onRequestBatteryOptimizationExemption = ::requestBatteryOptimizationExemption,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        addresses = localIpv4Addresses()
        refreshBatteryOptimizationState()
    }

    private fun updateSettings(updated: ProxySettings) {
        if (updated == settings) return
        settings = updated
        preferences.write(updated)
        ProxyService.applySettings(this)
    }

    private fun refreshBatteryOptimizationState() {
        batteryOptimizationIgnored = Build.VERSION.SDK_INT < Build.VERSION_CODES.M ||
            getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName)
    }

    private fun requestBatteryOptimizationExemption() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || batteryOptimizationIgnored) return
        val request = Intent(
            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            Uri.parse("package:$packageName"),
        )
        runCatching { startActivity(request) }
            .onFailure { startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
    }

    private fun requestRuntimePermissions() {
        val missing = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        if (missing.isNotEmpty()) requestPermissions(missing.toTypedArray(), REQUEST_PERMISSIONS)
    }

    private companion object {
        const val REQUEST_PERMISSIONS = 100
    }
}
