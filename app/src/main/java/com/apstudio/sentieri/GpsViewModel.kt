package com.apstudio.sentieri

import android.location.GnssStatus
import android.location.GpsStatus
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class GpsViewModel  : ViewModel(){
    private val _gpsStatus = MutableLiveData("started")
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

    fun updateGpsStatus(status: String) {
        _gpsStatus.value = status
        //Log.d("GpsView", "gps status $status")
    }

    fun updateVelocita(velocita: Double) {
        _velocita.value = velocita
        //Log.d("GpsView", "gps status $velocita")
    }
    fun updateRecordingStatus(isRecording: Boolean) {
        _isTraceRecordingActive.value = !isRecording
    }

}