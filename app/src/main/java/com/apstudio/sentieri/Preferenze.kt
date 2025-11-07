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
import androidx.core.content.edit

class Preferenze : PreferenceFragmentCompat() {
    private lateinit var preferenze : SharedPreferences
    private lateinit var sensorManager: SensorManager
    private var haBaro = false

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        preferenze = PreferenceManager.getDefaultSharedPreferences(requireContext())
        // TEST SENSORE BAROMETRO
        sensorManager = requireActivity().getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val hasPressureSensor = sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE) != null
        if (hasPressureSensor) {
            haBaro = true
            preferenze.edit { putBoolean("haBaro", haBaro) }
            preferenze.edit { putBoolean("setBaro", true) }
        } else {
            preferenze.edit { putBoolean("haBaro", haBaro) }
        }

        setPreferencesFromResource(R.xml.preferenze, rootKey)
    }

}