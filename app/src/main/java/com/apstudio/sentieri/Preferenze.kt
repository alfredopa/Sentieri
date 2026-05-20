package com.apstudio.sentieri

import android.content.Context
import android.content.SharedPreferences
import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.edit
import androidx.fragment.app.activityViewModels
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import com.apstudio.sentieri.db.SentieriDB
import com.apstudio.sentieri.db.SentieriRepo
import java.io.File

class Preferenze : PreferenceFragmentCompat() {
    private lateinit var preferenze: SharedPreferences
    private lateinit var sensorManager: SensorManager

    private val viewModel: SentieriViewModel by activityViewModels {
        val application = requireActivity().application
        val database = SentieriDB.getInstance(application)
        val repository = SentieriRepo(
            sentieriDao = database.sentieriDao(),
            trackDao = database.trackDao(),
            poiDao = database.poiDao(),
            fotoPoiDao = database.fotoPoiDao()
        )
        SentieriFactory(repository, application)
    }

    private var downloadDialog: AlertDialog? = null
    private var progressBar: ProgressBar? = null
    private var progressText: TextView? = null

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        preferenze = PreferenceManager.getDefaultSharedPreferences(requireContext())

        sensorManager = requireActivity().getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val hasPressureSensor = sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE) != null
        preferenze.edit { putBoolean("haBaro", hasPressureSensor) }
        if (hasPressureSensor && !preferenze.contains("setBaro")) {
            preferenze.edit { putBoolean("setBaro", true) }
        }

        setPreferencesFromResource(R.xml.preferenze, rootKey)

        val themePreference = findPreference<ListPreference>("seleziona_tema_mapsforge")
        themePreference?.let { populateThemePreference(it) }

        val ftpButton: Preference? = findPreference("download_ftp_button")
        ftpButton?.setOnPreferenceClickListener {
            viewModel.listDirectory()
            true
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        observeDownloadStatus()
        observeFtpFileList()
    }

    override fun onResume() {
        super.onResume()
        // Controllo di sicurezza al risveglio: se il download è finito nel frattempo, chiudi il dialogo
        if (viewModel.isDownloading.value == false && downloadDialog != null) {
            Log.d("Preferenze", "Chiusura dialogo residuo in onResume")
            downloadDialog?.dismiss()
            downloadDialog = null
            progressBar = null
            progressText = null
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
                themeBaseDir.listFiles { dir, name -> File(dir, name).isDirectory }?.forEach { themeFolder ->
                    val themeFiles = themeFolder.listFiles { _, name -> name.endsWith(".xml", ignoreCase = true) }
                    if (!themeFiles.isNullOrEmpty()) {
                        themeFiles.forEach { themeFile ->
                            entries.add("${themeFolder.name} / ${themeFile.nameWithoutExtension}")
                            entryValues.add(themeFile.absolutePath)
                        }
                    }
                }
            }
        }
        preference.entries = entries.toTypedArray()
        preference.entryValues = entryValues.toTypedArray()
    }

    private fun observeDownloadStatus() {
        // Osserva se il download è in corso
        viewModel.isDownloading.observe(viewLifecycleOwner) { isDownloading ->
            if (isDownloading == true) {
                if (downloadDialog == null) showDownloadDialog()
            } else {
                downloadDialog?.dismiss()
                downloadDialog = null
                progressBar = null
                progressText = null
            }
        }

        // Osserva il progresso
        viewModel.downloadProgress.observe(viewLifecycleOwner) { progress ->
            Log.d("Preferenze", "Observer progresso: $progress")
            if (downloadDialog?.isShowing == true) {
                if (progressBar == null || progressText == null) {
                    progressBar = downloadDialog?.findViewById(R.id.download_progress_bar)
                    progressText = downloadDialog?.findViewById(R.id.download_progress_text)
                }
                
                if (progress != null && progress >= 0) {
                    progressBar?.isIndeterminate = false
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        progressBar?.setProgress(progress, true)
                    } else {
                        progressBar?.progress = progress
                    }
                    progressText?.text = "$progress%"
                } else if (progress == -2) {
                    progressBar?.isIndeterminate = true
                    progressText?.text = "Decompressione files..."
                } else {
                    progressBar?.isIndeterminate = true
                    progressText?.text = "Download..."
                }
            }
        }

        // Osserva i messaggi finali
        viewModel.ftpDownloadStatus.observe(viewLifecycleOwner) { event ->
            event.getContentIfNotHandled()?.let { message ->
                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun observeFtpFileList() {
        viewModel.ftpFileList.observe(viewLifecycleOwner) { event ->
            event.getContentIfNotHandled()?.let { fileList ->
                if (fileList.isNotEmpty()) {
                    mostraDialogoLista(requireContext(), fileList)
                }
            }
        }
    }

    private fun showDownloadDialog() {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_download_progress, null)
        progressBar = dialogView.findViewById(R.id.download_progress_bar)
        progressText = dialogView.findViewById(R.id.download_progress_text)

        downloadDialog = AlertDialog.Builder(requireContext())
            .setTitle("Download Mappa")
            .setView(dialogView)
            .setCancelable(false)
            .create()

        downloadDialog?.show()
    }

    fun mostraDialogoLista(context: Context, elementi: List<String>) {
        val builder = AlertDialog.Builder(context)
        builder.setTitle("Seleziona file")
        builder.setItems(elementi.toTypedArray()) { dialog, quale ->
            val scelta = elementi[quale]
            viewModel.downloadFileFromFtp(scelta)
            dialog.dismiss()
        }
        builder.create().show()
    }
}
