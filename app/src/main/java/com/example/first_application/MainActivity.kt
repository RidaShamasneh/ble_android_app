package com.example.first_application

import android.Manifest
import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresPermission
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import com.example.first_application.ui.theme.First_applicationTheme
import java.util.*

class MainActivity : ComponentActivity() {

    private lateinit var bluetoothAdapter: BluetoothAdapter

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            permissions.entries.forEach {
                Log.d("BLE", "${it.key} = ${it.value}")
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val bluetoothManager = getSystemService(BluetoothManager::class.java)
        bluetoothAdapter = bluetoothManager.adapter

        requestBlePermissions()

        setContent {
            First_applicationTheme {
                BLEScreen(bluetoothAdapter)
            }
        }
    }

    private fun requestBlePermissions() {
        requestPermissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT
            )
        )
    }
}

@Composable
fun BLEScreen(bluetoothAdapter: BluetoothAdapter) {
    val context = LocalContext.current
    var devices by remember { mutableStateOf(listOf<BluetoothDevice>()) }
    var scanning by remember { mutableStateOf(false) }
    var connectedDevice by remember { mutableStateOf<String?>(null) }

    val scanner = bluetoothAdapter.bluetoothLeScanner

    val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            if (!devices.contains(device)) {
                devices = devices + device
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("BLE Scanner", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))

        Button(onClick = {
            if (!scanning) {
                if (ActivityCompat.checkSelfPermission(
                        context,
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
            val name = device.name ?: "Unknown"
            val address = device.address
            Text(
                text = "$name ($address)",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        connectToDevice(context, device) { connectedName ->
                            connectedDevice = connectedName
                        }
                    }
                    .padding(8.dp)
            )
        }

        connectedDevice?.let {
            Spacer(modifier = Modifier.height(16.dp))
            Text("Connected to: $it", style = MaterialTheme.typography.bodyLarge)
        }
    }
}
@RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
fun connectToDevice(context: Context, device: BluetoothDevice, onConnected: (String) -> Unit) {
    device.connectGatt(context, false, object : BluetoothGattCallback() {
        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.d("BLE", "Connected to ${device.name ?: device.address}")
                gatt.discoverServices()
                onConnected(device.name ?: device.address)
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.d("BLE", "Disconnected from ${device.name ?: device.address}")
            }
        }

        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                gatt.services.forEach { service ->
                    Log.d("BLE", "Service: ${service.uuid}")
                    service.characteristics.forEach { characteristic ->
                        Log.d("BLE", "Characteristic: ${characteristic.uuid}")
                        // Example: read characteristic
                        gatt.readCharacteristic(characteristic)
                    }
                }
            }
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val value = characteristic.value?.joinToString { it.toString() }
                Log.d("BLE", "Read from ${characteristic.uuid}: $value")
            }
        }
    })
}