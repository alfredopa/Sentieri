package com.apstudio.sentieri

import android.content.Context
import android.content.SharedPreferences
import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.Bundle
import androidx.core.content.edit
import androidx.preference.ListPreference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import java.io.File

class Preferenze : PreferenceFragmentCompat() {
    private lateinit var preferenze: SharedPreferences
    private lateinit var sensorManager: SensorManager

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        preferenze = PreferenceManager.getDefaultSharedPreferences(requireContext())

        // TEST SENSORE BAROMETRO
        sensorManager = requireActivity().getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val hasPressureSensor = sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE) != null

        preferenze.edit { putBoolean("haBaro", hasPressureSensor) }

        if (hasPressureSensor) {
            if (!preferenze.contains("setBaro")) {
                preferenze.edit { putBoolean("setBaro", true) }
            }
        }

        setPreferencesFromResource(R.xml.preferenze, rootKey)

        // Trova la ListPreference per i temi
        val themePreference = findPreference<ListPreference>("seleziona_tema_mapsforge")
        themePreference?.let {
            populateThemePreference(it)
        }
    }

    private fun populateThemePreference(preference: ListPreference) {
        val entries = mutableListOf<CharSequence>()
        val entryValues = mutableListOf<CharSequence>()

        entries.add("Default (OsmaRender)")
        entryValues.add("OSMARENDER")
        @Suppress("DEPRECATION")
        val mediaDirs = requireContext().externalMediaDirs
        if (mediaDirs.isNotEmpty()) {
            val themeBaseDir = File(mediaDirs[0], "Mappe")

            if (themeBaseDir.exists() && themeBaseDir.isDirectory) {
                themeBaseDir.listFiles { dir, name ->
                    File(dir, name).isDirectory
                }?.forEach { themeFolder ->
                    val themeFiles = themeFolder.listFiles { _, name ->
                        name.endsWith(".xml", ignoreCase = true)
                    }
                    if (!themeFiles.isNullOrEmpty()) {
                        themeFiles.forEach { themeFile ->
                            val entryName = "${themeFolder.name} / ${themeFile.nameWithoutExtension}"
                            entries.add(entryName)
                            entryValues.add(themeFile.absolutePath)
                        }
                    }
                }
            }
        }
        preference.entries = entries.toTypedArray()
        preference.entryValues = entryValues.toTypedArray()
    }
}
