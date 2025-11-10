package com.apstudio.sentieri

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.Build
import android.os.Bundle
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
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.NavController
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import androidx.preference.PreferenceManager.getDefaultSharedPreferences
import com.google.android.material.navigation.NavigationView
import org.mapsforge.map.android.graphics.AndroidGraphicFactory
import org.osmdroid.config.Configuration
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream


class MainActivity :
    AppCompatActivity() {
    private lateinit var viewModel: SentieriViewModel
    private lateinit var navController: NavController
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var appBarConfiguration: AppBarConfiguration // For handling the Up button and drawer
    private lateinit var preferenze: SharedPreferences
    private var haBaro = false
    private val PERMISSION_ALL = 123

    private val PERMISSIONS = buildList {
        add(Manifest.permission.ACCESS_COARSE_LOCATION)
        add(Manifest.permission.ACCESS_FINE_LOCATION)
        add(Manifest.permission.INTERNET)
        add(Manifest.permission.ACCESS_NETWORK_STATE)
        add(Manifest.permission.CAMERA)
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.R) {  // S_V2 è API level 32
            add(Manifest.permission.READ_EXTERNAL_STORAGE)
            add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
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
        SimpleFileLogger.log("Mainactivity", "MainActivity onCreate")
        super.onCreate(savedInstanceState)
        val app = applicationContext as AppSentieri
        viewModel =
            ViewModelProvider(app, app.sentieriViewModelFactory)[SentieriViewModel::class.java]
        // inizializza le preferenze
        initPreferenze()
        // verifica se tutti i permessi standard sono stati concessi
        checkAndRequestPermissions()
        // Non chiederemo più all'utente di disabilitare le ottimizzazioni.
        /*
            if (isBatteryOptimizationEnabled(this)) {
                // Spiega all'utente perché è necessario e poi richiedi
                // Esempio con un AlertDialog:
                AlertDialog.Builder(this)
                    .setTitle("Ottimizzazione Batteria")
                    .setMessage("Per garantire che [La Tua Funzionalità Chiave, es. il tracciamento GPS] funzioni correttamente in background, è consigliabile disabilitare le ottimizzazioni della batteria per questa app. Vuoi andare alle impostazioni?")
                    .setPositiveButton("Vai alle Impostazioni") { _, _ ->
                        requestIgnoreBatteryOptimizations(this)
                    }
                    .setNegativeButton("Annulla", null)
                    .show()
            }*/
        // gestione evento indietro
        setupBackPressHandling()
        Log.d("Mappa", "MainActivity onCreate: $intent")
        handleIntent(intent) // Gestisci anche l'intent iniziale che ha creato l'Activity
        enableEdgeToEdge()
    }

    private fun initApp() {
        setContentView(R.layout.activity_main)
        //  il layout verticale è impostato nel manifest
        //requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT

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
        textViewInHeader?.text = BuildConfig.VERSION_NAME
        navController = findNavController(R.id.nav_host_fragment)
        // Make sure actions in the ActionBar get propagated to the NavController
        // Connect the drawer layout to the navigation graph
        appBarConfiguration = AppBarConfiguration(navController.graph, drawerLayout)
        setupActionBarWithNavController(navController, appBarConfiguration)
        navigationView.setupWithNavController(navController)

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
        // controllo se cartelle mappe e tracce presenti e db layer
        verificaCartelleDB()

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
                return // Esce dalla funzione verificaCartelleDB se un file cruciale manca
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
        if (!dbFile.exists()) {
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
        // TEST SENSORE BAROMETRO
        val sensorManager: SensorManager = this.getSystemService(SENSOR_SERVICE) as SensorManager
        if (sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE) != null) {
            haBaro = true
            preferenze.edit { putBoolean("haBaro", haBaro) }
            preferenze.edit { putBoolean("setBaro", true) }
        } else {
            haBaro = false
            preferenze.edit { putBoolean("haBaro", haBaro) }
            preferenze.edit { putBoolean("setBaro", false) }
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
        val navigatedUp = navController.navigateUp()
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
            // Questo caso indica che navigateUp() è fallito MA non siamo alla destinazione iniziale.
            // Potrebbe accadere in scenari di navigazione complessi o se il NavController
            // non è configurato come previsto per questo flusso "Indietro".
            // Come fallback, esegui il comportamento di default.
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU && fallbackToSuper != null) {
                // Su API < 33, esegui la lambda di fallback fornita
                fallbackToSuper.invoke()
            } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                // Fallback di sicurezza se la lambda non è stata fornita per API < 33,
                // anche se con la struttura attuale dovrebbe esserlo.
                // Questa è la chiamata diretta a super.onBackPressed() originale.
                // ATTENZIONE: se sei dentro un OnBackPressedCallback, chiamare super.onBackPressed()
                // direttamente qui potrebbe non essere l'ideale. La gestione tramite
                // isEnabled = false; dispatcher.onBackPressed() è generalmente più pulita.
                // Tuttavia, la logica con `fallbackToSuper` è preferibile.
                super.onBackPressed() // Mantenuto per coerenza con la tua richiesta originale di fallback
            }
            // Per API 33+, se si arriva qui, il sistema gestirà la chiusura dell'Activity
            // se non ci sono altri callback registrati che gestiscono l'evento.
            // Non c'è un `super.onBackPressed()` diretto da chiamare nel contesto di `onBackInvoked`.
        }
    }

    private fun hasPermissions(context: Context, permissions: List<String>): Boolean =
        permissions.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_ALL) {
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                // All permissions granted, proceed
                initApp()
            } else {
                // Permissions denied, handle accordingly (e.g., show a message)
                Toast.makeText(this, "Permessi non assegnati", Toast.LENGTH_LONG).show()
            }
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
        if (!hasPermissions(this, PERMISSIONS)) {
            ActivityCompat.requestPermissions(this, PERMISSIONS.toTypedArray(), PERMISSION_ALL)
        } else {
            // All permissions are already granted, proceed with functionality.
            initApp()
        }
    }

    /*fun isBatteryOptimizationEnabled(context: Context): Boolean {
        val packageName = context.packageName
        val pm = context.getSystemService(POWER_SERVICE) as PowerManager
        return !pm.isIgnoringBatteryOptimizations(packageName)
    }

    fun requestIgnoreBatteryOptimizations(activity: AppCompatActivity) {
        val intent = Intent()
        val packageName = activity.packageName
        val pm = activity.getSystemService(POWER_SERVICE) as PowerManager
        if (!pm.isIgnoringBatteryOptimizations(packageName)) {
            intent.action = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
            intent.data = "package:$packageName".toUri()
            // È buona norma controllare se l'intent può essere gestito
            if (intent.resolveActivity(activity.packageManager) != null) {
                activity.startActivity(intent)
            } else {
                // Potrebbe non esserci un'activity per gestire questa azione
                // su alcuni dispositivi molto customizzati.
                // In questo caso, potresti guidare l'utente manualmente.
                // Log.w("BatteryOpt", "Nessuna activity per ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS")
                // Prova ad aprire le impostazioni generali di ottimizzazione batteria:
                val fallbackIntent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                if (fallbackIntent.resolveActivity(activity.packageManager) != null) {
                    activity.startActivity(fallbackIntent)
                } else {
                    // Log.w("BatteryOpt", "Nessuna activity per ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS")
                    // Mostra un messaggio all'utente per farlo manualmente
                }
            }
        }
    }*/

}