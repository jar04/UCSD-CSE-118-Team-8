package com.example.cse118_project.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Power
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.*
import com.example.cse118_project.data.Device
import com.example.cse118_project.data.DeviceRepository

@Composable
fun Home(
    repository: DeviceRepository,
    onAddDeviceClick: () -> Unit,
    onToggleDevice: (Device) -> Unit
) {
    val devices by repository.devices.collectAsState()
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
                    Text(text = "My Devices")
                }
            }

            item {
                CompactButton(
                    onClick = onAddDeviceClick,
                    colors = ButtonDefaults.secondaryButtonColors()
                ) {
                    Text(text = "+ Add Device")
                }
            }

            if (devices.isEmpty()) {
                item {
                    Text(
                        text = "No devices added",
                        style = MaterialTheme.typography.caption2,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            } else {
                items(devices) { device ->
                    DeviceItem(device = device, onToggle = { onToggleDevice(device) })
                }
            }
        }
    }
}

@Composable
fun DeviceItem(device: Device, onToggle: () -> Unit) {
    Chip(
        onClick = onToggle,
        label = { Text(text = device.name) },
        secondaryLabel = { Text(text = device.address) },
        icon = {
            Icon(
                imageVector = Icons.Filled.Power,
                contentDescription = "Toggle"
            )
        },
        modifier = Modifier.fillMaxWidth()
    )
}
