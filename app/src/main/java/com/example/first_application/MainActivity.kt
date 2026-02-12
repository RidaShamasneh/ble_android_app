package com.example.first_application

import android.os.Handler
import android.os.Looper

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
    var batteryLevel by remember { mutableStateOf<Int?>(null) }

    val scanner = bluetoothAdapter.bluetoothLeScanner

    val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            if (!devices.contains(device)) {
                devices = devices + device
            }
        }
    }

    Column(modifier = Modifier
        .fillMaxSize()
        .padding(16.dp)) {
        Text("BLE Battery Reader", style = MaterialTheme.typography.headlineMedium)
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
                        connectToBatteryService(context, device) { level ->
                            batteryLevel = level
                        }
                    }
                    .padding(8.dp)
            )
        }

        batteryLevel?.let {
            Spacer(modifier = Modifier.height(16.dp))
            Text("Battery Level: $it%", style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
fun connectToBatteryService(
    context: Context,
    device: BluetoothDevice,
    onBatteryLevelRead: (Int) -> Unit
) {
    val BATTERY_SERVICE_UUID = UUID.fromString("0000180F-0000-1000-8000-00805f9b34fb")
    val BATTERY_LEVEL_UUID = UUID.fromString("00002A19-0000-1000-8000-00805f9b34fb")
    val CCCD_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    device.connectGatt(context, false, object : BluetoothGattCallback() {

        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.d("BLE", "Connected to ${device.name ?: device.address}")
                gatt.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.d("BLE", "Disconnected from ${device.name ?: device.address}")
                gatt.close()
            }
        }

        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val batteryService = gatt.getService(BATTERY_SERVICE_UUID)
                val batteryChar = batteryService?.getCharacteristic(BATTERY_LEVEL_UUID)
                if (batteryChar != null) {
                    // Read once
                    gatt.readCharacteristic(batteryChar)

                    /*
                    Delay before enabling notifications:
                    On Android, the GATT stack sometimes isn’t ready to accept descriptor writes immediately after discoverServices().
                    A common workaround is to introduce a small delay before attempting to enable notifications.
                    */
                    Handler(Looper.getMainLooper()).postDelayed({
                        gatt.setCharacteristicNotification(batteryChar, true)
                        val descriptor = batteryChar.getDescriptor(CCCD_UUID)
                        if (descriptor != null) {
                            descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                            gatt.writeDescriptor(descriptor)
                        }
                    }, 500) // 500 ms delay


                } else {
                    Log.e("BLE", "Battery service not found")
                }
            }
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int
        ) {
            Log.d("BLE", "onDescriptorWrite called")
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d("BLE", "Notifications enabled for ${descriptor.characteristic.uuid}")
            } else {
                Log.e("BLE", "Failed to enable notifications")
            }
        }


        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS &&
                characteristic.uuid == BATTERY_LEVEL_UUID
            ) {
                val a = characteristic.value[0]
                Log.d("BLE", "characteristic.value --> : $a")
                val batteryPercent = characteristic.value[0].toInt() and 0xFF
                Log.d("BLE", "Battery Level (read): $batteryPercent%")
                onBatteryLevelRead(batteryPercent)
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            Log.d("BLE", "Battery Level (notify)!!")
            if (characteristic.uuid == BATTERY_LEVEL_UUID) {
                val batteryPercent = characteristic.value[0].toInt() and 0xFF
                Log.d("BLE", "Battery Level (notify): $batteryPercent%")
                onBatteryLevelRead(batteryPercent)
            }
        }
    })
}
