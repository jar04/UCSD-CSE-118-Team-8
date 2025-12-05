package com.example.cse118_project.presentation

import android.app.RemoteInput
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.*
import androidx.wear.input.RemoteInputIntentHelper
import com.example.cse118_project.ble.BleManager
import com.example.cse118_project.data.Device

private const val KEY_DEVICE_NAME = "device_name"

@Composable
fun AddDevice(
    onDeviceSelected: (Device) -> Unit,
    onCancel: () -> Unit = {}
) {
    val context = LocalContext.current
    val bleManager = remember { BleManager(context) }
    var deviceName by remember { mutableStateOf("") }
    var isScanning by remember { mutableStateOf(false) }
    var selectedDevice by remember { mutableStateOf<Device?>(null) }
    val scannedDevices = remember { mutableStateListOf<Device>() }

    DisposableEffect(Unit) {
        onDispose {
            bleManager.stopScanning()
        }
    }

    // Launcher for device name input
    val nameInputLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        result.data?.let { data ->
            val results: Bundle = RemoteInput.getResultsFromIntent(data)
            val input = results.getCharSequence(KEY_DEVICE_NAME)
            if (input != null) {
                deviceName = input.toString()
            }
        }
    }

    val listState = rememberScalingLazyListState()

    Scaffold(
        timeText = { TimeText() },
        vignette = { Vignette(vignettePosition = VignettePosition.TopAndBottom) },
        positionIndicator = { PositionIndicator(scalingLazyListState = listState) }
    ) {
        ScalingLazyColumn(
            modifier = Modifier.fillMaxSize(),
            anchorType = ScalingLazyListAnchorType.ItemStart,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            state = listState
        ) {
            item {
                ListHeader {
                    Text(text = "Add Device")
                }
            }

            item {
                Text(
                    text = "Device Name",
                    style = MaterialTheme.typography.caption1
                )
            }

            item {
                Chip(
                    onClick = {
                        val intent = RemoteInputIntentHelper.createActionRemoteInputIntent()
                        val remoteInputs = listOf(
                            RemoteInput.Builder(KEY_DEVICE_NAME)
                                .setLabel("Device Name")
                                .build()
                        )
                        RemoteInputIntentHelper.putRemoteInputsExtra(intent, remoteInputs)
                        nameInputLauncher.launch(intent)
                    },
                    label = { 
                        Text(text = if (deviceName.isEmpty()) "Enter name" else deviceName)
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item {
                Spacer(modifier = Modifier.height(8.dp))
            }

            item {
                Text(
                    text = "Scan for NUS Devices",
                    style = MaterialTheme.typography.caption1
                )
            }

            item {
                Button(
                    onClick = {
                        if (!isScanning) {
                            isScanning = true
                            scannedDevices.clear()
                            selectedDevice = null
                            try {
                                bleManager.startScanning(
                                    onDeviceFound = { device ->
                                        if (scannedDevices.none { it.address == device.address }) {
                                            scannedDevices.add(device)
                                        }
                                    },
                                    onScanFailed = { errorCode ->
                                        isScanning = false
                                        // Scan failed - stop loading indicator
                                    }
                                )
                            } catch (e: Exception) {
                                isScanning = false
                                // Scanning failed - likely on emulator
                            }
                        } else {
                            isScanning = false
                            bleManager.stopScanning()
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = if (isScanning) "Stop Scanning" else "Scan for Devices")
                }
            }

            if (isScanning) {
                item {
                    CircularProgressIndicator(modifier = Modifier.padding(8.dp))
                }
            }

            items(scannedDevices) { device ->
                Chip(
                    onClick = {
                        selectedDevice = device
                        isScanning = false
                        bleManager.stopScanning()
                    },
                    label = { Text(text = device.name) },
                    secondaryLabel = { Text(text = device.address) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = if (selectedDevice?.address == device.address) {
                        ChipDefaults.primaryChipColors()
                    } else {
                        ChipDefaults.secondaryChipColors()
                    }
                )
            }

            if (selectedDevice != null) {
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                }

                item {
                    Button(
                        onClick = {
                            selectedDevice?.let { device ->
                                val finalName = if (deviceName.isEmpty()) device.name else deviceName
                                onDeviceSelected(Device(finalName, device.address))
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(text = "Done")
                    }
                }
            }
        }
    }
}
