package com.apstudio.sentieri

import android.app.Application
import android.content.Intent
import android.location.Location
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
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
import com.apstudio.sentieri.layer.Event
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.federicomatera.agpxp.models.WayPoint
import org.apache.commons.net.ftp.FTP
import org.apache.commons.net.ftp.FTPClient
import org.apache.commons.net.ftp.FTPReply
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.FolderOverlay
import org.osmdroid.views.overlay.Polyline
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.sql.Timestamp
import java.util.concurrent.CopyOnWriteArrayList


data class LocationData(val geoPoint: GeoPoint, val bearing: Float)

class SentieriViewModel(private val repository: SentieriRepo, application: Application) :
    AndroidViewModel(application) {

    companion object {
        private const val MOVING_AVERAGE_WINDOW_SIZE =
            15 // Aumentato da 10 per maggiore stabilità GPS
        private const val GPS_ALTITUDE_SPIKE_THRESHOLD =
            25.0// Soglia massima di variazione di altitudine in metri tra due letture
        private const val MIN_VARIATION_THRESHOLD = 
            1.0 // Soglia minima in metri per accumulare dislivello (Hysteresis)
    }

    private var discardedGpsPointsCount: Int = 0
    private val WARMUP_READINGS_TO_DISCARD = 8

    var listaTracce: FolderOverlay = FolderOverlay() // overlay per aggiungere le tracce da gpx e db
    val recTraccia = FolderOverlay() // overlay per traccia in registrazione e marker inizio e fine
    val topoLayer = FolderOverlay()
    var puntiDaSeguire =
        mutableListOf<GeoPoint>() // percorso caricato in MappaFragment da SchedaFragment
    var titoloTracciaDaSeguire = ""

    // liste di punti gps e waypoint
    val puntiGPS = CopyOnWriteArrayList<WayPoint>()
    var wayPoint = mutableListOf<WayPoint>()
    var poiDBList = mutableListOf<PoiDB>()
    val fotoInPoiDB = mutableListOf<Uri>()
    val fotoList = mutableListOf<Uri>()
    val layerItems = mutableListOf<LayerItem>()
    val geoPuntiPercorso = mutableListOf<GeoPoint>()
    val toponimiSelezionati = mutableListOf<TopoMarkerData>() // New list
    var alertFuoriTraccia: Boolean = true
    var tracciaDaSeguire: String = ""
    var poi = GeoPoint(0.0, 0.0, 0.0)
    var mapRotation: Float = 0f
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

    private var oldPunto = GeoPoint(0.0, 0.0, 0.0)
    var ultZoom = (11)

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
    val distanzaMetri: LiveData<Int> = _distanzaMetri
    private val _dislivPiu = MutableLiveData(0.0)
    val dislivPiu: LiveData<Double> = _dislivPiu
    private val _dislivMeno = MutableLiveData(0.0)
    val dislivMeno: LiveData<Double> = _dislivMeno
    private val _velocita = MutableLiveData(0)
    val velocita: LiveData<Int> = _velocita
    private val _quota = MutableLiveData(0)
    val quota: LiveData<Int> = _quota
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

    // in Scheda per visualizzare pendenza oppure quota
    var mostraPendenza = false
    var coloriPuntiDaSeguire: List<Float>? = null

    // valori di riferimento della traccia da seguire
    var trackDistanza = 0f
    var trackAscesa = 0
    var trackDiscesa = 0

    // valori per barometro
    var haBaro = false
    var setBaro = false
    private var oldQuota: Double? = null
    private var accumuloDistanzaMetri: Int = 0
    private var accumuloDislivPiu: Double = 0.0
    private var accumuloDislivMeno: Double = 0.0

    // coefficiente per filtro passa basso quota barometro da 0 ad 1
    // 0 massimo smooth 1 minore smooth
    private val alfa: Double = 0.21 // Ridotto da 0.21 per eliminare più rumore

    // Variabile per tenere traccia dell'ultima quota che ha generato un incremento nel dislivello
    private var lastRecordedAltitudeForSum: Double? = null

    //private val alfaGPS: Double = 0.225  //0.21 prec
    var NORMAL_PRESSURE = 1013.25F
    private val _isCalibrato = MutableLiveData(false)
    val isCalibrato: LiveData<Boolean> = _isCalibrato
    var bottomState = 0
    var idTracciaGraficoCorrente: Int = -1

    // LiveData per comunicare messaggi alla UI (sostituisce i Toast diretti)
    private val _ftpDownloadStatus = MutableLiveData<Event<String>>()
    val ftpDownloadStatus: LiveData<Event<String>> = _ftpDownloadStatus

    // Potresti anche usare un LiveData per lo stato di caricamento
    private val _isDownloading = MutableLiveData<Boolean>(false)
    val isDownloading: LiveData<Boolean> = _isDownloading

    // LiveData per il progresso ---
    private val _downloadProgress = MutableLiveData<Int>(-1)
    val downloadProgress: LiveData<Int> = _downloadProgress

    // NUOVO: LiveData per l'elenco dei file FTP
    private val _ftpFileList = MutableLiveData<Event<List<String>>>()
    val ftpFileList: LiveData<Event<List<String>>> = _ftpFileList

    fun setDownloading(downloading: Boolean) {
        _isDownloading.postValue(downloading)
    }

    fun setDownloadProgress(progress: Int) {
        _downloadProgress.postValue(progress)
    }

    fun postFtpStatus(message: String) {
        _ftpDownloadStatus.postValue(Event(message))
    }

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

    }

    fun processNewLocationData(loc: Location, altitudine: Double, baroPress: Float) {
        if (!isRecording) return // Non processare se non stiamo registrando
        
        viewModelScope.launch(Dispatchers.IO) { // Esegui su un thread in background
            _performDataUpdate(loc, altitudine, baroPress)
        }
    }

    private fun _performDataUpdate(loc: Location?, altitudineOriginale: Double, baroPress: Float) {
        if (loc == null) {
            return
        }
        // 1. Determina l'altitudine da usare
        val usaAltitudineBaro = haBaro && setBaro && isCalibrato.value == true
        val altitudineCalcolata = if (usaAltitudineBaro) {
            MapUtils.calcolaAltitudineIpso(baroPress, NORMAL_PRESSURE).toDouble()
        } else {
            altitudineOriginale
        }
        val currentNewPunto = GeoPoint(loc.latitude, loc.longitude, altitudineCalcolata)
        //Log.d("SentieriViewModel", "currentNewPunto: $currentNewPunto")

        // 1. LOGICA DI WARM-UP e INIZIALIZZAZIONE (se isFixed è ancora falso)
        if (!isFixed) {
            // visualizza subito localizzazione corrente
            // Postiamo i dati base per mantenere la mappa al corrente, ma non i dati di tracciamento.
            _locationData.postValue(LocationData(currentNewPunto, loc.bearing))
            // A. Se è il primissimo punto in assoluto (oldPunto non valido)
            if (oldPunto.latitude == 0.0) {
                oldPunto = currentNewPunto
                referencePointForSlope = currentNewPunto

                if (usaAltitudineBaro) {
                    oldQuota = altitudineCalcolata
                    isFixed = true // Barometro procede subito
                    Log.i("Warmup", "Barometro: isFixed=true. oldQuota=${oldQuota}")
                } else {
                    // GPS: Non impostiamo isFixed=true. La history verrà riempita nei cicli successivi.
                    gpsAltitudeHistory.clear()
                    previousFilteredAltitude = null
                    Log.i("Warmup", "GPS: Primo punto. Inizio conteggio scarti.")
                }
            }
            // B. Logica di scarto delle letture iniziali (solo per GPS)
            if (!usaAltitudineBaro) {
                if (discardedGpsPointsCount < WARMUP_READINGS_TO_DISCARD) {
                    Log.d("Warmup", "GPS Warmup: $discardedGpsPointsCount/${WARMUP_READINGS_TO_DISCARD} ignorati.")
                    discardedGpsPointsCount++
                    return // ESCI: Non fare calcoli di distanza/dislivello/pendenza
                } else {
                    // Se abbiamo raggiunto la dimensione della finestra, possiamo considerare il warm-up finito
                    discardedGpsPointsCount >= WARMUP_READINGS_TO_DISCARD
                    isFixed = true
                    Log.i("Warmup", "GPS Warmup Finito. Inizio calcoli.")
                }
                // Aggiorna la history e previousFilteredAltitude per costruire la base di media mobile,
                // ma NON aggiornare UI, distanza o pendenza.
                if (gpsAltitudeHistory.size < MOVING_AVERAGE_WINDOW_SIZE) {
                    gpsAltitudeHistory.addLast(altitudineCalcolata)
                    previousFilteredAltitude = gpsAltitudeHistory.average()
                }
                oldPunto = currentNewPunto
                isFixed = true
                referencePointForSlope = currentNewPunto
            }
            // Se siamo qui e !isFixed, e usiamo il Barometro, o se il warm-up GPS è finito, procedi.
            if (!isFixed) {
                isFixed =
                    true // Se siamo qui per il Barometro, consideriamo il primo punto come fisso.
            }
        }


        // --- DA QUI IN POI, abbiamo la garanzia di avere un 'oldPunto' valido e precedente ---
        _locationData.postValue(LocationData(currentNewPunto, loc.bearing))
        _velocita.postValue((loc.speed * 3.6).toInt())
        _quota.postValue(altitudineCalcolata.toInt())

        // 3. Calcolo DISTANZA
        val distanzaSegmento = MapUtils.getDistanceInMeters(oldPunto, currentNewPunto)
        accumuloDistanzaMetri += distanzaSegmento
        _distanzaMetri.postValue(accumuloDistanzaMetri)

        // 4. Calcolo DISLIVELLO
        if (usaAltitudineBaro) {
            // Passa l'altitudine corrente. La funzione userà 'oldQuota' memorizzato.
            dislivelloBaro(altitudineCalcolata)
        } else {
            processGpsAltitude(altitudineOriginale, currentNewPunto)
        }

        // 5. Calcolo PENDENZA
        val velocitaCorrente = (loc.speed * 3.6).toInt()
        if (velocitaCorrente <= 0) {
            // Se siamo fermi, la pendenza DEVE essere 0.
                _pendenza.postValue(0)
        } else {
            // Calcoliamo la pendenza solo se ci stiamo muovendo
            referencePointForSlope?.let { refPoint ->
                val distanceFromRef = refPoint.distanceToAsDouble(currentNewPunto)
                if (distanceFromRef >= SLOPE_CALCULATION_DISTANCE_THRESHOLD) {
                    val dislivelloPendenza = currentNewPunto.altitude - refPoint.altitude
                    val pendenzaPercentuale = (dislivelloPendenza / distanceFromRef) * 100

                    // Applichiamo un limite ragionevole o un piccolo filtro se necessario
                    _pendenza.postValue(pendenzaPercentuale.toInt())

                    referencePointForSlope = currentNewPunto
                }
            }
        }

        // 6. Aggiorna lo stato per il prossimo ciclo
        salvaPuntoGPS(currentNewPunto)
        oldPunto = currentNewPunto
    }

    private fun dislivelloBaro(altitudineBaro: Double) {
        if (oldQuota == null) return

        // 1. Applica il filtro passa-basso per stabilizzare la lettura istantanea
        val quotaFiltrata = (alfa * altitudineBaro) + ((1 - alfa) * oldQuota!!)
        
        // 2. Se è il primo punto della sessione, inizializza il riferimento di accumulo
        if (lastRecordedAltitudeForSum == null) {
            lastRecordedAltitudeForSum = quotaFiltrata
        }

        // 3. Calcola la differenza rispetto all'ultima volta che abbiamo effettivamente AGGIORNATO il totale
        val deltaDallaUltimaSomma = quotaFiltrata - lastRecordedAltitudeForSum!!

        // 4. Applica la soglia di isteresi: accumula solo se la variazione è significativa (> 1 metro)
        if (kotlin.math.abs(deltaDallaUltimaSomma) >= MIN_VARIATION_THRESHOLD) {
            if (deltaDallaUltimaSomma > 0) {
                accumuloDislivPiu += deltaDallaUltimaSomma
                _dislivPiu.postValue(accumuloDislivPiu)
            } else {
                accumuloDislivMeno -= deltaDallaUltimaSomma
                _dislivMeno.postValue(accumuloDislivMeno)
            }
            // Aggiorna il punto di riferimento per il prossimo calcolo
            lastRecordedAltitudeForSum = quotaFiltrata
        }

        // Aggiorna oldQuota per il prossimo ciclo del filtro EMA
        oldQuota = quotaFiltrata
    }

    fun processGpsAltitude(gpsAltitude: Double, currentPoint: GeoPoint) {
        // **RIATTIVAZIONE E MODIFICA FILTRO GPS**
        // filtro basato su spike
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
        previousPointForGpsSlope = currentPoint
    }

    private fun updateAltitudeChanges(currentFilteredAltitude: Double, currentPoint: GeoPoint) {
        if (previousFilteredAltitude == null) {
            previousFilteredAltitude = currentFilteredAltitude
            previousPointForGpsSlope = currentPoint
            lastRecordedAltitudeForSum = currentFilteredAltitude
            return
        }

        // Inizializza il riferimento se mancante
        if (lastRecordedAltitudeForSum == null) {
            lastRecordedAltitudeForSum = currentFilteredAltitude
        }

        val deltaDallaUltimaSomma = currentFilteredAltitude - lastRecordedAltitudeForSum!!

        // Applica la soglia di isteresi anche al GPS (che è più rumoroso del barometro)
        if (kotlin.math.abs(deltaDallaUltimaSomma) >= MIN_VARIATION_THRESHOLD) {
            if (deltaDallaUltimaSomma > 0) {
                accumuloDislivPiu += deltaDallaUltimaSomma
                _dislivPiu.postValue(accumuloDislivPiu)
            } else {
                accumuloDislivMeno -= deltaDallaUltimaSomma
                _dislivMeno.postValue(accumuloDislivMeno)
            }
            lastRecordedAltitudeForSum = currentFilteredAltitude
        }
    }

    // Assicurati che resetCruscotto sia completo
    fun resetCruscotto() {
        accumuloDistanzaMetri = 0
        accumuloDislivPiu = 0.0
        accumuloDislivMeno = 0.0

        _quota.postValue(0)
        _dislivPiu.postValue(0.0)
        _dislivMeno.postValue(0.0)
        _velocita.postValue(0)
        _distanzaMetri.postValue(0)
        _pendenza.postValue(0)
        _tempoTrascorso.postValue("")
        _secondiMovimento.postValue(0)
        alertFuoriTraccia = false

        // --- AZZERAMENTO DELLO STATO CRITICO ---
        isFixed = false
        oldPunto = GeoPoint(0.0, 0.0, 0.0)
        oldQuota = null
        lastRecordedAltitudeForSum = null
        previousFilteredAltitude = null
        discardedGpsPointsCount = 0
        referencePointForSlope = null
        gpsAltitudeHistory.clear()
        // -----------------------------------------

        _locationData.postValue(LocationData(GeoPoint(0.0, 0.0, 0.0), 0f))
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
            percorso // valore di polyline restituita
            //  Crea la Polyline usando la funzione di MapUtils che la colora
            //    Passa la lista di punti che abbiamo appena caricato.
            //val percorsoColorato = MapUtils.disegnaLine(percorso)
            //percorsoColorato
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
        updatesJob =
            viewModelScope.launch { // Default è Dispatchers.Main se non specificato per viewModelScope
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

    fun trovaSentiero(id: Int): LiveData<Sentieri?> {
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
        listPunti.add(
            com.github.mikephil.charting.data.Entry(
                0f,
                puntiOriginali.first().altitude.toFloat()
            )
        )

        for (i in 1 until puntiOriginali.size) {
            val puntoCorrente = puntiOriginali[i]
            val distanzaSegmento = puntoPrecedente.distanceToAsDouble(puntoCorrente)
            val distanzaPrecedenteMetri = distanzaProgressivaMetri
            distanzaProgressivaMetri += distanzaSegmento

            while (distanzaProgressivaMetri >= prossimoTraguardoKm * 1000) {
                val distanzaTraguardoMetri = (prossimoTraguardoKm * 1000).toDouble()
                if (distanzaSegmento == 0.0) break // Evita divisione per zero
                val frazioneSegmento =
                    (distanzaTraguardoMetri - distanzaPrecedenteMetri) / distanzaSegmento
                // Ecco la formula di interpolazione completa
                val altitudineInterpolata =
                    puntoPrecedente.altitude + ((puntoCorrente.altitude - puntoPrecedente.altitude) * frazioneSegmento)
                // L'asse X è la distanza reale in KM
                val kmTraguardo = prossimoTraguardoKm.toFloat()
                listPunti.add(
                    com.github.mikephil.charting.data.Entry(
                        kmTraguardo,
                        altitudineInterpolata.toFloat()
                    )
                )
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

    /**
     * Elenca i file presenti nella directory remota del server FTP.
     */
// Rimuovo la doppia dichiarazione e sistemo il finally
    fun listDirectory(remotePath: String = "") {
        viewModelScope.launch(Dispatchers.IO) {
            _ftpDownloadStatus.postValue(Event("Richiesta lista file..."))
            // Non azzerare qui con una lista vuota se vuoi che l'observer scatti solo al cambio reale
            // _ftpFileList.postValue(emptyList()) 

            val ftpClient = FTPClient()
            try {
                val server = BuildConfig.FTP_SERVER
                val utente = BuildConfig.FTP_USER
                val password = BuildConfig.FTP_PASS
                val portaFtp = BuildConfig.FTP_PORT

                ftpClient.connect(server, portaFtp)
                ftpClient.login(utente, password)
                ftpClient.enterLocalPassiveMode()

                val files = ftpClient.listNames(remotePath)
                if (files != null) {
                    // Usiamo un piccolo trick per forzare l'aggiornamento del LiveData 
                    // anche se la lista è identica alla precedente
                    _ftpFileList.postValue(Event(files.toList()))
                }
            } catch (e: IOException) {
                Log.e("FTP_LIST", "Errore FTP", e)
                _ftpDownloadStatus.postValue(Event("Errore FTP: ${e.message}"))
            } finally {
                try {
                    if (ftpClient.isConnected) {
                        try { ftpClient.logout() } catch (_: Exception) {}
                        ftpClient.disconnect()
                    }
                } catch (e: Exception) {
                    Log.e("FTP_LIST", "Errore chiusura", e)
                }
            }
        }
    }


    /**
     * Avvia il download di un file specifico da un server FTP.
     */
    fun downloadFileFromFtp(percorsoFileRemoto: String) {
        val context = getApplication<Application>()
        
        // Imposta lo stato di download per mostrare il dialogo se necessario
        _isDownloading.value = true
        _downloadProgress.value = 0
        
        val intent = Intent(context, DownloadService::class.java).apply {
            action = DownloadService.ACTION_START_DOWNLOAD
            putExtra(DownloadService.EXTRA_FILE_PATH, percorsoFileRemoto)
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    fun scaricaFileDaDrive() {
        viewModelScope.launch(Dispatchers.IO) {
            _isDownloading.postValue(true)
            _downloadProgress.postValue(0)
            _ftpDownloadStatus.postValue(Event("Download da Remoto in corso..."))
            // URL di download diretto (uc = user content)
            //val urlString = "https://drive.usercontent.google.com/download?id=1sZl43O4aVJHYTO0anl8e5sq3j9XHgnKS&export=download&authuser=0&confirm=t&uuid=ce1c3d68-fe2f-4d4d-b785-c2a23a4758bb&at=ANTm3cy87PrdFq80FCYNG8I8rOVI%3A1768214837093"
            val urlString =
                "https://github.com/alfredopa/Sentieri/releases/download/risorse/Sardegna.zip"
            val nomeFile = "Sardegna.zip"
            var downloadSuccess = false

            try {
                val url = java.net.URL(urlString)
                val connection = url.openConnection() as java.net.HttpURLConnection

                // Fondamentale: Google Drive spesso usa redirect (302)
                connection.instanceFollowRedirects = true
                connection.requestMethod = "GET"
                // Opzionale: aggiungi un User-Agent per sembrare un browser
                connection.setRequestProperty("User-Agent", "Mozilla/5.0")

                connection.connect()

                // Se il file è grande, Google potrebbe rispondere con un codice diverso o
                // richiedere una conferma. Se il contentLength è piccolo (es < 1000 byte)
                // probabilmente stiamo scaricando la pagina di errore HTML invece del file.

                val fileSize = connection.contentLength.toLong()

                // Se il server non restituisce la dimensione o è troppo piccola,
                // potrebbe esserci l'avviso virus di Google.
                if (fileSize < 10000) {
                    Log.e(
                        "DRIVE",
                        "Il file sembra troppo piccolo. Probabile avviso virus di Google."
                    )
                    // Nota: gestire l'avviso virus via codice è molto complesso (richiede cookie)
                }

                val outputStream =
                    MapUtils.getOutputStreamForPublicDownload(getApplication(), nomeFile)
                        ?: throw IOException("Impossibile creare il file locale")

                val inputStream = connection.inputStream
                val buffer = ByteArray(8192)
                var bytesRead: Int
                var totalBytesRead = 0L

                outputStream.use { output ->
                    inputStream.use { input ->
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            totalBytesRead += bytesRead

                            if (fileSize > 0) {
                                val progress = ((totalBytesRead * 100) / fileSize).toInt()
                                if (progress > (_downloadProgress.value ?: 0)) {
                                    _downloadProgress.postValue(progress)
                                }
                            }
                        }
                    }
                }
                downloadSuccess = true
            } catch (e: Exception) {
                Log.e("HTTP_DOWNLOAD", "Errore: ${e.message}")
                _ftpDownloadStatus.postValue(Event("Errore: ${e.message}"))
            } finally {
                withContext(Dispatchers.Main) {
                    _isDownloading.postValue(false)
                    if (downloadSuccess) {
                        _ftpDownloadStatus.postValue(Event("Download file completato!"))
                        scompattaZip(nomeFile)
                    } else {
                        _ftpDownloadStatus.postValue(Event("Download fallito (controlla dimensione file)"))
                    }
                }
            }
        }
    }

    fun scompattaZip(fileScaricato: String) {
        _ftpDownloadStatus.postValue(Event("Download completato! Inizio decompressione..."))

        // --- CHIAMA LA FUNZIONE DI UNZIP QUI ---
        viewModelScope.launch(Dispatchers.IO) {
            val unzipSuccess =
                MapUtils.decomprimiZipInCartellaMappe(getApplication(), fileScaricato)
            // Comunica il risultato finale
            withContext(Dispatchers.Main) {
                if (unzipSuccess) {
                    _ftpDownloadStatus.postValue(Event("Mappa installata con successo!"))
                } else {
                    _ftpDownloadStatus.postValue(Event("Errore durante l'installazione della mappa."))
                }
            }
        }
    }

}