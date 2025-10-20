package com.apstudio.sentieri.db


import android.location.Location
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import org.osmdroid.util.GeoPoint

// Usa un "object" per creare un Singleton in modo semplice e sicuro
object LocationRepository {
    private val _gpsStatus = MutableLiveData("stopped")
    val gpsStatus: LiveData<String> = _gpsStatus
    private val _isTraceRecordingActive = MutableLiveData(false)
    val isTraceRecordingActive: LiveData<Boolean> = _isTraceRecordingActive
    private val _mslAltitude = MutableLiveData(0.0)
    val mslAltitude : LiveData<Double> = _mslAltitude
    var usaBaro : Boolean = false
    private val _velocita = MutableLiveData(0.0)
    val velocita: LiveData<Double> = _velocita
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
        val oldValue = _gpsStatus.value
        // Log dettagliato per capire chi sta chiamando e lo stato degli observer
        Log.d("GpsViewModel_Debug", "Attempting to updateGpsStatus to: $newStatus. Previous value: $oldValue. HasActiveObservers: ${_gpsStatus.hasActiveObservers()}. Thread: ${Thread.currentThread().name}")

        if (oldValue == newStatus) {
            Log.d("GpsViewModel_Debug", "Status is already $newStatus. Not re-emitting.")
            // Considera se vuoi forzare una ri-emissione in alcuni casi specifici per il primo avvio,
            // ma la modifica in onCreateOptionsMenu dovrebbe gestire meglio l'aggiornamento UI iniziale.
            return
        }
        // Quando chiami da un ViewModel (che di solito è manipolato dal main thread o da coroutines nel viewModelScope),
        // setValue è appropriato. LiveData si occuperà di notificare gli observer sul main thread.
        _gpsStatus.value = newStatus // Usa setValue se le modifiche avvengono prevalentemente dal main thread o viewModelScope
        Log.d("GpsViewModel_Debug", "GpsStatus LiveData new value set to: $newStatus")
    }

    fun updateMslAltitude(altitude: Double) {
        _mslAltitude.value = altitude
        //Log.d("GpsView", "gps status $altitude")
    }

    fun updateVelocita(velocita: Double) {
        _velocita.value = velocita
        //Log.d("GpsView", "gps status $velocita")
    }
}