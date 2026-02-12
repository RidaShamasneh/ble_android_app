package com.example.first_application

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import com.example.first_application.ui.theme.First_applicationTheme

class MainActivity : ComponentActivity() {

    private lateinit var bluetoothAdapter: BluetoothAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val bluetoothManager = getSystemService(BluetoothManager::class.java)
        bluetoothAdapter = bluetoothManager.adapter

        setContent {
            First_applicationTheme {
                BLEScreen(bluetoothAdapter)
            }
        }
    }
}

@Composable
fun BLEScreen(bluetoothAdapter: BluetoothAdapter) {
    val context = LocalContext.current   // ✅ Correct way to get Context inside Composables
    var devices by remember { mutableStateOf(listOf<String>()) }
    var scanning by remember { mutableStateOf(false) }

    val scanner = bluetoothAdapter.bluetoothLeScanner

    val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val name = result.device.name ?: "Unknown"
            val address = result.device.address
            val entry = "$name ($address)"
            if (!devices.contains(entry)) {
                devices = devices + entry
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("BLE Scanner", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))

        Button(onClick = {
            if (!scanning) {
                if (ActivityCompat.checkSelfPermission(
                        context,   // ✅ use LocalContext.current here
                        Manifest.permission.BLUETOOTH_SCAN
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    scanner.startScan(scanCallback)
                    scanning = true
                } else {
                    Log.e("BLE", "Missing BLUETOOTH_SCAN permission")
                }
            } else {
                scanner.stopScan(scanCallback)
                scanning = false
            }
        }) {
            Text(if (scanning) "Stop Scan" else "Start Scan")
        }

        Spacer(modifier = Modifier.height(16.dp))

        devices.forEach { device ->
            Text(text = device, style = MaterialTheme.typography.bodyLarge)
        }
    }
}
