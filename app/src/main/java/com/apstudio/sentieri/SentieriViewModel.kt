package com.apstudio.sentieri

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.liveData
import androidx.lifecycle.map
import androidx.lifecycle.viewModelScope
import com.apstudio.sentieri.db.FotoPoi
import com.apstudio.sentieri.db.LayerItem
import com.apstudio.sentieri.db.LocationRepository
import com.apstudio.sentieri.db.LocationRepository.clearTrack
import com.apstudio.sentieri.db.PoiDB
import com.apstudio.sentieri.db.Sentieri
import com.apstudio.sentieri.db.SentieriRepo
import com.apstudio.sentieri.db.TopoMarkerData
import com.apstudio.sentieri.layer.Event
import com.apstudio.sentieri.layer.placeholder.PlaceholderContent
import com.example.levo_sdk.domain.model.BtDevice
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.federicomatera.agpxp.models.WayPoint
import org.apache.commons.net.ftp.FTPClient
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.Polyline
import java.io.IOException

class SentieriViewModel(private val repository: SentieriRepo, application: Application) :
    AndroidViewModel(application) {

    // Bluetooth LiveData (ora osservano il LocationRepository)
    val btDevices = LocationRepository.btDevices
    val isConnected = LocationRepository.btIsConnected
    val ebikeMessage = LocationRepository.ebikeMessage
    val btStatus = LocationRepository.btStatus

    var recTraccia: SafeFolderOverlay = SafeFolderOverlay()
    var topoLayer: SafeFolderOverlay = SafeFolderOverlay()
    var puntiDaSeguire: MutableList<GeoPoint> = mutableListOf()
    var titoloTracciaDaSeguire = ""

    val puntiGPS = LocationRepository.puntiGPS
    var wayPoint: MutableList<WayPoint> = mutableListOf()
    var poiDBList: MutableList<PoiDB> = mutableListOf()
    var fotoInPoiDB: MutableList<Uri> = mutableListOf()
    var fotoList: MutableList<Uri> = mutableListOf()
    var layerItems: MutableList<LayerItem> = mutableListOf()
    var geoPuntiPercorso: MutableList<GeoPoint> = mutableListOf()
    var toponimiSelezionati: MutableList<TopoMarkerData> = mutableListOf()
    var toponimiSearchQuery: String? = null
    var toponimiSearchResults: List<PlaceholderContent.PlaceholderItem>? = null
    var alertFuoriTraccia = true
    private val _tracciaDaSeguire = MutableLiveData<String?>()
    var tracciaDaSeguire: String
        get() = _tracciaDaSeguire.value ?: ""
        set(value) {
            _tracciaDaSeguire.value = value
        }
    val tracciaDaSeguireLiveData: LiveData<String> = _tracciaDaSeguire.map { it ?: "" }
    var poi: GeoPoint = GeoPoint(0.0, 0.0, 0.0)
    var mapRotation: Float = 0f
    var bloccaMappa = false
    var connessione = false
    var menuMap = 0
    var uriMappa: Uri = Uri.EMPTY

    var isRecording: Boolean
        get() = LocationRepository.isRecording
        set(value) {
            LocationRepository.isRecording = value
        }

    var isFixed: Boolean
        get() = LocationRepository.isFixed
        set(value) {
            LocationRepository.isFixed = value
        }

    var ricerca: String = ""
    private val _isCalendarMode = MutableLiveData(false)
    val isCalendarMode: LiveData<Boolean> = _isCalendarMode.map { it ?: false }
    fun setCalendarMode(value: Boolean) { _isCalendarMode.value = value }

    var selectedDate: String? = null
    var ultPosizione: GeoPoint = GeoPoint(39.215, 9.11)
    var ultZoom = 15

    val distanzaMetri: LiveData<Int> = LocationRepository.distanzaMetri
    val dislivPiu: LiveData<Double> = LocationRepository.dislivPiu
    val dislivMeno: LiveData<Double> = LocationRepository.dislivMeno
    val quota: LiveData<Int> = LocationRepository.quota
    val pendenza: LiveData<Int> = LocationRepository.pendenza
    val velocita: LiveData<Int> = LocationRepository.velocitaKmh
    val secondiMovimento: LiveData<Long> = LocationRepository.secondiMovimentoLiveData
    val isCalibrato: LiveData<Boolean> = LocationRepository.isCalibrato
    val locationData: LiveData<LocationData> = LocationRepository.location.map {
        LocationData(GeoPoint(it.latitude, it.longitude, it.altitude), it.bearing)
    }

    // Bluetooth Methods (tramite Service)
    fun startBtDiscovery() {
        val intent = Intent(getApplication(), LocationService::class.java).apply {
            action = LocationService.ACTION_START_SCAN
        }
        getApplication<Application>().startService(intent)
    }

    fun stopBtDiscovery() {
        val intent = Intent(getApplication(), LocationService::class.java).apply {
            action = LocationService.ACTION_STOP_SCAN
        }
        getApplication<Application>().startService(intent)
    }

    fun connectToBtDevice(device: BtDevice) {
        val intent = Intent(getApplication(), LocationService::class.java).apply {
            action = LocationService.ACTION_CONNECT
            putExtra(LocationService.EXTRA_DEVICE_ADDRESS, device.address)
            putExtra(LocationService.EXTRA_DEVICE_NAME, device.name)
        }
        getApplication<Application>().startService(intent)
        
        // Salva l'indirizzo per riconnessione automatica
        PreferenceManager.getDefaultSharedPreferences(getApplication()).edit {
            putString("last_ebike_address", device.address)
        }
    }

    fun disconnectBt() {
        val intent = Intent(getApplication(), LocationService::class.java).apply {
            action = LocationService.ACTION_DISCONNECT
        }
        getApplication<Application>().startService(intent)
    }

    fun autoConnectEbike() {
        // La riconnessione automatica è ora gestita dal LocationService
        // basandosi sulle preferenze. Chiamiamo comunque il service per sicurezza
        // nel caso non fosse già in esecuzione o per forzare un check.
        val intent = Intent(getApplication(), LocationService::class.java)
        getApplication<Application>().startService(intent)
    }

    init {
        LocationRepository.restoreSessionState(application)
    }

    // values displayed in the dashboard
    var oraInizio: Long
        get() = LocationRepository.oraInizio
        set(value) { LocationRepository.oraInizio = value }

    val tempoTrascorso: LiveData<String> = LocationRepository.tempoTrascorso
    
    val elapsedTime: Long
        get() = if (oraInizio > 0) System.currentTimeMillis() - oraInizio else 0L

    private val _isAllarmeAttivo = MutableLiveData(true)
    val isAllarmeAttivo: LiveData<Boolean> = _isAllarmeAttivo

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

    var bottomState = 0

    private val _ftpDownloadStatus = MutableLiveData<Event<String>>()
    val ftpDownloadStatus: LiveData<Event<String>> = _ftpDownloadStatus

    private val _isDownloading = MutableLiveData(false)
    val isDownloading: LiveData<Boolean> = _isDownloading.map { it ?: false }

    private val _downloadProgress = MutableLiveData(0)
    val downloadProgress: LiveData<Int> = _downloadProgress.map { it ?: 0 }

    private val _downloadFileName = MutableLiveData<String>()
    val downloadFileName: LiveData<String> = _downloadFileName

    private val _ftpFileList = MutableLiveData<Event<List<String>>>()
    val ftpFileList: LiveData<Event<List<String>>> = _ftpFileList

    private val _mapInvalidateRequest = MutableLiveData<Event<Unit>>()
    val mapInvalidateRequest: LiveData<Event<Unit>> = _mapInvalidateRequest

    // LiveData for remaining values
    private val _remainingDist = MutableLiveData(0f)
    val remainingDist: LiveData<Float> = _remainingDist
    private val _remainingDPiu = MutableLiveData(0.0)
    val remainingDPiu: LiveData<Double> = _remainingDPiu
    private val _remainingDMeno = MutableLiveData(0.0)
    val remainingDMeno: LiveData<Double> = _remainingDMeno

    fun resetCruscotto() {
        clearTrack(getApplication())
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

            withContext(Dispatchers.Main.immediate) {
                geoPuntiPercorso.clear()
                geoPuntiPercorso.addAll(nuoviPunti)
            }

            poiList.clear()
            poiList.addAll(repository.getPuntiPoi(id))
            percorso
        }
    }

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
        alertFuoriTraccia = newState
    }

    fun rinominaSentiero(idSentiero: Int, nuovoNome: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.rinominaSentiero(idSentiero, nuovoNome)
        }
    }

    suspend fun preparaDatiGrafico(): List<com.github.mikephil.charting.data.Entry> {
        return withContext(Dispatchers.IO) {
            MapUtils.getPuntiInterpolati(geoPuntiPercorso)
        }
    }

    fun listDirectory(remotePath: String = "") {
        viewModelScope.launch(Dispatchers.IO) {
            _ftpDownloadStatus.postValue(Event("Richiesta lista file..."))

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

    fun downloadFileFromFtp(percorsoFileRemoto: String) {
        val context = getApplication<Application>()
        val nomeFile = percorsoFileRemoto.substringAfterLast("/")
        
        _downloadFileName.value = nomeFile
        _isDownloading.value = true
        _downloadProgress.value = 0
        
        val intent = Intent(context, DownloadService::class.java).apply {
            action = DownloadService.ACTION_START_DOWNLOAD
            putExtra(DownloadService.EXTRA_FILE_PATH, percorsoFileRemoto)
        }

        context.startForegroundService(intent)
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
}

data class LocationData(val geoPoint: GeoPoint, val bearing: Float)
