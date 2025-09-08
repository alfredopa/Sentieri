package com.apstudio.sentieri

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.GnssStatus
import android.location.Location
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
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStoreOwner
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.navigation.NavDeepLinkBuilder
import com.apstudio.sentieri.MappaFragment.Companion.SEND_LOCATION_ACTION

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
        private const val LOCATION_UPDATE_INTERVAL_MS = 2000L
        private const val MIN_DISTANCE_CHANGE_METERS = 3f
        private const val MIN_ACCURACY_METERS = 40f
        private const val TAG = "LocationService"
    }

    private lateinit var locationManager: LocationManager
    private lateinit var locationListener: LocationListener
    private var nmeaListener: OnNmeaMessageListener? = null
    private lateinit var gnssCallback: GnssStatus.Callback
    private  val baroRepo: BaroRepo by lazy { BaroRepo(this) }
    private var milliBar = 0.0F
    private var hasMslAltitude = false
    private var speedKnots: Double = 0.0
    private var speedKmh: Double = 0.0
    private val gpsViewModel: GpsViewModel by lazy {
        ViewModelProvider(application as ViewModelStoreOwner)[GpsViewModel::class.java]
    }

    override fun onCreate() {
        super.onCreate()
        //Log.d(TAG, "Service created")
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
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
                gpsViewModel.numSat = satelliteCount
            }

            override fun onFirstFix(ttffMillis: Int) {
                super.onFirstFix(ttffMillis)
                gpsViewModel.updateGpsStatus("fixed")
                //Log.d(TAG, "First fix in $ttffMillis ms")
            }

            override fun onStarted() {
                gpsViewModel.updateGpsStatus("started")
                //Log.d(TAG, "GNSS started")
                super.onStarted()
            }

            override fun onStopped() {
                gpsViewModel.updateGpsStatus("stopped")
                //Log.d(TAG, "GNSS stopped")
                super.onStopped()
            }
        }
    }

    private fun initializeLocationListener() {
        locationListener = LocationListener { newLocation ->
            //Log.d(TAG, "onLocationChanged: Accuracy = ${newLocation.accuracy}")
            if (newLocation.accuracy > MIN_ACCURACY_METERS) {
                //Log.w(TAG, "Location accuracy is too low: ${newLocation.accuracy}")
                return@LocationListener
            }
            sendBroadcast(newLocation)
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
        if (gpsViewModel.usaBaro) {
            //Log.d(TAG, "Starting barometer sensor updates")
            //baroRepo = BaroRepo(this)
            baroRepo.startSensorUpdates()
        }
    }

    private fun sendBroadcast(newLocation: Location) {
        val broadcastIntent = Intent().apply {
            action = SEND_LOCATION_ACTION
            putExtra("posizione", newLocation)
            putExtra("altitudine", gpsViewModel.mslAltitude.value!!)
            //SimpleFileLogger.log(TAG, "sendBroadcast - Android < 14: Using NMEA for MSL. Current gpsViewModel.mslAltitude = ${gpsViewModel.mslAltitude.value}, newLocation.altitude (WGS84) = ${newLocation.altitude}")

            if (gpsViewModel.usaBaro && baroRepo.baroData.isInitialized) {
                milliBar = baroRepo.baroData.value ?: 0.0F
                putExtra("milliBar", milliBar)
            }
            setClass(this@LocationService, MainActivity::class.java)
        }
        LocalBroadcastManager.getInstance(this).sendBroadcast(broadcastIntent)
        //Log.d(TAG, "Location broadcast sent: MilliBar = $milliBar, MSL Altitude = ${gpsViewModel.mslAltitude}")
    }

    private fun parseNmeaMessage(message: String) {
        //SimpleFileLogger.log(TAG, "NMEA Received: $message") // <-- AGGIUNGI QUESTO LOG
        if (message.length < 6) {
            //SimpleFileLogger.log(TAG, "NMEA message too short: $message")
            return
        }
        when (message.substring(0, 6)) {
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
                gpsViewModel.updateMslAltitude(mslFromNmea)
                SimpleFileLogger.log(TAG, "parseNmeaMessage - Updated gpsViewModel.mslAltitude from NMEA: ${gpsViewModel.mslAltitude.value}")
            }
        }
    }

    private fun parseGPRMC(message: String) {
        val parts = message.split(",")
        if (parts.size > 7 && parts[3].isNotEmpty()) { // Check if the message is valid and has enough fields
            speedKnots = parts[7].toDoubleOrNull() ?: 0.0
            speedKmh = speedKnots * 1.852
            gpsViewModel.updateVelocita(speedKmh)
            //Log.d("NMEA",  "Velocità (GPRMC): %.2f nodi, %.2f km/h, $speedKnots, $speedKmh")
        }
    }

    private fun parseGNVTG(message: String) {
        val parts = message.split(",")
        if (parts.size > 7) {
            speedKnots = parts[5].toDoubleOrNull() ?: 0.0
            speedKmh = parts[7].toDoubleOrNull() ?: (speedKnots * 1.852)
            gpsViewModel.updateVelocita(speedKmh)
            //Log.d("NMEA",  "Velocità (GNVTG): %.2f nodi, %.2f km/h, $speedKnots, $speedKmh")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        //Log.d(TAG, "Service destroyed")
        stopBarometer()
        gpsViewModel.updateGpsStatus("stopped")
        removeLocationUpdates()
    }

    private fun stopBarometer() {
        if (gpsViewModel.usaBaro) {
            //Log.d(TAG, "Stopping barometer sensor updates")
            baroRepo.stopSensorUpdates()
        }
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
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)

        // PendingIntent per navigare a MappaFragment
        val pendingIntent = NavDeepLinkBuilder(applicationContext)
            .setGraph(R.navigation.nav_graph)
            .setDestination(R.id.mappaFragment)
            .setComponentName(MainActivity::class.java)
            // Aggiungi qui eventuali argomenti se MappaFragment ne richiede
            // .setArguments(bundleOf("argomentoChiave" to valore))
            .createTaskStackBuilder() // Cruciale per un corretto back stack
            .getPendingIntent(
                0, // requestCode
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