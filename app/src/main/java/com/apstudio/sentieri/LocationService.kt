package com.apstudio.sentieri

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.GnssStatus
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.location.OnNmeaMessageListener
import android.os.IBinder
import android.renderscript.ScriptGroup
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStoreOwner
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.navigation.NavDeepLinkBuilder
import androidx.preference.PreferenceManager
import com.apstudio.sentieri.MappaFragment.Companion.SEND_LOCATION_ACTION

// attualmente il sensore barometro se esiste viene utlizzato all'interno del servizio
//creando il repository BaroRepo. Andrebbe spostato nel ViewModel o comunque utilizzato con un observer
// il Repository BaroRepo è iniettato nel servizio per recuperare i dati pressione
class LocationService : Service() {

    private lateinit var posizione: Location
    private lateinit var locationManager: LocationManager
    private lateinit var locationListener: LocationListener
    private lateinit var nmeaListener: OnNmeaMessageListener
    private lateinit var gnssCallback: GnssStatus.Callback
    private val NOTIFICATION_CHANNEL_ID = 1234
    private lateinit var baroRepo : BaroRepo
    private var haBaro = false
    private var setBaro = false
    private var milliBar = 0.0F

    private val gpsViewModel: GpsViewModel by lazy {
        ViewModelProvider(application as ViewModelStoreOwner)[GpsViewModel::class.java]
    }

    override fun onCreate() {
        super.onCreate()
        // Ottieni l'istanza del LocationManager
        locationManager = applicationContext.getSystemService(LOCATION_SERVICE) as LocationManager
        //gpsRepository = GpsRepository()
        // Legge se esiste e se attivare SENSORE BAROMETRO da Preferences
        //Log.d("service", "attiva service")
        val context = applicationContext
        val application = applicationContext as AppSentieri
        val gpsViewModel: GpsViewModel by lazy {
            ViewModelProvider(application)[GpsViewModel::class.java]
        }
        gnssCallback = object : GnssStatus.Callback() {
            override fun onSatelliteStatusChanged(status: GnssStatus) {
                //gpsViewModel.updateGpsStatus("aggiornato")
                val satelliteCount = status.satelliteCount
                var usedSatellites = 0
                for (i in 0 until satelliteCount) {
                    if (status.usedInFix(i)) {
                        usedSatellites++
                    }
                    //val constellationType = status.getConstellationType(i)
                    //val svid = status.getSvid(i)
                    //val cn0DbHz = status.getCn0DbHz(i)
                    //val elevationDegrees = status.getElevationDegrees(i)
                    //val azimuthDegrees = status.getAzimuthDegrees(i)
                    //Log.d("GnssStatusExample", "Satellite $i:")
                    //Log.d("GnssStatusExample", "  Costellazione: $constellationType")
                    //Log.d("GnssStatusExample", "  Svid: $svid")
                    //Log.d("GnssStatusExample", "  Cn0DbHz: $cn0DbHz")
                    //Log.d("GnssStatusExample", "  Elevazione°: $elevationDegrees")
                    //Log.d("GnssStatusExample", "  Azimuth: $azimuthDegrees")
                    //Log.d("GnssStatusExample", "  Usato nel fix: ${status.usedInFix(i)}")
                }
                //Log.d("GnssStatusExample", "Satelliti Totali : $satelliteCount")
                //Log.d("GnssStatusExample", "Satelliti usati: $usedSatellites")
                gpsViewModel.numSat = satelliteCount
            }
            // ... altri metodi di callback per gestire lo stato del GNSS
            override fun onFirstFix(ttffMillis: Int) {
                super.onFirstFix(ttffMillis)
                gpsViewModel.updateGpsStatus("fixed")
                //Log.d("gpservice", "Primmo fix in $ttffMillis ms")
            }
            override fun onStarted() {
                gpsViewModel.updateGpsStatus("started")
                //Log.d("gpservice", "started")
                super.onStarted()
            }
            override fun onStopped() {
                gpsViewModel.updateGpsStatus("stopped")
                //Log.d("gpservice", "stopped")
                super.onStopped()
            }
        }

        // Crea un LocationListener
        locationListener = LocationListener { location -> // invia posizione solo con velocità maggiore di 0.5 m/s, in futuro considerare valore utente di velocità minima da registrare
            // altitudine msl valorizzata da stringa  NMEA e corretta
            // COMMENTA PER TEST troppo restrittiva
            if (location.verticalAccuracyMeters > 50) return@LocationListener
            //if (BuildConfig.DEBUG)
            if (location.accuracy > 40) return@LocationListener
            // velocità in metri/secondo
            if (location.speed < 0.5f) return@LocationListener

            posizione = location
            // se assegna valore altitudine msl da NMEA
            if (gpsViewModel.mslAltitude != gpsViewModel.zeroMsl)
                posizione.altitude = gpsViewModel.mslAltitude

            /*var mslAltitude = 0.0
            var precMslAltitude : Float = 0.0F
            if (Build.VERSION.SDK_INT >= 34 && location.hasMslAltitude()) {
                mslAltitude = location.mslAltitudeMeters
                precMslAltitude = location.mslAltitudeAccuracyMeters
            }*/
//            Log.d("GGA", "Altitudine ${location.altitude} velocità ${location.speed} "  +
//                    "Accuracy ${location.accuracy}")

            if (haBaro && setBaro) {
                // assegna valore altitudine da Barometro
                milliBar = baroRepo.baroData.value!!
                //Log.d("service", "barometro millibar $milliBar")
            }
            sendBroadcast()
        }

        // Richiedi aggiornamenti della posizione
        if ((ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED) && (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED)
        ) {
              return
        }

        locationManager.registerGnssStatusCallback(
            ContextCompat.getMainExecutor(context), gnssCallback)

        // Crea NMEA listener
        nmeaListener = OnNmeaMessageListener { message, _ ->
            // Do something with NMEA message $GPGGA
            loggaNMEA(message)
        }
        // registra il listener di NMEA
        locationManager.addNmeaListener(nmeaListener, null)

        locationManager.requestLocationUpdates(
            LocationManager.GPS_PROVIDER,
            1500,
            2f,    // ATTENZIONE RIPORTA a 2M
            locationListener
        )
        val preferenze = PreferenceManager.getDefaultSharedPreferences(context)
        //Log.d("service", "attiva pref")
        if (preferenze.contains("haBaro")) {
            haBaro = preferenze.getBoolean("haBaro", false)
        }
        if (preferenze.contains("setBaro")) {
            setBaro = preferenze.getBoolean("setBaro", false)
        }
        if (haBaro && setBaro) {
            //Log.d("service", "startSensorUpdates")
            baroRepo = BaroRepo(context)
            //Log.d("baroRepo", "avvia barometro")
            baroRepo.startSensorUpdates()
        }
        createNotificationChannel()
    }

    private fun sendBroadcast(){
        val broadcastIntent = Intent()
        broadcastIntent.action = SEND_LOCATION_ACTION
        broadcastIntent.putExtra("posizione", posizione)
        if (haBaro && setBaro) {
            broadcastIntent.putExtra("milliBar", milliBar)
        }
        broadcastIntent.setClass(this, MainActivity::class.java)
        LocalBroadcastManager.getInstance(this).sendBroadcast(broadcastIntent)
    }

    private fun loggaNMEA(message : String) {
        //  $GPGGA,113951.00,3913.488983,N,00906.041103,E,1,03,1.6,0.0,M,46.8,M,,*6A
        /*   Ora UTC in cui è stata calcolata la posizione
             Latitudine espressa in gradi
             Longitudine espressa in gradi
             Qualità del posizionamento
                0= invalido
                1= GNSS fix
                2= DGPS fix
                3= PPS
                4= Posizionamento RTK
                6= posizione stimata (dead reckoning)
                7= Posizione inserita manualmente
                8= Posizione ottenuta da osservazioni simulate
             Numero di satelliti utilizzato
            HDOP
            Quota sul livello del mare
            Ondulazione del geoide rispetto all’ellissoide WGS84
         */

        if (message.startsWith('$'+"GPGGA") or message.startsWith('$'+"GNGGA")) {
            val nmeaSplit = message.split(",")
            val valido = nmeaSplit[6]
            if (valido == "1") {
                gpsViewModel.updateGpsStatus("fixed")
                gpsViewModel.mslAltitude = nmeaSplit[9].toDoubleOrNull() ?: -1.0
            }
            //Log.d("GGA", "NMEA $valido ${gpsViewModel.mslAltitude} ")
        }
        /*if (message.startsWith('$'+"GPGSA") or message.startsWith('$'+"GNGSA")) {
            val nmeaSplit = message.split(",")
            val vDop = nmeaSplit[15].toFloatOrNull() ?: 0.0f
            //bearing = nmeaSplit[7].toFloatOrNull() ?: 0.0f
            Log.d("GGA", "vDop, $message")
        }*/
        val message = message.split(",")

        if (message[0].equals("\$GPGSA", ignoreCase = true)) {
            if (message.size > 15 && message[15].isNotEmpty()) {
                val latestPdop = message[15]
//                Log.d("GSA", "NMEA Pdop $latestPdop  ")
            }

            if (message.size > 16 && message[16].isNotEmpty()) {
                val latestHdop = message[16]
//                Log.d("GSA", "NMEA Hdop $latestHdop  ")
            }

            if (message.size > 17 && message[17].isNotEmpty() && !message[17].startsWith(
                    "*"
                )
            ) {
                val latestVdop = message[17].split("\\*".toRegex()).dropLastWhile { it.isEmpty() }
                        .toTypedArray()[0]
//                Log.d("GSA", "NMEA Vdop $latestVdop  ")
            }
//            Log.d("GSA", "NMEA $message")
        }

    }

    override fun onDestroy() {
        super.onDestroy()
        // rimuovi il listener barometro
        if (haBaro && setBaro) {
            //Log.d("baroRepo", "stop barometro")
            baroRepo.stopSensorUpdates()
        }
        // Rimuovi il LocationListener
        locationManager.removeNmeaListener(nmeaListener)
        locationManager.removeUpdates(locationListener)
        // CALLBACK
        gpsViewModel.updateGpsStatus("stopped")
        locationManager.unregisterGnssStatusCallback(gnssCallback)
        //Log.d("LocationService", "stop gps")
    }

    override fun onBind(intent: Intent): IBinder {
        TODO("Return the communication channel to the service.")
    }

    private fun createNotificationChannel() {
        val channelName = "location service"
        val importance = NotificationManager.IMPORTANCE_HIGH
        val channelId = "serviceeee"
        val channel = NotificationChannel(channelId, channelName, importance)
        channel.setSound(null, null)
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
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
        startForeground(NOTIFICATION_CHANNEL_ID, notification)
    }

}