package com.apstudio.sentieri

import android.Manifest
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import android.window.OnBackInvokedDispatcher
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.app.ActivityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.navigation.NavController
import androidx.navigation.findNavController
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

        // aggiunge il permesso notifiche da Android 13
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            PERMISSIONS.add(Manifest.permission.POST_NOTIFICATIONS)
            onBackInvokedDispatcher.registerOnBackInvokedCallback(
                OnBackInvokedDispatcher.PRIORITY_DEFAULT
            ) {
                // Visualizza un messaggio di conferma
                if (viewModel.isRecording)
                    Toast.makeText(this, "Termina registrazione prima di chiudere l'app", Toast.LENGTH_SHORT).show()
                else
                    finishAffinity()
            }
        }

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
        setupActionBarWithNavController(navController, drawerLayout)

        // disabilitazione della voce barometro se non presente
        if (!haBaro) {
            val menuItem = navigationView.menu.findItem(R.id.barometro)
            menuItem.isVisible = false
        }
        // ABILITARE SE GESTISCO CLICK SUL MENUDRAWER
        /*navigationView.setNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.nav_exit -> {
                    // Handle home selection
                    Log.d("Navigation", "uscita")
                     true
                }
                else ->  false
            }
        }*/
        navigationView.setupWithNavController(navController)

        // inizializza le preferenze
        //initPreferenze()
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
        // Gestione delle impostazioni se non trova la stringa barometro apre impostazioni iniziali e crea il
        // file preferences.xml
        preferenze = getDefaultSharedPreferences(this)
        if (preferenze.contains("haBaro")) {
            haBaro = preferenze.getBoolean("haBaro", false)
        } else {
            navController.navigate(R.id.preferenze)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        return navController.navigateUp(drawerLayout) || super.onSupportNavigateUp()
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
    /*override fun onNavigationItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_exit -> {
                finishAffinity()
                // Gestisci il click sulla voce "Home" nel menu drawer
                return true
            }
            // ... altre voci del menu
        }
        return false
    }*/

}