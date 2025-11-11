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
    // 'haBaro' può essere una costante o una variabile locale,
    // non è necessario che sia una property della classe.

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        preferenze = PreferenceManager.getDefaultSharedPreferences(requireContext())

        // TEST SENSORE BAROMETRO
        sensorManager = requireActivity().getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val hasPressureSensor = sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE) != null

        // Scrivi il valore di 'haBaro' in modo che il resto dell'app sappia se il sensore esiste.
        // Questa operazione è sicura da fare ogni volta.
        preferenze.edit { putBoolean("haBaro", hasPressureSensor) }

        if (hasPressureSensor) {
            // --- LOGICA CORRETTA ---
            // Imposta 'setBaro' a true SOLO se non è mai stato impostato prima.
            // Se la chiave 'setBaro' non esiste, significa che è il primo avvio
            // con un barometro, quindi lo impostiamo come predefinito.
            if (!preferenze.contains("setBaro")) {
                preferenze.edit { putBoolean("setBaro", true) }
            }
        }
        // Non c'è bisogno di un 'else' qui, perché se non c'è il barometro,
        // lo switch 'setBaro' sarà comunque disabilitato grazie alla dipendenza in XML.

        setPreferencesFromResource(R.xml.preferenze, rootKey)
    }
}