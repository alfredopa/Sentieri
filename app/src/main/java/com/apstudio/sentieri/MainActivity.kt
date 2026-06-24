package com.apstudio.sentieri

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.TextView
import android.widget.Toast
import android.window.OnBackInvokedDispatcher
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.activity.viewModels
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.content.getSystemService
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import androidx.preference.PreferenceManager.getDefaultSharedPreferences
import com.apstudio.sentieri.db.SentieriDB
import com.apstudio.sentieri.db.SentieriRepo
import com.google.android.material.navigation.NavigationView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.mapsforge.map.android.graphics.AndroidGraphicFactory
import org.osmdroid.config.Configuration
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import kotlin.getValue
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.net.toUri

private const val LAST_VERSION_CODE = "last_version_code"
private const val PROMINENT_DISCLOSURE_SHOWN = "prominent_disclosure_shown"

class MainActivity :
    AppCompatActivity() {
    private val viewModel: SentieriViewModel by viewModels(
        factoryProducer = {
            val application = application
            val database = SentieriDB.getInstance(application)
            val repository = SentieriRepo(
                sentieriDao = database.sentieriDao(),
                trackDao = database.trackDao(),
                poiDao = database.poiDao(),
                fotoPoiDao = database.fotoPoiDao()
            )
            SentieriFactory(repository, application)
        }
    )
    private lateinit var navController: NavController
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var appBarConfiguration: AppBarConfiguration // For handling the Up button and drawer
    private lateinit var preferenze: SharedPreferences
    private var lastVersionCode = -1L // versione memorizzata nel file preferences
    private var currentVersionCode = -1L // versione corrente dell'app
    private var haBaro = false
    private val PERMISSION_ALL = 123

    private val PERMISSIONS = buildList {
        add(Manifest.permission.ACCESS_COARSE_LOCATION)
        add(Manifest.permission.ACCESS_FINE_LOCATION)
        add(Manifest.permission.INTERNET)
        add(Manifest.permission.ACCESS_NETWORK_STATE)
        add(Manifest.permission.CAMERA)
        // permesso da Android 13
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            add(Manifest.permission.FOREGROUND_SERVICE_LOCATION)
        } else {
            add(Manifest.permission.FOREGROUND_SERVICE)
        }
        add(Manifest.permission.RECORD_AUDIO)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        //SimpleFileLogger.log("Mainactivity", "MainActivity onCreate")
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        applicationContext as AppSentieri
        // status bar caratteri neri
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = true
        // inizializza le preferenze
        initPreferenze()
        // Controlla se è necessario l'aggiornamento
        if (currentVersionCode > lastVersionCode) {
            // Lancia una coroutine per fare il lavoro in background
            lifecycleScope.launch {
                // Puoi mostrare un indicatore di caricamento qui

                withContext(Dispatchers.IO) {
                    // Esegui l'operazione di I/O in background
                    verificaCartelleDB()
                }

                // Torna sul thread principale. Nascondi l'indicatore di caricamento
                // e aggiorna le preferenze
                preferenze.edit { putLong(LAST_VERSION_CODE, currentVersionCode) }

                // Continua con l'inizializzazione dell'UI che dipende da questi file
                initAppAndPermissions()
                // Gestisci l'intent DOPO l'inizializzazione di navController
                handleIntent(intent)
            }
        } else {
            // Nessun aggiornamento necessario, procedi subito
            verificaCartelleDB() // In questo caso sarà velocissimo perché i file esistono già
            initAppAndPermissions()
            // Gestisci l'intent DOPO l'inizializzazione di navController
            handleIntent(intent)
        }

        // gestione evento indietro
        setupBackPressHandling()

        // Registrazione del receiver per il download
        val filter = IntentFilter().apply {
            addAction(DownloadService.ACTION_DOWNLOAD_STARTED)
            addAction(DownloadService.ACTION_PROGRESS_UPDATE)
            addAction(DownloadService.ACTION_DOWNLOAD_COMPLETE)
        }

        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(downloadReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(downloadReceiver, filter)
        }

        //Log.d("Mappa", "MainActivity onCreate: $intent")
    }

    private fun initAppAndPermissions() {
        initApp()
        // Ritarda l'esecuzione per assicurarsi che l'UI sia caricata e visibile
        window.decorView.post {
            if (!preferenze.getBoolean(PROMINENT_DISCLOSURE_SHOWN, false)) {
                showProminentDisclosure()
            } else {
                checkAndRequestPermissions()
            }
        }
    }

    private fun showProminentDisclosure() {
        AlertDialog.Builder(this, R.style.AlertDialogCustom)
            .setTitle(getString(R.string.app_name))
            .setIcon(R.drawable.quattromori)
            .setMessage("Sentieri utilizza la tua posizione per mostrarti la mappa e registrare i tuoi percorsi. Per funzionare correttamente a schermo spento, l'app richiede l'accesso alla posizione in background e l'esclusione dalle ottimizzazioni batteria.")
            .setPositiveButton("Ho capito / Continua") { _, _ ->
                preferenze.edit { putBoolean(PROMINENT_DISCLOSURE_SHOWN, true) }
                checkAndRequestPermissions()
            }
            .setCancelable(false)
            .show()
    }

    private fun initApp() {
        setContentView(R.layout.activity_main)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        this.supportActionBar?.title = "Mappa"
        drawerLayout = findViewById(R.id.drawerLayout)
        val navigationView = findViewById<NavigationView>(R.id.nav_View)
        // Accedi all'header view
        val headerView: View = navigationView.getHeaderView(0) // Di solito l'header è all'indice 0
        // Trova la TextView nell'header
        // Sostituisci con l'ID corretto della tua TextView in nav_header.xml
        val textViewInHeader: TextView? = headerView.findViewById(R.id.textView1)
        // Oppure se usi l'ID che ho suggerito:
        // val textViewInHeader: TextView? = headerView.findViewById<TextView>(R.id.textViewNameToUpdate)
        textViewInHeader?.text = ("Sentieri ${BuildConfig.VERSION_NAME}")
        val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.navController
        // Make sure actions in the ActionBar get propagated to the NavController
        // Connect the drawer layout to the navigation graph
        appBarConfiguration = AppBarConfiguration(navController.graph, drawerLayout)
        setupActionBarWithNavController(navController, appBarConfiguration)
        navigationView.setupWithNavController(navController)
        navController.addOnDestinationChangedListener { _, destination, _ ->
            if (destination.id in (setOf(R.id.mappaFragment, R.id.gpkgLayer))) {
                toolbar.visibility = View.GONE
            } else {
                toolbar.visibility = View.VISIBLE
            }
        }

        // Le preferenze vanno caricate dal main e sono indispensabili per il
        // corretto caricamento delle mappe
        AndroidGraphicFactory.createInstance(this)
        Configuration.getInstance().load(
            applicationContext,
            getDefaultSharedPreferences(applicationContext)
        )

        // disabilitazione della voce barometro se non presente
        if (!haBaro) {
            val menuItem = navigationView.menu.findItem(R.id.barometro)
            menuItem.isVisible = false
        }

    }

    private fun verificaCartelleDB() {
        // verifica se esiste cartella Sentieri nello spazio file applicazione ATTENZIONE solo la cartella media visibile da file picker
        //val baseDir: File? = getAppSpecificExternalDirectory(applicationContext) // restituisce cartella data anzichè media
        val mediaStorageDir = this.externalMediaDirs
        val baseDir: File? = mediaStorageDir[0]
        val sentieriFolder = File(baseDir, "/Mappe")
        if (!sentieriFolder.exists()) {
            // crea cartelle Sentieri nello spazio file applicazione
            val percorso: Set<String> = setOf("/Mappe", "/Tracce")
            for (element in percorso) {
                val folderPath = baseDir?.absolutePath + element
                if (!creaCartelle(folderPath))
                    Toast.makeText(
                        this,
                        "Errore nella creazione della cartella $folderPath",
                        Toast.LENGTH_LONG
                    ).show()
            }
        }
        // controllo esistenza file db Geopackage
        val filesToInitialize = mapOf(
            "Layers.gpkg" to "databases",
            "Toponimi.gpkg" to "databases",
            "db_schema_config.xml" to "files" // 'null' indica che copyToInternalStorage userà this.filesDir
        )
        for ((fileName, subDir) in filesToInitialize) {
            val copiedFile = copyToInternalStorage(fileName, subDir)
            if (!copiedFile.exists()) {
                AlertDialog.Builder(this)
                    .setTitle("Errore di Inizializzazione")
                    .setMessage("File essenziale non trovato o non è stato possibile copiarlo:\n${copiedFile.name}\nNel percorso: ${copiedFile.absolutePath}")
                    .setPositiveButton("OK") { dialog, _ ->
                        dialog.dismiss()
                        // Potresti voler prendere ulteriori azioni qui, come chiudere l'activity
                        // se l'app non può funzionare senza questi file.
                        // E.g., finish()
                    }
                    .setCancelable(false) // Impedisce all'utente di chiudere il dialogo senza premere OK
                    .show()
                return // Esce dalla funzione verificaCartelleDB si un file cruciale manca
            }
        }
        // Tutti i file sono stati verificati e copiati con successo.
    }

    private fun creaCartelle(folderPath: String): Boolean {
        val sentieriFolder = File(folderPath)
        if (!sentieriFolder.exists()) {
            val isCreated = sentieriFolder.mkdirs()
            return isCreated
        } else {
            // La cartella esiste già
            return true
        }
    }

    // copia il file nella cartella dati interni data/data/packageName
    private fun copyToInternalStorage(databaseName: String, subDir: String? = null): File {
        val dataDir: File
        if (subDir == "databases") {
            dataDir = this.getDatabasePath(databaseName).parentFile!!
        } else {
            dataDir = this.filesDir
        }
        val dbFile = File(dataDir, databaseName)
        // il file non esiste oppure ha versone inferiore
        if (!dbFile.exists() || (currentVersionCode > lastVersionCode)) {
            try {
                val inputStream: InputStream = assets.open(databaseName)
                val outputStream: OutputStream = FileOutputStream(dbFile)
                val buffer = ByteArray(1024)
                var length: Int
                while (inputStream.read(buffer).also { length = it } > 0) {
                    outputStream.write(buffer, 0, length)
                }
                outputStream.flush()
                outputStream.close()
                inputStream.close()
            } catch (e: IOException) {
                Log.e("Geopackage", "Errore nella copia del file: ${e.message}")
            }
        }
        return dbFile
    }

    private fun initPreferenze() {
        // Gestione delle impostazioni default dopo installazione  crea il
        // file preferences.xml
        preferenze = getDefaultSharedPreferences(this)
        lastVersionCode = preferenze.getLong(LAST_VERSION_CODE, -1)
        currentVersionCode = getCurrentVersionCode()

        // TEST SENSORE BAROMETRO
        val sensorManager: SensorManager = this.getSystemService(SENSOR_SERVICE) as SensorManager
        if (sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE) != null) {
            haBaro = true
            preferenze.edit { putBoolean("haBaro", haBaro) }
            // --- MODIFICA CRUCIALE ---
            // Imposta "setBaro" a true SOLO se la preferenza non è mai stata impostata prima.
            // Questo fa esattamente quello che fa Preferenze.kt e risolve il conflitto.
            if (!preferenze.contains("setBaro")) {
                preferenze.edit { putBoolean("setBaro", true) }
            }
        } else {
            haBaro = false
            preferenze.edit { putBoolean("haBaro", haBaro) }
            // --- MODIFICA CRUCIALE ---
            // Se non c'è il barometro, assicurati che l'opzione sia sempre false.
            // Anche qui, è meglio farlo solo se la preferenza non esiste,
            // o semplicemente forzarlo, dato che senza sensore non ha senso.
            if (!preferenze.contains("setBaro")) {
                preferenze.edit { putBoolean("setBaro", false) }
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        return navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
    }

    private fun setupBackPressHandling() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Per API 33+ (Android 13 Tiramisu e successivi)
            // Assumendo che tu abbia android:enableOnBackInvokedCallback="true" nel Manifest
            onBackInvokedDispatcher.registerOnBackInvokedCallback(
                OnBackInvokedDispatcher.PRIORITY_DEFAULT
            ) {
                // Quando il sistema invoca questo callback, esegui la logica unificata
                handleCentralizedBackPressLogic()
            }
        } else {
            // Per API < 33
            val onBackPressedCallback =
                object : OnBackPressedCallback(true /* enabled by default */) {
                    override fun handleOnBackPressed() {
                        // Quando questo callback viene attivato (su API < 33),
                        // esegui la logica unificata.
                        // Se vuoi che il sistema gestisca il "back" dopo la tua logica
                        // (es. chiudere l'activity se non fai finishAffinity()),
                        // puoi disabilitare temporaneamente il callback e richiamare onBackPressed().
                        handleCentralizedBackPressLogic(
                            fallbackToSuper = {
                                isEnabled = false
                                onBackPressedDispatcher.onBackPressed() // Questa è la chiamata critica
                                isEnabled = true
                            }
                        )
                    }
                }
            onBackPressedDispatcher.addCallback(this /* LifecycleOwner */, onBackPressedCallback)
        }
    }

    private fun handleCentralizedBackPressLogic(fallbackToSuper: (() -> Unit)? = null) {
        if (viewModel.isRecording) {
            Toast.makeText(
                this,
                // Assicurati di avere questa stringa in res/values/strings.xml
                getString(R.string.finish_recording_before_closing),
                Toast.LENGTH_SHORT
            ).show()
            return // Esce presto se sta registrando
        }

        // Tenta prima di navigare indietro con NavController
        val navigatedUp = if (::navController.isInitialized) {
            navController.navigateUp()
        } else {
            false
        }
        if (navigatedUp) {
            return
        }

        // Se navigateUp() fallisce, siamo probabilmente alla destinazione iniziale del grafo.
        // Controlla esplicitamente se siamo alla destinazione iniziale.
        val currentDestId = navController.currentDestination?.id
        val startDestId = navController.graph.startDestinationId
        if (currentDestId == startDestId) {
            finishAffinity() // Chiudi l'app
        } else {
            fallbackToSuper?.invoke()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_ALL) {
            if (grantResults.contains(PackageManager.PERMISSION_DENIED)) {
                Toast.makeText(this, "Alcune funzionalità potrebbero non essere disponibili senza i permessi richiesti.", Toast.LENGTH_LONG).show()
            }
            // Una volta chiuso il dialogo dei permessi, controlla la batteria
            checkBatteryOptimization()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        Log.d("Mappa", "MainActivity onNewIntent: $intent")
        setIntent(intent) // Aggiorna l'intent che verrà restituito da getIntent()
        // Processa l'intent, ad esempio per aprire il file GPX
        handleIntent(intent)
        // Lascia che la Navigation Component gestisca il nuovo intent per il deep linking
        // Se l'intent è stato creato da NavDeepLinkBuilder, NavController lo gestirà.
        //navController.handleDeepLink(intent)
    }

    private fun handleIntent(intent: Intent?) {
        intent?.let {
            if (it.action == Intent.ACTION_VIEW && it.data != null) {
                val gpxUri = it.data
                Log.d("Mappa", "MainActivity: Navigating to MappaFragment with GPX URI: $gpxUri")

                val bundle = Bundle().apply {
                    putString("gpx_file_uri", gpxUri.toString())
                }
                // Assumendo che tu abbia un NavController chiamato 'navController'
                // e che MappaFragment sia la destinazione corrente o raggiungibile
                navController.navigate(
                    R.id.mappaFragment,
                    bundle
                ) // O un'azione specifica che porta a MappaFragment
            }
        }
    }

    fun checkAndRequestPermissions() {
        val list = PERMISSIONS.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (list.isNotEmpty()) {
            // Chiedi i permessi standard. Questo è asincrono.
            ActivityCompat.requestPermissions(this, list.toTypedArray(), PERMISSION_ALL)
        } else {
            // Se tutti i permessi sono già ok, controlla la batteria
            checkBatteryOptimization()
        }
    }

    private fun checkBatteryOptimization() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val packageName = this.packageName
            val pm = this.getSystemService(Context.POWER_SERVICE) as PowerManager
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                val intent = Intent().apply {
                    action = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
                    data = "package:$packageName".toUri()
                }
                startActivity(intent)
            }
        }
    }

    private val downloadReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            Log.d("MainActivity", "Ricevuto broadcast: ${intent.action}")
            when (intent.action) {
                DownloadService.ACTION_DOWNLOAD_STARTED -> {
                    val fileName = intent.getStringExtra(DownloadService.EXTRA_FILE_PATH)
                    viewModel.setDownloading(true)
                    viewModel.setDownloadProgress(0)
                    fileName?.let { viewModel.setDownloadFileName(it) }
                }
                DownloadService.ACTION_PROGRESS_UPDATE -> {
                    val progress = intent.getIntExtra(DownloadService.EXTRA_PROGRESS, -1)
                    val fileName = intent.getStringExtra(DownloadService.EXTRA_FILE_PATH)
                    //Log.d("MainActivity", "Update progresso: $progress")
                    viewModel.setDownloadProgress(progress)
                    fileName?.let { viewModel.setDownloadFileName(it) }
                }
                DownloadService.ACTION_DOWNLOAD_COMPLETE -> {
                    val message = intent.getStringExtra(DownloadService.EXTRA_MESSAGE) ?: ""
                    //Log.d("MainActivity", "Download completato: $message")
                    viewModel.setDownloading(false)
                    viewModel.postFtpStatus(message)
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
    }

    override fun onStop() {
        super.onStop()
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(downloadReceiver)
        } catch (e: Exception) {
            Log.w("MainActivity", "Errore unregisterReceiver", e)
        }
    }

    private fun getCurrentVersionCode(): Long {
        return try {
            packageManager.getPackageInfo(packageName, 0).longVersionCode
        } catch (_: Exception) {
            // In caso di errore, ritorna un valore che non innescherà l'aggiornamento
            -1L
        }
    }

    fun openDrawer() {
        if (::drawerLayout.isInitialized) {
            drawerLayout.openDrawer(GravityCompat.START)
        }
    }

}
