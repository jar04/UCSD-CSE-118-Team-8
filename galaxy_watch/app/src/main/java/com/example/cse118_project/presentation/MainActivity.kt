package com.example.cse118_project.presentation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.runtime.Composable
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import com.example.cse118_project.data.DeviceRepository
import com.example.cse118_project.presentation.theme.CSE118_ProjectTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        setTheme(android.R.style.Theme_DeviceDefault)

        setContent {
            WearApp()
        }
    }
}

@Composable
fun WearApp() {
    CSE118_ProjectTheme {
        val context = androidx.compose.ui.platform.LocalContext.current
        val repository = androidx.compose.runtime.remember { DeviceRepository.getInstance(context) }
        val navController = rememberSwipeDismissableNavController()

        SwipeDismissableNavHost(
            navController = navController,
            startDestination = "home"
        ) {
            composable("home") {
                val bleManager = androidx.compose.runtime.remember { com.example.cse118_project.ble.BleManager(context) }
                
                Home(
                    repository = repository,
                    onAddDeviceClick = { 
                        // Add timestamp to force recreation of AddDevice screen
                        navController.navigate("add_device/${System.currentTimeMillis()}")
                    },
                    onToggleDevice = { device ->
                        bleManager.toggleDevice(device.address)
                    }
                )
            }

            composable("add_device/{timestamp}") {
                AddDevice(
                    onDeviceSelected = { device ->
                        repository.addDevice(device)
                        navController.popBackStack()
                    },
                    onCancel = {
                        navController.popBackStack()
                    }
                )
            }
        }
    }
}