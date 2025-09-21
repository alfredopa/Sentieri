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

class SentieriViewModel(private val repository: SentieriRepo) : ViewModel() {

    companion object {
        // costanti per calcolo dislivello con GPS con filtro MovingAverage
        private const val ALTITUDE_CHANGE_THRESHOLD_METERS = 1.9 // Differenza minima di altitudine per considerare un cambio di quota
        private const val MOVING_AVERAGE_WINDOW_SIZE = 9 // Numero di valori da tenere in memoria per la media
        private const val MAX_ALTITUDE_JUMP_METERS_PER_UPDATE = 10.0
    }
    private val _traccia = MutableLiveData<Polyline>()
    val traccia : LiveData<Polyline> = _traccia
    val listaTracce = FolderOverlay()
    val recTraccia = FolderOverlay()
    val topoLayer = FolderOverlay()
    var line : Polyline = Polyline()
    // liste di punti gps e waypoint
    val puntiGPS = mutableListOf<WayPoint>()
    var wayPoint = mutableListOf<WayPoint>()
    var poiDBList = mutableListOf<PoiDB>()
    val fotoInPoiDB = mutableListOf<Uri>()
    val fotoList = mutableListOf<Uri>()
    val layerItems = mutableListOf<LayerItem>()
    val geoPuntiPercorso = mutableListOf<GeoPoint>()
    val toponimiSelezionati = mutableListOf<TopoMarkerData>() // New list
    var alertFuoriTraccia : Boolean = false
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
    private val _currentPosition = MutableLiveData<GeoPoint>()
    val currentPosition: LiveData<GeoPoint> = _currentPosition
    var newPunto =  GeoPoint(0.0,0.0,0.0)
    private var oldPunto =  GeoPoint(0.0,0.0,0.0)
    var ultZoom = (9)

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

    private val gpsAltitudeHistory: ArrayDeque<Double> = ArrayDeque(MOVING_AVERAGE_WINDOW_SIZE)
    private var previousFilteredAltitude: Double? = null
    // valori di riferimento della traccia da seguire
    var trackDistanza = 0f
    var trackAscesa = 0
    var trackDiscesa = 0

    // valori per barometro
    var haBaro = false
    var setBaro = false
    //private var newQuota: Int? = 0
    private var oldQuota: Int? = 0

    // coefficiente per filtro passa basso quota barometro da 0 ad 1
    // con 0.1 da valori troppo bassi (-200 dislivello)
    private val alfa: Double = 0.21
    private val alfaGPS: Double = 0.23  //0.25 prec
    var NORMAL_PRESSURE = 1013.25F
    private val _isCalibrato = MutableLiveData(false)
    val isCalibrato : LiveData<Boolean> = _isCalibrato
    var bottomState = 0

    init {
        val traccia = Polyline()
        _traccia.value = traccia
        _currentPosition.value = GeoPoint(0.0,0.0,0.0) // Inizializzazione
    }

    fun processNewLocationData(loc: Location, altitudine: Double, baroPress: Float) {
        viewModelScope.launch(Dispatchers.IO) { // Esegui su un thread in background
            // Qui ora chiami la logica che prima era in aggiornaDati
            // o sposti il contenuto di aggiornaDati qui.
            // Per esempio, rinominiamo la vecchia aggiornaDati in _performDataUpdate
            _performDataUpdate(loc, altitudine, baroPress)
        }
    }

    private suspend fun _performDataUpdate(loc: Location?, altitudineOriginale: Double, baroPress: Float) {
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
        _currentPosition.postValue(currentNewPunto) // Aggiorna il LiveData per la posizione corrente
        newPunto = currentNewPunto // Continua ad aggiornare newPunto per compatibilità
        // Log.d("SentieriViewModel", "processNewLocationData currentNewPunto: $currentNewPunto") // Log più specifico
        // Logica del primo fix
        if (!isFixed) {
            // Sincronizza l'accesso a oldPunto e isFixed se necessario,
            // anche se viewModelScope dovrebbe serializzare le chiamate a processNewLocationData
            oldPunto = currentNewPunto
            isFixed = true
            // Log.d("SentieriViewModel", "Primo fix GPS. OldPunto: $oldPunto")
            return // o semplicemente return, a seconda di come strutturi
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

        // Aggiunge il punto alla traccia (sul Main thread)
        withContext(Dispatchers.Main) {
            val currentPolyline = _traccia.value
            currentPolyline?.addPoint(currentNewPunto)
            _traccia.value = currentPolyline!! // Forza l'aggiornamento del LiveData
            Log.d("SentieriViewModel", "Traccia LiveData aggiornata con nuovo punto: $currentNewPunto. Tot punti: ${currentPolyline.actualPoints?.size}")
        }

        // Salva punto GPS (nella lista in memoria)
        salvaPuntoGPS(currentNewPunto) // Assicurati che questa lista sia thread-safe se accessibile da altrove
        // o che tutte le modifiche avvengano dentro questo scope.

        oldPunto = currentNewPunto
    }


    /*fun aggiornaDati(loc: Location?, altitudine: Double, baroPress: Float) {
        if (loc == null) {
            //Log.w("GGA", "Location is null, cannot update data")
            return
        }
        // newPunto = GeoPoint(loc.latitude, loc.longitude, altitudine) // Verrà impostato da _performDataUpdate
        // _currentPosition.value = newPunto // Aggiorna anche qui se aggiornaDati è chiamato direttamente
                                            // Ma l'obiettivo è usare processNewLocationData/_performDataUpdate
        
        // La logica principale è ora in _performDataUpdate, chiamata tramite processNewLocationData.
        // Questa funzione aggiornaDati potrebbe diventare obsoleta o avere un ruolo ridotto.
        // Per ora, ci assicuriamo che newPunto sia aggiornato se questa funzione viene ancora usata.
        var altitudineCalcolata = altitudine
        if (haBaro && setBaro && isCalibrato.value == true) {
            millibar = baroPress
            val altBaro = MapUtils.calcolaAltitudineIpso(millibar, NORMAL_PRESSURE).toDouble()
            altitudineCalcolata = altBaro
        } else {
            val nuovaQuotaGps = processGpsAltitude(altitudine)
            if (nuovaQuotaGps != null) {
                altitudineCalcolata = nuovaQuotaGps
            }
        }
        val tempNewPunto = GeoPoint(loc.latitude, loc.longitude, altitudineCalcolata)
        // _currentPosition.value = tempNewPunto // Se aggiornaDati è su MainThread
        newPunto = tempNewPunto


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
            // millibar = baroPress // Già fatto sopra
            // utilizza formula ipsometrica per calcolare altitudine
            // val altitudineBaro: Double = MapUtils.calcolaAltitudineIpso(millibar, NORMAL_PRESSURE).toDouble() // Già fatto sopra
            // newPunto = GeoPoint(loc.latitude, loc.longitude, altitudineBaro) // Già fatto sopra
            dislivelloBaro(altitudineCalcolata.toInt()) // Usa altitudineCalcolata
            _quota.value = altitudineCalcolata.toInt()
        } else {
            // la quota deve essere quella media calcolata con MovingAverage
            // val nuovaQuota = processGpsAltitude(altitudine) // Già fatto sopra e in altitudineCalcolata
            if (altitudineCalcolata != altitudine) { // Se processGpsAltitude ha prodotto un valore
                 _quota.value = altitudineCalcolata.toInt()
                // newPunto = GeoPoint(loc.latitude, loc.longitude, altitudineCalcolata) // Già fatto sopra
            } else { // Caso in cui processGpsAltitude potrebbe restituire null o l'altitudine originale
                 _quota.value = altitudine.toInt() // Fallback o valore non filtrato
                 // newPunto = GeoPoint(loc.latitude, loc.longitude, altitudine) // Già fatto sopra
            }
            // SimpleFileLogger.log("aggiornaDati", "nuovaQuota $nuovaQuota") // nuovaQuota non è più definita qui
        }

        if (oldPunto.latitude != 0.0 && oldPunto.longitude != 0.0) {
            _distanzaMetri.value = (distanzaMetri.value ?: 0) + MapUtils.getDistanceInMeters(oldPunto, newPunto)
        }

        // aggiunge il punto alla traccia
        // _traccia.value?.addPoint(newPunto) // Commentato perché gestito da _performDataUpdate
        // _traccia.value = _traccia.value // Commentato perché gestito da _performDataUpdate


        // salva punto nell'array globale di punti (wayPoints)
        salvaPuntoGPS(newPunto)
        // memorizza punto come oldpunto per confronto col prossimo aggiornamento
        oldPunto = newPunto
    }*/

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
        // 1. Controllo per salti anomali sull'altitudine GREZZA
        /*if (previousFilteredAltitude != null && gpsAltitudeHistory.isNotEmpty()) {
            if (abs(gpsAltitude - previousFilteredAltitude!!) > MAX_ALTITUDE_JUMP_METERS_PER_UPDATE) {
                SimpleFileLogger.log("ProcessGPS", "Salto di altitudine GPS scartato: da $previousFilteredAltitude a $gpsAltitude")
                // Non aggiorniamo lastRawGpsAltitude qui, manteniamo l'ultimo valore "buono"
                // e non procediamo con il filtraggio di questo valore anomalo.
                return previousFilteredAltitude // Restituisce l'ultima altitudine filtrata valida
            }
        }*/
        if (previousFilteredAltitude == null) {
            previousFilteredAltitude = gpsAltitude
        }
        // attende il numero di altitudeHistory punti prima di stimare altitudine
        if (gpsAltitudeHistory.size < MOVING_AVERAGE_WINDOW_SIZE -1) {
            gpsAltitudeHistory.add(gpsAltitude)
            return null
        } else {
            // Add the current altitude before calculating the average
            //val filteredAltitude = applyMovingAverage(gpsAltitude)
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
        _currentPosition.value = GeoPoint(0.0,0.0,0.0) // Resetta la posizione corrente
        newPunto = GeoPoint(0.0,0.0,0.0)
        oldPunto = GeoPoint(0.0,0.0,0.0)
        isFixed = false
        
        // Resetta anche la traccia LiveData
        val emptyPolyline = Polyline()
        _traccia.value = emptyPolyline 
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
            // legge eventuali waypoint della traccia dal DB Poi
            poiDao?.getPoibyID(id)?.forEach {
                poiList.add(it)
            }
            if (percorso.actualPoints.isNotEmpty()) {
                percorso = disegnaLine(percorso)
            }
            else
                return@Thread
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

    fun listaFotoId(id: Int): List<FotoPoi> {
        return repository.listaFotoId(id)
    }

}
