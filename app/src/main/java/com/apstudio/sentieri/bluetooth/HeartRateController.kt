package com.apstudio.sentieri.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.os.ParcelUuid
import android.util.Log
import com.example.levo_sdk.domain.model.BtDevice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

@SuppressLint("MissingPermission")
class HeartRateController(private val context: Context) {

    companion object {
        private const val TAG = "HeartRateController"
        val HR_SERVICE_UUID: UUID = UUID.fromString("0000180D-0000-1000-8000-00805f9b34fb")
        val HR_MEASUREMENT_CHAR_UUID: UUID = UUID.fromString("00002A37-0000-1000-8000-00805f9b34fb")
        val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }

    private val bluetoothManager by lazy { context.getSystemService(BluetoothManager::class.java) }
    private val bluetoothAdapter: BluetoothAdapter? get() = bluetoothManager?.adapter

    private val _devices = MutableStateFlow<List<BtDevice>>(emptyList())
    val devices: StateFlow<List<BtDevice>> = _devices.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _heartRate = MutableStateFlow(0)
    val heartRate: StateFlow<Int> = _heartRate.asStateFlow()

    private val _status = MutableSharedFlow<String>()
    val status: SharedFlow<String> = _status.asSharedFlow()

    private var currentGatt: BluetoothGatt? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var isConnecting = false
    private var targetAddress: String? = null
    private var autoReconnect = true

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device ?: return
            val name = device.name ?: result.scanRecord?.deviceName
            val address = device.address ?: return
            val currentList = _devices.value.toMutableList()
            if (currentList.none { it.address == address }) {
                currentList.add(BtDevice(name = name, address = address))
                _devices.value = currentList
            }
        }

        override fun onBatchScanResults(results: List<ScanResult>) {
            results.forEach { onScanResult(0, it) }
        }

        override fun onScanFailed(errorCode: Int) {
            _isScanning.value = false
            Log.e(TAG, "Scansione fallita con codice: $errorCode")
        }
    }

    fun startDiscovery() {
        val adapter = bluetoothAdapter ?: return
        if (!adapter.isEnabled) return
        val scanner = adapter.bluetoothLeScanner ?: return

        _devices.value = emptyList()
        _isScanning.value = true

        val filter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid(HR_SERVICE_UUID))
            .build()
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        try {
            // Scannerizza cercando specificamente il servizio HR, ma anche generico se vuoto
            scanner.startScan(listOf(filter), settings, scanCallback)
        } catch (e: Exception) {
            try {
                scanner.startScan(scanCallback)
            } catch (ex: Exception) {
                Log.e(TAG, "Impossibile avviare scansione HR", ex)
            }
        }

        // Auto-stop dopo 15 secondi
        scope.launch {
            delay(15000)
            stopDiscovery()
        }
    }

    fun stopDiscovery() {
        if (!_isScanning.value) return
        try {
            bluetoothAdapter?.bluetoothLeScanner?.stopScan(scanCallback)
        } catch (e: Exception) {
            Log.e(TAG, "Errore interruzione scansione", e)
        }
        _isScanning.value = false
    }

    fun connectToDevice(address: String, reconnect: Boolean = true) {
        targetAddress = address
        autoReconnect = reconnect
        val adapter = bluetoothAdapter ?: return
        if (!adapter.isEnabled) return

        val device: BluetoothDevice = try {
            adapter.getRemoteDevice(address)
        } catch (e: Exception) {
            Log.e(TAG, "Dispositivo non valido per address: $address", e)
            return
        }

        closeConnection()
        isConnecting = true
        scope.launch { _status.emit("Connessione fascia cardio...") }

        currentGatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        } else {
            device.connectGatt(context, false, gattCallback)
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                _isConnected.value = true
                isConnecting = false
                Log.d(TAG, "Fascia cardio connessa. Avvio discovery servizi...")
                scope.launch { _status.emit("Fascia cardio connessa") }
                gatt.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                _isConnected.value = false
                _heartRate.value = 0
                isConnecting = false
                Log.d(TAG, "Fascia cardio disconnessa")
                scope.launch { _status.emit("Fascia cardio disconnessa") }
                closeConnection()

                if (autoReconnect && targetAddress != null) {
                    scope.launch {
                        delay(5000)
                        targetAddress?.let { connectToDevice(it, true) }
                    }
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val service = gatt.getService(HR_SERVICE_UUID)
                val characteristic = service?.getCharacteristic(HR_MEASUREMENT_CHAR_UUID)
                if (characteristic != null) {
                    enableHrNotification(gatt, characteristic)
                } else {
                    Log.w(TAG, "Caratteristica Heart Rate 0x2A37 non trovata")
                }
            }
        }

        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            if (characteristic.uuid == HR_MEASUREMENT_CHAR_UUID) {
                val bpm = parseHeartRate(characteristic.value)
                if (bpm > 0) {
                    _heartRate.value = bpm
                }
            }
        }

        // Metodo API 33+
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            if (characteristic.uuid == HR_MEASUREMENT_CHAR_UUID) {
                val bpm = parseHeartRate(value)
                if (bpm > 0) {
                    _heartRate.value = bpm
                }
            }
        }
    }

    private fun enableHrNotification(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
        gatt.setCharacteristicNotification(characteristic, true)
        val descriptor = characteristic.getDescriptor(CCCD_UUID)
        if (descriptor != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                gatt.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
            } else {
                @Suppress("DEPRECATION")
                descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                @Suppress("DEPRECATION")
                gatt.writeDescriptor(descriptor)
            }
        }
    }

    private fun parseHeartRate(data: ByteArray?): Int {
        if (data == null || data.isEmpty()) return 0
        val flags = data[0].toInt()
        val is16Bit = (flags and 0x01) != 0
        return if (is16Bit && data.size >= 3) {
            (data[1].toInt() and 0xFF) or ((data[2].toInt() and 0xFF) shl 8)
        } else if (!is16Bit && data.size >= 2) {
            data[1].toInt() and 0xFF
        } else {
            0
        }
    }

    fun closeConnection() {
        try {
            currentGatt?.disconnect()
            currentGatt?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Errore chiusura gatt HR", e)
        }
        currentGatt = null
        _isConnected.value = false
        _heartRate.value = 0
    }

    fun release() {
        autoReconnect = false
        stopDiscovery()
        closeConnection()
    }
}
