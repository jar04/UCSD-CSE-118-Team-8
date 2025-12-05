package com.example.cse118_project.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "devices")

class DeviceRepository private constructor(private val context: Context) {
    private val scope = CoroutineScope(Dispatchers.IO)
    private val _devices = MutableStateFlow<List<Device>>(emptyList())
    val devices: StateFlow<List<Device>> = _devices.asStateFlow()

    companion object {
        private val DEVICES_KEY = stringPreferencesKey("devices_json")
        
        @Volatile
        private var INSTANCE: DeviceRepository? = null

        fun getInstance(context: Context): DeviceRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: DeviceRepository(context.applicationContext).also { 
                    INSTANCE = it
                    it.loadDevices()
                }
            }
        }
    }

    private fun loadDevices() {
        scope.launch {
            context.dataStore.data.map { preferences ->
                val json = preferences[DEVICES_KEY] ?: "[]"
                parseDevices(json)
            }.collect { deviceList ->
                _devices.value = deviceList
            }
        }
    }

    fun addDevice(device: Device) {
        scope.launch {
            val currentDevices = _devices.value
            if (currentDevices.none { it.address == device.address }) {
                val updatedDevices = currentDevices + device
                _devices.value = updatedDevices
                saveDevices(updatedDevices)
            }
        }
    }

    fun removeDevice(address: String) {
        scope.launch {
            val updatedDevices = _devices.value.filter { it.address != address }
            _devices.value = updatedDevices
            saveDevices(updatedDevices)
        }
    }

    private suspend fun saveDevices(deviceList: List<Device>) {
        val json = deviceList.joinToString(",") { 
            """{"name":"${it.name}","address":"${it.address}"}"""
        }
        context.dataStore.edit { preferences ->
            preferences[DEVICES_KEY] = "[$json]"
        }
    }

    private fun parseDevices(json: String): List<Device> {
        if (json == "[]") return emptyList()
        
        return try {
            json.trim('[', ']')
                .split("},")
                .mapNotNull { deviceJson ->
                    val cleanJson = if (deviceJson.endsWith("}")) deviceJson else "$deviceJson}"
                    val nameMatch = """"name":"([^"]+)"""".toRegex().find(cleanJson)
                    val addressMatch = """"address":"([^"]+)"""".toRegex().find(cleanJson)
                    
                    if (nameMatch != null && addressMatch != null) {
                        Device(nameMatch.groupValues[1], addressMatch.groupValues[1])
                    } else null
                }
        } catch (e: Exception) {
            emptyList()
        }
    }
}
