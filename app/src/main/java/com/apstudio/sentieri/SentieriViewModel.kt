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
        private const val MOVING_AVERAGE_WINDOW_SIZE = 11 // Numero di valori da tenere in memoria per la media
        private const val GPS_ALTITUDE_SPIKE_THRESHOLD = 8.0// Soglia massima di variazione di altitudine in metri tra due letture
    }

    var listaTracce : FolderOverlay = FolderOverlay() // overlay per aggiungere le tracce da gpx e db
    val recTraccia = FolderOverlay() // overlay per traccia in registrazione e marker inizio e fine
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

    // DA AGGIUNGERE PER OBSERVER
    //private val _isRecording = MutableLiveData<Boolean>(false)
    //val isRecording: LiveData<Boolean> = _isRecording
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
    // Variabili per il calcolo della pendenza
    private val _pendenza = MutableLiveData(0)
    val pendenza: LiveData<Int> = _pendenza
    private var referencePointForSlope: GeoPoint? = null
    private val SLOPE_CALCULATION_DISTANCE_THRESHOLD = 25.0

    private val gpsAltitudeHistory: ArrayDeque<Double> = ArrayDeque(MOVING_AVERAGE_WINDOW_SIZE)
    private var previousFilteredAltitude: Double? = null
    private var previousPointForGpsSlope: GeoPoint? = null

    // valori di riferimento della traccia da seguire
    var trackDistanza = 0f
    var trackAscesa = 0
    var trackDiscesa = 0

    // valori per barometro
    var haBaro = false
    var setBaro = false
    private var oldQuota: Int? = 0
    //private var previousPointForBaroSlope: GeoPoint? = null

    // coefficiente per filtro passa basso quota barometro da 0 ad 1
    // 0 massimo smooth 1 minore smooth
    // con 0.1 da valori troppo bassi (-200 dislivello)
    private val alfa: Double = 0.21
    //private val alfaGPS: Double = 0.225  //0.21 prec
    var NORMAL_PRESSURE = 1013.25F
    private val _isCalibrato = MutableLiveData(false)
    val isCalibrato : LiveData<Boolean> = _isCalibrato
    var bottomState = 0

    var idTracciaGraficoCorrente: Int = -1

    init {
        // L'UNICO trigger per l'aggiornamento dei dati ora è una nuova posizione.
        _combinedData.addSource(locationFromRepo) { location ->
            // Quando arriva una nuova location, recuperiamo gli ultimi valori disponibili
            // dagli altri LiveData. Il LocationService garantisce che siano ragionevolmente aggiornati.
            val msl = mslAltitudeFromRepo.value ?: location.altitude
            val baro = baroPressureFromRepo.value ?: 0.0f // Se è 0.0, lo gestiremo

            _combinedData.value = Triple(location, msl, baro)
        }
        // Osserva i dati combinati e avvia l'elaborazione
        _combinedData.observeForever { (location, mslAltitude, baroPressure) ->
            // Calcola lo stato del barometro QUI, usando le variabili di istanza del ViewModel
            val usaBaro = haBaro && setBaro && (isCalibrato.value == true)

            // Aggiungiamo un controllo di sicurezza per la pressione a 0.0
            if (usaBaro && baroPressure == 0.0f) {
                // Se stiamo usando il barometro ma la pressione è 0,
                // significa che stiamo ricevendo un dato "sporco" iniziale. Lo ignoriamo.
                //Log.w("ViewModelData", "Ignoro aggiornamento con pressione barometrica a 0.0")
                return@observeForever
            }
            // Se il controllo passa, procedi con l'elaborazione dei dati
            processNewLocationData(location, mslAltitude, baroPressure)
        }
        velocita.observeForever { currentSpeed ->
            if (currentSpeed == 0) {
                // Se la velocità è 0, anche la pendenza deve essere 0.
                // Controlliamo se il valore è già 0 per evitare aggiornamenti inutili.
                if (_pendenza.value != 0) {
                    _pendenza.postValue(0)
                }
            }
        }
    }

    fun processNewLocationData(loc: Location, altitudine: Double, baroPress: Float) {
        viewModelScope.launch(Dispatchers.IO) { // Esegui su un thread in background
            _performDataUpdate(loc, altitudine, baroPress)
        }
    }

    private fun _performDataUpdate(loc: Location?, altitudineOriginale: Double, baroPress: Float) {
        if (loc == null) {
            return    }
        // 1. Determina l'altitudine da usare
        val usaAltitudineBaro = haBaro && setBaro && isCalibrato.value == true
        val altitudineCalcolata = if (usaAltitudineBaro) {
            MapUtils.calcolaAltitudineIpso(baroPress, NORMAL_PRESSURE).toDouble()
        } else {
            altitudineOriginale
        }
        //Log.d("SentieriViewModel", "Altitudine calcolata con barometro: $baroPress")
        val currentNewPunto = GeoPoint(loc.latitude, loc.longitude, altitudineCalcolata)

        // 2. Logica di inizializzazione e calcolo sequenziale
        // Se oldPunto non è stato ancora impostato (latitudine a 0.0), questo è il PRIMO punto in assoluto.
        if (oldPunto.latitude == 0.0) {
            // Inizializza tutto e esci. Non fare NESSUN calcolo.
            _locationData.postValue(LocationData(currentNewPunto, loc.bearing))
            oldPunto = currentNewPunto
            isFixed = true

            referencePointForSlope = currentNewPunto

            if (usaAltitudineBaro) {
                oldQuota = altitudineCalcolata.toInt()
            } else {
                gpsAltitudeHistory.clear()
                gpsAltitudeHistory.addLast(altitudineCalcolata)
                previousFilteredAltitude = altitudineCalcolata
            }
            return
        }

        // --- DA QUI IN POI, abbiamo la garanzia di avere un 'oldPunto' valido e precedente ---
        _locationData.postValue(LocationData(currentNewPunto, loc.bearing))
        _velocita.postValue((loc.speed * 3.6).toInt())
        _quota.postValue(altitudineCalcolata.toInt())

        // 3. Calcolo DISTANZA
        val distanzaSegmento = MapUtils.getDistanceInMeters(oldPunto, currentNewPunto)
        val nuovaDistanzaTotale = (distanzaMetri.value ?: 0) + distanzaSegmento
        _distanzaMetri.postValue(nuovaDistanzaTotale)

        // 4. Calcolo DISLIVELLO
        if (usaAltitudineBaro) {
            // Passa l'altitudine corrente. La funzione userà 'oldQuota' memorizzato.
            dislivelloBaro(altitudineCalcolata.toInt())
        } else {
            processGpsAltitude(altitudineOriginale, currentNewPunto)
        }

        // 5. Calcolo PENDENZA
        if (_velocita.value!! > 0.1) {
            referencePointForSlope?.let { refPoint ->
                val distanceFromRef = refPoint.distanceToAsDouble(currentNewPunto)
                if (distanceFromRef >= SLOPE_CALCULATION_DISTANCE_THRESHOLD) {
                    val dislivelloPendenza = currentNewPunto.altitude - refPoint.altitude
                    if (distanceFromRef > 0) {
                        val pendenzaPercentuale = (dislivelloPendenza / distanceFromRef) * 100
                        _pendenza.postValue(pendenzaPercentuale.toInt())
                        //Log.d("SentieriViewModel", "Pendenza calcolata: $pendenzaPercentuale")
                    }
                    referencePointForSlope = currentNewPunto
                }
            }
        }

        // 6. Aggiorna lo stato per il prossimo ciclo
        salvaPuntoGPS(currentNewPunto)
        oldPunto = currentNewPunto
    }

    private fun dislivelloBaro(altitudineBaro: Int) {
        // Sicurezza: se per qualche motivo oldQuota è ancora 0, non fare nulla.
        if (oldQuota == 0) return

        // Applica il filtro e calcola il dislivello rispetto al valore PRECEDENTE
        val quotaFiltrata = ((alfa * altitudineBaro) + ((1 - alfa) * oldQuota!!)).toInt()
        val dislivello = quotaFiltrata - oldQuota!!

        if (dislivello > 0) {
            _dislivPiu.postValue((_dislivPiu.value ?: 0.0) + dislivello)
        } else if (dislivello < 0) {
            _dislivMeno.postValue((_dislivMeno.value ?: 0.0) - dislivello)
        }
        //Log.d("DislivelloBaro", "QuotaFiltrata: $quotaFiltrata, OldQuota: $oldQuota, Dislivello: $dislivello")
        // Aggiorna oldQuota per il PROSSIMO calcolo
        oldQuota = quotaFiltrata
    }

    fun processGpsAltitude(gpsAltitude: Double, currentPoint: GeoPoint) {
        // Se abbiamo un valore precedente con cui confrontarci...
        if (previousFilteredAltitude != null) {
            // Calcola la differenza assoluta tra la nuova lettura e l'ultima media calcolata.
            val diff = kotlin.math.abs(gpsAltitude - previousFilteredAltitude!!)

            // Se la differenza è troppo grande, è un "spike". Lo ignoriamo e usciamo.
            if (diff > GPS_ALTITUDE_SPIKE_THRESHOLD) {
                Log.w(
                    "GpsFilter",
                    "Spike GPS rilevato e ignorato. Valore: $gpsAltitude, Media precedente: $previousFilteredAltitude, Diff: $diff"
                )
                return // Esci dalla funzione, non processare questo valore anomalo.
            }
        }

        // La media mobile richiede di riempire prima la "finestra" di dati.
        if (gpsAltitudeHistory.size < MOVING_AVERAGE_WINDOW_SIZE) {
            gpsAltitudeHistory.addLast(gpsAltitude)
            previousFilteredAltitude = gpsAltitudeHistory.average() // Calcola una media iniziale
            previousPointForGpsSlope = currentPoint // Inizializza il punto
            return
        }

        // Rimuovi il valore più vecchio e aggiungi il nuovo
        gpsAltitudeHistory.removeFirst()
        gpsAltitudeHistory.addLast(gpsAltitude)

        val currentFilteredAltitude = gpsAltitudeHistory.average()

        if (previousFilteredAltitude != null) {
            updateAltitudeChanges(currentFilteredAltitude, currentPoint)
        }

        previousFilteredAltitude = currentFilteredAltitude
    }

    private fun updateAltitudeChanges(currentFilteredAltitude: Double, currentPoint: GeoPoint) {
        if (previousFilteredAltitude == null) {
            previousFilteredAltitude = currentFilteredAltitude
            previousPointForGpsSlope = currentPoint // Inizializza
            return
        }

        previousFilteredAltitude?.let { prevAlt ->
            val altitudeDifference = currentFilteredAltitude - prevAlt
            if (altitudeDifference > 0) {
                _dislivPiu.postValue((_dislivPiu.value ?: 0.0) + altitudeDifference)
            } else if (altitudeDifference < 0) {
                _dislivMeno.postValue((_dislivMeno.value ?: 0.0) - altitudeDifference) // -altitudeDifference per renderlo positivo
            }
        }
        //SimpleFileLogger.log("updateAltitudeChanges", "filteredAltitude $currentFilteredAltitude previousFilteredAltitude $previousFilteredAltitude dislivPiu ${dislivPiu.value} dislivMeno ${dislivMeno.value}")
        //Log.d("updateAltitudeChanges", "filteredAltitude $currentFilteredAltitude previousFilteredAltitude $previousFilteredAltitude dislivPiu ${dislivPiu.value} dislivMeno ${dislivMeno.value}")
    }

    // Assicurati che resetCruscotto sia completo
    fun resetCruscotto() {
        _quota.value = 0
        _dislivPiu.value = 0.0
        _dislivMeno.value = 0.0
        _velocita.value = 0
        _distanzaMetri.value = 0
        _pendenza.value = 0
        _tempoTrascorso.value = ""
        _secondiMovimento.value = 0
        alertFuoriTraccia = false

        // --- AZZERAMENTO DELLO STATO CRITICO ---
        isFixed = false
        oldPunto = GeoPoint(0.0, 0.0, 0.0)
        oldQuota = 0
        previousFilteredAltitude = null
        referencePointForSlope = null
        gpsAltitudeHistory.clear()
        // -----------------------------------------

        _locationData.value = LocationData(GeoPoint(0.0, 0.0, 0.0), 0f)
        //Log.d("ViewModelLifecycle", "resetCruscotto ESEGUITO. isFixed=${isFixed}, oldQuota=${oldQuota}")
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
    suspend fun preparaDatiGrafico(idTracciaNuova: Int): List<com.github.mikephil.charting.data.Entry> {
        // La logica di controllo per evitare ricalcoli può rimanere
        // if (idTracciaNuova == idTracciaGraficoCorrente && ...) { return ... }

        idTracciaGraficoCorrente = idTracciaNuova
        //Log.d("GRAF_VM", "Preparazione dati grafico per la traccia $idTracciaNuova...")

        return withContext(Dispatchers.IO) {
            repository.getPuntiTraccia(idTracciaNuova)
            // Chiama la funzione di calcolo che ora restituisce una lista di Entry
            getPuntiInterpolati(geoPuntiPercorso)
        }
    }

    private fun getPuntiInterpolati(puntiOriginali: List<GeoPoint>): List<com.github.mikephil.charting.data.Entry> {
        val listPunti = mutableListOf<com.github.mikephil.charting.data.Entry>()
        if (puntiOriginali.size < 2) {
            return listPunti
        }
        // La logica di interpolazione rimane la stessa, cambia solo l'oggetto creato alla fine
        var distanzaProgressivaMetri = 0.0
        var puntoPrecedente = puntiOriginali.first()
        var prossimoTraguardoKm = 1

        // Aggiungi il punto di partenza
        listPunti.add(com.github.mikephil.charting.data.Entry(0f, puntiOriginali.first().altitude.toFloat()))

        for (i in 1 until puntiOriginali.size) {
            val puntoCorrente = puntiOriginali[i]
            val distanzaSegmento = puntoPrecedente.distanceToAsDouble(puntoCorrente)
            val distanzaPrecedenteMetri = distanzaProgressivaMetri
            distanzaProgressivaMetri += distanzaSegmento

            while (distanzaProgressivaMetri >= prossimoTraguardoKm * 1000) {
                val distanzaTraguardoMetri = (prossimoTraguardoKm * 1000).toDouble()
                if (distanzaSegmento == 0.0) break // Evita divisione per zero
                val frazioneSegmento = (distanzaTraguardoMetri - distanzaPrecedenteMetri) / distanzaSegmento
                // Ecco la formula di interpolazione completa
                val altitudineInterpolata = puntoPrecedente.altitude + ((puntoCorrente.altitude - puntoPrecedente.altitude) * frazioneSegmento)
                // L'asse X è la distanza reale in KM
                val kmTraguardo = prossimoTraguardoKm.toFloat()
                listPunti.add(com.github.mikephil.charting.data.Entry(kmTraguardo, altitudineInterpolata.toFloat()))
                prossimoTraguardoKm++
            }
            puntoPrecedente = puntoCorrente
        }

        // Aggiungi l'ultimo punto
        val distanzaFinaleKm = (distanzaProgressivaMetri / 1000.0).toFloat()
        val quotaFinale = puntiOriginali.last().altitude.toFloat()
        listPunti.add(com.github.mikephil.charting.data.Entry(distanzaFinaleKm, quotaFinale))
        //Log.d("GRAF_VM", "Calcolo completato per MPAndroidChart. Punti: ${listPunti.size}")
        return listPunti
    }
}
