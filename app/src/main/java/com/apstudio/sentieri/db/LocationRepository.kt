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
    var usaBaro : Boolean = false
    private val _velocita = MutableLiveData(0)
    val velocita: LiveData<Int> = _velocita
    var numSat : Int = 0

    private val _trackPoints = MutableLiveData<List<GeoPoint>>(emptyList())
    val trackPoints: LiveData<List<GeoPoint>> = _trackPoints

    // Questo è il metodo che il LocationService chiamerà
    fun addTrackPoint(location: Location) {
        val newPoint = GeoPoint(location.latitude, location.longitude, location.altitude)
        val currentList = _trackPoints.value ?: emptyList()
        // Usa postValue perché questo metodo sarà chiamato da un background thread nel service
        _trackPoints.postValue(currentList + newPoint)
    }

    fun clearTrack() {
        _trackPoints.postValue(emptyList())
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
}