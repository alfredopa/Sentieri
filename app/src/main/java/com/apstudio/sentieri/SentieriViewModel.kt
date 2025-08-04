package com.apstudio.sentieri

import android.location.Location
import android.net.Uri
import androidx.fragment.app.Fragment
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.liveData
import androidx.lifecycle.viewModelScope
import com.apstudio.sentieri.MapUtils.disegnaLine
import com.apstudio.sentieri.db.FotoPoi
import com.apstudio.sentieri.db.LayerItem
import com.apstudio.sentieri.db.PoiDB
import com.apstudio.sentieri.db.Sentieri
import com.apstudio.sentieri.db.SentieriDB
import com.apstudio.sentieri.db.SentieriRepo
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.federicomatera.agpxp.models.WayPoint
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.FolderOverlay
import org.osmdroid.views.overlay.Polyline
import java.sql.Timestamp

class SentieriViewModel(private val repository: SentieriRepo) : ViewModel() {

    companion object {
        // costanti per calcolo dislivello con GPS con filtro MovingAverage
        private const val ALTITUDE_CHANGE_THRESHOLD_METERS = 1.5 // Differenza minima di altitudine per considerare un cambio di quota
        private const val MOVING_AVERAGE_WINDOW_SIZE = 9 // Numero di valori da tenere in memoria per la media
    }
    private val _traccia = MutableLiveData<Polyline>()
    val traccia : LiveData<Polyline> = _traccia
    val listaTracce = FolderOverlay()
    val recTraccia = FolderOverlay()
    var line : Polyline = Polyline()
    // liste di punti gps e waypoint
    val puntiGPS = mutableListOf<WayPoint>()
    var wayPoint = mutableListOf<WayPoint>()
    var poiDBList = mutableListOf<PoiDB>()
    val fotoInPoiDB = mutableListOf<Uri>()
    val fotoList = mutableListOf<Uri>()
    val layerItems = mutableListOf<LayerItem>()
    val geoPuntiPercorso = mutableListOf<GeoPoint>()
    var alertFuoriTraccia : Boolean = false
    var tracciaDaSeguire : String = ""
    var poi = GeoPoint(0.0, 0.0)
    var bloccaMappa = true
    var connessione = false
    var menuMap = 0             // indice della mappa utilizzata secondo le voci del menu mappa
    var uriMappa: Uri = Uri.EMPTY
    var isFixed = false

    private var updatesJob: Job? = null
    var isRecording = false
    var ricerca = String()
    var ultPosizione = GeoPoint(40.120875, 9.012893, 40.0)   // posizione iniziale mappa
    var newPunto =  GeoPoint(0.0,0.0,0.0)
    private var oldPunto =  GeoPoint(0.0,0.0,0.0)
    var ultZoom = (9)

    // valori visualizzati nel cruscotto
    private val _distanzaMetri = MutableLiveData(0)
    val distanzaMetri : LiveData<Int> = _distanzaMetri
    private val _dislivPiu = MutableLiveData<Double>(0.0)
    val dislivPiu: LiveData<Double> = _dislivPiu
    private val _dislivMeno = MutableLiveData<Double>(0.0)
    val dislivMeno: LiveData<Double> = _dislivMeno
    private val _velocita = MutableLiveData(0)
    val velocita : LiveData<Int> = _velocita
    private val _quota = MutableLiveData(0)
    val quota : LiveData<Int> = _quota
    var oraInizio: Long = 0
    var elapsedTime: Long = 0
    private val _tempoTrascorso = MutableLiveData<String>()
    val tempoTrascorso: LiveData<String> = _tempoTrascorso
    private val _secondiMovimento = MutableLiveData<Long>(0)
    val secondiMovimento: LiveData<Long> = _secondiMovimento

    // valori per il calcolo del dislivello con GPS con filtro MovingAverage
    //private var previousAltitude: Double? = null
    //private val altitudeHistory = mutableListOf<Double>()
    private val gpsAltitudeHistory: ArrayDeque<Double> = ArrayDeque(MOVING_AVERAGE_WINDOW_SIZE)
    private var previousFilteredAltitude: Double? = null
    // valori di riferimento della traccia da seguire
    var trackDistanza = 0f
    var trackAscesa = 0
    var trackDiscesa = 0

    // valori per barometro
    var haBaro = false
    var setBaro = false
    private var newQuota: Int? = 0
    private var oldQuota: Int? = 0

    // coefficiente per filtro passa basso quota barometro da 0 ad 1
    // con 0.1 da valori troppo bassi (-200 dislivello)
    private val alfa: Double = 0.23
    private var millibar = 0F
    var NORMAL_PRESSURE = 1013.25F
    private val _isCalibrato = MutableLiveData(false)
    val isCalibrato : LiveData<Boolean> = _isCalibrato
    var bottomState = 0

    init {
        val traccia = Polyline()
        _traccia.value = traccia
    }

    fun aggiornaDati(loc: Location?, altitudine: Double, baroPress: Float) {
        if (loc == null) {
            //Log.w("GGA", "Location is null, cannot update data")
            return
        }
        newPunto = GeoPoint(loc.latitude, loc.longitude, altitudine)
        // al primo aggiornamento di posizione valorizza isFixed true
        if (!isFixed) {
            // al primo fix gps oldPunto e newPunto coincidono
            oldPunto = newPunto
            isFixed = true
            //Log.d("aggiornaDati", "ViewModel primo  fixed true")
            return
        }

        //if (newPunto.latitude == oldPunto.latitude && newPunto.longitude == oldPunto.longitude) return
        _velocita.value = (loc.speed * 3.6).toInt()
        //Log.d("aggiornaDati", "Velocità ${velocita.value}")
        // determina se calcolare altitudine da Gps o barometro assegna nuova altitudine al LiveData
        if (haBaro && setBaro && isCalibrato.value == true) {
            millibar = baroPress
            // utilizza formula ipsometrica per calcolare altitudine
            val altitudineBaro: Double = MapUtils.calcolaAltitudineIpso(millibar, NORMAL_PRESSURE).toDouble()
            newPunto = GeoPoint(loc.latitude, loc.longitude, altitudineBaro)
            dislivelloBaro(altitudineBaro.toInt())
            _quota.value = altitudineBaro.toInt()
        } else {
            // la quota deve essere quella media calcolata con MovingAverage
            val nuovaQuota = processGpsAltitude(altitudine)
            //val nuovaQuota = dislivelloGPS(altitudine)
            if (nuovaQuota != null) {
                _quota.value = nuovaQuota.toInt()
                newPunto = GeoPoint(loc.latitude, loc.longitude, nuovaQuota)
            }
            //SimpleFileLogger.log("aggiornaDati", "nuovaQuota $nuovaQuota")
        }

        if (oldPunto.latitude != 0.0 && oldPunto.longitude != 0.0) {
            _distanzaMetri.value = (distanzaMetri.value ?: 0) + MapUtils.getDistanceInMeters(oldPunto, newPunto)
        }

        // aggiunge il punto alla traccia
        _traccia.value?.addPoint(newPunto)
        // salva punto nell'array globale di punti (wayPoints)
        salvaPuntoGPS(newPunto)
        // memorizza punto come oldpunto per confronto col prossimo aggiornamento
        oldPunto = newPunto
    }

    private fun dislivelloBaro(altitudineBaro: Int) {
        if (oldQuota == 0) {
            oldQuota = altitudineBaro
            return
        }
        // Filtro passa basso
        val quotaFiltrata  = ((alfa * altitudineBaro) + ((1 - alfa) * oldQuota!!)).toInt()
        // Calcola il dislivello positivo formula IPSOMETRICA
        if (quotaFiltrata > oldQuota!!) {
            val diffPiu = quotaFiltrata - oldQuota!!
            _dislivPiu.value = _dislivPiu.value?.plus(diffPiu)
        } else {
            val diffMeno = oldQuota!! - quotaFiltrata
            _dislivMeno.value = _dislivMeno.value?.plus(diffMeno)
        }
        // Aggiorna la quota precedente
        oldQuota = quotaFiltrata
        // Imposta quota come media filtrata
        newQuota = quotaFiltrata
        //Log.d("viewmodel", "aggiornadati ${quotaIpso.value}  new $newQuotaIpso  d+ ${dislivPiuIpso.value} d- ${dislivMenoIpso.value}")
    }

    fun processGpsAltitude(gpsAltitude: Double): Double? {
        // CALCOLO DISLIVELLO CON QUOTA DA GPS
        // attende il numero di altitudeHistory punti prima di stimare altitudine
        if (gpsAltitudeHistory.size < MOVING_AVERAGE_WINDOW_SIZE -1) { // -1 because we add the current one before checking size in applyMovingAverage
            gpsAltitudeHistory.addLast(gpsAltitude) // Add to history even before full window for average calculation
            return null
        } else {
            // Add the current altitude before calculating the average
            // applyMovingAverage will also add it to the history
            val filteredAltitude = applyMovingAverage(gpsAltitude)
            updateAltitudeChanges(filteredAltitude)
            return filteredAltitude
        }
    }

    private fun updateAltitudeChanges(currentFilteredAltitude: Double) {
        if (previousFilteredAltitude == null) {
            previousFilteredAltitude = currentFilteredAltitude
            return
        }
        previousFilteredAltitude?.let { prevAlt ->
            val altitudeDifference = currentFilteredAltitude - prevAlt
            if (altitudeDifference > ALTITUDE_CHANGE_THRESHOLD_METERS) {
                // Assuming _positiveAltitudeChange is LiveData/StateFlow initialized to 0.0
                _dislivPiu.value = (_dislivPiu.value ?: 0.0) + altitudeDifference
                previousFilteredAltitude = currentFilteredAltitude
            } else if (altitudeDifference < -ALTITUDE_CHANGE_THRESHOLD_METERS) { // Consider a threshold for descent too
                // Accumulate absolute descent if the change is significantly negative
                _dislivMeno.value = (_dislivMeno.value ?: 0.0) + altitudeDifference
                previousFilteredAltitude = currentFilteredAltitude
            }
        }
        //SimpleFileLogger.log("updateAltitudeChanges", "filteredAltitude $currentFilteredAltitude previousFilteredAltitude $previousFilteredAltitude dislivPiu ${dislivPiu.value} dislivMeno ${dislivMeno.value}")
        //Log.d("updateAltitudeChanges", "filteredAltitude $currentFilteredAltitude previousFilteredAltitude $previousFilteredAltitude dislivPiu ${dislivPiu.value} dislivMeno ${dislivMeno.value}")

    }

    /**
     * Applies a moving average filter to the given altitude.
     * Adds the altitude to the history and ensures the history does not exceed the window size.
     */
    private fun applyMovingAverage(altitude: Double): Double {
        gpsAltitudeHistory.addLast(altitude)
        if (gpsAltitudeHistory.size > MOVING_AVERAGE_WINDOW_SIZE) {
            gpsAltitudeHistory.removeFirst()
        }
        return gpsAltitudeHistory.average()
    }

    //--------------------------------------------------------------------------------------------------------------------------------
    // Codice precedente per calcolo dislivello GPS
    /*private fun dislivelloGPS(altitudineGps: Double): Double? {
        // CALCOLO DISLIVELLO CON QUOTA DA GPS
        // attende il numero di altitudeHistory punti prima di stimare altitudine
        //Log.d("dislivelloGPS", "altitudineGps $altitudineGps")
        return if (altitudeHistory.size < movingAverageWindowSize) {
            altitudeHistory.add(altitudineGps)
            null
        } else {
            addLocation(altitudineGps)
        }
    }*/

    /*private fun addLocation(altitudineGps: Double): Double {
        // Filtra i dati di altitudine usando una media mobile
        val filteredAltitude = applyMovingAverage(altitudineGps)
        // Calcola la differenza di altitudine
        if (previousAltitude != null) {
            val altitudeDifference = (filteredAltitude - previousAltitude!!)
            // Accumula le differenze positive
            if (altitudeDifference > 1) {
                _dislivPiu.value = (_dislivPiu.value ?: 0.0) + altitudeDifference
            } else {
                _dislivMeno.value = (_dislivMeno.value ?: 0.0) + abs(altitudeDifference)
            }
        }
        SimpleFileLogger.log("addLocation",  "filteredAltitude $filteredAltitude previousAltitude $previousAltitude dislivPiu ${dislivPiu.value} dislivMeno ${dislivMeno.value}")
        // Aggiorna l'altitudine precedente
        previousAltitude = filteredAltitude
        return filteredAltitude
    }*/

    /*private fun applyMovingAverage(altitude: Double): Double {
        altitudeHistory.add(altitude)
        if (altitudeHistory.size > movingAverageWindowSize) {
            altitudeHistory.removeAt(0)
        }
        return altitudeHistory.average()
    }*/
    //-------------------------------------------------------------------------------------------------------------------------------------------

    fun resetCruscotto() {
        _quota.value = 0
        _dislivPiu.value = 0.0
        _dislivMeno.value = 0.0
        _velocita.value = 0
        _distanzaMetri.value = 0
        _tempoTrascorso.value = ""
        _secondiMovimento.value = 0
    }

    fun baroCalibrato(barometro: Boolean) {
        _isCalibrato.value = barometro
    }

    private fun salvaPuntoGPS(punto: GeoPoint) {
        val newWayPoint = WayPoint(
            latitude = punto.latitude,
            longitude = punto.longitude,
            elevation = punto.altitude,
            time = Timestamp(System.currentTimeMillis()),
        )
        puntiGPS.add(newWayPoint)
        //Log.d("viewmodel", "salvapuntiGPS $newWayPoint")
    }

    // legge i punti della traccia dal DB Track
    fun leggiTrack(mFinestra: Fragment, id: Int, poiList: MutableList<PoiDB>): Polyline {
        var punto : GeoPoint
        var percorso = Polyline()
        var latit  : Double
        var longit : Double
        var elev    : Double
        val appContext = mFinestra.context
        val dao = appContext?.let { SentieriDB.getInstance(it).trackDao }
        val poiDao = appContext?.let { SentieriDB.getInstance(it).poiDao }
        geoPuntiPercorso.clear()
        val thread = Thread {
            dao?.getTraccia(id)?.forEach {
                latit  = it.Latit.toDouble()
                longit = it.Longit.toDouble()
                elev   = it.Ele.toDouble()
                punto = GeoPoint(latit, longit, elev)
                percorso.addPoint(punto)
                // serve per grafico altimetria
                geoPuntiPercorso.add(punto)
                //Log.v("thread", "$Latit : $Longit")
            }
            if (percorso.actualPoints.isNotEmpty()) {
                percorso = disegnaLine(percorso)
            }
            else
                return@Thread
            // legge waypoint della traccia dal DB Poi
            poiDao?.getPoibyID(id)?.forEach {
                poiList.add(it)
           }

        }
        thread.start()
        thread.join()// Attendi che il CountDownLatch raggiunga 0 (lettura completata)
        return percorso
    }

    private fun incrementMovementSeconds() {
        _secondiMovimento.value = (_secondiMovimento.value ?: 0) + 1
    }

    // coroutine per aggiornamento del tempo di registrazione sul cruscotto
    fun startUpdates() {
        if (updatesJob?.isActive == true) {
            // Coroutine is already running, no need to start a new one
            return
        }
        updatesJob = viewModelScope.launch {
            while (true) {
                val currentTime = System.currentTimeMillis()
                elapsedTime = currentTime - oraInizio
                _tempoTrascorso.value = MapUtils.formatElapsedTime(elapsedTime)
                //Log.d("Mappa", "Tempo trascorso: $elapsedTime  ${tempoTrascorso.value}")
                if (velocita.value != 0) {
                    incrementMovementSeconds()
                }
                delay(1000)
            }
        }
    }

    fun stopUpdates() {
        updatesJob?.cancel()
        updatesJob = null
//Log.d("Mappa", "Stop running")
    }

    /*// filtro basato su velocità ascensionale in m/sec
    velocità ascensionale media in bici, espressa in m/sec:
    Ciclista principiante su pendenza moderata (5-10%): 0.5 - 1.0 m/sec
    Ciclista intermedio su pendenza moderata (5-10%): 1.0 - 1.5 m/sec
    Ciclista avanzato su pendenza moderata (5-10%): 1.5 - 2.0 m/sec
    Ciclista professionista su pendenza moderata (5-10%): 2.0 - 3.0 m/sec
    Ciclista su salita ripida (15-20%): 0.2 - 1.0 m/sec (indipendentemente dal livello di forma fisica)

    fun filterGpsPoints(points: List<GpsPoint>): List<GpsPoint> {
        val maxAscentSpeed = 5.0 // m/s (esempio per camminata)
        val filteredPoints = mutableListOf<GpsPoint>()

        for (i in 0 until points.size - 1) {
            val p1 = points[i]
            val p2 = points[i + 1]
            val deltaH = p2.altitude - p1.altitude
            val deltaT = p2.timestamp - p1.timestamp
            val ascentSpeed = deltaH / deltaT

            if (ascentSpeed <= maxAscentSpeed) {
                filteredPoints.add(p2)
            }
        }
        return filteredPoints
    }*/

    fun getSavedSentieri() = liveData {
        repository.sentieriDB.collect {
            emit(it)
        }
    }

    fun trovaSentiero(id: Int): LiveData<Sentieri> {
        return repository.cercaId(id)
    }

    fun cercaNome(searchQuery: String): LiveData<List<Sentieri>> {
        return repository.cercaNome(searchQuery).asLiveData()
    }

    suspend fun salvaSentiero(sentiero: Sentieri): Long {
        return repository.insertDB(sentiero)
    }

    fun cancellaSentiero(id: Int) {
        viewModelScope.launch {
            repository.cancellaSentiero(id)
        }
    }

    /*fun cercaPoi(id: Int): List<PoiDB> {
        return repository.cercaPoi(id)
    }*/

    fun listaFotoId(id: Int): List<FotoPoi> {
        return repository.listaFotoId(id)
    }

    override fun onCleared() {
        super.onCleared()
        isRecording = false
    }

}
