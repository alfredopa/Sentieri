package com.example.levo_sdk.domain

import com.example.levo_sdk.domain.model.BtDevice
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

interface BluetoothController {
    val isScanning: StateFlow<Boolean>
    val isConnected: StateFlow<Boolean>
    val connectedDeviceName: StateFlow<String>
    val devices: StateFlow<List<BtDevice>>
    val errors: SharedFlow<String>

    fun startDiscovery()
    fun stopDiscovery()
    fun connectToDevice(device: BtDevice, autoConnect: Boolean = true): Flow<ConnectionResult>
    fun closeConnection()
    fun release()
    
    suspend fun trySendMessage(message: String): String?
}
