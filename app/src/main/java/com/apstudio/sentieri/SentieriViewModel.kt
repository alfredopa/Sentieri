package com.apstudio.sentieri

import android.location.Location
import android.net.Uri
import android.util.Log
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
import kotlin.math.abs

class SentieriViewModel(private val repository: SentieriRepo) : ViewModel() {

    companion object {
        // Soglia per il cambio di altitudine GPS significativo (per calcolo dislivello)
        private const val GPS_ALTITUDE_CHANGE_THRESHOLD_METERS = 1.9
        // Coefficiente per il filtro passa-basso (EMA). Valori più bassi = più smussamento. Range (0, 1)
        private const val GPS_LOW_PASS_ALPHA = 0.3 // Puoi sperimentare con questo valore
        // Soglia massima di variazione di altitudine per scartare valori anomali (in metri al secondo)
        // Ad esempio, se l'aggiornamento è ogni secondo, 50m è una variazione enorme.
        private const val MAX_ALTITUDE_JUMP_METERS_PER_UPDATE = 50.0
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

    // Valori per il calcolo del dislivello con GPS
    private var previousFilteredGpsAltitude: Double? = null
    private var lastRawGpsAltitude: Double? = null // Per il controllo dei salti

    // valori di riferimento della traccia da seguire
    var trackDistanza = 0f
    var trackAscesa = 0
    var trackDiscesa = 0

    // valori per barometro
    var haBaro = false
    var setBaro = false
    private var newQuota: Int? = 0 // Usato per dislivelloBaro, potrebbe essere rinominato o rimosso se non strettamente necessario altrove
    private var oldQuota: Int? = 0 // Usato per dislivelloBaro

    // coefficiente per filtro passa basso quota barometro da 0 ad 1
    private val alfaBaro: Double = 0.21 // Rinominato per chiarezza
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
            return
        }
        // L'altitudine passata qui è quella grezza dal GPS
        newPunto = GeoPoint(loc.latitude, loc.longitude, altitudine)

        if (!isFixed) {
            oldPunto = newPunto
            lastRawGpsAltitude = altitudine // Inizializza anche l'altitudine grezza
            isFixed = true
            return
        }

        _velocita.value = (loc.speed * 3.6).toInt()

        if (haBaro && setBaro && isCalibrato.value == true) {
            millibar = baroPress
            val altitudineBaro: Double = MapUtils.calcolaAltitudineIpso(millibar, NORMAL_PRESSURE).toDouble()
            dislivelloBaro(altitudineBaro.toInt()) // Calcola dislivello basato su altitudine barometrica filtrata
            _quota.value = previousFilteredGpsAltitude?.toInt() ?: altitudineBaro.toInt() // Usa la quota filtrata barometrica come _quota.value
            newPunto = GeoPoint(loc.latitude, loc.longitude, _quota.value!!.toDouble())
        } else {
            val nuovaQuotaFiltrataGps = processGpsAltitude(altitudine)
            if (nuovaQuotaFiltrataGps != null) {
                _quota.value = nuovaQuotaFiltrataGps.toInt()
                // newPunto viene aggiornato con la quota filtrata GPS
                newPunto = GeoPoint(loc.latitude, loc.longitude, nuovaQuotaFiltrataGps)
            } else {
                // Se processGpsAltitude restituisce null (es. all'inizio o per valori scartati),
                // potresti voler mantenere l'ultima quota valida o usare l'altitudine grezza.
                // Per ora, newPunto mantiene l'altitudine grezza passata.
                // e _quota.value non viene aggiornato, mantenendo l'ultimo valore valido.
            }
        }

        if (oldPunto.latitude != 0.0 && oldPunto.longitude != 0.0) {
            _distanzaMetri.value = (distanzaMetri.value ?: 0) + MapUtils.getDistanceInMeters(oldPunto, newPunto)
        }

        _traccia.value?.addPoint(newPunto)
        salvaPuntoGPS(newPunto)
        oldPunto = newPunto
    }

    private fun dislivelloBaro(altitudineBaroLetta: Int) {
        if (oldQuota == 0) { // oldQuota è l'equivalente di previousFilteredAltitude per il barometro
            oldQuota = altitudineBaroLetta
            return
        }
        // Filtro passa basso per il barometro
        val quotaFiltrataBaro  = ((alfaBaro * altitudineBaroLetta) + ((1 - alfaBaro) * oldQuota!!)).toInt()

        if (quotaFiltrataBaro > oldQuota!!) {
            val diffPiu = quotaFiltrataBaro - oldQuota!!
            _dislivPiu.value = _dislivPiu.value?.plus(diffPiu)
        } else {
            val diffMeno = oldQuota!! - quotaFiltrataBaro
            _dislivMeno.value = _dislivMeno.value?.plus(diffMeno)
        }
        oldQuota = quotaFiltrataBaro
        // newQuota = quotaFiltrataBaro // Se newQuota era usato per _quota.value, ora _quota.value viene impostato direttamente in aggiornaDati
    }

    fun processGpsAltitude(currentRawGpsAltitude: Double): Double? {
        // 1. Controllo per salti anomali sull'altitudine GREZZA
        if (lastRawGpsAltitude != null) {
            if (abs(currentRawGpsAltitude - lastRawGpsAltitude!!) > MAX_ALTITUDE_JUMP_METERS_PER_UPDATE) {
                Log.w("ProcessGPS", "Salto di altitudine GPS scartato: da $lastRawGpsAltitude a $currentRawGpsAltitude")
                // Non aggiorniamo lastRawGpsAltitude qui, manteniamo l'ultimo valore "buono"
                // e non procediamo con il filtraggio di questo valore anomalo.
                return previousFilteredGpsAltitude // Restituisce l'ultima altitudine filtrata valida
            }
        }
        lastRawGpsAltitude = currentRawGpsAltitude // Aggiorna l'ultima altitudine grezza valida

        // 2. Applicazione del filtro passa-basso (EMA)
        val filteredAltitude: Double
        if (previousFilteredGpsAltitude == null) {
            // Primo valore, il filtro inizia da qui
            filteredAltitude = currentRawGpsAltitude
        } else {
            // Formula EMA: EMA_attuale = (Valore_attuale * alfa) + (EMA_precedente * (1 - alfa))
            filteredAltitude = (currentRawGpsAltitude * GPS_LOW_PASS_ALPHA) + (previousFilteredGpsAltitude!! * (1.0 - GPS_LOW_PASS_ALPHA))
        }

        // 3. Aggiornamento del dislivello basato sull'altitudine filtrata
        updateGpsAltitudeChanges(filteredAltitude)

        previousFilteredGpsAltitude = filteredAltitude
        return filteredAltitude
    }

    private fun updateGpsAltitudeChanges(currentFilteredGpsAltitude: Double) {
        if (previousFilteredGpsAltitude == null) {
            // Non c'è un'altitudine precedente filtrata per calcolare la differenza,
            // ma previousFilteredGpsAltitude verrà impostato in processGpsAltitude
            // per il prossimo calcolo.
            return
        }

        // Utilizza l'ALTITUDINE FILTRATA PRECEDENTE MEMORIZZATA (previousFilteredGpsAltitude)
        // per il calcolo del dislivello, non quella passata come argomento se non è la prima volta.
        // Questo evita un doppio conteggio o un calcolo errato se la funzione viene chiamata in modo imprevisto.
        // Tuttavia, la logica attuale in processGpsAltitude si assicura che previousFilteredGpsAltitude
        // sia aggiornato correttamente prima di chiamare questa funzione, quindi possiamo usare
        // il previousFilteredGpsAltitude di stato della classe.

        val altitudeDifference = currentFilteredGpsAltitude - this.previousFilteredGpsAltitude!! // Usa il membro della classe

        if (altitudeDifference > GPS_ALTITUDE_CHANGE_THRESHOLD_METERS) {
            _dislivPiu.value = (_dislivPiu.value ?: 0.0) + altitudeDifference
        } else if (altitudeDifference < -GPS_ALTITUDE_CHANGE_THRESHOLD_METERS) {
            // Per il dislivello negativo, sommiamo il valore assoluto della differenza
            _dislivMeno.value = (_dislivMeno.value ?: 0.0) + abs(altitudeDifference)
        }
        // Non aggiorniamo previousFilteredGpsAltitude qui, viene fatto in processGpsAltitude
    }


    // Rimuoviamo il vecchio applyMovingAverage e gpsAltitudeHistory se non più usati
    // private val gpsAltitudeHistory: ArrayDeque<Double> = ArrayDeque(MOVING_AVERAGE_WINDOW_SIZE)
    // private fun applyMovingAverage(altitude: Double): Double { ... }


    fun resetCruscotto() {
        _quota.value = 0
        _dislivPiu.value = 0.0
        _dislivMeno.value = 0.0
        _velocita.value = 0
        _distanzaMetri.value = 0
        _tempoTrascorso.value = ""
        _secondiMovimento.value = 0
        alertFuoriTraccia = false
        previousFilteredGpsAltitude = null // Resetta anche lo stato del filtro GPS
        lastRawGpsAltitude = null
        oldQuota = 0 // Resetta lo stato del filtro barometrico
    }

    fun baroCalibrato(barometro: Boolean) {
        _isCalibrato.value = barometro
    }

    private fun salvaPuntoGPS(punto: GeoPoint) {
        val newWayPoint = WayPoint(
            latitude = punto.latitude,
            longitude = punto.longitude,
            elevation = punto.altitude, // Salva l'altitudine usata per il punto (già filtrata)
            time = Timestamp(System.currentTimeMillis()),
        )
        puntiGPS.add(newWayPoint)
    }

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
                geoPuntiPercorso.add(punto)
            }
            if (percorso.actualPoints.isNotEmpty()) {
                percorso = disegnaLine(percorso)
            }
            else
                return@Thread
            poiDao?.getPoibyID(id)?.forEach {
                poiList.add(it)
           }

        }
        thread.start()
        try { // È buona norma usare try-join per InterruptedException
            thread.join()
        } catch (e: InterruptedException) {
            Log.e("LeggiTrack", "Thread interrotto", e)
            Thread.currentThread().interrupt() // Ripristina lo stato di interruzione
        }
        return percorso
    }

    private fun incrementMovementSeconds() {
        _secondiMovimento.value = (_secondiMovimento.value ?: 0) + 1
    }

    fun startUpdates() {
        if (updatesJob?.isActive == true) {
            return
        }
        updatesJob = viewModelScope.launch {
            while (true) {
                val currentTime = System.currentTimeMillis()
                elapsedTime = currentTime - oraInizio
                _tempoTrascorso.value = MapUtils.formatElapsedTime(elapsedTime)
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
    }

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

    fun cercaPoi(id: Int): List<PoiDB> {
        return repository.cercaPoi(id)
    }

    fun listaFotoId(id: Int): List<FotoPoi> {
        return repository.listaFotoId(id)
    }

    override fun onCleared() {
        super.onCleared()
    }

}
