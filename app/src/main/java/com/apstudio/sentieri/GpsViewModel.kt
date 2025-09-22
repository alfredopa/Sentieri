package com.apstudio.sentieri

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class GpsViewModel  : ViewModel(){
    private val _gpsStatus = MutableLiveData("stopped")
    val gpsStatus: LiveData<String> = _gpsStatus
    private val _isTraceRecordingActive = MutableLiveData(false)
    val isTraceRecordingActive: LiveData<Boolean> = _isTraceRecordingActive
    private val _mslAltitude = MutableLiveData(0.0)
    val mslAltitude : LiveData<Double> = _mslAltitude
    var usaBaro : Boolean = false
    var numSat : Int = 0
    private val _velocita = MutableLiveData(0.0)
    val velocita: LiveData<Double> = _velocita
    // aggiungere tutti i livedata utili
    //private val _numSat = MutableLiveData(0)
    //var numSat: LiveData<Int> = _numSat

    fun updateMslAltitude(altitude: Double) {
        _mslAltitude.value = altitude
        //Log.d("GpsView", "gps status $altitude")
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

    fun updateVelocita(velocita: Double) {
        _velocita.value = velocita
        //Log.d("GpsView", "gps status $velocita")
    }
    fun updateRecordingStatus(isRecording: Boolean) {
        _isTraceRecordingActive.value = !isRecording
    }

    // In GpsViewModel.kt
    fun testForceGpsStatus(newStatus: String) {
        Log.d("GpsViewModel_Debug", "testForceGpsStatus chiamato con: $newStatus. Valore precedente: ${_gpsStatus.value}")
        _gpsStatus.value = newStatus
    }

}