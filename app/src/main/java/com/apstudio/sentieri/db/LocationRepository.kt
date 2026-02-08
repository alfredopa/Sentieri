package com.apstudio.sentieri.db


import android.location.Location
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import org.osmdroid.util.GeoPoint// Usa un "object" per creare un Singleton in modo semplice e sicuro
object LocationRepository {
    private val _gpsStatus = MutableLiveData("stopped")
    val gpsStatus: LiveData<String> = _gpsStatus
    private val _mslAltitude = MutableLiveData(0.0)
    val mslAltitude : LiveData<Double> = _mslAltitude
    private val _velocita = MutableLiveData(0)
    val velocita: LiveData<Int> = _velocita
    private val _baroPressure = MutableLiveData<Float>()
    val baroPressure: LiveData<Float> = _baroPressure
    private val _location = MutableLiveData<Location>()
    val location: LiveData<Location> = _location
    var usaBaro : Boolean = false
    var numSat : Int = 0
    // Lista privata che contiene l'intera traccia.
    val trackPointsList = mutableListOf<GeoPoint>()
    // LiveData che espone la lista COMPLETA.
    // Utile per ridisegnare la traccia dopo una rotazione dello schermo.
    private val _trackPoints = MutableLiveData<List<GeoPoint>>()
    val trackPoints: LiveData<List<GeoPoint>> = _trackPoints
    // LiveData che emette SOLO l'ultimo punto aggiunto.
    // L'interfaccia utente osserverà questo per aggiornamenti efficienti in tempo reale.
    private val _newTrackPoint = MutableLiveData<GeoPoint>()
    val newTrackPoint: LiveData<GeoPoint> = _newTrackPoint

    // Questo metodo ora è molto più efficiente.
    fun addTrackPoint(location: Location) {
        _location.postValue(location)
        val newPoint = GeoPoint(location.latitude, location.longitude, location.altitude)
        // 1. Aggiungi il punto alla nostra lista interna.
        trackPointsList.add(newPoint)
        // 2. Notifica gli observer che c'è un SOLO nuovo punto.
        _newTrackPoint.postValue(newPoint)
    }

    fun clearTrack() {
        trackPointsList.clear()
        // Quando puliamo, notifichiamo che la lista completa è ora vuota.
        _trackPoints.postValue(emptyList())
    }

    // Metodo per richiedere la traccia completa quando necessario (es. rotazione schermo)
    fun requestFullTrack() {
        _trackPoints.postValue(trackPointsList.toList())
    }

    fun getFullTrackSnapshot(): List<GeoPoint> {
        // Usiamo 'toList()' per creare una copia immutabile, garantendo
        // che non ci siano problemi di modifica concorrente.
        return trackPointsList.toList()
    }

    fun updateGpsStatus(newStatus: String) {
        if (_gpsStatus.value != newStatus) {
            _gpsStatus.postValue(newStatus)
            Log.d("LocationRepository", "GpsStatus LiveData new value posted: $newStatus")
        }
    }

    fun updateMslAltitude(altitude: Double) {
        if (_mslAltitude.value != altitude) {
            _mslAltitude.postValue(altitude)
        }
    }

    fun updateVelocita(velocita: Int) {
        if (_velocita.value != velocita) {
            _velocita.postValue(velocita)
        }
    }

    fun updateBaroPressure(pressure: Float) {
        if (_baroPressure.value != pressure) {
            _baroPressure.postValue(pressure)
        }
    }
}