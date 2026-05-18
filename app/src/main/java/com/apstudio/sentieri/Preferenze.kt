package com.apstudio.sentieri

import android.content.Context
import android.content.SharedPreferences
import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
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
        //  popola lista mappe da download FTP
        // Rimosso caricamento iniziale: listDirectory() sarà chiamato solo al click sul bottone.
        // viewModel.listDirectory() // Rimosso
        // Trova la ListPreference per i temi
        val themePreference = findPreference<ListPreference>("seleziona_tema_mapsforge")
        themePreference?.let {
            populateThemePreference(it)
        }

        // 1. Trova la preferenza-bottone usando la sua key.
        val ftpButton: Preference? = findPreference("download_ftp_button")

        // 2. Imposta un listener per il click.
        ftpButton?.setOnPreferenceClickListener {
            // Avvia il caricamento della lista, e l'Observer mostrerà il dialogo
            // solo quando la lista è pronta.
            viewModel.listDirectory()
            // Restituisci 'true' per indicare che hai gestito l'evento di click.
            true
        }
        // Observe del download status
        observeDownloadStatus()
        observeFtpFileList()
    }

    override fun onDestroy() {
        super.onDestroy()
        downloadReceiver?.let {
            try { requireContext().unregisterReceiver(it) } catch (e: Exception) {}
        }
    }

    private var downloadReceiver: android.content.BroadcastReceiver? = null

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
        // Registra il BroadcastReceiver per aggiornamenti dal DownloadService
        val filter = android.content.IntentFilter().apply {
            addAction(DownloadService.ACTION_DOWNLOAD_STARTED)
            addAction(DownloadService.ACTION_PROGRESS_UPDATE)
            addAction(DownloadService.ACTION_DOWNLOAD_COMPLETE)
        }
        
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: android.content.Intent?) {
                when (intent?.action) {
                    DownloadService.ACTION_DOWNLOAD_STARTED -> {
                        if (downloadDialog == null) showDownloadDialog()
                    }
                    DownloadService.ACTION_PROGRESS_UPDATE -> {
                        val progress = intent.getIntExtra(DownloadService.EXTRA_PROGRESS, 0)
                        if (downloadDialog == null) showDownloadDialog()
                        downloadDialog?.findViewById<ProgressBar>(R.id.download_progress_bar)?.progress = progress
                        downloadDialog?.findViewById<TextView>(R.id.download_progress_text)?.text = "$progress%"
                    }
                    DownloadService.ACTION_DOWNLOAD_COMPLETE -> {
                        val message = intent.getStringExtra(DownloadService.EXTRA_MESSAGE) ?: ""
                        downloadDialog?.dismiss()
                        downloadDialog = null
                        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
        this.downloadReceiver = receiver
        
        // In Android 13+ è richiesto RECEIVER_NOT_EXPORTED per receiver interni
        if (Build.VERSION.SDK_INT >= 33) {
            requireContext().registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            requireContext().registerReceiver(receiver, filter)
        }
        
        Log.d("Preferenze", "Download BroadcastReceiver registrato correttamente")

        viewModel.ftpDownloadStatus.observe(this) { event ->
            event.getContentIfNotHandled()?.let { message ->
                // Mostra il messaggio finale solo se il dialogo non è attivo
                if (downloadDialog == null) {
                    Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun observeFtpFileList() {
        viewModel.ftpFileList.observe(this) { fileList ->
            // Quando la lista viene aggiornata (sia vuota che piena),
            // controlla se è il momento di mostrare il dialogo.

            // Per evitare di mostrare il dialogo durante un download attivo, controlliamo isDownloading.
            if (viewModel.isDownloading.value != true) {
                if (fileList.isNotEmpty()) {
                    // Se la lista è pronta e non stiamo scaricando, mostra il dialogo
                    mostraDialogoLista(requireContext())
                } else {
                    // Se la lista è vuota, potresti voler mostrare un messaggio
                    // (Questo è già gestito dal messaggio di ftpDownloadStatus nel observeDownloadStatus)
                }
            }

            // Nascondi l'indicatore di caricamento se la lista è stata ricevuta (sia piena che vuota)
            setLoadingIndicator(false)
        }
    }

    private fun setLoadingIndicator(isLoading: Boolean) {
        if (isLoading) {
            // Mostra un messaggio di caricamento quando listDirectory() viene chiamato
            Toast.makeText(requireContext(), "Caricamento lista file FTP...", Toast.LENGTH_SHORT).show()
        }
        // Nota: Se isLoading è false, non facciamo nulla qui, perché l'aggiornamento finale
        // (successo/fallimento della lista) è gestito tramite ftpDownloadStatus
        // o quando ftpFileList si popola e mostra il dialogo.
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

    fun mostraDialogoLista(context: Context) {
        // 1. La serie di stringhe da visualizzare
        val elementi = viewModel.ftpFileList.value
        Log.d("preferenze", "elementi: $elementi")

        // 2. Creazione del Builder
        val builder = AlertDialog.Builder(context)
        builder.setTitle("Seleziona file da scaricare")

        // 3. Impostazione degli elementi e del click listener
        builder.setItems(elementi?.toTypedArray()) { dialog, quale ->
            // 'quale' è l'indice della stringa cliccata (0, 1, 2...)
            val scelta = elementi!!.get(quale)
            Toast.makeText(context, "Hai scelto: $scelta", Toast.LENGTH_SHORT).show()
            Log.d("preferenze", "scelta: $scelta")
            viewModel.downloadFileFromFtp(scelta)
            dialog.dismiss() // Chiude il dialogo dopo la selezione
        }

        // 4. Mostra il dialogo
        val dialog = builder.create()
        dialog.show()
    }

}