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
import android.os.Environment
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import android.window.OnBackInvokedDispatcher
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.app.ActivityCompat
import androidx.core.view.GravityCompat
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
    private val PERMISSION_ALL = 1
    private val PERMISSIONS = mutableListOf(
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.INTERNET,
        Manifest.permission.ACCESS_NETWORK_STATE,
        Manifest.permission.READ_EXTERNAL_STORAGE,
        Manifest.permission.WRITE_EXTERNAL_STORAGE,
        Manifest.permission.CAMERA
        // permesso da Android 13
        //Manifest.permission.POST_NOTIFICATIONS
    )
    private val REQUEST_MANAGE_ALL_FILES_ACCESS_PERMISSION = 2296
    private var allPermissionsGranted = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Tutti i permessi a false
        allPermissionsGranted = false

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

        // inizializza le preferenze
        initPreferenze()
        // verifica se tutti i permessi standard sono stati concessi
        if (!hasPermissions(this, PERMISSIONS)) {
            ActivityCompat.requestPermissions(this, PERMISSIONS.toTypedArray(), PERMISSION_ALL)
        }
        // verifica accesso a tutti i file
        checkAndRequestStoragePermission()
           if (allPermissionsGranted) {
            initApp()
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

        // disabilitazione della voce barometro se non presente
        if (!haBaro) {
            val menuItem = navigationView.menu.findItem(R.id.barometro)
            menuItem.isVisible = false
        }

        // Le preferenze vanno caricate dal main e sono indispensabili per il
        // corretto caricamento delle mappe
        AndroidGraphicFactory.createInstance(this)
        Configuration.getInstance().load(
            applicationContext,
            getDefaultSharedPreferences(applicationContext)
        )
        // verifica se esiste cartella Sentieri nella Root /storage/emulated/0/
        val folderBase = Environment.getExternalStorageDirectory().absolutePath + "/Sentieri"
        val sentieriFolder = File(folderBase)
        if (!sentieriFolder.exists()) {
            //Controllare se i permessi sono stati concessi ContextCompat.checkSelfPermission()
            if (Environment.isExternalStorageManager()) {
                // crea cartelle Sentieri nella sdcard
                val percorso: Set<String> = setOf("/Sentieri/Mappe", "/Sentieri/Tracce")
                for (element in percorso) {
                    val folderPath = Environment.getExternalStorageDirectory().absolutePath + element
                    if (!creaCartelle(folderPath))
                        Toast.makeText(this, "Errore nella creazione della cartella $folderPath", Toast.LENGTH_LONG).show()
                    //Log.d("Preferenze", "Errore nella creazione della cartella Sentieri")
                    else
                        Log.d("Preferenze", "Cartella creata in: $folderPath")
                }
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
        val sensorManager: SensorManager = this.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        if (sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE) != null) {
            haBaro = true
            preferenze.edit().putBoolean("haBaro", haBaro).apply()
            preferenze.edit().putBoolean("setBaro", true).apply()
        } else {
            haBaro = false
            preferenze.edit().putBoolean("haBaro", haBaro).apply()
            preferenze.edit().putBoolean("setBaro", false).apply()
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
            Toast.makeText(this, "Termina registrazione prima di chiudere l'app", Toast.LENGTH_SHORT).show()
        } else {
            // Check if we are at the start destination and the backstack is empty
            if (navController.currentDestination?.id == navController.graph.startDestinationId &&
                !navController.popBackStack()) {
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

    private fun hasPermissions(
        mainActivity: MainActivity,
        permissions: List<String>
    ): Boolean {
        for (permission in permissions) {
            if (ActivityCompat.checkSelfPermission(
                    mainActivity,
                    permission
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return false
            }
        }
        return true
    }

    private fun checkAndRequestStoragePermission() {
        if (!hasAllFilesAccessPermission()) {
            requestAllFilesAccessPermission()
        } else {
            allPermissionsGranted = true
            // Il permesso è già concesso, procedi con l'accesso ai file
        }
    }

    private fun hasAllFilesAccessPermission(): Boolean {
        return Environment.isExternalStorageManager()
    }

    private fun requestAllFilesAccessPermission() {
        val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
        intent.addCategory("android.intent.category.DEFAULT")
        intent.data = Uri.parse(String.format("package:%s", applicationContext.packageName))
        startActivityForResult(intent, REQUEST_MANAGE_ALL_FILES_ACCESS_PERMISSION)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_MANAGE_ALL_FILES_ACCESS_PERMISSION) {
                if (Environment.isExternalStorageManager()) {
                    allPermissionsGranted = true
                    initApp()
                }
                else
                    Toast.makeText(this, "Permessi non assegnati", Toast.LENGTH_SHORT).show()
        }
    }

}