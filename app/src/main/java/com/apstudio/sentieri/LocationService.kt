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
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.navigation.NavDeepLinkBuilder
import com.apstudio.sentieri.db.LocationRepository

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
        private const val TAG = "LocationService"
    }

    private lateinit var locationManager: LocationManager
    private lateinit var locationListener: LocationListener
    private var nmeaListener: OnNmeaMessageListener? = null
    private lateinit var gnssCallback: GnssStatus.Callback
    private  val baroRepo: BaroRepo by lazy { BaroRepo(this) }
    private var hasMslAltitude = false
    private var speedKnots: Double = 0.0
    private var speedKmh: Int = 0

    override fun onCreate() {
        super.onCreate()
        //Log.d(TAG, "Service created")
        locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
        hasMslAltitude = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE // Android 14

        initializeGnssCallback()
        initializeLocationListener()
        initializeNmeaListener()
        requestLocationUpdates()
        initializeBarometer()
        createNotificationChannel()
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
            LocationRepository.processNewLocation(newLocation, mslAltitude, baroPress)
        }
    }

    private fun initializeNmeaListener() {
        // al momento utilizzare sempre la velocità ricavata da NMEA
        nmeaListener = OnNmeaMessageListener { message, _ ->
           parseNmeaMessage(message)
        }
    }

    private fun requestLocationUpdates() {
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
            "\$GPGGA", "\$GNGGA" -> {
                parseGPGGA(message)}
            "\$GPRMC" -> {
                parseGPRMC(message)}
            "\$GNVTG" -> {
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
        super.onDestroy()
        //Log.d(TAG, "Service destroyed")
        stopBarometer()
        LocationRepository.updateGpsStatus("stopped")
        removeLocationUpdates()
    }

    private fun stopBarometer() {
        baroRepo.stopSensorUpdates()
    }

    private fun removeLocationUpdates() {
        nmeaListener?.let {
            locationManager.removeNmeaListener(it)
            //Log.d(TAG, "Removed NMEA listener")
        }
        locationManager.removeUpdates(locationListener)
        locationManager.unregisterGnssStatusCallback(gnssCallback)
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }

    private fun createNotificationChannel() {
        val channelName = "Location Service"
        val importance = NotificationManager.IMPORTANCE_HIGH
        val channelId = "LOCATION_SERVICE_CHANNEL" // Assicurati che sia univoco
        val channel = NotificationChannel(channelId, channelName, importance)
        channel.setSound(null, null) // Considera se vuoi un suono o meno
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
        val notifyIntent = Intent(this, MainActivity::class.java).apply {
            // FLAG_ACTIVITY_SINGLE_TOP: se l'attività è già in esecuzione, non ne crea una nuova
            // FLAG_ACTIVITY_CLEAR_TOP: pulisce eventuali altre attività sopra di essa
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }

        // PendingIntent per navigare a MappaFragment
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            notifyIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, channelId)
            .setOngoing(true)
            .setContentTitle("Registrazione GPS in corso") // Titolo più conciso
            .setContentText("Sentieri sta registrando la traccia") // Usa setContentText per il corpo
            .setSmallIcon(R.drawable.ic_start)
            .setContentIntent(pendingIntent)
            .setCategory(NotificationCompat.CATEGORY_SERVICE) // Usa NotificationCompat per coerenza
            .setPriority(NotificationCompat.PRIORITY_DEFAULT) // Usa NotificationCompat per coerenza
            // .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE) // Per Android 12+ se vuoi che appaia subito
            .build()

        // Usa un ID univoco per startForeground, non una costante stringa per il channelId
        // LOCATION_SERVICE_CHANNEL è l'ID della notifica, non del canale qui
        startForeground(LOCATION_SERVICE_CHANNEL, notification) // Usa l'ID definito nella companion object
    }

}
