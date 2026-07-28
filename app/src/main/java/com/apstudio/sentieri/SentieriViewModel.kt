package com.apstudio.sentieri

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.liveData
import androidx.lifecycle.map
import androidx.lifecycle.viewModelScope
import com.apstudio.sentieri.db.FotoPoi
import com.apstudio.sentieri.db.LayerItem
import com.apstudio.sentieri.db.LocationRepository
import com.apstudio.sentieri.db.LocationRepository.clearTrack
import com.apstudio.sentieri.db.LocationRepository.incrementMovementSeconds
import com.apstudio.sentieri.db.PoiDB
import com.apstudio.sentieri.db.Sentieri
import com.apstudio.sentieri.db.SentieriRepo
import com.apstudio.sentieri.db.TopoMarkerData
import com.apstudio.sentieri.layer.Event
import com.apstudio.sentieri.layer.placeholder.PlaceholderContent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.federicomatera.agpxp.models.WayPoint
import org.apache.commons.net.ftp.FTPClient
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.Polyline
import java.io.IOException

data class LocationData(val geoPoint: GeoPoint, val bearing: Float)

class SentieriViewModel(private val repository: SentieriRepo, application: Application) :
    AndroidViewModel(application) {

    companion object {
        private const val MOVING_AVERAGE_WINDOW_SIZE =
            15 // Aumentato da 10 per maggiore stabilità GPS
    }

    private var discardedGpsPointsCount: Int = 0

    var recTraccia = SafeFolderOverlay() // overlay per traccia in registrazione e marker inizio e fine
    var topoLayer = SafeFolderOverlay()
    var puntiDaSeguire =
        mutableListOf<GeoPoint>() // percorso caricato in MappaFragment da SchedaFragment
    var titoloTracciaDaSeguire = ""

    // liste di punti gps e waypoint
    val puntiGPS get() = LocationRepository.puntiGPS
    var wayPoint = mutableListOf<WayPoint>()
    var poiDBList = mutableListOf<PoiDB>()
    val fotoInPoiDB = mutableListOf<Uri>()
    val fotoList = mutableListOf<Uri>()
    val layerItems = mutableListOf<LayerItem>()
    val geoPuntiPercorso = mutableListOf<GeoPoint>()
    val toponimiSelezionati = mutableListOf<TopoMarkerData>() // New list
    var toponimiSearchQuery: String? = null
    var toponimiSearchResults: List<PlaceholderContent.PlaceholderItem>? = null
    var alertFuoriTraccia: Boolean = true
    //var tracciaDaSeguire: String = ""
    private val _tracciaDaSeguire = MutableLiveData("")
    var tracciaDaSeguire: String
        get() = _tracciaDaSeguire.value ?: ""
        set(value) { _tracciaDaSeguire.value = value }

    val tracciaDaSeguireLiveData: LiveData<String> = _tracciaDaSeguire
    var poi = GeoPoint(0.0, 0.0, 0.0)
    var mapRotation: Float = 0f
    var bloccaMappa = true
    var connessione = false
    var menuMap = 0             // indice della mappa utilizzata secondo le voci del menu mappa
    var uriMappa: Uri = Uri.EMPTY
    private var updatesJob: Job? = null

    var isRecording: Boolean
        get() = LocationRepository.isRecording
        set(value) { LocationRepository.isRecording = value }

    // Fai lo stesso per isFixed (se presente):
    var isFixed: Boolean
        get() = LocationRepository.isFixed
        set(value) { LocationRepository.isFixed = value }
    var ricerca = String()
    private val _isCalendarMode = MutableLiveData(false)
    val isCalendarMode: LiveData<Boolean> = _isCalendarMode
    fun setCalendarMode(enabled: Boolean) { _isCalendarMode.value = enabled }
    
    var selectedDate: String? = null
    var ultPosizione = GeoPoint(40.120875, 9.012893, 40.0)   // posizione iniziale mappa
    private var oldPunto = GeoPoint(0.0, 0.0, 0.0)
    var ultZoom = (11)

    // LiveData per osservare i dati dal Repository
    // Nel ViewModel, sostituisci le dichiarazioni dei LiveData:
    val distanzaMetri = LocationRepository.distanzaMetri
    val dislivPiu = LocationRepository.dislivPiu
    val dislivMeno = LocationRepository.dislivMeno
    val quota = LocationRepository.quota
    val pendenza = LocationRepository.pendenza
    val velocita = LocationRepository.velocitaKmh
    val secondiMovimento = LocationRepository.secondiMovimentoLiveData
    val isCalibrato = LocationRepository.isCalibrato
    val locationData: LiveData<LocationData> = LocationRepository.location.map { loc ->
        LocationData(
            GeoPoint(loc.latitude, loc.longitude, loc.altitude),
            loc.bearing
        )
    }
    init {
        LocationRepository.restoreSessionState(application)
    }

    // values displayed in the dashboard
    var oraInizio: Long
        get() = LocationRepository.oraInizio
        set(value) { LocationRepository.oraInizio = value }
    var elapsedTime: Long = 0
    private val _tempoTrascorso = MutableLiveData<String>()
    val tempoTrascorso: LiveData<String> = _tempoTrascorso
    private val _isAllarmeAttivo = MutableLiveData(true)
    val isAllarmeAttivo: LiveData<Boolean> = _isAllarmeAttivo

    // Variabili per il calcolo della pendenza
    private var referencePointForSlope: GeoPoint? = null

    private val gpsAltitudeHistory: ArrayDeque<Double> = ArrayDeque(MOVING_AVERAGE_WINDOW_SIZE)
    private var previousFilteredAltitude: Double? = null

    // in Scheda per visualizzare pendenza oppure quota
    var mostraPendenza = true
    var coloriPuntiDaSeguire: List<Float>? = null

    // valori di riferimento della traccia da seguire
    var trackDistanza = 0f
    var trackAscesa = 0
    var trackDiscesa = 0

    // valori per barometro
    var haBaro = false
    var setBaro = false
    private var oldQuota: Double? = null

    // Variabile per tenere traccia dell'ultima quota che ha generato un incremento nel dislivello
    private var lastRecordedAltitudeForSum: Double? = null

    //private val alfaGPS: Double = 0.225  //0.21 prec
    var NORMAL_PRESSURE = 1013.25F
    var bottomState = 0

    // LiveData per comunicare messaggi alla UI (sostituisce i Toast diretti)
    private val _ftpDownloadStatus = MutableLiveData<Event<String>>()
    val ftpDownloadStatus: LiveData<Event<String>> = _ftpDownloadStatus

    // Potresti anche usare un LiveData per lo stato di caricamento
    private val _isDownloading = MutableLiveData(false)
    val isDownloading: LiveData<Boolean> = _isDownloading

    // LiveData per il progresso ---
    private val _downloadProgress = MutableLiveData(-1)
    val downloadProgress: LiveData<Int> = _downloadProgress

    // LiveData per il nome del file in download
    private val _downloadFileName = MutableLiveData<String>()
    val downloadFileName: LiveData<String> = _downloadFileName

    // NUOVO: LiveData per l'elenco dei file FTP
    private val _ftpFileList = MutableLiveData<Event<List<String>>>()
    val ftpFileList: LiveData<Event<List<String>>> = _ftpFileList

    // LiveData per richiedere il ridisegno della mappa
    private val _mapInvalidateRequest = MutableLiveData<Event<Unit>>()
    val mapInvalidateRequest: LiveData<Event<Unit>> = _mapInvalidateRequest

    // Valori rimanenti calcolati come MediatorLiveData per reagire sia ai progressi che al cambio traccia
    val remainingDist = MediatorLiveData<Int>().apply {
        val update = {
            val currentDist = distanzaMetri.value ?: 0
            value = if (tracciaDaSeguire.isNotEmpty()) {
                (trackDistanza - currentDist).toInt().coerceAtLeast(0)
            } else 0
        }
        addSource(distanzaMetri) { update() }
        addSource( tracciaDaSeguireLiveData) { update() }
    }

    val remainingDPiu = MediatorLiveData<Double>().apply {
        val update = {
            val currentDPiu = dislivPiu.value ?: 0.0
            value = if (tracciaDaSeguire.isNotEmpty()) {
                (trackAscesa.toDouble() - currentDPiu).coerceAtLeast(0.0)
            } else 0.0
        }
        addSource(dislivPiu) { update() }
        addSource(tracciaDaSeguireLiveData) { update() }
    }

    val remainingDMeno = MediatorLiveData<Double>().apply {
        val update = {
            val currentDMeno = dislivMeno.value ?: 0.0
            value = if (tracciaDaSeguire.isNotEmpty()) {
                (kotlin.math.abs(trackDiscesa.toDouble()) - currentDMeno).coerceAtLeast(0.0)
            } else 0.0
        }
        addSource(dislivMeno) { update() }
        addSource(tracciaDaSeguireLiveData) { update() }
    }

    fun requestMapInvalidate() {
        _mapInvalidateRequest.postValue(Event(Unit))
    }

    fun setDownloading(downloading: Boolean) {
        _isDownloading.postValue(downloading)
    }

    fun setDownloadProgress(progress: Int) {
        _downloadProgress.postValue(progress)
    }

    fun setDownloadFileName(name: String) {
        _downloadFileName.postValue(name)
    }

    fun postFtpStatus(message: String) {
        _ftpDownloadStatus.postValue(Event(message))
    }


    // Assicurati che resetCruscotto sia completo
    fun resetCruscotto() {
        clearTrack(getApplication())
        // --- AZZERAMENTO DELLO STATO CRITICO ---
        isFixed = false
        oldPunto = GeoPoint(0.0, 0.0, 0.0)
        oldQuota = null
        lastRecordedAltitudeForSum = null
        previousFilteredAltitude = null
        discardedGpsPointsCount = 0
        referencePointForSlope = null
        gpsAltitudeHistory.clear()

    }


    // legge i punti della traccia dal DB Track
    suspend fun leggiTrack(id: Int, poiList: MutableList<PoiDB>): Polyline {
        return withContext(Dispatchers.IO) {
            val puntiTracciaDalDb = repository.getPuntiTraccia(id)
            val percorso = Polyline()
            val nuoviPunti = mutableListOf<GeoPoint>()

            puntiTracciaDalDb.forEach {
                val punto = GeoPoint(it.Latit.toDouble(), it.Longit.toDouble(), it.Ele.toDouble())
                percorso.addPoint(punto)
                nuoviPunti.add(punto)
            }

            // AGGIORNAMENTO: Usiamo withContext(Dispatchers.Main.immediate)
            // per assicurarci che la lista sia pronta prima che il frammento la richieda
            withContext(Dispatchers.Main.immediate) {
                geoPuntiPercorso.clear()
                geoPuntiPercorso.addAll(nuoviPunti)
                //Log.d("Grafo", "Lista geoPuntiPercorso aggiornata: ${geoPuntiPercorso.size} punti")
            }

            poiList.clear()
            poiList.addAll(repository.getPuntiPoi(id))
            percorso
        }
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

    fun getSentieriPerData(dateQuery: String): LiveData<List<Sentieri>> {
        return repository.getSentieriPerData(dateQuery).asLiveData()
    }

    fun getGiorniConRegistrazioni(): LiveData<List<String>> {
        return repository.getGiorniConRegistrazioni().asLiveData()
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

    fun rinominaSentiero(idSentiero: Int, nuovoNome: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.rinominaSentiero(idSentiero, nuovoNome)
        }
    }

    suspend fun preparaDatiGrafico(): List<com.github.mikephil.charting.data.Entry> {
        // Rimuovi o commenta eventuali controlli "idTracciaNuova == idTracciaGraficoCorrente"
        // per forzare il ricalcolo basato sulla lista geoPuntiPercorso aggiornata.

        return withContext(Dispatchers.IO) {
            // Se geoPuntiPercorso è vuoto (es. dopo rotazione), ricarichiamolo
            if (geoPuntiPercorso.isEmpty()) {
                // Chiama una funzione di recupero punti se necessario
            }
            MapUtils.getPuntiInterpolati(geoPuntiPercorso)
        }
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
        val nomeFile = percorsoFileRemoto.substringAfterLast("/")
        
        // Imposta lo stato iniziale
        _downloadFileName.value = nomeFile
        _isDownloading.value = true
        _downloadProgress.value = 0
        
        val intent = Intent(context, DownloadService::class.java).apply {
            action = DownloadService.ACTION_START_DOWNLOAD
            putExtra(DownloadService.EXTRA_FILE_PATH, percorsoFileRemoto)
        }

        context.startForegroundService(intent)
    }
}