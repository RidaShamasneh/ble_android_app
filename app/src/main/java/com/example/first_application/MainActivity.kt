package com.example.first_application

import android.Manifest
import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
import java.nio.ByteBuffer
import java.nio.ByteOrder
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
    var roll by remember { mutableStateOf<Float?>(null) }
    var pitch by remember { mutableStateOf<Float?>(null) }
    var yaw by remember { mutableStateOf<Float?>(null) }

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
        Text("BLE Battery + UART Reader", style = MaterialTheme.typography.headlineMedium)
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
                        connectToServices(
                            context,
                            device,
                            onBatteryLevelRead = { level -> batteryLevel = level },
                            onUartDataReceived = { values ->
                                roll = values[0]
                                pitch = values[1]
                                yaw = values[2]
                            }
                        )
                    }
                    .padding(8.dp)
            )
        }

        batteryLevel?.let {
            Spacer(modifier = Modifier.height(16.dp))
            Text("Battery Level: $it%", style = MaterialTheme.typography.bodyLarge)
        }

        roll?.let { Text("Roll: $it") }
        pitch?.let { Text("Pitch: $it") }
        yaw?.let { Text("Yaw: $it") }
    }
}

@RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
fun connectToServices(
    context: Context,
    device: BluetoothDevice,
    onBatteryLevelRead: (Int) -> Unit,
    onUartDataReceived: (FloatArray) -> Unit
) {
    val BATTERY_SERVICE_UUID = UUID.fromString("0000180F-0000-1000-8000-00805f9b34fb")
    val BATTERY_LEVEL_UUID = UUID.fromString("00002A19-0000-1000-8000-00805f9b34fb")

    val UART_SERVICE_UUID = UUID.fromString("0000fee9-0000-1000-8000-00805f9b34fb")
    val UART_RX_UUID = UUID.fromString("d44bc439-abfd-45a2-b575-925416129600")
    val UART_TX_UUID = UUID.fromString("d44bc439-abfd-45a2-b575-925416129601")

    val CCCD_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    device.connectGatt(context, false, object : BluetoothGattCallback() {

        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.d("BLE", "Connected to ${device.name ?: device.address}")
                gatt.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.d("BLE", "Disconnected from ${device.name ?: device.address}")
                gatt.close()
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                // Battery Service
                val batteryService = gatt.getService(BATTERY_SERVICE_UUID)
                val batteryChar = batteryService?.getCharacteristic(BATTERY_LEVEL_UUID)
                batteryChar?.let {
                    gatt.readCharacteristic(it)
                    Handler(Looper.getMainLooper()).postDelayed({
                        gatt.setCharacteristicNotification(it, true)
                        val descriptor = it.getDescriptor(CCCD_UUID)
                        descriptor?.let { d ->
                            d.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                            gatt.writeDescriptor(d)
                        }
                    }, 500)
                }

                // UART Service
                val uartService = gatt.getService(UART_SERVICE_UUID)
                val uartRxChar = uartService?.getCharacteristic(UART_RX_UUID)
                uartRxChar?.let {
                    Handler(Looper.getMainLooper()).postDelayed({
                        gatt.setCharacteristicNotification(it, true)
                        val descriptor = it.getDescriptor(CCCD_UUID)
                        descriptor?.let { d ->
                            d.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                            gatt.writeDescriptor(d)
                        }
                    }, 500)
                }
            }
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                when (characteristic.uuid) {
                    BATTERY_LEVEL_UUID -> {
                        val batteryPercent = characteristic.value[0].toInt() and 0xFF
                        Log.d("BLE", "Battery Level: $batteryPercent%")
                        onBatteryLevelRead(batteryPercent)
                    }
                }
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            when (characteristic.uuid) {
                BATTERY_LEVEL_UUID -> {
                    val batteryPercent = characteristic.value[0].toInt() and 0xFF
                    Log.d("BLE", "Battery Level (notify): $batteryPercent%")
                    onBatteryLevelRead(batteryPercent)
                }
                UART_RX_UUID -> {
                    val data = characteristic.value
                    if (data.size >= 16) {
                        val buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
                        val roll = buffer.getFloat(4)
                        val pitch = buffer.getFloat(8)
                        val yaw = buffer.getFloat(12)
                        Log.d("BLE", "Parsed -> Roll: $roll, Pitch: $pitch, Yaw: $yaw")
                        onUartDataReceived(floatArrayOf(roll, pitch, yaw))
                    } else {
                        Log.e("BLE", "UART RX data too short: ${data.size} bytes")
                    }
                }
            }
        }
    })
}