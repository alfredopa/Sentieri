package com.apstudio.sentieri

import android.location.Location
import android.net.Uri
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.liveData
import androidx.lifecycle.viewModelScope
import com.apstudio.sentieri.db.FotoPoi
import com.apstudio.sentieri.db.LayerItem
import com.apstudio.sentieri.db.LocationRepository
import com.apstudio.sentieri.db.PoiDB
import com.apstudio.sentieri.db.Sentieri
import com.apstudio.sentieri.db.SentieriRepo
import com.apstudio.sentieri.db.TopoMarkerData
import com.patrykandpatrick.vico.core.entry.ChartEntryModelProducer
import com.patrykandpatrick.vico.core.entry.FloatEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.federicomatera.agpxp.models.WayPoint
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.FolderOverlay
import org.osmdroid.views.overlay.Polyline
import java.sql.Timestamp
import java.util.concurrent.CopyOnWriteArrayList

data class LocationData(val geoPoint: GeoPoint, val bearing: Float)

class SentieriViewModel(private val repository: SentieriRepo) : ViewModel() {

    companion object {
        // costanti per calcolo dislivello con GPS con filtro MovingAverage
        private const val ALTITUDE_CHANGE_THRESHOLD_METERS = 1.9 // Differenza minima di altitudine per considerare un cambio di quota
        private const val MOVING_AVERAGE_WINDOW_SIZE = 9 // Numero di valori da tenere in memoria per la media
        private const val MAX_ALTITUDE_JUMP_METERS_PER_UPDATE = 10.0
    }

    val listaTracce = FolderOverlay()
    val recTraccia = FolderOverlay()
    val topoLayer = FolderOverlay()
    var line : Polyline = Polyline()
    // liste di punti gps e waypoint
    val puntiGPS = CopyOnWriteArrayList<WayPoint>()
    var wayPoint = mutableListOf<WayPoint>()
    var poiDBList = mutableListOf<PoiDB>()
    val fotoInPoiDB = mutableListOf<Uri>()
    val fotoList = mutableListOf<Uri>()
    val layerItems = mutableListOf<LayerItem>()
    val geoPuntiPercorso = mutableListOf<GeoPoint>()
    val toponimiSelezionati = mutableListOf<TopoMarkerData>() // New list
    var alertFuoriTraccia : Boolean = true
    var tracciaDaSeguire : String = ""
    var poi = GeoPoint(0.0, 0.0, 0.0)
    var bloccaMappa = true
    var connessione = false
    var menuMap = 0             // indice della mappa utilizzata secondo le voci del menu mappa
    var uriMappa: Uri = Uri.EMPTY
    var isFixed = false

    private var updatesJob: Job? = null
    var isRecording = false
    var ricerca = String()
    var ultPosizione = GeoPoint(40.120875, 9.012893, 40.0)   // posizione iniziale mappa

    private val _locationData = MutableLiveData<LocationData>()
    val locationData: LiveData<LocationData> = _locationData
    
    private var oldPunto =  GeoPoint(0.0,0.0,0.0)
    var ultZoom = (9)

    // LiveData per osservare i dati dal Repository
    val locationFromRepo: LiveData<Location> = LocationRepository.location
    val mslAltitudeFromRepo: LiveData<Double> = LocationRepository.mslAltitude
    val baroPressureFromRepo: LiveData<Float> = LocationRepository.baroPressure

    // NUOVO: MediatorLiveData per combinare tutte le fonti di dati
    private val _combinedData = MediatorLiveData<Triple<Location, Double, Float>>()

    // valori visualizzati nel cruscotto
    private val _distanzaMetri = MutableLiveData(0)
    val distanzaMetri : LiveData<Int> = _distanzaMetri
    private val _dislivPiu = MutableLiveData(0.0)
    val dislivPiu: LiveData<Double> = _dislivPiu
    private val _dislivMeno = MutableLiveData(0.0)
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
    private val _isAllarmeAttivo = MutableLiveData(true)
    val isAllarmeAttivo: LiveData<Boolean> = _isAllarmeAttivo

    private val gpsAltitudeHistory: ArrayDeque<Double> = ArrayDeque(MOVING_AVERAGE_WINDOW_SIZE)
    private var previousFilteredAltitude: Double? = null
    // valori di riferimento della traccia da seguire
    var trackDistanza = 0f
    var trackAscesa = 0
    var trackDiscesa = 0

    // valori per barometro
    var haBaro = false
    var setBaro = false
    private var oldQuota: Int? = 0

    // coefficiente per filtro passa basso quota barometro da 0 ad 1
    // con 0.1 da valori troppo bassi (-200 dislivello)
    private val alfa: Double = 0.21
    private val alfaGPS: Double = 0.23  //0.25 prec
    var NORMAL_PRESSURE = 1013.25F
    private val _isCalibrato = MutableLiveData(false)
    val isCalibrato : LiveData<Boolean> = _isCalibrato
    var bottomState = 0

    val chartProducer = ChartEntryModelProducer()
    var idTracciaGraficoCorrente: Int = -1

    init {
        // Combina i dati da diverse fonti in un unico LiveData
        _combinedData.addSource(locationFromRepo) { location ->
            val msl = mslAltitudeFromRepo.value ?: location.altitude
            val baro = baroPressureFromRepo.value ?: 0.0f
            _combinedData.value = Triple(location, msl, baro)
        }
        _combinedData.addSource(mslAltitudeFromRepo) { msl ->
            locationFromRepo.value?.let { location ->
                val baro = baroPressureFromRepo.value ?: 0.0f
                _combinedData.value = Triple(location, msl, baro)
            }
        }
        _combinedData.addSource(baroPressureFromRepo) { baro ->
            locationFromRepo.value?.let { location ->
                val msl = mslAltitudeFromRepo.value ?: location.altitude
                _combinedData.value = Triple(location, msl, baro)
            }
        }

        // Osserva i dati combinati e avvia l'elaborazione
        _combinedData.observeForever { (location, mslAltitude, baroPressure) ->
            processNewLocationData(location, mslAltitude, baroPressure)
        }
    }

    fun processNewLocationData(loc: Location, altitudine: Double, baroPress: Float) {
        viewModelScope.launch(Dispatchers.IO) { // Esegui su un thread in background
            _performDataUpdate(loc, altitudine, baroPress)
        }
    }

    private fun _performDataUpdate(loc: Location?, altitudineOriginale: Double, baroPress: Float) {
        if (loc == null) {
            return
        }
        // Calcola altitudine effettiva (GPS o Baro)
        var altitudineCalcolata = altitudineOriginale
        var usaAltitudineBaro = false
        if (haBaro && setBaro && isCalibrato.value == true) {
            val altBaro = MapUtils.calcolaAltitudineIpso(baroPress, NORMAL_PRESSURE).toDouble()
            altitudineCalcolata = altBaro
            usaAltitudineBaro = true
        } else {
            val quotaGpsProcessata = processGpsAltitude(altitudineOriginale) // Presuppone che sia una funzione non bloccante e veloce
            if (quotaGpsProcessata != null) {
                altitudineCalcolata = quotaGpsProcessata
            }
        }

        val currentNewPunto = GeoPoint(loc.latitude, loc.longitude, altitudineCalcolata)
        _locationData.postValue(LocationData(currentNewPunto, loc.bearing))
        Log.d("SentieriViewModel", "processNewLocationData currentNewPunto: $currentNewPunto") // Log più specifico
        // Logica del primo fix
        if (!isFixed) {
            oldPunto = currentNewPunto
            isFixed = true
            // Log.d("SentieriViewModel", "Primo fix GPS. OldPunto: $oldPunto")
            return
        }

        // Aggiorna velocità (usa postValue per LiveData da background thread)
        _velocita.postValue((loc.speed * 3.6).toInt())

        // Aggiorna quota
        if (usaAltitudineBaro) {
            // dislivelloBaro aggiorna _dislivPiu e _dislivMeno internamente,
            // assicurati che usino postValue() se sono LiveData
            dislivelloBaro(altitudineCalcolata.toInt()) // Assumi che dislivelloBaro aggiorni i LiveData con postValue
            _quota.postValue(altitudineCalcolata.toInt())
        } else {
            _quota.postValue(altitudineCalcolata.toInt()) // altitudineCalcolata qui è già la processGpsAltitude
        }

        // Aggiorna distanza
        if (oldPunto.latitude != 0.0 && oldPunto.longitude != 0.0) {
            val nuovaDistanza = (distanzaMetri.value ?: 0) + MapUtils.getDistanceInMeters(oldPunto, currentNewPunto)
            _distanzaMetri.postValue(nuovaDistanza)
        }

        // Salva punto GPS (nella lista in memoria)
        salvaPuntoGPS(currentNewPunto) // Assicurati che questa lista sia thread-safe se accessibile da altrove
        oldPunto = currentNewPunto
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
            _dislivPiu.postValue((_dislivPiu.value?: 0.0) + diffPiu)
        } else {
            val diffMeno = oldQuota!! - quotaFiltrata
            _dislivMeno.postValue((_dislivMeno.value?: 0.0) + diffMeno)
        }
        // Aggiorna la quota precedente
        oldQuota = quotaFiltrata
    }

    fun processGpsAltitude(gpsAltitude: Double): Double? {
        // CALCOLO DISLIVELLO CON QUOTA DA GPS
        if (previousFilteredAltitude == null) {
            previousFilteredAltitude = gpsAltitude
        }
        // attende il numero di altitudeHistory punti prima di stimare altitudine
        if (gpsAltitudeHistory.size < MOVING_AVERAGE_WINDOW_SIZE -1) {
            gpsAltitudeHistory.add(gpsAltitude)
            return null
        } else {
            // Add the current altitude before calculating the average
            val filteredAltitude  = (alfaGPS * gpsAltitude + ((1 - alfaGPS) * previousFilteredAltitude!!))
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
                _dislivPiu.postValue((_dislivPiu.value ?: 0.0) + altitudeDifference) 
                previousFilteredAltitude = currentFilteredAltitude
            } else if (altitudeDifference < -ALTITUDE_CHANGE_THRESHOLD_METERS) { // Consider a threshold for descent too
                // Accumulate absolute descent if the change is significantly negative
                _dislivMeno.postValue((_dislivMeno.value ?: 0.0) + altitudeDifference) 
                previousFilteredAltitude = currentFilteredAltitude
            }
        }
        //SimpleFileLogger.log("updateAltitudeChanges", "filteredAltitude $currentFilteredAltitude previousFilteredAltitude $previousFilteredAltitude dislivPiu ${dislivPiu.value} dislivMeno ${dislivMeno.value}")
        //Log.d("updateAltitudeChanges", "filteredAltitude $currentFilteredAltitude previousFilteredAltitude $previousFilteredAltitude dislivPiu ${dislivPiu.value} dislivMeno ${dislivMeno.value}")
    }

    fun resetCruscotto() {
        _quota.value = 0
        _dislivPiu.value = 0.0
        _dislivMeno.value = 0.0
        _velocita.value = 0
        _distanzaMetri.value = 0
        _tempoTrascorso.value = ""
        _secondiMovimento.value = 0
        alertFuoriTraccia = false
        previousFilteredAltitude = null // Resetta anche lo stato del filtro GPS
        oldQuota = 0 // Resetta lo stato del filtro barometrico
        gpsAltitudeHistory.clear()
        _locationData.value = LocationData(GeoPoint(0.0,0.0,0.0), 0f) // Resetta la posizione corrente
        oldPunto = GeoPoint(0.0,0.0,0.0)
        isFixed = false
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

    suspend fun leggiTrack(id: Int, poiList: MutableList<PoiDB>): Polyline {
        return withContext(Dispatchers.IO) {
            val percorso = Polyline()
            geoPuntiPercorso.clear()
            poiList.clear()

            // --- CHIAMATE PULITE AL REPOSITORY ---
            val puntiTracciaDalDb = repository.getPuntiTraccia(id)
            val puntiPoiDalDb = repository.getPuntiPoi(id)

            puntiTracciaDalDb.forEach {
                val punto = GeoPoint(it.Latit.toDouble(), it.Longit.toDouble(), it.Ele.toDouble())
                percorso.addPoint(punto)
                geoPuntiPercorso.add(punto)
            }

            poiList.addAll(puntiPoiDalDb)

            //  Crea la Polyline usando la funzione di MapUtils che la colora
            //    Passa la lista di punti che abbiamo appena caricato.
            val percorsoColorato = MapUtils.disegnaLine(percorso)
            percorsoColorato
        }
    }

    private fun incrementMovementSeconds() {
        // Deve usare postValue se chiamato da una coroutine non Main
        _secondiMovimento.postValue((_secondiMovimento.value ?: 0) + 1)
    }

    // coroutine per aggiornamento del tempo di registrazione sul cruscotto
    fun startUpdates() {
        if (updatesJob?.isActive == true) {
            // Coroutine is already running, no need to start a new one
            return
        }
        updatesJob = viewModelScope.launch { // Default è Dispatchers.Main se non specificato per viewModelScope
            while (true) {
                val currentTime = System.currentTimeMillis()
                elapsedTime = currentTime - oraInizio
                // _tempoTrascorso può usare .value se startUpdates è garantito essere chiamato/eseguito su Main
                _tempoTrascorso.value = MapUtils.formatElapsedTime(elapsedTime) 
                //Log.d("Mappa", "Tempo trascorso: $elapsedTime  ${tempoTrascorso.value}")
                if ((velocita.value ?: 0) != 0) { // Controlla nullabilità di velocita.value
                    incrementMovementSeconds() // incrementMovementSeconds ora usa postValue
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
        repository.getTuttiSentieri().collect {
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
        return repository.insertSentiero(sentiero)
    }

    fun cancellaSentiero(id: Int) {
        viewModelScope.launch {
            repository.cancellaSentieroCompleto(id)
        }
    }

    fun listaFotoId(id: Int): List<FotoPoi> {
        return repository.getFotoPoi(id)
    }

    fun toggleAllarmeState() {
        val newState = !(_isAllarmeAttivo.value ?: true)
        _isAllarmeAttivo.value = newState
        alertFuoriTraccia = newState // Aggiorna anche la vecchia variabile se serve altrove
    }

    /**
     * Prepara i dati per il grafico UNA SOLA VOLTA.
     * Controlla se i dati sono già stati generati.
     */
    fun preparaDatiGrafico(idTracciaNuova: Int) {
        if (chartProducer.getModel() != null && idTracciaNuova == idTracciaGraficoCorrente) {
            Log.d("GRAF_VM", "Dati grafico già presenti per la traccia $idTracciaNuova. Salto la generazione.")
            return
        }
        // Se l'ID è diverso o il modello è vuoto, (ri)carichiamo i dati.
        idTracciaGraficoCorrente = idTracciaNuova

        Log.d("GRAF_VM", "Preparazione dati grafico per la traccia $idTracciaNuova...")

        viewModelScope.launch(Dispatchers.IO) {
            // Ricarica i dati della traccia specifica dal repository
            val puntiTraccia = repository.getPuntiTraccia(idTracciaNuova)

            if (puntiTraccia.isEmpty()) {
                Log.w("GRAF_VM", "Nessun punto trovato per la traccia $idTracciaNuova.")
                chartProducer.setEntries(emptyList<FloatEntry>())
                return@launch
            }

            // Aggiorna la lista globale (se ancora ti serve per altro)
            geoPuntiPercorso.clear()
            geoPuntiPercorso.addAll(
                puntiTraccia.map { GeoPoint(it.Latit.toDouble(), it.Longit.toDouble(), it.Ele.toDouble()) }
            )
            // Chiama la funzione di calcolo
            getPuntiInterpolati(geoPuntiPercorso)
        }
    }

    /**
     * Funzione privata che calcola i punti per il grafico.
     * È la tua vecchia funzione 'getpunti', ma ora vive qui.
     */
    private fun getPuntiInterpolati(puntiOriginali: List<GeoPoint>) {
        val listPunti = mutableListOf<FloatEntry>()

        if (puntiOriginali.isEmpty()) {
            chartProducer.setEntries(emptyList<FloatEntry>())
            return
        }

        val listEle: ArrayList<GeoPoint> = ArrayList(puntiOriginali)
        val puntiRidotti = MapUtils.douglasPeucker(listEle, 200.0)

        puntiRidotti.forEach {
            val quota = it.altitude.toFloat()
            val punto = FloatEntry(puntiRidotti.indexOf(it).toFloat(), quota)
            listPunti.add(punto)
            Log.d("punti_vm", "getpunti: ${punto.x} ${punto.y}")
        }

        // Popola il producer con i dati calcolati
        chartProducer.setEntries(listPunti)
    }
}
