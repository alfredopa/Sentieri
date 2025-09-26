package com.apstudio.sentieri.layer

import android.app.Application
import android.graphics.Color
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import com.apstudio.sentieri.db.FieldSchemaInfo
import mil.nga.geopackage.GeoPackage
import mil.nga.geopackage.GeoPackageFactory
import mil.nga.geopackage.GeoPackageManager
import mil.nga.geopackage.features.user.FeatureRow
import org.osmdroid.gpkg.overlay.features.PolygonOptions
import java.io.File
import kotlin.random.Random

class LayerViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "LayerViewModel"
        private const val DATABASE_NAME = "Layers.gpkg"
        private const val CONFIG_FILE_NAME = "db_schema_config.xml"
    }

    var geoPackageInstance: GeoPackage? = null
        private set // Rendi il setter privato se l'apertura è gestita internamente

    // Assumendo che featureList e labelConfig siano ancora qui
    val featureList: MutableList<FeatureTableInfo> = mutableListOf() // Inizializza come necessario
    var labelConfig: MutableMap<String, List<FieldSchemaInfo>> = mutableMapOf()
    var currentActiveTableName: String? = null
    val polygonOptions = PolygonOptions().apply {
        strokeWidth = 2f
        fillColor = Color.argb(50, 255, 0, 255)
        strokeColor = Color.argb(100, 0, 0, 0)
    }
    /**
     * Apre il GeoPackage se non è già aperto e carica la configurazione.
     * Se usi ViewModel semplice, aggiungi: context: Context come parametro.
     */
    fun openGeoPackageAndLoadConfig() { // Rinominato per chiarezza
        getApplication<Application>() // Per AndroidViewModel

        if (geoPackageInstance != null) {
            // Potrebbe essere già aperto, ma verifichiamo se la configurazione necessita di essere ricaricata
            // o se l'istanza è valida. Se fosse chiusa, le operazioni fallirebbero.
            Log.d(TAG, "GeoPackage instance exists. Verifying and loading config if needed.")
            try {
                // Una semplice operazione per vedere se è vivo, es. ottenere il path.
                // Se chiuso, questo potrebbe lanciare un'eccezione.
                geoPackageInstance?.path
                loadConfigIfNeeded( geoPackageInstance!!) // Passa l'istanza esistente
            } catch (e: Exception) {
                Log.e(TAG, "Error using existing GeoPackage instance. It might be closed. Re-opening.", e)
                actuallyOpenAndConfigGeoPackage()
            }
            return
        }
        actuallyOpenAndConfigGeoPackage()
    }

    private fun actuallyOpenAndConfigGeoPackage() {
        val context = getApplication<Application>()
        val dataDir = context.getDatabasePath(DATABASE_NAME).parentFile
        val geoPackageFile = File(dataDir, DATABASE_NAME)
        if (!geoPackageFile.exists()) {
            Log.e(TAG, "GeoPackage file does not exist at: ${geoPackageFile.absolutePath}")
            // Qui potresti voler aggiornare uno StateFlow/LiveData per notificare l'UI
            return
        }
        val geoPackageManager: GeoPackageManager = GeoPackageFactory.getManager(context)
        try {
            Log.d(TAG, "Attempting to open GeoPackage: ${geoPackageFile.name}")
            val openedGeoPackage = geoPackageManager.openExternal(geoPackageFile)
            if (openedGeoPackage == null) {
                Log.e(TAG, "Error opening GeoPackage: ${geoPackageFile.name}")
                // Aggiorna StateFlow/LiveData
                return
            }
            geoPackageInstance = openedGeoPackage
            Log.i(TAG, "GeoPackage '${openedGeoPackage.name}' opened successfully by ViewModel.")

            // Carica la configurazione e popola featureList dopo l'apertura
            loadConfigAndPopulateFeatures(openedGeoPackage)

        } catch (e: Exception) {
            Log.e(TAG, "Exception while opening GeoPackage: ${geoPackageFile.name}", e)
            geoPackageInstance = null // Assicura che sia null in caso di fallimento
            // Aggiorna StateFlow/LiveData
        }
    }

    /**
     * Metodo interno che carica la configurazione dell'etichetta E popola la featureList.
     * Chiamato dopo che il GeoPackage è stato aperto con successo.
     */
    private fun loadConfigAndPopulateFeatures(geoPackage: GeoPackage) {
        val context = getApplication<Application>()
        val pathGeoPackage = geoPackage.path ?: run {
            Log.e(TAG, "GeoPackage path is null, cannot load config.")
            return
        }
        val configurator = DatabaseSchemaConfigurator(context, pathGeoPackage)
        val configFile = File(context.filesDir, CONFIG_FILE_NAME)
        if (!configFile.exists()) {
            Log.i(TAG, "Config file not found, generating: ${configFile.absolutePath}")
            configurator.generateAndWriteConfigFile()
        }
        (configurator.loadConfigFromFile() as? MutableMap<String, List<FieldSchemaInfo>>)?.also {
            //(configurator.loadConfigFromFile() as? MutableMap<String, List<Pair<String, Boolean>>>)?.also {
            labelConfig = it
            Log.d(TAG, "LabelConfig loaded successfully.")
        } ?: Log.e(TAG, "Failed to load or cast labelConfig.")

        // 2. Popola featureList
        featureList.clear() // Pulisci la lista prima di ripopolarla
        try {
            geoPackage.featureTables.forEach { tableName ->
                val featureDao = geoPackage.getFeatureDao(tableName)
                featureDao.count() // Conteggio degli elementi
                val contentsDao = geoPackageInstance?.contentsDao
                val contents = contentsDao?.queryForId(tableName)
                // Logica per determinare isVisible (es. da preferenze, o default)
                val isVisibleInitially = false // O leggi da una configurazione persistente
                // Descrizione - potresti volerla rendere più dinamica o configurabile
                val description = contents?.identifier ?: "Nessuna descrizione"
                // Colore - default o da configurazione
                val color = contents?.description ?: "#0000FF"

                featureList.add(
                    FeatureTableInfo(
                        name = tableName,
                        isVisible = isVisibleInitially,
                        descrTabella = description,
                        //numRecord = count.toInt(),
                        colore = color,
                        readData = false, // Inizia come non letta
                        listOverlay = null // Inizia senza overlay
                    )
                )
            }
            Log.i(TAG, "FeatureList populated with ${featureList.size} tables.")
            // Qui potresti voler notificare l'UI che featureList è pronta, es. tramite LiveData/StateFlow
            // _featureListLiveData.postValue(featureList)
        } catch (e: Exception) {
            Log.e(TAG, "Error populating featureList from GeoPackage.", e)
            // Gestisci l'errore, magari pulendo featureList o notificando l'UI
            featureList.clear()
        }
    }

    // Questo metodo è stato rinominato da `loadConfigIfNeeded` per evitare confusione,
    // dato che ora la configurazione viene caricata insieme alle feature.
    // Se hai bisogno di ricaricare SOLO la configurazione per un GeoPackage già aperto,
    // puoi creare un metodo separato.
    private fun loadConfigIfNeeded(geoPackage: GeoPackage) {
        val context = getApplication<Application>()
        val pathGeoPackage = geoPackage.path ?: run {
            Log.e(TAG, "GeoPackage path is null, cannot load config.")
            return
        }
        val configurator = DatabaseSchemaConfigurator(context, pathGeoPackage)
        val configFile = File(context.filesDir, CONFIG_FILE_NAME)
        if (!configFile.exists()) {
            configurator.generateAndWriteConfigFile()
        }
        (configurator.loadConfigFromFile() as? MutableMap<String, List<FieldSchemaInfo>>)?.also {
            labelConfig = it
            Log.d(TAG, "LabelConfig (re)loaded successfully.")
        } ?: Log.e(TAG, "Failed to (re)load or cast labelConfig.")
    }

    fun creaLabel(featureRow: FeatureRow, tableName: String): String {
        val campiLabel = labelConfig[tableName]
        val labelBuilder = StringBuilder()

        // 1. Aggiungi il tableName come valore iniziale, se non è vuoto.
        if (tableName.isNotEmpty()) {
            labelBuilder.append(tableName)
        }

        campiLabel?.forEachIndexed { index, (fieldName, description,  isVisible) ->
            if (isVisible) {
                // Ottieni il valore del campo direttamente da featureRow.values
                val fieldValue = featureRow.values[index]?.toString()

                // Controlla che fieldValue non sia null e non sia la stringa "null"
                if (fieldValue != null && fieldValue != "null") {
                    // 2. Se labelBuilder ha già del contenuto (tableName o un campo precedente),
                    //    aggiungi un carattere di nuova riga per separare.
                    if (labelBuilder.isNotEmpty()) {
                        labelBuilder.append("\n")
                    }

                    // 3. Accoda il nome del campo e il suo valore.
                    labelBuilder.append(fieldName)
                        .append(": ")
                        .append(fieldValue)
                }
            }
        }
        return labelBuilder.toString()
    }

    fun getRandomIntColor(alpha: Int = 255): Int { // alpha da 0 (trasparente) a 255 (opaco)
        val red = Random.nextInt(256)
        val green = Random.nextInt(256)
        val blue = Random.nextInt(256)
        return Color.argb(alpha, red, green, blue)
    }

    fun closeGeoPackage() {
        geoPackageInstance?.let { geoPkg ->
            try {
                geoPkg.close()
                Log.i(TAG, "GeoPackage '${geoPkg.name}' closed in onCleared.")
            } catch (e: Exception) {
                Log.e(TAG, "Error closing GeoPackage '${geoPkg.name}' in onCleared.", e)
            }
        }
        geoPackageInstance = null
    }

    override fun onCleared() {
        super.onCleared()
        closeGeoPackage()
        Log.d(TAG, "LayerViewModel cleared.")
    }
}