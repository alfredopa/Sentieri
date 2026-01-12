package com.apstudio.sentieri

import android.content.Context
import android.content.SharedPreferences
import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.Bundle
import android.widget.Toast
import androidx.core.content.edit
import androidx.lifecycle.ViewModelProvider
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import android.view.LayoutInflater
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.observe
import com.apstudio.sentieri.db.SentieriDB
import com.apstudio.sentieri.db.SentieriRepo
import java.io.File

class Preferenze : PreferenceFragmentCompat() {
    private lateinit var preferenze: SharedPreferences
    private lateinit var sensorManager: SensorManager
    // Ottieni il ViewModel condiviso
    private val viewModel: SentieriViewModel by activityViewModels {
        val application = requireActivity().application
        // 1. Ottieni una singola istanza del database
        val database = SentieriDB.getInstance(application)
        // 2. Crea il repository passando TUTTI i DAO richiesti
        val repository = SentieriRepo(
            sentieriDao = database.sentieriDao(),
            trackDao = database.trackDao(),
            poiDao = database.poiDao(),
            fotoPoiDao = database.fotoPoiDao()
        )
        // 3. Crea la factory con il repository e l'applicazione
        SentieriFactory(repository, application)
    }
    private var downloadDialog: AlertDialog? = null

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

        // 1. Trova la preferenza-bottone usando la sua key.
        val ftpButton: Preference? = findPreference("download_ftp_button")

        // 2. Imposta un listener per il click.
        ftpButton?.setOnPreferenceClickListener {
            // 3. Esegui la tua funzione qui.
            scaricaFileDaFtp() // Chiamiamo la funzione che vogliamo eseguire
            // Restituisci 'true' per indicare che hai gestito l'evento di click.
            true
        }
        // Observe del download status
        observeDownloadStatus()
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

    private fun scaricaFileDaFtp() {
        //Toast.makeText(requireContext(), "Avvio del download FTP...", Toast.LENGTH_SHORT).show()
        //viewModel.scaricaFileDaFtp()
        Toast.makeText(requireContext(), "Avvio del download Remoto...", Toast.LENGTH_SHORT).show()
        viewModel.scaricaFileDaDrive()
    }

    private fun observeDownloadStatus() {
        viewModel.isDownloading.observe(this) { isDownloading ->
            if (isDownloading) {
                // Mostra il dialogo di download
                showDownloadDialog()
            } else {
                // Nascondi il dialogo
                downloadDialog?.dismiss()
                downloadDialog = null
            }
        }

        viewModel.downloadProgress.observe(this) { progress ->
            // Aggiorna la progress bar all'interno del dialogo
            downloadDialog?.findViewById<ProgressBar>(R.id.download_progress_bar)?.progress = progress
            downloadDialog?.findViewById<TextView>(R.id.download_progress_text)?.text = "$progress%"
        }

        viewModel.ftpDownloadStatus.observe(this) { event ->
            event.getContentIfNotHandled()?.let { message ->
                // Mostra il messaggio finale solo se il dialogo non è attivo
                if (downloadDialog == null) {
                    Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun showDownloadDialog() {
        // Infla un layout personalizzato per il dialogo
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_download_progress, null)

        // Crea e mostra l'AlertDialog
        downloadDialog = AlertDialog.Builder(requireContext())
            .setTitle("Download Mappa")
            .setView(dialogView)
            .setCancelable(false) // Impedisce all'utente di chiuderlo
            .create()

        downloadDialog?.show()
    }

}
