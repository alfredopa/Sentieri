package com.apstudio.sentieri.layer

import android.app.Application
import android.graphics.Color
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.apstudio.sentieri.db.FieldSchemaInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import mil.nga.geopackage.GeoPackage
import mil.nga.geopackage.GeoPackageFactory
import mil.nga.geopackage.GeoPackageManager
import mil.nga.geopackage.features.user.FeatureRow
import org.osmdroid.gpkg.overlay.features.PolygonOptions
import org.osmdroid.util.GeoPoint
import java.io.File
import kotlin.random.Random


// Evento per l'aggiornamento dei layer (usato al posto del primo FragmentResultListener)
// L'oggetto Event serve per consumare l'evento una sola volta.
class Event<out T>(private val content: T) {
    private var hasBeenHandled = false
    fun getContentIfNotHandled(): T? {
        return if (hasBeenHandled) null else {
            hasBeenHandled = true
            content
        }
    }
}

class LayerViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "LayerViewModel"
        private const val DATABASE_NAME = "Layers.gpkg"
        private const val CONFIG_FILE_NAME = "db_schema_config.xml"
    }
    private val _isReady = MutableLiveData<Boolean>()
    val isReady: LiveData<Boolean> = _isReady

    var geoPackageInstance: GeoPackage? = null
        private set // Rendi il setter privato se l'apertura è gestita internamente
    val loadingStatus = mutableMapOf<String, Boolean>()
    // Assumendo che featureList e labelConfig siano ancora qui
    val featureList: MutableList<FeatureTableInfo> = mutableListOf() // Inizializza come necessario
    //var labelConfig: MutableMap<String, List<FieldSchemaInfo>> = mutableMapOf()
    lateinit var labelConfig: Map<String, List<FieldSchemaInfo>>
    var currentActiveTableName: String? = null
    private var lastKnownVisibilityState: Map<String, Boolean>? = null
    val polygonOptions = PolygonOptions().apply {
        strokeWidth = 2f
        fillColor = Color.argb(50, 255, 0, 255)
        strokeColor = Color.argb(100, 0, 0, 0)
    }
    // 1. LiveData per notificare a MappaFragment di applicare le modifiche ai layer.
    //    Viene "triggerato" quando il dialogo GpkgLayer viene chiuso.
    private val _layerUpdateRequest = MutableLiveData<Event<Unit>>()
    val layerUpdateRequest: LiveData<Event<Unit>> = _layerUpdateRequest

    // 2. LiveData per notificare a MappaFragment di centrare la mappa su una coordinata.
    //    Viene "triggerato" dal click su un item in FeatureList.
    private val _navigateToPointRequest = MutableLiveData<Event<GeoPoint>>()
    val navigateToPointRequest: LiveData<Event<GeoPoint>> = _navigateToPointRequest

    /**
     * Chiamato dal dialogo GpkgLayer quando viene chiuso.
     * Notifica a MappaFragment di aggiornare gli overlay.
     */
    fun requestLayerUpdate() {
        _layerUpdateRequest.value = Event(Unit)
    }

    /**
     * Chiamato dal dialogo FeatureList quando un utente clicca su una feature.
     * Notifica a MappaFragment di animare la mappa verso il punto cliccato.
     */
    fun requestNavigationToPoint(latitude: Double, longitude: Double) {
        if (latitude != 0.0 && longitude != 0.0) {
            _navigateToPointRequest.value = Event(GeoPoint(latitude, longitude))
        }
    }
    /**
     * Salva una "fotografia" dello stato di visibilità corrente dei layer.
     *
     * QUANDO USARLO:
     * Chiama questo metodo dal metodo `onPause()` di `MappaFragment`.
     */
    fun recordCurrentLayerVisibility() {
        // Crea una mappa associando il nome di ogni layer (it.name) al suo stato di visibilità (it.isVisible).
        lastKnownVisibilityState = featureList.associate { it.name to it.isVisible }
        Log.d("LayerViewModel", "Stato di visibilità dei layer salvato.")
    }

    /**
     * Controlla se lo stato di visibilità attuale è diverso dall'ultima "fotografia" salvata.
     *
     * QUANDO USARLO:
     * Chiama questo metodo dal metodo `onResume()` di `MappaFragment` per decidere
     * se è necessario eseguire il costoso aggiornamento degli overlay.
     *
     * @return `true` se la visibilità è cambiata (o se è la prima volta che viene controllata),
     *         `false` se nulla è cambiato.
     */
    fun haveLayerVisibilitiesChanged(): Boolean {
        // Caso 1: Se non abbiamo mai salvato uno stato (es. al primo avvio),
        // dobbiamo considerare che ci sia una modifica per forzare il disegno iniziale.
        if (lastKnownVisibilityState == null) {
            Log.d("LayerViewModel", "Nessuno stato precedente salvato. Si assume una modifica.")
            return true
        }

        // Caso 2: Confronta lo stato attuale con quello salvato.
        val currentState = featureList.associate { it.name to it.isVisible }

        // Il confronto tra due mappe in Kotlin è molto efficiente e controlla
        // che entrambe le mappe abbiano le stesse chiavi con gli stessi valori.
        val hasChanged = currentState != lastKnownVisibilityState

        if (hasChanged) {
            Log.d("LayerViewModel", "Rilevato cambiamento nella visibilità dei layer.")
        }

        return hasChanged
    }

    /**
     * Avvia l'apertura asincrona del GeoPackage e il caricamento della configurazione.
     * Non blocca più il thread chiamante.
     */
    fun openGeoPackageAndLoadConfig() {
        // Se è già stato caricato con successo, non fare nulla.
        if (isReady.value == true) {
            Log.d(TAG, "ViewModel e GeoPackage sono già pronti.")
            return
        }
        // Se è già in corso, non avviarlo di nuovo.
        if (loadingStatus["_global_"] == true) {
            Log.d(TAG, "Caricamento del GeoPackage già in corso.")
            return
        }

        Log.d(TAG, "Avvio caricamento asincrono del GeoPackage...")
        loadingStatus["_global_"] = true // Imposta un flag di caricamento globale

        // Lancia l'operazione pesante in background
        viewModelScope.launch {
            // La chiamata a una funzione 'suspend' è corretta qui dentro
            val success = actuallyOpenAndConfigGeoPackage()

            // Aggiorna il LiveData sul thread principale al termine
            _isReady.postValue(success) // Usa postValue per sicurezza da coroutine
            loadingStatus["_global_"] = false // Rimuovi il flag
        }
    }


/**
 * Funzione sospesa che esegue l'apertura del file I/O su un thread in background.
 * @return True se ha successo, altrimenti False.
 */
private suspend fun actuallyOpenAndConfigGeoPackage(): Boolean = withContext(Dispatchers.IO) {
    val context = getApplication<Application>()
    val dataDir = context.getDatabasePath(DATABASE_NAME).parentFile
    val geoPackageFile = File(dataDir, DATABASE_NAME)
    if (!geoPackageFile.exists()) {
        Log.e(TAG, "File GeoPackage non esiste: ${geoPackageFile.absolutePath}")
        return@withContext false
    }
    val geoPackageManager: GeoPackageManager = GeoPackageFactory.getManager(context)
    try {
        val openedGeoPackage = geoPackageManager.openExternal(geoPackageFile)
        if (openedGeoPackage == null) {
            Log.e(TAG, "Errore durante l'apertura del GeoPackage: ${geoPackageFile.name}")
            return@withContext false
        }
        geoPackageInstance = openedGeoPackage
        Log.i(TAG, "GeoPackage '${openedGeoPackage.name}' aperto con successo.")

        // Carica la configurazione e popola la lista delle feature
        loadConfigAndPopulateFeatures(openedGeoPackage)
        return@withContext true // Successo

    } catch (e: Exception) {
        Log.e(TAG, "Eccezione durante l'apertura del GeoPackage: ${geoPackageFile.name}", e)
        geoPackageInstance = null
        return@withContext false // Fallimento
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
                    labelBuilder.append(description)
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
