package com.apstudio.sentieri

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.location.GnssStatus
import android.location.LocationListener
import android.location.LocationManager
import android.location.OnNmeaMessageListener
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import android.content.SharedPreferences
import android.util.Log
import com.apstudio.sentieri.db.LocationRepository
import com.example.levo_sdk.data.LevoBluetoothController
import com.example.levo_sdk.domain.BluetoothController
import com.example.levo_sdk.domain.ConnectionResult
import com.example.levo_sdk.domain.model.BtDevice
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect

/**
 * LocationService is a foreground service responsible for tracking the device's location
 * and providing location updates. It utilizes the GPS provider and optionally a barometer
 * sensor if available.
 * GGA
 * * Global Positioning System Fix Data
 * 184936.00
 * * Tempo  18:49:36.00
 * 4508.43100,N
 * * Latitudine  45°08.43100' N
 * 00737.21937,E
 * * Longitudine 7°37.21937' E
 * 1
 * * Qualità del Fix:
 * 0 = Invalido
 * 1 = GPS fix
 * 2 = DGPS fix
 * 3 = Fix GPS PPS
 * 4 = RTK (Real Time Kinematic) intera
 * 5 = RTK float
 * 6 = Navigazione Stimata (dead reckoning)
 * 7 = Input Manuale
 * 8 = Simulazione
 * 06
 *
 * 6 Satelliti usati nella soluzione
 * 1.5
 *
 * HDOP
 * 278.4,M
 *
 * Altitudine   278.4  metri s.l.m
 * 47.2 , M
 *
 * Altezza sul geoide WGS84 =47.2  metri
 * vuoto
 *
 * Tempo dall'ultimo aggiornamento DGPS
 * vuoto
 *
 * Id della stazione DGPS
 *
 * <p>
 * Key features:
 * <ul>
 *   <li><b>GPS Location Tracking:</b> Continuously monitors the device's GPS location.</li>
 *   <li><b>Barometer Integration (Optional):</b> Integrates with a barometer sensor to
 *       provide altitude data when available and enabled.</li>
 *   <li><b>NMEA Message Handling:</b> Parses NMEA messages to obtain accurate altitude information.</li>
 *   <li><b>Foreground Service:</b> Runs as a foreground service with a persistent notification
 *       to ensure uninterrupted operation.</li>
 *   <li><b>Location Update Broadcasting:</b> Sends location updates to registered receivers
 *       via LocalBroadcastManager.</li>
 *   <li><b>GNSS Status Monitoring:</b> Tracks GNSS status and satellite information.</li>
 * </ul>
 */
class LocationService : LifecycleService() {

    companion object {
        private const val LOCATION_SERVICE_CHANNEL = 1234 // canale delle notifiche
        private var LOCATION_UPDATE_INTERVAL_MS = 3000L
        private var MIN_DISTANCE_CHANGE_METERS = 6f

        private const val MIN_ACCURACY_METERS = 40f
        
        // Azioni Bluetooth
        const val ACTION_START_SCAN = "com.apstudio.sentieri.ACTION_START_SCAN"
        const val ACTION_STOP_SCAN = "com.apstudio.sentieri.ACTION_STOP_SCAN"
        const val ACTION_CONNECT = "com.apstudio.sentieri.ACTION_CONNECT"
        const val ACTION_DISCONNECT = "com.apstudio.sentieri.ACTION_DISCONNECT"
        const val EXTRA_DEVICE_ADDRESS = "EXTRA_DEVICE_ADDRESS"
        const val EXTRA_DEVICE_NAME = "EXTRA_DEVICE_NAME"
    }

    private lateinit var locationManager: LocationManager
    private lateinit var locationListener: LocationListener
    private var nmeaListener: OnNmeaMessageListener? = null
    private lateinit var gnssCallback: GnssStatus.Callback
    private  val baroRepo: BaroRepo by lazy { BaroRepo(this) }
    private var hasMslAltitude = false
    private var speedKnots: Double = 0.0
    private var speedKmh: Int = 0
    private var wakeLock: PowerManager.WakeLock? = null
    private var timerJob: Job? = null
    
    // Bluetooth / E-bike
    private lateinit var bluetoothController: BluetoothController
    private var bluetoothJob: Job? = null
    private val preferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
        if (key == "mostra_dati_ebike" || key == "last_ebike_address") {
            handleBluetoothReconnect(prefs)
        }
    }
    
    override fun onCreate() {
        super.onCreate()
        
        // Non chiamiamo startForeground qui all'avvio
        // Lo faremo solo se e quando inizia la registrazione.

        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        // PARTIAL_WAKE_LOCK mantiene la CPU attiva anche a schermo spento
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Sentieri::RecordingWakeLock")
        wakeLock?.acquire()
        //Log.d(TAG, "Service created")
        locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
        hasMslAltitude = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE // Android 14

        initializeGnssCallback()
        initializeLocationListener()
        initializeNmeaListener()
        // Rimosso requestLocationUpdates() da qui, lo gestiremo in base a isRecording
        initializeBarometer()

        // Bluetooth Setup
        bluetoothController = LevoBluetoothController(this)
        
        // Collega i flussi del controller al Repository
        lifecycleScope.launch {
            bluetoothController.isConnected.collect { LocationRepository.updateBtConnectionState(it) }
        }
        lifecycleScope.launch {
            bluetoothController.connectedDeviceName.collect { LocationRepository.updateBtConnectionState(LocationRepository.btIsConnected.value == true, it) }
        }
        lifecycleScope.launch {
            bluetoothController.isScanning.collect { LocationRepository.updateBtScanning(it) }
        }
        lifecycleScope.launch {
            bluetoothController.devices.collect { LocationRepository.updateBtDevices(it) }
        }

        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        prefs.registerOnSharedPreferenceChangeListener(preferenceListener)
        handleBluetoothReconnect(prefs)
        
        // Ripristina lo stato della sessione se necessario
        LocationRepository.restoreSessionState(this)
        startTimerIfRecording()
        
        // Osserva lo stato di registrazione per gestire la notifica foreground E il GPS
        LocationRepository.isRecordingLiveData.observe(this) { recording ->
            updateNotification()
            if (recording) {
                requestLocationUpdates()
            } else {
                removeLocationUpdates()
            }
        }
    }

    private fun handleBluetoothReconnect(prefs: SharedPreferences) {
        val enabled = prefs.getBoolean("mostra_dati_ebike", true)
        if (enabled) {
            autoConnectEbike(prefs)
        } else {
            bluetoothJob?.cancel()
            bluetoothController.closeConnection()
            LocationRepository.updateBtConnectionState(false)
            LocationRepository.updateBtStatus("Bluetooth disattivato")
        }
    }

    private fun autoConnectEbike(prefs: SharedPreferences) {
        val address = prefs.getString("last_ebike_address", null)
        if (!address.isNullOrEmpty() && LocationRepository.btIsConnected.value != true) {
            connectToBtDevice(address)
        }
    }

    private fun connectToBtDevice(address: String) {
        bluetoothJob?.cancel()
        bluetoothJob = lifecycleScope.launch {
            bluetoothController.connectToDevice(BtDevice(name = null, address = address))
                .collect { result ->
                    when (result) {
                        is ConnectionResult.ConnectionEstablished -> {
                            LocationRepository.updateBtConnectionState(true, "E-bike")
                            LocationRepository.updateBtStatus("Connesso")
                        }
                        is ConnectionResult.TransferSucceeded -> {
                            //Log.d("EbikeDebug", "Service: Ricevuto TransferSucceeded, SoC: ${result.message.soc}")
                            LocationRepository.updateEbikeMessage(result.message)
                        }
                        is ConnectionResult.Error -> {
                            LocationRepository.updateBtConnectionState(false)
                            LocationRepository.updateBtStatus("Errore: ${result.message}")
                            // Riprova dopo un po' se è un errore di connessione
                            delay(10000)
                            autoConnectEbike(PreferenceManager.getDefaultSharedPreferences(this@LocationService))
                        }
                    }
                }
        }
    }

    private fun startTimerIfRecording() {
        if (LocationRepository.isRecording && timerJob == null) {
            timerJob = lifecycleScope.launch {
                while (true) {
                    LocationRepository.incrementMovementSeconds()
                    delay(1000)
                }
            }
        }
    }

    private fun stopTimer() {
        timerJob?.cancel()
        timerJob = null
    }

    private fun initializeGnssCallback() {
        gnssCallback = object : GnssStatus.Callback() {
            override fun onSatelliteStatusChanged(status: GnssStatus) {
                val satelliteCount = status.satelliteCount
                var usedSatellites = 0
                for (i in 0 until satelliteCount) {
                    if (status.usedInFix(i)) {
                        usedSatellites++
                    }
                }
                LocationRepository.numSat = satelliteCount
            }

            override fun onFirstFix(ttffMillis: Int) {
                super.onFirstFix(ttffMillis)
                //Log.d("LocationService_Debug", "onFirstFix chiamato. Tento di aggiornare lo stato a 'fixed'")
                LocationRepository.updateGpsStatus("fixed")
            }

            override fun onStarted() {
                LocationRepository.updateGpsStatus("started")
                //Log.d(TAG, "GNSS started")
                super.onStarted()
            }

            override fun onStopped() {
                LocationRepository.updateGpsStatus("stopped")
                //Log.d(TAG, "GNSS stopped")
                super.onStopped()
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        startTimerIfRecording()
        
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        
        when (intent?.action) {
            ACTION_START_SCAN -> {
                bluetoothController.startDiscovery()
            }
            ACTION_STOP_SCAN -> {
                bluetoothController.stopDiscovery()
            }
            ACTION_CONNECT -> {
                val address = intent.getStringExtra(EXTRA_DEVICE_ADDRESS)
                if (address != null) connectToBtDevice(address)
            }
            ACTION_DISCONNECT -> {
                bluetoothJob?.cancel()
                bluetoothController.closeConnection()
            }
            "ACTION_UPDATE_NOTIFICATION" -> {
                updateNotification()
            }
            else -> {
                // Caso di avvio generico: controlla se riconnettere
                handleBluetoothReconnect(prefs)
            }
        }

        val activityType =
            intent?.getStringExtra("ACTIVITY_TYPE") ?: "mtb" // Usa mountain_bike se null
        //Log.d(TAG, "Servizio avviato con tipo attività: $activityType")

        when (activityType) {
            "mtb" -> {
                LOCATION_UPDATE_INTERVAL_MS = 3000L // 4 secondi
                MIN_DISTANCE_CHANGE_METERS = 7f// 6 metri
            }

            "trekking" -> {
                LOCATION_UPDATE_INTERVAL_MS = 5000L // 2 secondi
                MIN_DISTANCE_CHANGE_METERS = 6f       //3 metri
            }
            // Puoi aggiungere altri casi qui, es. "corsa"
            else -> {
                // Default di sicurezza
                LOCATION_UPDATE_INTERVAL_MS = 3000L
                MIN_DISTANCE_CHANGE_METERS = 7f
            }
        }
        return START_STICKY
    }

    private fun initializeLocationListener() {
        locationListener = LocationListener { newLocation ->
            // Filtro accuratezza
            if (newLocation.accuracy > MIN_ACCURACY_METERS) return@LocationListener

            // Recupera i valori attuali necessari per il calcolo
            val mslAltitude = LocationRepository.mslAltitude.value ?: newLocation.altitude
            val baroPress = baroRepo.getLatestPressure() ?: 0.0f

            // Avvia l'elaborazione nel Repository (avviene nel Foreground Service)
            LocationRepository.processNewLocation(this, newLocation, mslAltitude, baroPress)
        }
    }

    private fun initializeNmeaListener() {
        // al momento utilizzare sempre la velocità ricavata da NMEA
        nmeaListener = OnNmeaMessageListener { message, _ ->
           parseNmeaMessage(message)
        }
    }

    private var isGpsRunning = false
    
    private fun requestLocationUpdates() {
        if (isGpsRunning) return
        
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            //Log.e(TAG, "Location permissions not granted")
            return
        }

        locationManager.registerGnssStatusCallback(
            ContextCompat.getMainExecutor(this), gnssCallback
        )

        nmeaListener?.let {
            locationManager.addNmeaListener(it, null)
        }

        locationManager.requestLocationUpdates(
            LocationManager.GPS_PROVIDER,
            LOCATION_UPDATE_INTERVAL_MS,
            MIN_DISTANCE_CHANGE_METERS,
            locationListener
        )
        isGpsRunning = true
    }

    private fun initializeBarometer() {
        // Avvia il sensore sempre se disponibile, il repository deciderà se usarlo
        baroRepo.startSensorUpdates()
    }

    private fun parseNmeaMessage(message: String) {
        //SimpleFileLogger.log(TAG, "NMEA Received: $message") // <-- AGGIUNGI QUESTO LOG
        if (message.length < 6) {
            //SimpleFileLogger.log(TAG, "NMEA message too short: $message")
            return
        }
        when (message.take(6)) {
            $$"$GPGGA", $$"$GNGGA" -> {
                parseGPGGA(message)}
            $$"$GPRMC" -> {
                parseGPRMC(message)}
            $$"$GNVTG" -> {
                parseGNVTG(message)}
        }
    }

    private fun parseGPGGA(message: String) {
        val nmeaParts = message.split(",")
        if (nmeaParts.size > 9) {
            val fixQuality = nmeaParts[6]
            val mslFromNmea = nmeaParts[9].toDoubleOrNull()
            //SimpleFileLogger.log(TAG, "parseNmeaMessage - GGA Fix Quality: $fixQuality, MSL from NMEA string: ${nmeaParts[9]}, Parsed: $mslFromNmea")
            if (fixQuality != "0" && mslFromNmea != null) { // O controlla anche altri codici di fix validi
                LocationRepository.updateMslAltitude(mslFromNmea)
                //SimpleFileLogger.log(TAG, "parseNmeaMessage - Updated LocationRepository.mslAltitude from NMEA: ${LocationRepository.mslAltitude.value}")
            }
        }
    }

    private fun parseGPRMC(message: String) {
        val parts = message.split(",")
        if (parts.size > 7 && parts[3].isNotEmpty()) { // Check if the message is valid and has enough fields
            speedKnots = parts[7].toDoubleOrNull() ?: 0.0
            speedKmh = (speedKnots * 1.852).toInt()
            LocationRepository.updateVelocita(speedKmh)
            //Log.d("NMEA",  "Velocità (GPRMC): %.2f nodi, %.2f km/h, $speedKnots, $speedKmh")
        }
    }

    private fun parseGNVTG(message: String) {
        val parts = message.split(",")
        if (parts.size > 7) {
            speedKnots = parts[5].toDoubleOrNull() ?: 0.0
            speedKmh = (parts[7].toDoubleOrNull() ?: (speedKnots * 1.852)).toInt()
            LocationRepository.updateVelocita(speedKmh)
            //Log.d("NMEA",  "Velocità (GNVTG): %.2f nodi, %.2f km/h, $speedKnots, $speedKmh")
        }
    }

    override fun onDestroy() {
        stopTimer()
        stopBarometer()
        
        // Bluetooth Cleanup
        bluetoothJob?.cancel()
        PreferenceManager.getDefaultSharedPreferences(this).unregisterOnSharedPreferenceChangeListener(preferenceListener)
        if (::bluetoothController.isInitialized) {
            bluetoothController.release()
        }

        LocationRepository.updateGpsStatus("stopped")
        removeLocationUpdates()
        wakeLock?.release()
        super.onDestroy()
    }

    private fun stopBarometer() {
        baroRepo.stopSensorUpdates()
    }

    private fun removeLocationUpdates() {
        if (!isGpsRunning) return
        
        nmeaListener?.let {
            locationManager.removeNmeaListener(it)
            //Log.d(TAG, "Removed NMEA listener")
        }
        locationManager.removeUpdates(locationListener)
        locationManager.unregisterGnssStatusCallback(gnssCallback)
        isGpsRunning = false
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }

    private fun updateNotification() {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (LocationRepository.isRecording) {
            val title = "Registrazione GPS in corso"
            val text = "Sto registrando la traccia..."
            val notification = buildNotification(title, text)
            // Se stiamo registrando, il servizio DEVE essere in foreground
            startForeground(LOCATION_SERVICE_CHANNEL, notification)
        } else {
            // Se non stiamo registrando, togliamo il servizio dal primo piano.
            // La notifica sparirà, e il servizio rimarrà attivo in background per il Bluetooth
            // finché il sistema lo consente.
            stopForeground(STOP_FOREGROUND_REMOVE)
            // Assicuriamoci che qualsiasi notifica residua (magari postata con notify()) venga rimossa
            manager.cancel(LOCATION_SERVICE_CHANNEL)
        }
    }

    private fun buildNotification(title: String, text: String): android.app.Notification {
        val channelId = "LOCATION_SERVICE_CHANNEL"
        val channelName = "Location Service"
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        
        if (manager.getNotificationChannel(channelId) == null) {
            val channel = NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_LOW)
            channel.setSound(null, null)
            channel.setShowBadge(false)
            manager.createNotificationChannel(channel)
        }

        val notifyIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            this, 0, notifyIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, channelId)
            .setOngoing(true)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_start)
            .setContentIntent(pendingIntent)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setSilent(true) // Impedisce il suono ad ogni aggiornamento
            .build()
    }
}
