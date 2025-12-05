package com.example.cse118_project.ble

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.ParcelUuid
import android.util.Log
import com.example.cse118_project.data.Device
import java.util.*

@SuppressLint("MissingPermission") // Permissions should be handled in Activity
class BleManager(private val context: Context) {

    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private val bluetoothLeScanner = bluetoothAdapter?.bluetoothLeScanner
    
    private var bluetoothGatt: BluetoothGatt? = null
    private var scanCallback: ScanCallback? = null

    companion object {
        val NUS_SERVICE_UUID: UUID = UUID.fromString("6E400001-B5A3-F393-E0A9-E50E24DCCA9E")
        val NUS_RX_CHAR_UUID: UUID = UUID.fromString("6E400002-B5A3-F393-E0A9-E50E24DCCA9E")
        val NUS_TX_CHAR_UUID: UUID = UUID.fromString("6E400003-B5A3-F393-E0A9-E50E24DCCA9E")
    }

    fun startScanning(onDeviceFound: (Device) -> Unit, onScanFailed: ((Int) -> Unit)? = null) {
        // Stop any existing scan first
        stopScanning()
        
        if (bluetoothAdapter == null) {
            Log.e("BleManager", "Bluetooth adapter is null")
            onScanFailed?.invoke(-1)
            return
        }
        
        if (!bluetoothAdapter.isEnabled) {
            Log.e("BleManager", "Bluetooth is not enabled")
            onScanFailed?.invoke(-2)
            return
        }
        
        val filters = listOf(
            ScanFilter.Builder()
                .setServiceUuid(ParcelUuid(NUS_SERVICE_UUID))
                .build()
        )
        
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val device = result.device
                val name = device.name ?: "Unknown Device"
                Log.d("BleManager", "Found device: $name (${device.address})")
                onDeviceFound(Device(name, device.address))
            }

            override fun onScanFailed(errorCode: Int) {
                Log.e("BleManager", "Scan failed with error code: $errorCode")
                onScanFailed?.invoke(errorCode)
            }
        }
        
        try {
            bluetoothLeScanner?.startScan(filters, settings, scanCallback)
            Log.d("BleManager", "Started BLE scan")
        } catch (e: SecurityException) {
            Log.e("BleManager", "Permission denied for BLE scan", e)
            onScanFailed?.invoke(-3)
        } catch (e: Exception) {
            Log.e("BleManager", "Failed to start scan", e)
            onScanFailed?.invoke(-4)
        }
    }

    fun stopScanning() {
        scanCallback?.let { callback ->
            try {
                bluetoothLeScanner?.stopScan(callback)
                Log.d("BleManager", "Stopped BLE scan")
            } catch (e: Exception) {
                Log.e("BleManager", "Error stopping scan", e)
            }
            scanCallback = null
        }
    }
    
    fun testConnection(deviceAddress: String, onSuccess: () -> Unit, onFailure: (String) -> Unit) {
        Log.d("BleManager", "Testing connection to: $deviceAddress")
        
        if (bluetoothAdapter == null) {
            Log.e("BleManager", "Bluetooth adapter is null")
            onFailure("Bluetooth not available")
            return
        }
        
        val device = bluetoothAdapter.getRemoteDevice(deviceAddress)
        var testGatt: BluetoothGatt? = null
        
        testGatt = device.connectGatt(context, false, object : BluetoothGattCallback() {
            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                when (newState) {
                    BluetoothProfile.STATE_CONNECTED -> {
                        Log.d("BleManager", "Test connection successful")
                        gatt.disconnect()
                        onSuccess()
                    }
                    BluetoothProfile.STATE_DISCONNECTED -> {
                        if (status != BluetoothGatt.GATT_SUCCESS) {
                            Log.e("BleManager", "Test connection failed with status: $status")
                            onFailure("Status code: $status")
                        }
                    }
                }
            }
        })
        
        if (testGatt == null) {
            Log.e("BleManager", "Failed to create test GATT connection")
            onFailure("Failed to initiate connection")
        }
    }

    fun toggleDevice(deviceAddress: String) {
        Log.d("BleManager", "Attempting to toggle device: $deviceAddress")
        
        if (bluetoothAdapter == null) {
            Log.e("BleManager", "Bluetooth adapter is null")
            return
        }
        
        val device = bluetoothAdapter.getRemoteDevice(deviceAddress)
        Log.d("BleManager", "Got remote device: ${device.address}")
        
        bluetoothGatt = device.connectGatt(context, false, object : BluetoothGattCallback() {
            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                Log.d("BleManager", "Connection state changed - status: $status, newState: $newState")
                
                when (newState) {
                    BluetoothProfile.STATE_CONNECTED -> {
                        Log.d("BleManager", "Successfully connected to $deviceAddress")
                        Log.d("BleManager", "Discovering services...")
                        gatt.discoverServices()
                    }
                    BluetoothProfile.STATE_DISCONNECTED -> {
                        Log.d("BleManager", "Disconnected from $deviceAddress")
                        bluetoothGatt = null
                    }
                    BluetoothProfile.STATE_CONNECTING -> {
                        Log.d("BleManager", "Connecting to $deviceAddress...")
                    }
                    else -> {
                        Log.d("BleManager", "Unknown connection state: $newState")
                    }
                }
                
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    Log.e("BleManager", "Connection failed with status: $status")
                }
            }

            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                Log.d("BleManager", "Services discovered - status: $status")
                
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    val services = gatt.services
                    Log.d("BleManager", "Found ${services.size} services")
                    
                    services.forEach { service ->
                        Log.d("BleManager", "Service UUID: ${service.uuid}")
                    }
                    
                    val service = gatt.getService(NUS_SERVICE_UUID)
                    if (service == null) {
                        Log.e("BleManager", "NUS service not found! Looking for: $NUS_SERVICE_UUID")
                        gatt.disconnect()
                        return
                    }
                    
                    Log.d("BleManager", "Found NUS service")
                    val rxChar = service.getCharacteristic(NUS_RX_CHAR_UUID)
                    
                    if (rxChar == null) {
                        Log.e("BleManager", "RX characteristic not found! Looking for: $NUS_RX_CHAR_UUID")
                        gatt.disconnect()
                        return
                    }
                    
                    Log.d("BleManager", "Found RX characteristic, writing 'toggle'...")
                    rxChar.value = "toggle".toByteArray(Charsets.UTF_8)
                    rxChar.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                    val writeResult = gatt.writeCharacteristic(rxChar)
                    Log.d("BleManager", "Write characteristic result: $writeResult")
                } else {
                    Log.e("BleManager", "Service discovery failed with status: $status")
                    gatt.disconnect()
                }
            }

            override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
                Log.d("BleManager", "Characteristic write callback - status: $status")
                
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    Log.d("BleManager", "Successfully wrote 'toggle' to RX characteristic")
                } else {
                    Log.e("BleManager", "Failed to write characteristic - status: $status")
                }
                
                // Disconnect after toggling
                Log.d("BleManager", "Disconnecting...")
                gatt.disconnect()
            }
        })
        
        if (bluetoothGatt == null) {
            Log.e("BleManager", "Failed to create GATT connection")
        } else {
            Log.d("BleManager", "GATT connection initiated")
        }
    }
}
