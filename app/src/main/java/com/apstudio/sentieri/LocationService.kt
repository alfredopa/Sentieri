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
        private const val MIN_DISTANCE_CHANGE_METERS = 2f
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

            /*if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                val rawMslAltitude = newLocation.mslAltitudeMeters
                SimpleFileLogger.log(TAG, "sendBroadcast - Android 14+: newLocation.mslAltitudeMeters = $rawMslAltitude, newLocation.altitude (WGS84) = ${newLocation.altitude}")
                if (!rawMslAltitude.isNaN()) {
                    gpsViewModel.mslAltitude = rawMslAltitude
                } else {
                    SimpleFileLogger.log(TAG, "sendBroadcast - Android 14+: mslAltitudeMeters is NaN. ViewModel MSL altitude will rely on NMEA if available.")
                    // Qui potresti decidere se gpsViewModel.mslAltitude debba mantenere l'ultimo valore NMEA
                    // o essere impostato a un valore che indica "non disponibile".
                    // Attualmente, se è NaN, gpsViewModel.mslAltitude non viene aggiornato qui.
                }
            } else {
                SimpleFileLogger.log(TAG, "sendBroadcast - Android < 14: Using NMEA for MSL. Current gpsViewModel.mslAltitude = ${gpsViewModel.mslAltitude}, newLocation.altitude (WGS84) = ${newLocation.altitude}")
                // gpsViewModel.mslAltitude è già stato (o sarà) impostato da parseNmeaMessage
            }*/

            putExtra("altitudine", gpsViewModel.mslAltitude)
            SimpleFileLogger.log(TAG, "sendBroadcast - Android < 14: Using NMEA for MSL. Current gpsViewModel.mslAltitude = ${gpsViewModel.mslAltitude}, newLocation.altitude (WGS84) = ${newLocation.altitude}")

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
            if (fixQuality == "1" && mslFromNmea != null) { // O controlla anche altri codici di fix validi
                gpsViewModel.mslAltitude = mslFromNmea
                SimpleFileLogger.log(TAG, "parseNmeaMessage - Updated gpsViewModel.mslAltitude from NMEA: ${gpsViewModel.mslAltitude}")
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
        val channelId = "LOCATION_SERVICE_CHANNEL"
        val channel = NotificationChannel(channelId, channelName, importance)
        channel.setSound(null, null)
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)

        val pendingIntent = NavDeepLinkBuilder(applicationContext)
            .setGraph(R.navigation.nav_graph)
            .setDestination(R.id.mappaFragment)
            .setComponentName(MainActivity::class.java)
            .createPendingIntent()
            .let {
                // Crea un nuovo PendingIntent con i flag corretti
                val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                PendingIntent.getActivity(
                    this,
                    0,
                    Intent(this, MainActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    },
                    flags
                )
            }

        val builder = NotificationCompat.Builder(this, channelId)
        val notification: Notification =
            builder
                .setOngoing(true)
                .setContentInfo("Traccia Sentieri in registrazione")
                .setContentTitle("Registrazione traccia in corso ")
                .setSmallIcon(R.drawable.ic_start)
                .setContentIntent(pendingIntent)
                .setCategory(Notification.CATEGORY_SERVICE)
                .setPriority(NotificationManager.IMPORTANCE_DEFAULT)
                .build()
        startForeground(LOCATION_SERVICE_CHANNEL, notification)
    }

}