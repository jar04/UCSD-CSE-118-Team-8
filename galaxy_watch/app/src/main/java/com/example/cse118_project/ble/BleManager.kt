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

    fun startScanning(onDeviceFound: (Device) -> Unit) {
        // Stop any existing scan first
        stopScanning()
        
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
                onDeviceFound(Device(name, device.address))
            }

            override fun onScanFailed(errorCode: Int) {
                Log.e("BleManager", "Scan failed: $errorCode")
            }
        }
        
        bluetoothLeScanner?.startScan(filters, settings, scanCallback)
    }

    fun stopScanning() {
        scanCallback?.let { callback ->
            bluetoothLeScanner?.stopScan(callback)
            scanCallback = null
        }
    }

    fun toggleDevice(deviceAddress: String) {
        val device = bluetoothAdapter?.getRemoteDevice(deviceAddress)
        bluetoothGatt = device?.connectGatt(context, false, object : BluetoothGattCallback() {
            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    Log.d("BleManager", "Connected to $deviceAddress")
                    gatt.discoverServices()
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    Log.d("BleManager", "Disconnected from $deviceAddress")
                    bluetoothGatt = null
                }
            }

            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    val service = gatt.getService(NUS_SERVICE_UUID)
                    val rxChar = service?.getCharacteristic(NUS_RX_CHAR_UUID)
                    if (rxChar != null) {
                        rxChar.value = "toggle".toByteArray(Charsets.UTF_8)
                        rxChar.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                        gatt.writeCharacteristic(rxChar)
                    }
                }
            }

            override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    Log.d("BleManager", "Wrote 'toggle' to RX characteristic")
                    // Disconnect after toggling for this simple use case
                    gatt.disconnect()
                }
            }
        })
    }
}
