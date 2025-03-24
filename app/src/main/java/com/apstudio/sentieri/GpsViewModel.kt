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
    var is_Calibrato : Boolean = false
    val zeroMsl : Double = 0.0
    var mslAltitude : Double = zeroMsl
    var numSat : Int = 0
    // aggiungere tutti i livedata utili
    //private val _numSat = MutableLiveData(0)
    //var numSat: LiveData<Int> = _numSat

    fun updateGpsStatus(status: String) {
        _gpsStatus.value = status
        //Log.d("GpsView", "gps status $status")
    }

}