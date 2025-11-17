package com.apstudio.sentieri

import android.app.Activity
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
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import java.io.File
import androidx.core.content.edit
import androidx.preference.ListPreference
import androidx.preference.Preference
import com.apstudio.sentieri.MapUtils.getFileNameFromUri

class Preferenze : PreferenceFragmentCompat() {
    private lateinit var preferenze: SharedPreferences
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

        setPreferencesFromResource(R.xml.preferenze, rootKey)

        // Trova la ListPreference per i temi
        val themePreference = findPreference<ListPreference>("seleziona_tema_mapsforge")
        if (themePreference != null) {
            // Popola la preferenza con i temi trovati
            populateThemePreference(themePreference)
        }
        /*findPreference<Preference>("seleziona_tema_mapsforge")?.setOnPreferenceClickListener {
            // Usa ACTION_OPEN_DOCUMENT_TREE per selezionare una cartella
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                //type = "application/octet-stream"
                type = "text/xml" // o "application/xml"
                addCategory(Intent.CATEGORY_OPENABLE)
            }
            themeFolderSelectorLauncher.launch(intent)
            true
        }*/
    }

    private fun populateThemePreference(preference: ListPreference) {
        val entries = mutableListOf<CharSequence>()
        val entryValues = mutableListOf<CharSequence>()

        entries.add("Default (OsmaRender)")
        entryValues.add("OSMARENDER")

        val mediaDirs = requireContext().externalMediaDirs
        if (mediaDirs.isNotEmpty()) {
            val themeBaseDir = File(mediaDirs[0], "Mappe")

            if (themeBaseDir.exists() && themeBaseDir.isDirectory) {
                // Scansiona tutte le sottocartelle in /Mappe/
                themeBaseDir.listFiles { dir, name ->
                    File(dir, name).isDirectory
                }?.forEach { themeFolder ->

                    // --- INIZIO MODIFICA ---
                    // 1. Trova TUTTI i file .xml, non solo il primo.
                    val themeFiles = themeFolder.listFiles { _, name ->
                        name.endsWith(".xml", ignoreCase = true)
                    }

                    // 2. Se la lista di file .xml non è vuota, itera su ciascuno di essi.
                    if (!themeFiles.isNullOrEmpty()) {
                        themeFiles.forEach { themeFile ->
                            // 3. Crea una voce per OGNI file .xml trovato.
                            // Per il nome visualizzato, usiamo "NomeCartella / NomeFile"
                            // per evitare ambiguità se più cartelle hanno file con lo stesso nome.
                            // Es: "MioTema / tema.xml"
                            val entryName = "${themeFolder.name} / ${themeFile.nameWithoutExtension}"
                            entries.add(entryName)

                            // Il valore salvato sarà sempre il percorso assoluto e univoco del file.
                            entryValues.add(themeFile.absolutePath)
                        }
                    }
                    // --- FINE MODIFICA ---
                }
            }
        }

        preference.entries = entries.toTypedArray()
        preference.entryValues = entryValues.toTypedArray()
    }


    // Aggiungi un launcher per il selettore di file
    /*private val themeFolderSelectorLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { themeUri ->
                val fileTheme = getFileNameFromUri(requireContext(), themeUri)
                // Salva l'URI del file nelle preferenze
                preferenze.edit { putString("tema_mapsforge_folder_uri", themeUri.toString()) }
                Toast.makeText(requireContext(), "Tema selezionato $fileTheme", Toast.LENGTH_SHORT).show()
            }
        }
    }*/

}