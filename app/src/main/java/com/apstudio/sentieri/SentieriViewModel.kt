package com.apstudio.sentieri

import android.location.Location
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.fragment.app.Fragment
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.liveData
import com.apstudio.sentieri.MapUtils.disegnaLine
import com.apstudio.sentieri.db.FotoPoi
import com.apstudio.sentieri.db.LayerItem
import com.apstudio.sentieri.db.PoiDB
import com.apstudio.sentieri.db.Sentieri
import com.apstudio.sentieri.db.SentieriDB
import com.apstudio.sentieri.db.SentieriRepo
import net.federicomatera.agpxp.models.WayPoint
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.FolderOverlay
import org.osmdroid.views.overlay.Polyline
import java.sql.Timestamp
import java.util.concurrent.TimeUnit

class SentieriViewModel(private val repository: SentieriRepo) : ViewModel() {

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
    var menuMap = 0
    var isFixed = false
    var running = true
    var isRecording = false
    var ricerca = String()
    var ultPosizione = GeoPoint(40.120875, 9.012893, 40.0)
    private var locDaGPS = Location("")
    var newPunto =  GeoPoint(0.0,0.0,0.0)
    private var oldPunto =  GeoPoint(0.0,0.0,0.0)
    //var puntiPostFix = 0    // deve incrementare almeno 6 punti letti per migliorare altitudine GPS
    var ultZoom = (9)
    var oraInizio: Long = 0
    //var tempoMovimento : Date? = null
    // valori visualizzati nel cruscotto
    val distanzaMetri = MutableLiveData(0)
    val dislivPiu = MutableLiveData(0)
    val dislivMeno = MutableLiveData(0)
    val dislivPiuIpso = MutableLiveData(0)
    val dislivMenoIpso = MutableLiveData(0)
    val velocita = MutableLiveData(0)
    val quota = MutableLiveData(0)
    var quotaIpso = MutableLiveData(0)
    // valori per il calcolo del dislivello con GPS con filtro MovingAverage
    private var previousAltitude: Int? = null
    private var dislivelloPiu: Double = 0.0
    private var dislivelloMeno: Double = 0.0
    private val altitudeHistory = mutableListOf<Double>()
    private val movingAverageWindowSize = 15 // Regola secondo necessità
    // valori di riferimento della traccia da seguire
    var trackDistanza = 0f
    var trackAscesa = 0
    var trackDiscesa = 0

    // valori per barometro
    var haBaro = false
    var setBaro = false
    private var newQuota: Int? = 0
    private var oldQuota: Int? = 0
    private var newQuotaIpso: Int? = 0
    private var oldQuotaIpso: Int? = 0
    // coefficiente per filtro passa basso quota barometro da 0 ad 1
    // con 0.1 da valori troppo bassi (-200 dislivello)
    private val alfa: Double = 0.25
    private var millibar = 0F
    var NORMAL_PRESSURE = 1013.25F
    var is_Calibrato = false
    var BottomState = 0

    // USARE CODICE PER CONVERTIRE I VALORI IN LIVEDATA DA MODIFICA DELLE PREFERENZE
    /*privateval _isMapOnline = MutableLiveData<Boolean>()
    val isMapOnline: LiveData<Boolean> = _isMapOnline

    private val _setBaro = MutableLiveData<Boolean>()
    val setBaro: LiveData<Boolean> = _setBaro

    fun updateIsMapOnline(isMapOnline: Boolean) {
        _isMapOnline.value = isMapOnline
    }

    fun updateSetBaro(setBaro: Boolean) {
        _setBaro.value = setBaro
    }*/

    init {
        val traccia = Polyline()
        _traccia.value = traccia
    }

    fun aggiornaDati(loc: Location, milliBar: Float) {
        locDaGPS = loc
        newPunto = GeoPoint(loc.latitude, loc.longitude, loc.altitude)

        // al primo aggiornamento di posizione valorizza isFixed true
        if (!isFixed) {
            // al primo fix gps oldPunto e newPunto coincidono
            oldPunto = GeoPoint(loc.latitude, loc.longitude, loc.altitude)
            isFixed = true
            Log.d("Mappa", "fixed true")
            return
        }

        if (newPunto.latitude == oldPunto.latitude && newPunto.longitude == oldPunto.longitude) return
        velocita.value = (loc.speed * 3.6).toInt()
        // determina se calcolare altitudine da Gps o barometro
        if (haBaro && setBaro) {
            millibar = milliBar
            dislivelloBaro()
            quotaIpso.value = newQuotaIpso
        } else
            dislivelloGPS()

        distanzaMetri.value = (distanzaMetri.value ?: 0) + MapUtils.getDistanceInMeters(oldPunto, newPunto)
        //distanzaH.value = (distanzaH.value ?: 0) + MapUtils.distance(oldPunto, newPunto)

        // aggiunge il punto alla traccia
        _traccia.value?.addPoint(newPunto)
        // salva punto nell'array globale di punti (wayPoints)
        salvaPuntoGPS(newPunto)
        // memorizza punto come oldpunto per confronto col prossimo aggiornamento
        oldPunto = newPunto
        // assegnata nuova quota al LiveData
        quota.value = newQuota
        quotaIpso.value = newQuotaIpso
    }

    private fun dislivelloBaro() {
        // trova la quota attuale col valore millibar
        if (millibar == 0.0F)
            return
        newQuota = MapUtils.calcolaAltitudine(millibar, NORMAL_PRESSURE).toInt()
        newQuotaIpso = MapUtils.calcolaAltitudineIpso(millibar, NORMAL_PRESSURE).toInt()
        if (oldQuota == 0) {
            oldQuota = newQuota
        }
        //formula ipsometrica
        if (oldQuotaIpso == 0) {
            oldQuotaIpso = newQuotaIpso
        }

        // Filtro passa basso
        val quotaFiltrata  = ((alfa * newQuota!!) + ((1 - alfa) * oldQuota!!)).toInt()
        val quotaFiltrataIpso = ((alfa * newQuotaIpso!!) + ((1 - alfa) * oldQuotaIpso!!)).toInt()

        // Calcola il dislivello positivo
        if (quotaFiltrata > oldQuota!!) {
            val diffPiu = quotaFiltrata - oldQuota!!
            dislivPiu.value = dislivPiu.value?.plus(diffPiu)
        } else {
            val diffMeno = oldQuota!! - quotaFiltrata
            dislivMeno.value = dislivMeno.value?.plus(diffMeno)
        }
        // Aggiorna la quota precedente
        oldQuota = quotaFiltrata
        // Imposta quota come media filtrata
        newQuota = quotaFiltrata

        // Calcola il dislivello positivo formula IPSOMETRICA
        if (quotaFiltrataIpso > oldQuotaIpso!!) {
            val diffPiu = quotaFiltrataIpso - oldQuotaIpso!!
            dislivPiuIpso.value = dislivPiuIpso.value?.plus(diffPiu)
        } else {
            val diffMeno = oldQuotaIpso!! - quotaFiltrataIpso
            dislivMenoIpso.value = dislivMenoIpso.value?.plus(diffMeno)
        }
        // Aggiorna la quota precedente
        oldQuotaIpso = quotaFiltrataIpso
        // Imposta quota come media filtrata
        newQuotaIpso = quotaFiltrataIpso
        // FINE Calcola il dislivello positivo formula IPSOMETRICA
        Log.d("viewmodel", "aggiornadati ${quotaIpso.value}  new $newQuotaIpso  d+ ${dislivPiuIpso.value} d- ${dislivMenoIpso.value}")
    }

    private fun dislivelloGPS() {
        // CALCOLO DISLIVELLO CON QUOTA DA GPS
        // attende 10 misurazioni prima di stimare altitudine
        if (altitudeHistory.size < movingAverageWindowSize) {
            altitudeHistory.add(locDaGPS.altitude)
            return
        }
        addLocation(locDaGPS)
        /*if (puntiPostFix < 6) {
            puntiPostFix++
            //Log.d("aggiornaDati", "puntiPostFix $puntiPostFix")
            return
        }
        val diffQuota: Int
        // deve calcolare se oldPunto < newPunto = salita aumenta dislivello
        if (oldPunto.altitude < newPunto.altitude) {
            diffQuota = (newPunto.altitude - oldPunto.altitude).toInt()
            if (diffQuota > 1)
                dislivPiu.value = dislivPiu.value?.plus(diffQuota)
        } else {
            diffQuota = (oldPunto.altitude - newPunto.altitude).toInt()
            // è negativo
            if (diffQuota > 1)
                dislivMeno.value = dislivMeno.value?.plus(diffQuota)
        }
        newQuota = newPunto.altitude.toInt()
        //Log.d("aggiornaDati",  "quota da GPS $newQuota")
         */

    }

    private fun addLocation(location: Location) {
        var currentAltitude: Double
        if (Build.VERSION.SDK_INT >= 34 && location.hasMslAltitude())
             currentAltitude = location.mslAltitudeMeters
        else
             currentAltitude = location.altitude

        // Filtra i dati di altitudine usando una media mobile
        val filteredAltitude = applyMovingAverage(currentAltitude)
        // Calcola la differenza di altitudine
        if (previousAltitude != null) {
            val altitudeDifference = (filteredAltitude - previousAltitude!!)
            // Accumula le differenze positive
            if (altitudeDifference > 1) {
                dislivPiu.value = dislivPiu.value?.plus(altitudeDifference)
            } else {
                dislivMeno.value = dislivMeno.value?.plus(altitudeDifference)
            }
        }
        // Aggiorna l'altitudine precedente
        previousAltitude = filteredAltitude
        newQuota = filteredAltitude
    }

    private fun applyMovingAverage(altitude: Double): Int {
        altitudeHistory.add(altitude)
        if (altitudeHistory.size > movingAverageWindowSize) {
            altitudeHistory.removeAt(0)
        }
        return altitudeHistory.average().toInt()
    }
    private fun salvaPuntoGPS(punto: GeoPoint) {
        val newWayPoint = WayPoint(
            latitude = punto.latitude,
            longitude = punto.longitude,
            elevation = newQuota!!.toDouble(),
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
            if (percorso.actualPoints.size > 0) {
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

    /*fun tempoTrascorso() : String {
        // Tempo da inizio
        var millisecondi = System.currentTimeMillis()
        val tempo_trascorso = millisecondi - oraInizio
        val ore = tempo_trascorso / (1000 * 60 * 60)
        val minuti = (tempo_trascorso / (1000 * 60)) % 60
        val secondi = (tempo_trascorso / (1000 )) % 60
        //return ("${ore}:${minuti}:${secondi}")
        return String.format("%d h %d min %d s", ore, minuti, secondi)
    }*/

    fun tempoTrascorso(): String {
        // Converti millisecondi in unità di tempo
        val millisecondi = System.currentTimeMillis()
        val tempo_trascorso = millisecondi - oraInizio
        val seconds = TimeUnit.MILLISECONDS.toSeconds(tempo_trascorso)
        val minutes = TimeUnit.SECONDS.toMinutes(seconds)
        val hours = TimeUnit.MINUTES.toHours(minutes)

        // Formatta le ore, minuti e secondi
        //val formattedHours = if (hours > 0) "%02d:" else ""
        val formattedHours = "%02d:"
        val formattedMinutes = "%02d:"
        val formattedSeconds = "%02d"

        // Crea la stringa formattata
        return String.format(
            formattedHours + formattedMinutes + formattedSeconds,
            hours,
            minutes % 60,
            seconds % 60
        )
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
        return repository.CercaId(id)
    }

    fun cercaNome(searchQuery: String): LiveData<List<Sentieri>> {
        return repository.cercaNome(searchQuery).asLiveData()
    }

    suspend fun salvaSentiero(sentiero: Sentieri) {
        repository.insertDB(sentiero)
    }

    fun cercaPoi(id: Int): List<PoiDB> {
        return repository.cercaPoi(id)
    }

    fun listaFotoId(id: Int): List<FotoPoi> {
        return repository.listaFotoId(id)
    }

    override fun onCleared() {
        //Log.d("viewmodel","viewmodel cleared")
        //stopSensorUpdates()
        isRecording = false
    }

}
