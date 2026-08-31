package com.example.levo_sdk.data

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import com.example.levo_sdk.data.protocol.LevoProtocol
import com.example.levo_sdk.data.protocol.toHexString
import com.example.levo_sdk.data.receivers.BluetoothStateReceiver
import com.example.levo_sdk.data.receivers.ScanDeviceReceiver
import com.example.levo_sdk.domain.BluetoothController
import com.example.levo_sdk.domain.ConnectionResult
import com.example.levo_sdk.domain.model.BtDevice
import com.example.levo_sdk.domain.model.BtMessage
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID
import kotlin.time.Duration.Companion.milliseconds

@SuppressLint("MissingPermission")
class LevoBluetoothController(
    private val context: Context
) : BluetoothController {

    companion object {
        const val LEVO_SERVICE_UUID = "00000001-3731-3032-494d-484f42525554"
        const val LEVO_WRITE_CHAR_UUID = "00000021-3731-3032-494d-484f42525554"
        const val LEVO_READ_CHAR_UUID = "00000011-3731-3032-494d-484f42525554"
        val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
        
        val POLL_SOC = byteArrayOf(0x00, 0x0C.toByte())
        val POLL_BATT_TEMP = byteArrayOf(0x00, 0x03)
        val POLL_VOLTAGE = byteArrayOf(0x00, 0x05)
        val POLL_CURRENT = byteArrayOf(0x00, 0x06)
        val POLL_RIDER_POWER = byteArrayOf(0x01, 0x00)
        val POLL_SPEED = byteArrayOf(0x01, 0x02)
        val POLL_ODOMETER = byteArrayOf(0x01, 0x04)
        val POLL_ASSIST_LEVEL = byteArrayOf(0x01, 0x05)
    }

    private val bluetoothManager by lazy { context.getSystemService(BluetoothManager::class.java) }
    private val bluetoothAdapter by lazy { bluetoothManager?.adapter }

    private val _devices = MutableStateFlow<List<BtDevice>>(emptyList())
    override val devices: StateFlow<List<BtDevice>> get() = _devices.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    override val isScanning: StateFlow<Boolean> get() = _isScanning.asStateFlow()

    private val _isConnected = MutableStateFlow(false)
    override val isConnected: StateFlow<Boolean> get() = _isConnected.asStateFlow()

    private val _errors = MutableSharedFlow<String>()
    override val errors: SharedFlow<String> get() = _errors.asSharedFlow()

    private val _connectedDeviceName = MutableStateFlow("")
    override val connectedDeviceName: StateFlow<String> get() = _connectedDeviceName.asStateFlow()

    private var currentGatt: BluetoothGatt? = null
    private val gattMutex = Mutex()
    private var pollingJob: Job? = null
    private var readDeferred: CompletableDeferred<ByteArray?>? = null
    
    private var lastMessage = BtMessage()
    private val notificationFlow = MutableSharedFlow<Pair<BtMessage, String>>(replay = 1)

    private val scanDeviceReceiver = ScanDeviceReceiver { device ->
        _devices.update { devices ->
            val newDevice = BtDevice(device.name, device.address, device.type, device.bluetoothClass, false)
            if (devices.any { it.address == newDevice.address }) devices else devices + newDevice
        }
    }

    private val discoveryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                BluetoothAdapter.ACTION_DISCOVERY_STARTED -> _isScanning.value = true
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> _isScanning.value = false
            }
        }
    }

    private val bluetoothStateReceiver = BluetoothStateReceiver { isConnected, bluetoothDevice ->
        if (bluetoothAdapter?.bondedDevices?.contains(bluetoothDevice) == true) {
            _isConnected.update { isConnected }
            _connectedDeviceName.update { if (isConnected) bluetoothDevice.name ?: "" else "" }
        }
    }

    init {
        updatePairedDevices()
        context.registerReceiver(bluetoothStateReceiver, IntentFilter().apply {
            addAction(BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED)
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
        })
    }

    private fun updatePairedDevices() {
        if (!hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) return
        bluetoothAdapter?.bondedDevices?.map { 
            BtDevice(it.name, it.address, it.type, it.bluetoothClass, true)
        }?.also { devices ->
            _devices.update { devices }
        }
    }

    override fun startDiscovery() {
        if (!hasPermission(Manifest.permission.BLUETOOTH_SCAN)) return
        context.registerReceiver(scanDeviceReceiver, IntentFilter(BluetoothDevice.ACTION_FOUND))
        val filter = IntentFilter().apply {
            addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        }
        context.registerReceiver(discoveryReceiver, filter)
        updatePairedDevices()
        bluetoothAdapter?.startDiscovery()
    }

    override fun stopDiscovery() {
        if (!hasPermission(Manifest.permission.BLUETOOTH_SCAN)) return
        bluetoothAdapter?.cancelDiscovery()
    }

    override fun connectToDevice(device: BtDevice): Flow<ConnectionResult> {
        return callbackFlow {
            if (!hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
                trySend(ConnectionResult.Error("Missing Bluetooth Permissions"))
                close()
                return@callbackFlow
            }

            // Chiudi eventuali connessioni precedenti per evitare status 133 (multipli clientIf)
            closeConnection()

            val bluetoothDevice = bluetoothAdapter?.getRemoteDevice(device.address)
            if (bluetoothDevice == null) {
                trySend(ConnectionResult.Error("Device not found"))
                close()
                return@callbackFlow
            }

            stopDiscovery()

            // Timeout per la connessione (15 secondi)
            val timeoutJob = launch {
                delay(15000)
                if (!_isConnected.value) {
                    //Log.d("EbikeDebug", "SDK: Connection Timeout")
                    trySend(ConnectionResult.Error("Timeout connessione"))
                    close()
                }
            }

            val gattCallback = object : BluetoothGattCallback() {
                override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                    //Log.d("EbikeDebug", "SDK: onConnectionStateChange status=$status, newState=$newState")
                    if (status != BluetoothGatt.GATT_SUCCESS) {
                        Log.e("EbikeDebug", "SDK: GATT Error $status")
                        timeoutJob.cancel()
                        _isConnected.update { false }
                        pollingJob?.cancel()
                        trySend(ConnectionResult.Error("GATT Error $status"))
                        close()
                        return
                    }

                    if (newState == BluetoothProfile.STATE_CONNECTED) {
                        //Log.d("EbikeDebug", "SDK: Connected, discovering services...")
                        timeoutJob.cancel()
                        gatt.discoverServices()
                    } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                        //Log.d("EbikeDebug", "SDK: Disconnected")
                        _isConnected.update { false }
                        pollingJob?.cancel()
                        trySend(ConnectionResult.Error("Disconnected"))
                        close()
                    }
                }

                override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                    //Log.d("EbikeDebug", "SDK: onServicesDiscovered status=$status")
                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        val service = gatt.getService(UUID.fromString(LEVO_SERVICE_UUID))
                        val writeChar = service?.getCharacteristic(UUID.fromString(LEVO_WRITE_CHAR_UUID))
                        val readChar = service?.getCharacteristic(UUID.fromString(LEVO_READ_CHAR_UUID))

                        if (service != null && writeChar != null && readChar != null) {
                            enableNotifications(gatt, readChar)
                            trySend(ConnectionResult.ConnectionEstablished)
                            _isConnected.update { true }
                            _connectedDeviceName.update { device.name ?: "Specialized Levo" }
                            startPolling(gatt, writeChar, readChar) { msg, log ->
                                trySend(ConnectionResult.TransferSucceeded(msg, log))
                            }
                        } else {
                            trySend(ConnectionResult.Error("Levo Service not found"))
                            close()
                        }
                    } else {
                        trySend(ConnectionResult.Error("Service discovery failed"))
                        close()
                    }
                }

                override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) {
                    handleNotification(value)
                }

                override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray, status: Int) {
                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        readDeferred?.complete(value)
                        handleNotification(value)
                    } else {
                        readDeferred?.complete(null)
                    }
                }
            }

            //Log.d("EbikeDebug", "SDK: Connecting to ${device.address}...")
            val gatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                bluetoothDevice.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
            } else {
                bluetoothDevice.connectGatt(context, false, gattCallback)
            }
            currentGatt = gatt

            awaitClose {
                //Log.d("EbikeDebug", "SDK: awaitClose - closing current connection")
                // Non chiamare closeConnection() qui per evitare ricorsione se chiudiamo tramite close() del flow
                pollingJob?.cancel()
                gatt.disconnect()
                gatt.close()
                if (currentGatt == gatt) {
                    currentGatt = null
                }
                _isConnected.update { false }
            }
        }.flowOn(Dispatchers.IO)
    }

    private fun startPolling(
        gatt: BluetoothGatt,
        writeChar: BluetoothGattCharacteristic,
        readChar: BluetoothGattCharacteristic,
        onUpdate: (BtMessage, String) -> Unit
    ) {
        pollingJob = CoroutineScope(Dispatchers.IO).launch {
            launch { notificationFlow.collect { (msg, log) -> onUpdate(msg, log) } }
            val fastItems = listOf(POLL_SPEED, POLL_RIDER_POWER, POLL_ASSIST_LEVEL)
            val slowItems = listOf(POLL_SOC, POLL_BATT_TEMP, POLL_VOLTAGE, POLL_CURRENT, POLL_ODOMETER)
            var slowIndex = 0
            while (isActive) {
                try {
                    for (item in fastItems) {
                        requestAndRead(gatt, writeChar, readChar, item)
                        delay(50.milliseconds)
                    }
                    requestAndRead(gatt, writeChar, readChar, slowItems[slowIndex])
                    slowIndex = (slowIndex + 1) % slowItems.size
                    delay(100.milliseconds)
                } catch (e: Exception) {
                    //Log.d("EbikeDebug", "errore catch polling")
                    delay(5000.milliseconds)
                }
            }
        }
    }

    private fun enableNotifications(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
        gatt.setCharacteristicNotification(characteristic, true)
        val descriptor = characteristic.getDescriptor(CCCD_UUID)
        if (descriptor != null) {
            descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            gatt.writeDescriptor(descriptor)
        }
    }

    private fun handleNotification(data: ByteArray?) {
        if (data == null || data.size < 2) return
        synchronized(this) {
            val updated = LevoProtocol.decode(data, lastMessage)
            if (updated != lastMessage) {
                lastMessage = updated
                val log = "[Levo] ${data.take(2).toByteArray().toHexString()}: ${data.toHexString()}"
                //Log.d("EbikeDebug", "SDK: Dato ricevuto e decodificato: $updated") // <-- AGGIUNGI QUESTO
                notificationFlow.tryEmit(lastMessage to log)
            }
        }
    }

    private suspend fun requestAndRead(
        gatt: BluetoothGatt,
        writeChar: BluetoothGattCharacteristic,
        readChar: BluetoothGattCharacteristic,
        type: ByteArray
    ): ByteArray? = gattMutex.withLock {
        readDeferred = CompletableDeferred()
        writeChar.value = type
        if (!gatt.writeCharacteristic(writeChar)) return null
        val result = withTimeoutOrNull(500) { readDeferred?.await() }
        if (result == null) {
            if (gatt.readCharacteristic(readChar)) {
                return withTimeoutOrNull(500) { readDeferred?.await() }
            }
        }
        return result
    }

    override suspend fun trySendMessage(message: String): String? = null

    override fun closeConnection() {
        pollingJob?.cancel()
        currentGatt?.disconnect()
        currentGatt?.close()
        currentGatt = null
        _isConnected.update { false }
    }

    override fun release() {
        try {
            context.unregisterReceiver(scanDeviceReceiver)
            context.unregisterReceiver(discoveryReceiver)
            context.unregisterReceiver(bluetoothStateReceiver)
        } catch (e: Exception) {}
        closeConnection()
    }

    private fun hasPermission(permission: String): Boolean = context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
}
