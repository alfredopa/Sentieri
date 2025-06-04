package com.apstudio.sentieri

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import android.window.OnBackInvokedDispatcher
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.drawerlayout.widget.DrawerLayout
import androidx.navigation.NavController
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import androidx.preference.PreferenceManager.getDefaultSharedPreferences
import com.apstudio.sentieri.db.SentieriRepo
import com.google.android.material.navigation.NavigationView
import org.mapsforge.map.android.graphics.AndroidGraphicFactory
import org.osmdroid.config.Configuration
import java.io.File


class MainActivity :
    AppCompatActivity() {   // NavigationView.OnNavigationItemSelectedListener { //ABILITA EVENTI MENUDRAWER

    private val viewModel: SentieriViewModel by viewModels {
        SentieriFactory(
            SentieriRepo(this)
        )
    }
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
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.R)  {  // S_V2 è API level 32
            add(Manifest.permission.READ_EXTERNAL_STORAGE)
            add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
        // permesso da Android 13
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }

        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE){
            add(Manifest.permission.FOREGROUND_SERVICE_LOCATION)
        }
        else {
            add(Manifest.permission.FOREGROUND_SERVICE)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // inizializza le preferenze
        initPreferenze()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            onBackInvokedDispatcher.registerOnBackInvokedCallback(
                OnBackInvokedDispatcher.PRIORITY_DEFAULT
            ) {
                handleBackPress()
            }
        } else {
            onBackPressedDispatcher.addCallback(this, object :
                OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    handleBackPress()
                }
            })
        }
        // verifica se tutti i permessi standard sono stati concessi
        checkAndRequestPermissions()

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
        }
    }

    private fun initApp() {
        setContentView(R.layout.activity_main)
        //  il layout verticale è impostato nel manifest
        //requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        drawerLayout = findViewById(R.id.drawerLayout)
        val navigationView = findViewById<NavigationView>(R.id.nav_View)
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
        // verifica se esiste cartella Sentieri nello spazio file applicazione ATTENZIONE solo la cartella media visibile da file picker
        //val baseDir: File? = getAppSpecificExternalDirectory(applicationContext) // restituisce cartella data anzichè media
        val mediaStorageDir = this.getExternalMediaDirs()
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

    private fun initPreferenze() {
        // Gestione delle impostazioni default dopo installazione  crea il
        // file preferences.xml
        preferenze = getDefaultSharedPreferences(this)
        // TEST SENSORE BAROMETRO
        val sensorManager: SensorManager = this.getSystemService(SENSOR_SERVICE) as SensorManager
        if (sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE) != null) {
            haBaro = true
            preferenze.edit() { putBoolean("haBaro", haBaro) }
            preferenze.edit() { putBoolean("setBaro", true) }
        } else {
            haBaro = false
            preferenze.edit() { putBoolean("haBaro", haBaro) }
            preferenze.edit() { putBoolean("setBaro", false) }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        return navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
    }

    private fun handleBackPress() {
        /*if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START)
            return // Stop further back handling here
        }*/
        if (viewModel.isRecording) {
            Toast.makeText(
                this,
                "Termina registrazione prima di chiudere l'app",
                Toast.LENGTH_SHORT
            ).show()
        } else {
            // Check if we are at the start destination and the backstack is empty
            if (navController.currentDestination?.id == navController.graph.startDestinationId &&
                !navController.popBackStack()
            ) {
                finishAffinity()
            } else {
                // Otherwise, let the NavController handle the back press
                if (!navController.navigateUp()) {
                    // This should ideally not be reached if the above check is correct
                    super.onBackPressed() // For compatibility with older versions
                }
            }
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

    fun checkAndRequestPermissions() {
        if (!hasPermissions(this, PERMISSIONS)) {
            ActivityCompat.requestPermissions(this, PERMISSIONS.toTypedArray(), PERMISSION_ALL)
        } else {
            // All permissions are already granted, proceed with functionality.
            initApp()
        }
    }

    fun isBatteryOptimizationEnabled(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val packageName = context.packageName
            val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            return !pm.isIgnoringBatteryOptimizations(packageName)
        }
        return false // Le ottimizzazioni specifiche sono per Marshmallow (API 23) e successivi
    }

    fun requestIgnoreBatteryOptimizations(activity: AppCompatActivity) {
        val intent = Intent()
        val packageName = activity.packageName
        val pm = activity.getSystemService(Context.POWER_SERVICE) as PowerManager
        if (!pm.isIgnoringBatteryOptimizations(packageName)) {
            intent.action = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
            intent.data = Uri.parse("package:$packageName")
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
    }

}