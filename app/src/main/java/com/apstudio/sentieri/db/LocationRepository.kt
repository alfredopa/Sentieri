package com.apstudio.sentieri.db

import android.location.Location
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.apstudio.sentieri.MapUtils
import net.federicomatera.agpxp.models.WayPoint
import org.osmdroid.util.GeoPoint
import java.sql.Timestamp
import java.util.concurrent.CopyOnWriteArrayList

object LocationRepository {
    private const val MOVING_AVERAGE_WINDOW_SIZE = 15
    private const val GPS_ALTITUDE_SPIKE_THRESHOLD = 25.0
    private const val MIN_VARIATION_THRESHOLD_GPS = 1.0
    private const val MIN_VARIATION_THRESHOLD_BARO = 0.5 // Più sensibile per il barometro
    private const val SLOPE_CALCULATION_DISTANCE_THRESHOLD = 25.0
    private const val WARMUP_READINGS_TO_DISCARD = 8
    private const val EMA_ALFA = 0.21

    // Stato sessione
    var isRecording = false
    var isFixed = false
    var normalPressure = 1013.25f
    var usaBaro = false
    var numSat: Int = 0

    // Accumulatori
    private var accumuloDistanzaMetri = 0
    private var accumuloDislivPiu = 0.0
    private var accumuloDislivMeno = 0.0
    private var secondiMovimento = 0L

    // Variabili elaborazione
    private var oldPunto = GeoPoint(0.0, 0.0, 0.0)
    private var oldQuotaEMA: Double? = null
    private var lastRecordedAltitudeForSum: Double? = null
    private var referencePointForSlope: GeoPoint? = null
    private var discardedGpsPointsCount = 0
    private val gpsAltitudeHistory = ArrayDeque<Double>(MOVING_AVERAGE_WINDOW_SIZE)
    private var previousFilteredAltitude: Double? = null
    private var rejectedGpsPointsCount = 0 // Per il recupero dello spike filter

    // LiveData per UI
    private val _location = MutableLiveData<Location>()
    val location: LiveData<Location> = _location

    private val _distanzaMetri = MutableLiveData(0)
    val distanzaMetri: LiveData<Int> = _distanzaMetri

    private val _dislivPiu = MutableLiveData(0.0)
    val dislivPiu: LiveData<Double> = _dislivPiu

    private val _dislivMeno = MutableLiveData(0.0)
    val dislivMeno: LiveData<Double> = _dislivMeno

    private val _quota = MutableLiveData(0)
    val quota: LiveData<Int> = _quota

    private val _pendenza = MutableLiveData(0)
    val pendenza: LiveData<Int> = _pendenza

    private val _velocitaKmh = MutableLiveData(0)
    val velocitaKmh: LiveData<Int> = _velocitaKmh

    private val _secondiMovimento = MutableLiveData(0L)
    val secondiMovimentoLiveData: LiveData<Long> = _secondiMovimento

    private val _gpsStatus = MutableLiveData("stopped")
    val gpsStatus: LiveData<String> = _gpsStatus

    // MSL Altitude (Richiesta dal parser NMEA del Service)
    private val _mslAltitude = MutableLiveData(0.0)
    val mslAltitude: LiveData<Double> = _mslAltitude

    private val _baroPressure = MutableLiveData(0F)
    val baroPressure: LiveData<Float> = _baroPressure

    private val _isCalibrato = MutableLiveData(false)
    val isCalibrato: LiveData<Boolean> = _isCalibrato
    private var calibratoInterno: Boolean = false // Variabile per i calcoli

    // Dati traccia
    val trackPointsList = mutableListOf<GeoPoint>()
    val puntiGPS = CopyOnWriteArrayList<WayPoint>()
    private val _newTrackPoint = MutableLiveData<GeoPoint>()
    val newTrackPoint: LiveData<GeoPoint> = _newTrackPoint

    private val _trackPoints = MutableLiveData<List<GeoPoint>>()
    val trackPoints: LiveData<List<GeoPoint>> = _trackPoints

    fun processNewLocation(loc: Location, msl: Double, baroPress: Float) {
        _location.postValue(loc)

        // LOGICA MSL PIÙ ROBUSTA:
        // Se msl è uguale al valore precedente del repository, potrebbe essere un dato NMEA "congelato".
        // In tal caso, usiamo l'altitudine GPS standard per rilevare i cambiamenti.
        val currentGpsAlt = loc.altitude
        val mslValida = if (msl == 0.0 || msl == _mslAltitude.value) currentGpsAlt else msl
        updateMslAltitude(mslValida)

        // Calcola altitudine barometrica se possibile, anche se non stiamo registrando,
        // per permettere la visualizzazione in tempo reale della quota nel cruscotto.
        val altitudineBarometrica = if (usaBaro && isCalibrato.value == true) {
            MapUtils.calcolaAltitudineIpso(baroPress, normalPressure).toDouble()
        } else null

        // Aggiorna la quota LiveData (usata dal cruscotto)
        if (altitudineBarometrica != null) {
            // Se siamo in modalità BARO, applichiamo il filtro EMA e aggiorniamo la quota
            val filtered = (EMA_ALFA * altitudineBarometrica) + ((1 - EMA_ALFA) * (oldQuotaEMA ?: altitudineBarometrica))
            oldQuotaEMA = filtered
            _quota.postValue(filtered.toInt())
        } else {
            _quota.postValue(mslValida.toInt())
        }

        if (!isRecording) return

        val speedKmh = (loc.speed * 3.6).toInt()
        updateVelocita(speedKmh)

        // Usiamo la quota filtrata (se disponibile) o quella MSL per il punto della traccia
        val quotaPunto = oldQuotaEMA ?: mslValida
        val currentPoint = GeoPoint(loc.latitude, loc.longitude, quotaPunto)
        // Se la velocità è < 2 km/h e siamo molto vicini all'ultimo punto registrato (< 5 metri),
        // ignoriamo il punto per evitare "rumore" nella traccia mentre si è fermi.
        if (speedKmh < 2 && trackPointsList.isNotEmpty()) {
            val lastPoint = trackPointsList.last()
            val distanceFromLast = MapUtils.getDistanceInMeters(lastPoint, currentPoint)
            if (distanceFromLast < 5.0) {
                return // Esci senza aggiungere il punto alla lista né al DB
            }
        }
        // 1. Aggiungiamo SEMPRE il punto alla traccia visiva sulla mappa,
        // anche durante il warm-up, altrimenti la linea non appare.
        trackPointsList.add(currentPoint)
        puntiGPS.add(
            WayPoint(
                currentPoint.latitude,
                currentPoint.longitude,
                currentPoint.altitude,
                Timestamp(System.currentTimeMillis())
            )
        )
        _newTrackPoint.postValue(currentPoint)

        // 2. Logica di Warm-up (solo per i calcoli statistici di dislivello e distanza)
        if (!isFixed) {
            if (oldPunto.latitude == 0.0) {
                oldPunto = currentPoint
                referencePointForSlope = currentPoint
                if (usaBaro && isCalibrato.value == true) {
                    isFixed = true
                }
            }
            if (!(usaBaro && isCalibrato.value == true)) {
                if (discardedGpsPointsCount < WARMUP_READINGS_TO_DISCARD) {
                    discardedGpsPointsCount++
                    return // Salta i calcoli statistici per ora
                }
                isFixed = true
                oldPunto = currentPoint
                referencePointForSlope = currentPoint
            }
        }

        // 3. Calcoli statistici (vengono eseguiti solo se isFixed == true)
        if (isFixed) {
            accumuloDistanzaMetri += MapUtils.getDistanceInMeters(oldPunto, currentPoint).toInt()
            _distanzaMetri.postValue(accumuloDistanzaMetri)

            if (usaBaro && isCalibrato.value == true) {
                updateGainLossBaro(quotaPunto)
            } else {
                updateGainLossGps(mslValida) // Passiamo solo la quota msl
            }
        }
        if (speedKmh > 2) { // Calcola pendenza solo in movimento
            referencePointForSlope?.let { ref ->
                val dist = ref.distanceToAsDouble(currentPoint)
                if (dist >= SLOPE_CALCULATION_DISTANCE_THRESHOLD) {
                    _pendenza.postValue(((currentPoint.altitude - ref.altitude) / dist * 100).toInt())
                    referencePointForSlope = currentPoint
                }
            }
        } else _pendenza.postValue(0)

        oldPunto = currentPoint
    }

    private fun updateGainLossGps(alt: Double) {
        // RECUPERO DA SPIKE: se scartiamo troppi punti, resettiamo il riferimento
        if (previousFilteredAltitude != null && kotlin.math.abs(alt - previousFilteredAltitude!!) > GPS_ALTITUDE_SPIKE_THRESHOLD) {
            rejectedGpsPointsCount++
            if (rejectedGpsPointsCount < 4) return // Scarta il punto
            else {
                // Dopo 4 rifiuti, forziamo il reset sulla nuova quota per sbloccare il calcolo
                gpsAltitudeHistory.clear()
                rejectedGpsPointsCount = 0
            }
        } else {
            rejectedGpsPointsCount = 0
        }

        gpsAltitudeHistory.addLast(alt)
        if (gpsAltitudeHistory.size > MOVING_AVERAGE_WINDOW_SIZE) gpsAltitudeHistory.removeFirst()
        val currentFiltered = gpsAltitudeHistory.average()

        if (lastRecordedAltitudeForSum == null) lastRecordedAltitudeForSum = currentFiltered
        val delta = currentFiltered - lastRecordedAltitudeForSum!!

        if (kotlin.math.abs(delta) >= MIN_VARIATION_THRESHOLD_GPS) {
            if (delta > 0) accumuloDislivPiu += delta else accumuloDislivMeno -= delta
            _dislivPiu.postValue(accumuloDislivPiu)
            _dislivMeno.postValue(accumuloDislivMeno)
            lastRecordedAltitudeForSum = currentFiltered
        }
        previousFilteredAltitude = currentFiltered
    }

    fun updateGainLossBaro(alt: Double) {
        val filtered = oldQuotaEMA ?: alt
        if (lastRecordedAltitudeForSum == null) lastRecordedAltitudeForSum = filtered
        val delta = filtered - lastRecordedAltitudeForSum!!

        // Soglia più bassa per maggiore precisione barometrica
        if (kotlin.math.abs(delta) >= MIN_VARIATION_THRESHOLD_BARO) {
            if (delta > 0) accumuloDislivPiu += delta else accumuloDislivMeno -= delta
            _dislivPiu.postValue(accumuloDislivPiu)
            _dislivMeno.postValue(accumuloDislivMeno)
            lastRecordedAltitudeForSum = filtered
        }
    }


    fun updateVelocita(v: Int) { _velocitaKmh.postValue(v) }
    fun updateMslAltitude(a: Double) { _mslAltitude.postValue(a) }
    fun updateGpsStatus(s: String) { _gpsStatus.postValue(s) }
    fun updateBaroPressure(p: Float) { /* usata indirettamente via processNewLocation */ }

    fun baroCalibrato(valore: Boolean) {
        calibratoInterno = valore // Aggiorna per i calcoli interni
        _isCalibrato.postValue(valore) // Notifica l'UI
    }

    fun incrementMovementSeconds() {
        if (isRecording && (_velocitaKmh.value ?: 0) > 2) { // 2 km/h soglia minima
            secondiMovimento++
            _secondiMovimento.postValue(secondiMovimento)
        }
    }

    fun getFullTrackSnapshot(): List<GeoPoint> = trackPointsList.toList()

    fun clearTrack() {
        isRecording = false
        isFixed = false
        accumuloDistanzaMetri = 0
        accumuloDislivPiu = 0.0
        accumuloDislivMeno = 0.0
        secondiMovimento = 0L
        oldPunto = GeoPoint(0.0, 0.0, 0.0)
        oldQuotaEMA = null
        lastRecordedAltitudeForSum = null
        referencePointForSlope = null
        discardedGpsPointsCount = 0
        gpsAltitudeHistory.clear()
        previousFilteredAltitude = null

        trackPointsList.clear()
        puntiGPS.clear()

        // NOTIFICA IL RESET ALLA UI
        _trackPoints.postValue(emptyList())

        _distanzaMetri.postValue(0)
        _dislivPiu.postValue(0.0)
        _dislivMeno.postValue(0.0)
        _quota.postValue(0)
        _pendenza.postValue(0)
        _velocitaKmh.postValue(0)
        _secondiMovimento.postValue(0L)
        _mslAltitude.postValue(0.0)
    }
}