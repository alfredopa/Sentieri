package com.apstudio.sentieri

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.hardware.Sensor
import android.hardware.SensorManager
import android.icu.text.CaseMap.Fold
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import java.io.File


class Preferenze : PreferenceFragmentCompat() {
    private lateinit var preferenze : SharedPreferences
    private lateinit var sensorManager: SensorManager
    private var haBaro = false
    //private var isMapOnline = true

    // all' installazione app deve settare le mappe online in quanto non dovrebbero essere sempre presenti quelle offline
    // e creare il percorso Sentieri da usare per mappe offline, foto e tracce

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        preferenze = PreferenceManager.getDefaultSharedPreferences(requireContext())
        //isMapOnline = preferenze.getBoolean("isMapOnline", true)
        // TEST SENSORE BAROMETRO
        sensorManager = requireActivity().getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val hasPressureSensor = sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE) != null
        if (hasPressureSensor) {
            haBaro = true
            preferenze.edit().putBoolean("haBaro", haBaro).apply()
            preferenze.edit().putBoolean("setBaro", true).apply()
        } else {
            preferenze.edit().putBoolean("haBaro", haBaro).apply()
        }
        //preferenze.edit().putBoolean("isMapOnline", isMapOnline).apply()*/

        setPreferencesFromResource(R.xml.preferenze, rootKey)
    }

}