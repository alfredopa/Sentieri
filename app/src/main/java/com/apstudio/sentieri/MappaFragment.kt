package com.apstudio.sentieri

import android.Manifest
import android.app.Activity
import android.content.ComponentCallbacks2
import android.content.ComponentName
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Paint
import android.icu.text.SimpleDateFormat
import android.location.LocationManager
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import android.view.KeyEvent
import android.view.KeyEvent.KEYCODE_VOLUME_DOWN
import android.view.KeyEvent.KEYCODE_VOLUME_UP
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.toColorInt
import androidx.core.net.toUri
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import androidx.navigation.fragment.findNavController
import androidx.preference.PreferenceManager
import btools.routingapp.IBRouterService
import com.apstudio.sentieri.MapUtils.convertMillisToISO8601JavaTime
import com.apstudio.sentieri.MapUtils.dataOraIso8601
import com.apstudio.sentieri.MapUtils.disegnaLine
import com.apstudio.sentieri.MapUtils.formatSeconds
import com.apstudio.sentieri.MapUtils.getFileNameFromUri
import com.apstudio.sentieri.MapUtils.showCustomSnackbar
import com.apstudio.sentieri.databinding.FragmentMappaBinding
import com.apstudio.sentieri.db.FotoPoi
import com.apstudio.sentieri.db.FotoPoiDao
import com.apstudio.sentieri.db.LocationRepository
import com.apstudio.sentieri.db.PoiDB
import com.apstudio.sentieri.db.PoiDao
import com.apstudio.sentieri.db.Sentieri
import com.apstudio.sentieri.db.SentieriDB
import com.apstudio.sentieri.db.TrackDao
import com.apstudio.sentieri.layer.FeatureTableInfo
import com.apstudio.sentieri.layer.LAYER_DIALOG_REQUEST_KEY
import com.apstudio.sentieri.layer.LayerViewModel
import com.apstudio.sentieri.layer.LineStringFeature
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import mil.nga.geopackage.GeoPackageFactory
import mil.nga.geopackage.features.user.FeatureCursor
import mil.nga.geopackage.features.user.FeatureDao
import mil.nga.geopackage.features.user.FeatureRow
import mil.nga.geopackage.geom.GeoPackageGeometryData
import mil.nga.sf.GeometryType
import mil.nga.sf.LineString
import mil.nga.sf.MultiPolygon
import net.federicomatera.agpxp.GpxParser
import net.federicomatera.agpxp.GpxWriter
import net.federicomatera.agpxp.models.Gpx
import net.federicomatera.agpxp.models.GpxMetadata
import net.federicomatera.agpxp.models.Link
import net.federicomatera.agpxp.models.Track
import net.federicomatera.agpxp.models.WayPoint
import org.mapsforge.map.rendertheme.ExternalRenderTheme
import org.mapsforge.map.rendertheme.InternalRenderTheme
import org.mapsforge.map.rendertheme.XmlRenderTheme
import org.osmdroid.api.IGeoPoint
import org.osmdroid.api.IMapController
import org.osmdroid.gpkg.overlay.OsmMapShapeConverter
import org.osmdroid.gpkg.overlay.features.MarkerOptions
import org.osmdroid.gpkg.overlay.features.PolygonOptions
import org.osmdroid.gpkg.overlay.features.PolylineOptions
import org.osmdroid.mapsforge.MapsForgeTileProvider
import org.osmdroid.mapsforge.MapsForgeTileSource
import org.osmdroid.tileprovider.MapTileProviderBasic
import org.osmdroid.tileprovider.modules.ArchiveFileFactory
import org.osmdroid.tileprovider.modules.OfflineTileProvider
import org.osmdroid.tileprovider.tilesource.MapBoxTileSource
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.tileprovider.util.SimpleRegisterReceiver
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.FolderOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.compass.CompassOverlay
import org.osmdroid.views.overlay.compass.InternalCompassOrientationProvider
import org.osmdroid.views.overlay.infowindow.BasicInfoWindow
import org.osmdroid.views.overlay.simplefastpoint.LabelledGeoPoint
import org.osmdroid.views.overlay.simplefastpoint.SimpleFastPointOverlay
import org.osmdroid.views.overlay.simplefastpoint.SimpleFastPointOverlayOptions
import org.osmdroid.views.overlay.simplefastpoint.SimplePointTheme
import java.io.File
import java.io.IOException
import java.text.DecimalFormat
import java.text.NumberFormat
import java.util.Date
import java.util.Locale

private const val TAG = "MappaFragment"

class MappaFragment : Fragment(), MenuProvider, SharedPreferences.OnSharedPreferenceChangeListener,
    View.OnKeyListener, ComponentCallbacks2 {

    companion object {
        const val SEND_LOCATION_ACTION = "com.apstudio.sentieri.posizione"
        private const val TAG_AUDIO = "AudioRecording" // Tag per log audio
        // Il nome del package dell'app BRouter e il nome del servizio (dal manifest di BRouter)
        private const val BROUTER_PACKAGE = "btools.routingapp"
        private const val BROUTER_SERVICE_CLASS = "btools.routingapp.BRouterService"
    }

    // Struttura dati per contenere i risultati dell'elaborazione in background
    private data class ProcessedFeatureData(
        val points: MutableList<IGeoPoint>?,
        val polygons: MutableList<Polygon>?,
        val lineStrings: MutableList<LineStringFeature>?
    )

    private lateinit var viewModel: SentieriViewModel
    private val METERS_IN_A_KILOMETER = 1000.0 // Changed from Int to Double for precision
    private val SECONDS_IN_AN_HOUR = 3600.0 // Changed from Int to Double for precision

    // viewModel del LocationService con scope Application

    val layerModel: LayerViewModel by lazy {
        val application = requireActivity().application
        ViewModelProvider(
            application as ViewModelStoreOwner,
            ViewModelProvider.AndroidViewModelFactory.getInstance(application)
        )[LayerViewModel::class.java]
    }

    private var _binding: FragmentMappaBinding? = null
    private val binding get() = _binding!!

    private lateinit var database: SentieriDB
    private lateinit var bottomSheetBehavior: BottomSheetBehavior<ConstraintLayout>

    private val SELECT_GPX_FILE = 10
    private val SELECT_MAP_FILE = 20
    private lateinit var gpsMarker: Marker
    private val displayedTopoMarkers = mutableListOf<Marker>()
    private var uri: Uri? = null
    private lateinit var currentTrackPolyline: Polyline // La traccia che disegna sulla mappa
    private var alertDialog: AlertDialog? = null

    // memorizza istanza del menu per aggiornare icone
    private var menu: Menu? = null

    //gestione preferenze e listener
    private lateinit var preferenze: SharedPreferences
    private val mapView: MapView
        get() = binding.Mapview

    //icone blocco mappa
    private val PIN_RED = R.drawable.pin_rosso
    private val PIN_BLACK = R.drawable.pin_nero
    private var coloreTraccia: Int = 0
    // Variabili per la registrazione audio
    private var audioFileName: String? = null
    private var mediaRecorder: MediaRecorder? = null
    private var audioOutputFile: File? = null
    private var currentAudioFilePath: String? =
        null // Per salvare il percorso dell'ultima registrazione
    private var isAudioRecording = false
    private val recordingDurationMs: Long = 5000 // 5 secondi
    private val audioHandler = Handler(Looper.getMainLooper())
    // BRouter
    private var brouterService: IBRouterService? = null
    private var isBound = false
    private var destinationMarker: Marker? = null
    private var isSelectingDestination = false
    private var startPointForRouting: GeoPoint? = null
    private var endPointForRouting: GeoPoint? = null
    private val connection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            brouterService = IBRouterService.Stub.asInterface(service)
            isBound = true
            Log.d(TAG, "BRouterService connesso con successo.")

            // Se ci sono punti in attesa (impostati dal click del pulsante), calcola il percorso ora.
            if (startPointForRouting != null && endPointForRouting != null) {
                calculateRoute(startPointForRouting!!, endPointForRouting!!)
                // Pulisci i punti per evitare ricalcoli indesiderati
                startPointForRouting = null
                endPointForRouting = null
            }
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            brouterService = null
            isBound = false
            Log.d(TAG, "BRouterService disconnesso.")
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            // Assicurati che AppSentieri sia il nome corretto della tua classe Application
            // e che il ViewModelProvider sia configurato correttamente.
            viewModel =
                ViewModelProvider(requireActivity().application as AppSentieri)[SentieriViewModel::class.java]
        } catch (e: Exception) {
            Log.e(TAG, "FATALE: Errore durante l'inizializzazione del ViewModel in onCreate!", e)
            // Considera di gestire questo errore in modo più drastico se l'app non può funzionare senza viewModel
        }
        // Inizializza le preferenze e registra il listener
        preferenze = PreferenceManager.getDefaultSharedPreferences(requireContext())
        preferenze.registerOnSharedPreferenceChangeListener(this)
        // Legge se esiste SENSORE BAROMETRO da Preferences
        //haBaro indica se esiste sensore barometrico fisico
        if (preferenze.contains("haBaro")) {
            viewModel.haBaro = preferenze.getBoolean("haBaro", false)
            // setBaro indica se si preferisce usare il sensore barometrico fisico oppure no
            // in mancanza del sensore utilizza solo gps per altitudine
            if (preferenze.contains("setBaro")) {
                viewModel.setBaro = preferenze.getBoolean("setBaro", false)
            }
        }
        // Get the database instance (using the singleton)
        database = SentieriDB.getInstance(requireContext())
    }

    private fun onReturnFromLayerDialog(featureInfo: FeatureTableInfo) {
        if (_binding == null) {
            Log.w(TAG, "onReturnFromLayerDialog called when _binding is null. Aborting.")
            return
        }

        var needsInvalidate = false

        if (featureInfo.listOverlay == null) {
            featureInfo.listOverlay = mutableListOf()
        }

        featureInfo.listOverlay?.forEach { existingOverlay ->
            mapView.overlayManager.remove(existingOverlay)
        }

        if (featureInfo.isVisible) {
            if (featureInfo.listOverlay!!.isNotEmpty()) {
                featureInfo.listOverlay!!.forEach { overlay ->
                    overlay.isEnabled = true
                    if (!mapView.overlays.contains(overlay)) {
                        mapView.overlayManager.add(overlay)
                    }
                    // Re-attach listeners now that the overlay is added to the new map
                    reattachListenersToOverlay(overlay)
                }
                needsInvalidate = true
            } else {
                puntiSuMappa(featureInfo.name, featureInfo)
                // puntiSuMappa is responsible for initial listener setup and invalidation
            }
        } else {
            featureInfo.listOverlay!!.forEach { overlay ->
                overlay.isEnabled = false
                // Listeners don't need to be re-attached if not visible,
                // but ensure they are properly configured if they become visible again.
                // The reattachListenersToOverlay call when isVisible becomes true will handle it.
            }
            needsInvalidate = true
        }

        if (needsInvalidate) {
            mapView.invalidate()
        }
    }

    // Helper function to re-attach listeners
    private fun reattachListenersToOverlay(
        overlay: org.osmdroid.views.overlay.Overlay
    ) {
        if (!isAdded || context == null) { // Ensure fragment is attached and has context
            Log.w(TAG, "reattachListenersToOverlay: Fragment not attached or context is null.")
            return
        }

        when (overlay) {
            is SimpleFastPointOverlay -> {
                // Re-attach listener for SimpleFastPointOverlay (created by creaOverlayPunti)
                overlay.setOnClickListener { points, pointClicked ->
                    (points[pointClicked] as? LabelledGeoPoint)?.label?.let { label ->
                        mostraAlertDialogSemplice(label) //, featureInfo.descrTabella)
                    }
                }
            }

            is FolderOverlay -> {
                // Re-attach listeners for items within a FolderOverlay
                overlay.items.forEach { item ->
                    when (item) {
                        is Polygon -> {
                            // Re-attach listener for Polygons
                            item.setOnClickListener { polygon, map, eventPos ->
                                val retrievedLabel = polygon.relatedObject as? String
                                if (retrievedLabel != null) {
                                    mostraAlertDialogSemplice(retrievedLabel) //, featureInfo.descrTabella)
                                }
                                true
                            }
                        }

                        is Polyline -> {
                            // Re-attach listener for Polylines
                            // Crucially, re-initialize InfoWindow with the current mapView instance
                            item.infoWindow = BasicInfoWindow(
                                R.layout.bonuspack_bubble,
                                mapView
                            )
                            item.setOnClickListener { clickedPolyline, map, eventPosition ->
                                clickedPolyline.infoWindowLocation = eventPosition
                                clickedPolyline.showInfoWindow()
                                map.controller.animateTo(eventPosition)
                                true
                            }
                        }
                    }
                }
            }
            // Add other overlay types here if necessary
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentMappaBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // Dì al sistema che questa View può ricevere il focus.
        view.isFocusableInTouchMode = true
        // Richiedi esplicitamente il focus per questa View.
        view.requestFocus()
        // Imposta il listener per gli eventi della tastiera su questa View.
        view.setOnKeyListener(this)
        // verifica se sono passati argomenti
        // argomenti da gpx
        arguments?.getString("gpx_file_uri")?.let { uriString ->
            val gpxUri = uriString.toUri()
            caricaGPX(gpxUri)
            arguments?.remove("gpx_file_uri")
        }

        // punto passato da ricerca Toponimi
        arguments?.let { bundle ->
            val latitude = bundle.getDouble(
                "latitude",
                Double.NaN
            ) // Usa un valore di default o controlla se esiste la chiave
            val longitude = bundle.getDouble("longitude", Double.NaN)

            if (!latitude.isNaN() && !longitude.isNaN()) {
                val targetPoint = GeoPoint(latitude, longitude)
                mapView.controller.setCenter(targetPoint)
                mapView.controller.setZoom(15.0)
                Log.d(TAG, "MapView onviewcreated: $targetPoint")
                mapView.controller.animateTo(targetPoint)
            }
            arguments?.clear()
        }

        // Imposta il listener per il risultato da GpkgLayer
        parentFragmentManager.setFragmentResultListener(
            LAYER_DIALOG_REQUEST_KEY,
            viewLifecycleOwner
        ) { requestKey, bundle ->
            // Questo blocco viene eseguito quando GpkgLayer invia un risultato
            // con la LAYER_DIALOG_REQUEST_KEY specificata.
            if (requestKey == LAYER_DIALOG_REQUEST_KEY) {
                layerModel.featureList.forEach { featureInfo ->
                    //if (featureInfo.isVisible)
                    Log.d(
                        TAG,
                        "FragmentResultListener processing layer: " + featureInfo.name + ", isVisible: " + featureInfo.isVisible
                    )
                    onReturnFromLayerDialog(featureInfo)
                }
                // Puoi recuperare dati dal bundle se GpkgLayer li ha inviati
                // val userAction = bundle.getString("userAction")
                mapView.invalidate()
            }
        }
        // Ascolta i risultati da FeatureList
        parentFragmentManager.setFragmentResultListener(
            "feature_click_request",
            this
        ) { requestKey, bundle ->
            if (requestKey == "feature_click_request") {
                val latitude = bundle.getDouble("clicked_latitude")
                val longitude = bundle.getDouble("clicked_longitude")
                // Elevation è opzionale
                val elevation =
                    if (bundle.containsKey("clicked_elevation")) bundle.getDouble("clicked_elevation") else null
                // Ora hai le coordinate, usale come preferisci
                // Esempio: centra la mappa su questo punto
                val clickedPoint = GeoPoint(latitude, longitude)
                elevation?.let { clickedPoint.altitude = it }
                mapView.controller.animateTo(clickedPoint) // o setCenter
                Toast.makeText(
                    requireContext(),
                    "Punto cliccato: Lat $latitude, Lon $longitude",
                    Toast.LENGTH_LONG
                ).show()
                // Potresti anche voler aggiornare viewModel.poi se necessario
                viewModel.poi = clickedPoint // Assumendo che viewModel.poi sia un GeoPoint
            }
        }
        // aggiunge il bottomsheet ed il menu
        bottomSheetBehavior = BottomSheetBehavior.from(binding.cruscotto.root)
        // Set the initial state to hidden AFTER the layout is complete
        bottomSheetBehavior.isHideable = true // Assicurati che possa essere nascosto
        bottomSheetBehavior.skipCollapsed = false // IMPORTANTE: non saltare lo stato collassato
        // Imposta la peekHeight desiderata per quando è collassato
        bottomSheetBehavior.peekHeight = 120 // O il valore in pixel desiderato
        // Inizia nascosto
        binding.cruscotto.root.post { // Per sicurezza, attendi il layout
            if (isAdded) {
                bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
            }
        }
        // Assegna un listener alla mappa per gestire la pressione dei tasti
        mapView.isFocusableInTouchMode = true
        mapView.requestFocus()
        mapView.setOnKeyListener(this)
        mapView.setDestroyMode(false)
        // verifica se è stata memorizzato in MenuMap l'indice della mappa da usare
        if (preferenze.contains("MenuMap")) {
            viewModel.menuMap = preferenze.getInt("MenuMap", 0)
            // Era selezionata mappa offline
            if (viewModel.menuMap == 0) {
                mapView.isTilesScaledToDpi = false
                mapView.setUseDataConnection(false)
                // recupera Uri della mappa offline da preferenze
                if (preferenze.contains("URIMappa")) {
                    val uriMappa = preferenze.getString("URIMappa", "")!!.toUri()
                    apreMappa(uriMappa)
                    viewModel.uriMappa = uriMappa
                    menu?.findItem(0)?.isChecked = true
                } else {
                    // se non trova stringa mappa carica OpenStreetMap
                    mapView.isTilesScaledToDpi = true
                    mapView.setUseDataConnection(true)
                    //if (preferenze.contains("URLMappa")) {
                    online(viewModel.menuMap)
                }
            } else {
                // era selezionata mappa online
                mapView.isTilesScaledToDpi = true
                mapView.setUseDataConnection(true)
                online(viewModel.menuMap)
            }
        } else {
            // nessuna mappa selezionata apre OpenStreetMap
            mapView.isTilesScaledToDpi = true
            mapView.setUseDataConnection(true)
            online(1)
            viewModel.menuMap = 1 // indica mappa OpenStreetMap
        }

        val coloreDefault = R.color.black
        coloreTraccia = if (preferenze.contains("colore_traccia")) {
            preferenze.getInt("colore_traccia", coloreDefault)
        } else {
            coloreDefault
        }

        val menuHost: MenuHost = requireActivity()
        menuHost.addMenuProvider(this, viewLifecycleOwner, Lifecycle.State.RESUMED)
        //SimpleFileLogger.log("Mappa", "onViewCreated ")
        //aggiunge i folder overlay, listaTracce che conterrà tutte le tracce aggiunte  overlays alla mapview
        // e rectraccia che conterrà la traccia corrente
        if (mapView.overlays.isEmpty()) {
            //val overlayManager = mapView.overlayManager
            mapView.overlayManager.add(viewModel.listaTracce)
            mapView.overlayManager.add(viewModel.recTraccia)
            mapView.overlayManager.add(viewModel.topoLayer) // folder overlay toponimi
            // Reimposta il listener per ogni polyline nel FolderOverlay, necessario per mantenere l'evento dopo il cambio del fragment
            for (overlay in viewModel.listaTracce.items) {
                if (overlay is Polyline) {
                    setPolylineClickListener(overlay)
                }
                if (overlay is Marker) {
                    setMarkerClickListener(overlay)
                }
            }
            // Aggiunta marker GPS e compass alla mappa e non ai folder overlay
            // Marker per GPS attivato
            gpsMarker = Marker(mapView)
            // il  marker segue il punto su mappa
            gpsMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            gpsMarker.setVisible(false)
            gpsMarker.title = "Gps"
            gpsMarker.icon = ResourcesCompat.getDrawable(
                requireContext().resources,
                R.drawable.punto_gps,
                requireContext().theme
            )
            mapView.overlayManager.add(gpsMarker)

            // add compass to map
            val compassOverlay =
                CompassOverlay(context, InternalCompassOrientationProvider(context), mapView)
            compassOverlay.enableCompass()
            compassOverlay.setCompassCenter(36f, 36f)
            mapView.overlayManager.add(compassOverlay)
        }

        mapView.zoomController?.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
        mapView.setMultiTouchControls(true)
        mapView.minZoomLevel = 7.0
        mapView.maxZoomLevel = 19.0
        val mapController: IMapController = MapController(mapView)
        mapController.setCenter(viewModel.ultPosizione)
        mapController.setZoom(viewModel.ultZoom.toDouble())

        aggiornaUIFabBlocMappa()

        // Bottone per bloccare ancoraggio mappa al gps
        binding.fabBlocMappa.setOnClickListener {
            viewModel.bloccaMappa = !viewModel.bloccaMappa // Cambia lo stato nel ViewModel
            aggiornaUIFabBlocMappa() // Aggiorna la UI del FAB
        }

        // Bottone per attivare la fotocamera
        binding.camera.setOnClickListener {
            //Log.d("camera", "viemodel ${viewModel.traccia.points.size}")
            val directions =
                MappaFragmentDirections.actionMappaFragmentToCameraFragment()
            this@MappaFragment.findNavController().navigate(directions)
        }

        // Imposta il listener per il click del bottone Allarma
        binding.cruscotto.btnAllarme.setOnClickListener {
            viewModel.toggleAllarmeState()
        }

        // Listener per il Floating Action Button
        binding.fabSelectDestination.setOnClickListener {
            if (viewModel.isRecording && viewModel.isFixed) {
                if (!isSelectingDestination) {
                    enterDestinationSelectionMode()
                } else {
                    exitDestinationSelectionMode()
                }
            } else {
                Toast.makeText(requireContext(), "Calcolo percorso solo con registrazione avviata", Toast.LENGTH_SHORT).show()
            }

        }

// Listener per il pulsante di conferma
        binding.buttonConfirmDestination.setOnClickListener {
            // 1. Prendi il punto di destinazione dal marker
            val destinationPoint = destinationMarker?.position
            if (destinationPoint == null) {
                Toast.makeText(requireContext(), "Posizione di destinazione non valida.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // 2. Salva i punti nelle variabili di istanza
            //    Usa la posizione GPS corrente come partenza, o una fissa se non disponibile
            startPointForRouting = gpsMarker.position?:currentTrackPolyline.points[0]
            endPointForRouting = destinationPoint

            // 3. Avvia il processo di binding. La logica in onServiceConnected farà il resto.
            if (!isBound) {
                val intent = Intent().apply {
                    component = ComponentName(
                        BROUTER_PACKAGE,
                        BROUTER_SERVICE_CLASS
                    )
                }
                try {
                    requireContext().bindService(intent, connection, Context.BIND_AUTO_CREATE)
                    Log.d(TAG, "Tentativo di connessione a BRouterService per il calcolo del percorso...")
                    Toast.makeText(requireContext(), "Calcolo percorso in corso...", Toast.LENGTH_SHORT).show()
                } catch (e: SecurityException) {
                    Log.e(TAG, "Impossibile connettersi: BRouter non è installato o mancano i permessi. ${e.message}")
                    Toast.makeText(requireContext(), "Impossibile avviare BRouter.", Toast.LENGTH_LONG).show()
                    // Pulisci i punti in caso di errore
                    startPointForRouting = null
                    endPointForRouting = null
                }
            } else {
                // Se il servizio è GIÀ connesso, possiamo calcolare direttamente.
                calculateRoute(startPointForRouting!!, endPointForRouting!!)
                startPointForRouting = null
                endPointForRouting = null
            }

            // Esci dalla modalità selezione
            exitDestinationSelectionMode()
        }


        // avvia gli observer per aggiornamento dati cruscotto
        val numberFormat = NumberFormat.getNumberInstance(Locale.getDefault())
        viewModel.distanzaMetri.observe(viewLifecycleOwner) { distanzaMetri ->
            binding.cruscotto.tvDist.text = MapUtils.formattastring(distanzaMetri)
        }
        LocationRepository.velocita.observe(viewLifecycleOwner) { velocita ->
            binding.cruscotto.tvVelo.text = getString(R.string.kmh, velocita.toInt())
        }
        viewModel.quota.observe(viewLifecycleOwner) { quota ->
            binding.cruscotto.tvQuota.text = numberFormat.format(quota)
        }
        viewModel.dislivPiu.observe(viewLifecycleOwner) { dislivPiu ->
            binding.cruscotto.tvDPiu.text = numberFormat.format(dislivPiu.toInt())
        }
        viewModel.dislivMeno.observe(viewLifecycleOwner) { dislivMeno ->
            binding.cruscotto.tvDMeno.text = numberFormat.format(dislivMeno.toInt())
        }
        viewModel.tempoTrascorso.observe(viewLifecycleOwner) { tempoTrascorso ->
            binding.cruscotto.tvTempo.text = tempoTrascorso
        }
        viewModel.secondiMovimento.observe(viewLifecycleOwner) { secondiMovimento ->
            binding.cruscotto.tvTempoMov.text = formatSeconds(secondiMovimento)
        }
        // Osserva il LiveData dello stato dell'allarme
        viewModel.isAllarmeAttivo.observe(viewLifecycleOwner) { isAttivo ->
            // Quando lo stato cambia (o alla prima osservazione), aggiorna la UI
            updateBtnAllarmeUI(isAttivo)
        }

        // determina se l'altitudine deve essere barometrica o dal GPS
        // setta il flag is_Calibrato nel gpsViewModel, utilizzato da LocationService
        viewModel.isCalibrato.observe(viewLifecycleOwner) {
            if (it) {
                binding.cruscotto.tvCalcQuota.text = "BARO"
                LocationRepository.usaBaro = true
            } else {
                binding.cruscotto.tvCalcQuota.text = "GPS"
                LocationRepository.usaBaro = false
            }
        }

        // Aggiorna la posizione del marker GPS
        LocationRepository.gpsStatus.observe(viewLifecycleOwner) { status ->
            updateGpsIcon(status) // Aggiorna l'icona in base al nuovo stato
        }

        // Observer per i dati di localizzazione (posizione e orientamento)
        viewModel.locationData.observe(viewLifecycleOwner) { locationData ->
            if (!isFragmentVisibleAndActive()) return@observe

            val newGeoPoint = locationData.geoPoint
            if (newGeoPoint.latitude == 0.0 && newGeoPoint.longitude == 0.0) {
                return@observe // Ignora posizioni non valide o iniziali

            }
            // Aggiorna la posizione del marker GPS
            if (::gpsMarker.isInitialized) {
                gpsMarker.position = newGeoPoint
            }

            // Aggiunge il marker d'inizio al primo punto della registrazione
            if (viewModel.isRecording && LocationRepository.trackPointsList.size == 1) {
                if (isAdded && context != null) {
                    MapUtils.markInizioFine(
                        requireContext(),
                        newGeoPoint,
                        mapView,
                        viewModel.recTraccia,
                        0
                    )
                }
            }

            // Se il blocco mappa è attivo, orienta e centra la mappa
            if (viewModel.bloccaMappa) {
                val gpsbearing = locationData.bearing
                var t: Float = 360 - gpsbearing
                if (t < 0) t += 360f
                if (t > 360) t -= 360f
                t = (t.toInt() / 5 * 5).toFloat() // Arrotonda ai 5 gradi più vicini

                mapView.mapOrientation = t
                mapView.controller?.animateTo(newGeoPoint)
            }

            // Logica per l'allarme "Fuori Traccia"
            if (viewModel.alertFuoriTraccia && viewModel.tracciaDaSeguire.isNotEmpty()) {
                if (!isAlertDialogShowing()) {
                    val tracciaDaSeguire = viewModel.listaTracce.items.find {
                        it is Polyline && it.title == viewModel.tracciaDaSeguire
                    } as? Polyline

                    tracciaDaSeguire?.let {
                        if (!it.isCloseTo(newGeoPoint, 30.0, mapView)) {
                            mostraAllarmeFuoriTraccia()
                        }
                    }
                }
            }
        }

        // 1. Inizializza la Polyline per la traccia in registrazione
        currentTrackPolyline = Polyline()
        // Imposta lo stile per renderla visibile
        currentTrackPolyline.outlinePaint.color = coloreTraccia //Color.RED
        currentTrackPolyline.outlinePaint.strokeWidth = 10f
        mapView.overlays.add(currentTrackPolyline)

        // 2. Observer per la LISTA COMPLETA (per il disegno iniziale/dopo rotazione)
        LocationRepository.trackPoints.observe(viewLifecycleOwner) { fullTrack ->
            Log.d(
                TAG,
                "Observer 'trackPoints' (lista completa) attivato con ${fullTrack.size} punti."
            )
            // Imposta tutti i punti in una volta. Questo accade raramente.
            currentTrackPolyline.setPoints(fullTrack)
            mapView.invalidate()
        }

        // 3. Observer per il NUOVO PUNTO (per aggiornamenti efficienti)
        LocationRepository.newTrackPoint.observe(viewLifecycleOwner) { newPoint ->
            Log.d(TAG, "Observer 'newTrackPoint' (punto singolo) attivato.")
            // Aggiungi solo l'ultimo punto alla linea. Questo è molto più veloce.
            currentTrackPolyline.addPoint(newPoint)
            mapView.invalidate()
        }

    }

    /*
    DA AGGIUNGERE PER OBSERVER
    private fun setupRecordingStateObserver() {
        viewModel.isRecording.observe(viewLifecycleOwner) { isRecording ->
            // Questa lambda verrà eseguita OGNI VOLTA che isRecording cambia.
            // Tutta la logica della UI va qui.

            if (isRecording) {
                // STATO: REGISTRAZIONE ATTIVA
                gpsMarker.setVisible(true)
                accendiSchermo()
                requireActivity().startService(Intent(context, LocationService::class.java))
                bottomSheetBehavior.isHideable = false
                bottomSheetBehavior.peekHeight = 120
                bottomSheetBehavior.state = BottomSheetBehavior.STATE_HALF_EXPANDED
                LocationRepository.updateGpsStatus("started")
                // Aggiorna anche l'icona del menu se necessario
                menu?.findItem(R.id.action_registra)?.setIcon(R.drawable.ic_stop)

            } else {
                // STATO: REGISTRAZIONE FERMA
                requireActivity().stopService(Intent(context, LocationService::class.java))
                gpsMarker.setVisible(false)
                requireActivity().window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                LocationRepository.updateGpsStatus("stopped")
                if (viewModel.haBaro) viewModel.setBaro = true // Ripristina preferenza

                bottomSheetBehavior.isHideable = true
                bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN

                // Aggiorna l'icona del menu
                menu?.findItem(R.id.action_registra)?.setIcon(R.drawable.ic_rec)
            }
            mapView.invalidate()
        }
    }*/


    private fun mostraAllarmeFuoriTraccia() {
        val allarme = EditText(requireActivity())
        val builder = AlertDialog.Builder(requireContext(), R.style.AlertDialogCustom)
        with(builder)
        {
            setTitle("Fuori traccia")
            val layout = LinearLayout(context)
            layout.orientation = LinearLayout.VERTICAL
            allarme.setText("ATTENZIONE SEI FUORI TRACCIA")
            layout.addView(allarme)
            builder.setView(layout)
            setNegativeButton(android.R.string.cancel) { _, _ -> }
            alertDialog = create()
            alertDialog?.show()
        }
    }

    // aggiunge il click listener alla polyline per aprire l'info window
    private fun setPolylineClickListener(polyline: Polyline) {
        polyline.setOnClickListener { mpolyline, mapView, eventPos ->
            // Il layout è stato copiato nelle risorse potrebbe differire dall'originale
            //Log.d("MappaFragment", "OnClickListener della Polyline ATTIVATO! Titolo: ${mpolyline.title}")
            mpolyline.infoWindow = BasicInfoWindow(R.layout.bonuspack_bubble, mapView)
            mpolyline.infoWindowLocation = eventPos
            mpolyline.showInfoWindow()
            mapView.controller.animateTo(eventPos)
            true // Ritorna true per indicare che l'evento è stato gestito
        }
    }

    // aggiunge il click listener al marker per aprire l'info window
    private fun setMarkerClickListener(marker: Marker) {
        marker.setOnMarkerClickListener { mMarker, mapView ->
            // Apri la info window qui, usando eventPos come posizione
            mMarker.infoWindow = BasicInfoWindow(R.layout.bonuspack_bubble, mapView)
            mMarker.showInfoWindow()
            mapView.controller.animateTo(marker.position)
            true // Ritorna true per indicare che l'evento è stato gestito
        }
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()
        //Log.d("Mappa", "MappaFragment onResume ")
        // Controlli per verificare valori da altri fragment da scheda sentieri e visualizzazione waypoint
        // verifica se è valorizzata line, quindi è stato passsata dal pulsante Segui
        // e lo mostra sulla mappa qui carica traccia dal db con waypoint e lista foto
        if (viewModel.line.actualPoints.isNotEmpty()) {
            if (viewModel.line.title.isNotEmpty()) {
                (activity as AppCompatActivity).supportActionBar?.title = viewModel.line.title
            }
            val nuovaTraccia = Polyline(mapView)
            nuovaTraccia.setPoints(viewModel.line.actualPoints)
            val mbounds = viewModel.line.bounds
            nuovaTraccia.title = viewModel.line.title
            setPolylineClickListener(nuovaTraccia)
            disegnaLine(nuovaTraccia)
            viewModel.listaTracce.add(nuovaTraccia)
            addMarker(nuovaTraccia)
            viewModel.listaTracce.items.lastIndex
            // il post serve a terminare la fase di disegno prima di eseguire lo zoom
            mapView.post {
                mapView.zoomToBoundingBox(mbounds.increaseByScale(1.2f), false)
            }
            viewModel.line.actualPoints.clear()

            // carica i waypoints dalla lista wayPoints da non salvare con traccia
            if (viewModel.wayPoint.isNotEmpty()) {
                viewModel.wayPoint.forEach {
                    val poiMarker = Marker(mapView)
                    poiMarker.title = it.name
                    poiMarker.icon = ResourcesCompat.getDrawable(
                        requireContext().resources,
                        R.drawable.ic_finish,
                        requireContext().theme
                    )
                    poiMarker.position.latitude = it.latitude
                    poiMarker.position.longitude = it.longitude
                    poiMarker.position.altitude = it.elevation ?: 0.0
                    viewModel.listaTracce.add(poiMarker)
                }
            }

            // carica i waypoints creati durante la registrazione da salvare con traccia
            if (viewModel.poiDBList.isNotEmpty()) {
                viewModel.poiDBList.forEach {
                    val poiMarker = Marker(mapView)
                    poiMarker.title = it.NomePOI
                    poiMarker.icon = ResourcesCompat.getDrawable(
                        requireContext().resources,
                        R.drawable.ic_finish,
                        requireContext().theme
                    )
                    poiMarker.position.latitude = it.Latit
                    poiMarker.position.longitude = it.Longit
                    poiMarker.position.altitude = it.Ele
                    viewModel.listaTracce.add(poiMarker)
                }
            }
        }

        if (viewModel.poi != GeoPoint(0.0, 0.0, 0.0)) {
            // ciclo per trovare il waypoint corrispondente da visualizzare sulla mappa
            for (overlay in viewModel.listaTracce.items) {
                if (overlay is Marker && overlay.position == viewModel.poi) {
                    val alMarker: Marker = overlay
                    alMarker.infoWindow = BasicInfoWindow(R.layout.bonuspack_bubble, mapView)
                    alMarker.showInfoWindow()
                    // animazioni con velocità 0 altrimenti rallenta eccessivamente la visualizzazione
                    mapView.controller.animateTo(
                        alMarker.position,
                        viewModel.ultZoom.toDouble(),
                        0
                    )
                    GeoPoint(alMarker.position)
                    break
                }
            }
            viewModel.poi = GeoPoint(0.0, 0.0, 0.0)
        }

        if (viewModel.isRecording) {
            // in registrazione ripristina marker gps,bottomsheet allo stato precedente
            LocationRepository.updateGpsStatus(LocationRepository.gpsStatus.value!!)
            accendiSchermo()
            viewModel.locationData.value?.geoPoint?.let { gpsMarker.position = it }
            gpsMarker.setVisible(true)
            bottomSheetBehavior.isHideable = false
            bottomSheetBehavior.peekHeight = 120
            bottomSheetBehavior.state = viewModel.bottomState
            // RICHIEDI LA TRACCIA COMPLETA PER RIDISEGNARLA CORRETTAMENTE
            LocationRepository.requestFullTrack()
            showCustomSnackbar(requireContext(),"Registrazione in corso", Snackbar.LENGTH_SHORT)
            /*Snackbar.make(binding.root, "Registrazione in corso", Snackbar.LENGTH_SHORT).apply {
                setBackgroundTint(ContextCompat.getColor(requireContext(), R.color.purple_500))
                setTextColor(Color.WHITE)
                show()
            }*/
        }

        // ridisegna eventuali layer aggiunti da GpkgLayer
        layerModel.featureList.forEach { featureInfo ->
            //if (featureInfo.isVisible)
            Log.d(
                TAG,
                "onResume processing layer: " + featureInfo.name + ", isVisible: " + featureInfo.isVisible
            )
            onReturnFromLayerDialog(featureInfo)
        }

        // toponimi
        // Clear existing toponym markers
        displayedTopoMarkers.forEach { mapView.overlays.remove(it) }
        displayedTopoMarkers.clear()
        // Process selected toponyms from ViewModel
        if (viewModel.toponimiSelezionati.isNotEmpty()) {
            viewModel.toponimiSelezionati.forEach { topoData -> // topoData.id ora esiste
                val newMarker = RemovableMarker(mapView)
                newMarker.id = topoData.id // Imposta l'ID univoco sul marker!
                newMarker.position = GeoPoint(topoData.latitude, topoData.longitude)
                newMarker.title = topoData.name
                newMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                //Log.d("MappaFragment_Debug", "Creating marker: ID='${newMarker.id}', Title='${newMarker.title}'")

                newMarker.icon = ResourcesCompat.getDrawable(
                    requireContext().resources,
                    R.drawable.pin_rosso, requireContext().theme
                )

                newMarker.setOnMarkerClickListener { marker, mv ->
                    // ... (codice invariato per il click normale) ...
                    if (marker.isInfoWindowShown) {
                        marker.closeInfoWindow()
                    } else {
                        if (marker.infoWindow == null) {
                            marker.infoWindow = BasicInfoWindow(R.layout.bonuspack_bubble, mv)
                        }
                        marker.showInfoWindow()
                        mv.controller.animateTo(marker.position)
                    }
                    true
                }

                newMarker.onMarkerLongClick =
                    { markerInstance -> // markerInstance è il RemovableMarker
                        val toponimoDataToRemove = viewModel.toponimiSelezionati.find {
                            it.id == markerInstance.id // Cerca per ID univoco
                        }

                        if (toponimoDataToRemove != null) {
                            viewModel.toponimiSelezionati.remove(toponimoDataToRemove)
                            mapView.overlays.remove(markerInstance)
                            displayedTopoMarkers.remove(markerInstance)
                            mapView.invalidate()
                            Toast.makeText(
                                requireContext(),
                                "Rimosso ${markerInstance.title}",
                                Toast.LENGTH_SHORT
                            ).show()
                        } else {
                            //Log.w("MappaFragment_Debug", "TopoData not found for removal. Marker ID='${markerInstance.id}', Title='${markerInstance.title}'")
                        }
                        true // Event consumed
                    }
                displayedTopoMarkers.add(newMarker)
                mapView.overlays.add(newMarker)
            }
        }
        mapView.invalidate()

    }

    override fun onPrepareMenu(menu: Menu) {
        super.onPrepareMenu(menu)
        // soluzione per aggiornare icona gps dopo cambio fragment in quanto observer non aggiorna
        if (viewModel.isRecording) {
            LocationRepository.updateGpsStatus(LocationRepository.gpsStatus.value!!)
        }
    }

    override fun onPause() {
        super.onPause()
        // memorizza valori per ripristinare la mappa
        viewModel.ultZoom = mapView.zoomLevel
        viewModel.ultPosizione = mapView.mapCenter as GeoPoint
        //memorizza stato del bottomSheet
        if (::bottomSheetBehavior.isInitialized)
            viewModel.bottomState = bottomSheetBehavior.state
        mapView.onPause() //needed for compass, my location overlays, v6.0.0 and up
    }

    private fun offline() {
        val intent = Intent(Intent.ACTION_GET_CONTENT)
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        intent.addCategory(Intent.CATEGORY_OPENABLE)
        intent.type = "application/octet-stream"
        startActivityForResult(intent, SELECT_MAP_FILE)
    }

    // apertura mappa offline locale da Uri
    private fun apreMappa(uri: Uri) {
        val uriPathHelper = URIPathHelper()
        val filePath = uriPathHelper.getPath(requireContext(), uri)

        try {
            // Prova ad inizializzare OsmDroid qui
        } catch (e: Exception) {
            Log.e("Sentieri", "Errore inizializzazione OsmDroid", e)
        }
        //--------------------------------------------------------------------------------------------------
        val maps: Array<File?> = arrayOfNulls(1)
        val f = File(filePath!!)
        if (f.exists()) {
            maps[0] = f
        }

// estensioni registrate: zip, gemf, sqlite, mbtiles e map
        val extension = f.extension
        if (!ArchiveFileFactory.isFileExtensionRegistered(extension) && extension != "map") {
            val context: Context = requireActivity().application
            Toast.makeText(
                context,
                "Il file selezionato non contiene dati mappa",
                Toast.LENGTH_LONG
            ).show()
            return
        }
// Mappe MapsForge estensione .map il path del rendertheme è hard coded, da cambiare
        val forgeMappa: MapsForgeTileProvider
        val offlineMappa: OfflineTileProvider
        var theme: XmlRenderTheme?
        if (f.name.contains(".map")) {
            val mediaDir = requireContext().externalMediaDirs
            val documentsDir = mediaDir[0]
            var nomeTema = ""
            val folderTema = File("$documentsDir/Mappe/4UMaps/4UMaps.xml")
            if (folderTema.exists()) {
                theme = ExternalRenderTheme("$documentsDir/Mappe/4UMaps/4UMaps.xml")
                nomeTema = "4UMaps"
            } else {
                theme = InternalRenderTheme.OSMARENDER
            }
            val fromFiles = MapsForgeTileSource.createFromFiles(maps, theme, nomeTema)
            forgeMappa = MapsForgeTileProvider(
                SimpleRegisterReceiver(activity),
                fromFiles, null
            )
            mapView.tileProvider = forgeMappa
        } else {
            offlineMappa = OfflineTileProvider(
                SimpleRegisterReceiver(
                    requireContext()
                ), maps
            )
            mapView.tileProvider = offlineMappa
            val archives = offlineMappa.archives
// importante setIgnoreTileSource consente apertura rapida della mappa evitando il controllo del tipo di sorgente presente nel file tiles
            archives[0].setIgnoreTileSource(true)
        }

        viewModel.connessione = false
        mapView.setUseDataConnection(false)
        mapView.setTileSource(TileSourceFactory.DEFAULT_TILE_SOURCE)
        //Log.d("Mappa", "Mappa caricata  ")
        mapView.invalidate()
    }

    private fun online(mappa: Int) {
        var scarica: MapTileProviderBasic? = null
        viewModel.connessione = true
        // salvo indice menu selezionato
        viewModel.menuMap = mappa
        mapView.setUseDataConnection(true)
        when (mappa) {
            1 -> scarica =
                MapTileProviderBasic(context, TileSourceFactory.MAPNIK)  // OpenStreetmap
            2 -> scarica = MapTileProviderBasic(context, TileSourceFactory.OpenTopo) // OpenTopo
            3 -> scarica = MappaMapBox() // MapBox
        }
// salva la mappa scelta nelle preferenze
        preferenze.edit { putInt("MenuMap", mappa) }
        mapView.tileProvider = scarica
        mapView.invalidate()
    }

    private fun MappaMapBox(): MapTileProviderBasic {
        val MAPBOXSATELLITELABELLED: OnlineTileSourceBase =
            MapBoxTileSource("MapBox", 1, 19, 256, ".png")
        (MAPBOXSATELLITELABELLED as MapBoxTileSource).retrieveAccessToken(requireContext())
        MAPBOXSATELLITELABELLED.setMapboxMapid("mapbox.satellite")
        MAPBOXSATELLITELABELLED.accessToken =
            "pk.eyJ1IjoiYWxmcmVkb3BhIiwiYSI6ImNtMDBzMmQ3ODBoMWIya3NuejJ5NnNzMG0ifQ.kXnCG27oE6go9msYdp3pkA"
        //"pk.eyJ1IjoiYWxmcmVkb3BhIiwiYSI6ImNrd29tYXJiZjAwd24ydnJ0Yno3NGJ4aHUifQ.4QyOTn9AYZhWCyWSs36R_w"
        TileSourceFactory.addTileSource(MAPBOXSATELLITELABELLED)
        val bitmapProvider = MapTileProviderBasic(requireContext(), MAPBOXSATELLITELABELLED)
        return bitmapProvider
    }

    private fun altDaBaro() {
// chiede se calibrare il barometro oppure annullare
        if (viewModel.haBaro) {
            val dlgBaro = DlgFragment()
            dlgBaro.setOnclickCallback {
// annulla calibrazione quindi utilizza solo GPS, setBaro viene momentaneaente settato su false e riportato a true alla prossima registrazione
                viewModel.setBaro = false
                //viewModel.puntiPostFix = 0
                attivaGps()
            }
            dlgBaro.show(requireActivity().supportFragmentManager, "dlgBaro")
        }
    }

    // FINE REGISTRAZIONE TRACCIA
    private fun stopGPS() {
        // se non ha fixato non chiede di salvare
        if (!viewModel.isFixed) {
            stopObserver() // Arresta gli observer
            fermaRecording(false)
            azzeraCruscotto()
            return
        }
        // altrimenti chiede se salvare traccia
        val builder = AlertDialog.Builder(requireContext(), R.style.AlertDialogCustom)
        val inputEditTextField = EditText(requireActivity())
        inputEditTextField.setText("Traccia")
        with(builder)
        {   // chiede salvataggio traccia
            setTitle("Salva traccia")
            setMessage("Vuoi memorizzare la traccia").setView(inputEditTextField)
            setPositiveButton(
                "Ok"
            ) { _, _ ->
                fermaRecording(true)
                stopObserver() // Arresta gli observer
                salvaTraccia(inputEditTextField.text.toString())
                azzeraCruscotto()
            }
            setNegativeButton(android.R.string.cancel) { _, _ ->
                fermaRecording(false)
                azzeraCruscotto()
            }
            setNeutralButton("Continua") { _, _ -> }
            setCancelable(false) // Impedisce la chiusura tramite tocco esterno o tasto Indietro
            show()
        }
    }

    private fun fermaRecording(fine: Boolean = false) {
// ferma aggiornamenti posizione ui e ferma servizio LocationService
        // Log.d("Posizione","Stop servizio")
        requireActivity().stopService(Intent(context, LocationService::class.java))
        viewModel.stopUpdates()
        viewModel.isRecording = false
        gpsMarker.setVisible(false)
        LocationRepository.updateGpsStatus("stopped")
// rimuove impostazione schermo sempre acceso
        requireActivity().window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
// aggiunge marker fine percorso
        if (fine) {
            viewModel.locationData.value?.geoPoint?.let {
                MapUtils.markInizioFine(
                    requireContext(),
                    it,
                    mapView,
                    viewModel.recTraccia,
                    1
                )
            }
        }
//setBaro indica se si preferisce usare il sensore barometrico fisico oppure no
// a fine registrazione ripristina preferenza barometro
        if (viewModel.haBaro)
            viewModel.setBaro = true
        bottomSheetBehavior.peekHeight = 0
        bottomSheetBehavior.isHideable = true
        bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
        mapView.invalidate()
    }

    private fun stopObserver() {
        viewModel.distanzaMetri.removeObservers(viewLifecycleOwner)
        viewModel.velocita.removeObservers(viewLifecycleOwner)
        viewModel.quota.removeObservers(viewLifecycleOwner)
        viewModel.dislivPiu.removeObservers(viewLifecycleOwner)
        viewModel.dislivMeno.removeObservers(viewLifecycleOwner)
        viewModel.secondiMovimento.removeObservers(viewLifecycleOwner)
        //gpsViewModel.gpsStatus.removeObservers(viewLifecycleOwner)
    }

    private fun attivaGps() {
        val locationManager =
            requireActivity().getSystemService(Context.LOCATION_SERVICE) as LocationManager
        // Check if GPS provider is enabled
        val isGpsProviderEnabled =
            locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
        if (!isGpsProviderEnabled) {
            val builder = AlertDialog.Builder(requireContext(), R.style.AlertDialogCustom)
            with(builder)
            {
                setTitle("Sensore GPS")
                setMessage("Il dispositivo non è dotato di sensore GPS")
                setPositiveButton(
                    "Chiudi"
                ) { _, _ ->
                }
                    .show()
            }
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
// Richiedi l'autorizzazione solo se la versione dell'API è uguale o superiore a TIRAMISU
            if (ActivityCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
// L'autorizzazione non e stato ancora fatta, la richiesta deve essere fatta
                val REQUEST_CODE_POST_NOTIFICATIONS = 1
                ActivityCompat.requestPermissions(
                    requireContext() as Activity,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    REQUEST_CODE_POST_NOTIFICATIONS
                )
                return
            }
        }
        // pulizia eventuali registrazione precedente
        LocationRepository.clearTrack()
        viewModel.isFixed = false
// Cambia stato GPS ON
        gpsMarker.setVisible(true)
// imposta schermo sempre acceso
        accendiSchermo()
// inizio registrazione posizione
        viewModel.isRecording = true
        viewModel.oraInizio = System.currentTimeMillis()
        viewModel.startUpdates()
// avvia il servizio per tracciare locazione in background
        requireActivity().startService(Intent(context, LocationService::class.java))
        bottomSheetBehavior.isHideable = false
        bottomSheetBehavior.peekHeight = 120
        bottomSheetBehavior.halfExpandedRatio = 0.5f
        bottomSheetBehavior.state = BottomSheetBehavior.STATE_HALF_EXPANDED
        LocationRepository.updateGpsStatus("started")
        //updateGpsIcon("started")
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == SELECT_GPX_FILE && resultCode == AppCompatActivity.RESULT_OK) {
            if (data != null) {
                uri = data.data
                caricaGPX(uri!!)
                //uri?.run { caricaGPX(uri!!) }
            }
        }
        if (requestCode == SELECT_MAP_FILE && resultCode == AppCompatActivity.RESULT_OK) {
            if (data != null) {
                uri = data.data
                apreMappa(uri!!)
// salva la mappa offline scelta nelle preferenze
                preferenze.edit { putString("URIMappa", uri.toString()) }
                preferenze.edit { putInt("MenuMap", 0) }
                viewModel.uriMappa = uri!!
            }
        }
    }

    private fun caricaGPX(uri: Uri) {
//val line = Polyline(mapView, false, false)
        val line = Polyline(mapView)
        var punto: GeoPoint
        var oldPunto: GeoPoint? = null
        val stream = requireActivity().contentResolver.openInputStream(uri)
        val parser = GpxParser()
        var altiNulla = 0
        val gpx = parser.parse(stream!!)
        if (gpx.tracks == null) {
            Toast.makeText(
                requireActivity(),
                "Il file GPX non è valido",
                Toast.LENGTH_SHORT
            ).show()
            stream.close()
            return
        }
        stream.close()

        viewModel.trackDistanza = 0f
        viewModel.trackAscesa = 0
        viewModel.trackDiscesa = 0
// nome file (da Maputils)
        line.title = getFileNameFromUri(requireContext(), uri)

// carica i punti della traccia se esistono- da verificare con gpx multisegmento
// usa altinulla per contare gli elementi con altitudine 0
        if (!gpx.tracks.isEmpty()) {
            gpx.tracks[0].trackPoints.forEach {
//Log.d("Punto","${it.latitude}${it.longitude}")
// verifica esistenza valore altitudine
                if (it.elevation != null) {
                    punto = GeoPoint(it.latitude, it.longitude, it.elevation)
// confronta con la precedente altitudine non nulla e verifica se aumenta ascesa oppure discesa
                    if (oldPunto?.altitude != null) {
                        if (it.elevation > oldPunto.altitude) {
                            viewModel.trackAscesa += (it.elevation.toInt() - oldPunto.altitude.toInt())
                        } else {
                            viewModel.trackDiscesa += (it.elevation.toInt() - oldPunto.altitude.toInt())
                        }
                    }
                } else {
                    punto = GeoPoint(it.latitude, it.longitude)
                    altiNulla += 1
                }
// calcola distanza della traccia, da utilizzare se viene seguita per caloolare distanza rimanente
                if (oldPunto != null) {
                    val distToPunto = MapUtils.getDistanceInMeters(oldPunto, punto)
                    viewModel.trackDistanza += distToPunto
                }

                oldPunto = GeoPoint(it.latitude, it.longitude, it.elevation ?: 0.0)
                line.addPoint(punto)
            }
            disegnaLine(line)
            viewModel.listaTracce.add(line)
            addMarker(line)
        }

// carica i waypoints nella lista wayPoints da non salvare con traccia
        gpx.wayPoints?.forEach {
            viewModel.wayPoint.add(it)
            val waymarker = Marker(mapView)
            waymarker.title = it.name
            waymarker.icon = ResourcesCompat.getDrawable(
                requireContext().resources,
                R.drawable.ic_finish,
                requireContext().theme
            )
            waymarker.position.latitude = it.latitude
            waymarker.position.longitude = it.longitude
            waymarker.position.altitude = it.elevation ?: 0.0
// carica nel FolderOverlay
            viewModel.listaTracce.add(waymarker)
        }

// verifica il numero dei punti con il valore altinulla se coincide tutti i punti hanno altitudine nulla
        if (!gpx.tracks.isEmpty()) {
            if (gpx.tracks[0].trackPoints.size == altiNulla) {
                val snackbar =
                    view?.let { it1 ->
                        Snackbar.make(
                            it1,
                            "La traccia non ha informazioni su altitudine",
                            Snackbar.LENGTH_LONG
                        ).setAction("Action", null)
                    }
                snackbar!!.setActionTextColor(Color.WHITE)
                val snackbarView = snackbar.view
                snackbarView.setBackgroundColor(Color.RED)
                snackbar.show()                //.show()
            }
        }
//Log.d("caricagpx", mapView.zoomLevel.toString())
// esegue la visualizzazione dopo aver aggiornato lo zoom della mappa
        if (!gpx.tracks.isEmpty()) {
            mapView.post {
                mapView.zoomToBoundingBox(line.bounds.increaseByScale(1.2f), false)
            }
            MapUtils.alertSegui(requireContext(), viewModel, line)
        }
    }

    private fun updateGpsIcon(status: String?) {
        val menuItem = menu?.findItem(R.id.gps) ?: run { // Usa l'ID corretto qui
            Log.w(
                TAG,
                "updateGpsIcon: Tentativo di aggiornare l'icona ma il MENU o L'ITEM (R.id.gps) E' NULL."
            )
            return
        }

        val iconRes = when (status) {
            "started" -> R.drawable.gps_started // O l'icona che usi per "ricerca GPS"
            "fixed" -> R.drawable.gps_on     // Icona per GPS fixato
            "stopped" -> R.drawable.gps_off   // Icona per GPS spento/non attivo
            else -> R.drawable.gps_off      // Default a spento se lo stato è null o non riconosciuto
        }

        try {
            // menuItem qui non dovrebbe essere nullo grazie al check precedente
            menuItem.icon = ContextCompat.getDrawable(requireContext(), iconRes)
        } catch (e: Exception) {
            Log.e(TAG, "Errore durante l'aggiornamento dell'icona GPS", e)
        }
    }

    private fun mediaSpeed(): Double {
        // Calcola la velocità media in km/h
        // Ottieni i valori dai LiveData/StateFlow e gestisci il caso di null
        val distanceMeters = viewModel.distanzaMetri.value ?: 0
        val movingSeconds = viewModel.secondiMovimento.value ?: 0
        // Ritorna zero se non c'è stato movimento
        if (movingSeconds.toInt() == 0) {
            return 0.0 // Return Double for consistency
        }
        // Convert to Double early to maintain precision
        val distanceKilometers = distanceMeters.toDouble() / METERS_IN_A_KILOMETER
        val movingHours = movingSeconds.toDouble() / SECONDS_IN_AN_HOUR
        // Calculate speed in km/h
        return distanceKilometers / movingHours
    }

    private fun salvaTraccia(nomeTraccia: String) {
        var ultimoID: Long
        val dateString = dataOraIso8601()
        val sentiero = Sentieri(
            id = 0,
            nome = nomeTraccia,
            descrizione = "Traccia",
            lunghezza = viewModel.distanzaMetri.value!!.toDouble(),
            dislivello = viewModel.dislivPiu.value!!.toInt(),
            discesa = viewModel.dislivMeno.value!!.toInt(),
            HrMed = 0,
            HrMax = 0,
            DataOra = convertMillisToISO8601JavaTime(viewModel.oraInizio),
            TempMedia = 0.0,
            TempMax = 0.0,
            TempMin = 0.0,
            DataFine = dateString,
            TempoTot = (viewModel.elapsedTime / 1000).toDouble(),
            TempoInMov = viewModel.secondiMovimento.value!!.toDouble(),
            MediaVel = mediaSpeed()
        )

        viewModel.viewModelScope.launch(Dispatchers.IO) {
            // salva il nuovo record in Tabella Sentiero
            ultimoID = viewModel.salvaSentiero(sentiero)

//ciclo caricamento punti GPS in lista punti db
            val tracciaDao: TrackDao =
                SentieriDB.getInstance(requireActivity().application).trackDao()

            viewModel.puntiGPS.forEach {
                val trackPoint = com.apstudio.sentieri.db.Track(
                    Id = 0,
                    Trackid = ultimoID.toInt(),
                    Latit = it.latitude.toFloat(),
                    Longit = it.longitude.toFloat(),
                    Ele = it.elevation!!.toFloat(),
                    Ora = it.time.toString()
                )
                tracciaDao.insertDB(trackPoint)
                //Log.d("Track","$trackPoint")
            }


// scrive waypoint se inseriti durante registrazione traccia
// la lista è PoiDB
            if (viewModel.poiDBList.isNotEmpty()) {
                val poiDao: PoiDao =
                    SentieriDB.getInstance(requireActivity().application).poiDao()
                viewModel.poiDBList.forEach {
                    val poi = PoiDB(
                        Id = 0,
                        Trackid = ultimoID.toInt(),
                        Latit = it.Latit,
                        Longit = it.Longit,
                        Ele = it.Ele,
                        NomePOI = it.NomePOI,
                        DescrPOI = it.DescrPOI,
                        UriPath = it.UriPath,
                        Time = it.Time
                    )
                    poiDao.insertDB(poi)
                    //Log.d("Track","$trackPoint")
                }
            }

// memorizza uri e nome file delle foto scattate in registrazione traccia
            if (viewModel.fotoInPoiDB.isNotEmpty()) {
                val fotoDao: FotoPoiDao =
                    SentieriDB.getInstance(requireActivity().application).fotoPoiDao()
                viewModel.fotoInPoiDB.forEach {
                    val foto = FotoPoi(
                        id = 0,
                        trackid = ultimoID.toInt(),
                        uriPath = it.toString(),
                        nomeFoto = getFileNameFromUri(requireContext(), it)
                    )

                    fotoDao.insertDB(foto)
                    //Log.d("Track","$trackPoint")
                }
            }
        }
// scrive file GPX in cartella Downloads
        if (nomeTraccia.isNotEmpty()) {
            val scriviGpx = GpxWriter()
            val alink = Link("", "")
            val time = Date()
            val gpx = Gpx(
                xmlns = "http://www.topografix.com/GPX/1/1",
                version = "1.1",
                creator = "Sentieri",
                metadata = (GpxMetadata(alink, time)),
                wayPoints = viewModel.wayPoint.toList(),
                tracks = listOf(
                    Track(
                        name = nomeTraccia,
                        trackPoints = (viewModel.puntiGPS)
                    )
                )
            )

            // METODO con ContentResolver
            val resolver = requireContext().contentResolver
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, "$nomeTraccia.gpx")
                put(MediaStore.MediaColumns.MIME_TYPE, "application/gpx+xml")
                //put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOCUMENTS)
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }

            val uri = resolver.insert(MediaStore.Files.getContentUri("external"), contentValues)

            uri?.let {
                resolver.openOutputStream(it)?.use { outputStream ->
                    scriviGpx.write(gpx, outputStream)
                }
            }

            val builder = AlertDialog.Builder(requireContext(), R.style.AlertDialogCustom)
            with(builder)
            {
                val mediaSpeed = mediaSpeed()
                DecimalFormat("##.##").format(mediaSpeed)
                setTitle("Percorso concluso")
                val message = """
                        Distanza percorsa: ${viewModel.distanzaMetri.value}
                        Dislivello positivo (d+): ${viewModel.dislivPiu.value?.toInt()}
                        Dislivello negativo (d-): ${viewModel.dislivMeno.value?.toInt()}
                        Tempo trascorso: ${binding.cruscotto.tvTempo.text}
                        Tempo in movimento: ${binding.cruscotto.tvTempoMov.text}
                        Velocità media: ${DecimalFormat("##.##").format(mediaSpeed)}
                """.trimIndent()
                setMessage(message)
                setPositiveButton(
                    "Chiudi"
                ) { dialog, _ ->
                    dialog.dismiss()
                    // User clicked OK button
                }
                create()
                show()
            }
            azzeraCruscotto()
        } else
            Toast.makeText(
                requireActivity(),
                "Il nome del file è nullo",
                Toast.LENGTH_SHORT
            ).show()

    }

    private fun azzeraCruscotto() {
        // azzera i valori del viewModel visualizzati nel cruscotto
        viewModel.resetCruscotto()
    }

    fun isAlertDialogShowing(): Boolean {
        return alertDialog?.isShowing == true
    }

    fun isFragmentVisibleAndActive(): Boolean {
        return isAdded && lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
    }

    // i Wp sono caricati nelle liste wayPoint per quelli che sono caricati da dB e Gpx
// e in poiDBList per quelli ripresi durante registrazione traccia e devono essere salvati nel db PoiDB
// In MappaFragment.kt
    private fun salvaWayPoint(nome: String, descr: String /*, audioPath: String? */) {
        // Aggiungi il nuovo waypoint SOLO a viewModel.poiDBList come PoiDB
        val currentGeoPoint = viewModel.locationData.value?.geoPoint
        if (currentGeoPoint == null || (currentGeoPoint.latitude == 0.0 && currentGeoPoint.longitude == 0.0)) {
            Toast.makeText(
                requireContext(),
                "Posizione non disponibile per salvare il waypoint.",
                Toast.LENGTH_SHORT
            ).show()
            return
        }
// Aggiungi il nuovo waypoint SOLO a viewModel.poiDBList come PoiDB
        viewModel.poiDBList.add(
            PoiDB(
                Id = 0,
                Trackid = 0,
                Latit = currentGeoPoint.latitude,
                Longit = currentGeoPoint.longitude,
                Ele = currentGeoPoint.altitude,
                NomePOI = nome,
                DescrPOI = descr,
                UriPath = currentAudioFilePath ?: "",
                Time = Date().toString() // Considera di usare un formato di data/ora più standard o un Long
            )
        )

        // La logica per visualizzare il marker sulla mappa può rimanere,
        // creando un'istanza temporanea di WayPoint se necessario per il marker,
        // ma NON aggiungerla a viewModel.wayPoint.
        val markerDisplayWayPoint = WayPoint(
            latitude = currentGeoPoint.latitude,
            longitude = currentGeoPoint.longitude,
            elevation = currentGeoPoint.altitude,
            name = nome,
            description = descr,
            src = currentAudioFilePath // Esempio se UriPath mappa a source
            // time = Date() // Se necessario e il costruttore lo accetta
        )

        val waymarker = Marker(mapView)
        waymarker.title = markerDisplayWayPoint.name
        waymarker.icon = ResourcesCompat.getDrawable(
            requireContext().resources,
            R.drawable.ic_finish,
            requireContext().theme
        )
        waymarker.position.latitude = markerDisplayWayPoint.latitude
        waymarker.position.longitude = markerDisplayWayPoint.longitude
        waymarker.position.altitude = markerDisplayWayPoint.elevation ?: 0.0
        viewModel.listaTracce.add(waymarker) // Se questa è una lista separata per i marker sulla mappa
    }

    private fun accendiSchermo() {
        val window = requireActivity().window
        val currentFlags = window.attributes.flags // Ottieni i flag correnti della finestra

        if ((currentFlags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) != 0) {
            // Il flag FLAG_KEEP_SCREEN_ON è GIA' impostato
            //Log.d("ScreenOnCheck", "FLAG_KEEP_SCREEN_ON è già attivo.")
            // Non c'è bisogno di aggiungerlo di nuovo se è questa la tua intenzione
        } else {
            // Il flag FLAG_KEEP_SCREEN_ON NON è impostato
            //Log.d("ScreenOnCheck", "FLAG_KEEP_SCREEN_ON non è attivo. Lo imposto ora.")
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    override fun onDestroyView() {
        alertDialog?.dismiss()
        alertDialog = null
        if (_binding != null) {
            mapView.overlayManager.clear() // Rimuove tutti gli overlay dalla mappa
            mapView.onDetach()             // Importante per OSMDroid per un corretto cleanup
        }
        super.onDestroyView() // Chiamare super prima di nullificare _binding
        _binding = null
    }


    override fun onDestroy() {
        super.onDestroy() // Chiamare super per primo
        preferenze.unregisterOnSharedPreferenceChangeListener(this)
        if (::database.isInitialized && database.isOpen) {
            database.close()
        }
    }

    override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
        menuInflater.inflate(R.menu.main_menu, menu)
// viene richiamata alla creazione del menu, quindi  anche quando si cambia il fragment
        this.menu = menu
        // Aggiorna l'icona con lo stato corrente del GPS ViewModel
        // Questo gestisce il caso in cui l'observer iniziale è scattato prima che il menu fosse pronto
        updateGpsIcon(LocationRepository.gpsStatus.value)
    }

    @RequiresApi(Build.VERSION_CODES.S)
    override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
// Handle the menu selection
        when (menuItem.itemId) {
            R.id.Offline -> {
                menuItem.isChecked = !menuItem.isChecked
                offline()
            }

            R.id.Online -> {
                menuItem.isChecked = !menuItem.isChecked
                online(1)
            }

            R.id.Mapquest -> {
                menuItem.isChecked = !menuItem.isChecked
                online(2)
            }

            R.id.MapBox -> {
                menuItem.isChecked = !menuItem.isChecked
                online(3)
            }

            R.id.lista -> {
                val directions =
                    MappaFragmentDirections.actionMappaFragmentToSentieriFragment()
                this@MappaFragment.findNavController().navigate(directions)
            }

            R.id.gps -> {
// Attiva o disattiva GPS
                if (viewModel.isRecording) {
                    stopGPS()
                } else {
                    // se è presente barometro ed è settato per essere usato, verifica poi se è gia calibrato non è necessario
                    if (viewModel.haBaro && viewModel.setBaro) {
                        if (!viewModel.isCalibrato.value!!)
                            altDaBaro()
                        else
                            attivaGps()
                    } else
                        attivaGps()
                }
            }

            R.id.poi -> {
// Crea nuovo waypoint
                if (viewModel.isRecording) {
                    if (!viewModel.isFixed)
                        Toast.makeText(
                            requireActivity(),
                            "Fix Gps non ancora disponbile",
                            Toast.LENGTH_LONG
                        )
                            .show()
                    else
                        creaWayPoint()
                } else
                    Toast.makeText(
                        requireActivity(),
                        "Waypoint solo in modalita' registrazione traccia",
                        Toast.LENGTH_LONG
                    )
                        .show()

            }

            R.id.gpx -> {
// apre file manager per scelta gpx
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "application/octet-stream"
                    //putExtra(EXTRA_MIME_TYPES, arrayOf("application/gpx"))
                }
                startActivityForResult(intent, SELECT_GPX_FILE)
            }

            R.id.Geopackage -> {
                // Avvia il processo di binding quando l'activity diventa visibile.
                val intent = Intent().apply {
                    component = ComponentName(
                        BROUTER_PACKAGE,
                        BROUTER_SERVICE_CLASS
                    )
                }
                try {
                    requireContext().bindService(intent, connection, Context.BIND_AUTO_CREATE)
                    Log.d(TAG, "Tentativo di connessione a BRouterService...")
                } catch (e: SecurityException) {
                    Log.e(
                        TAG,
                        "Impossibile connettersi al servizio. L'app BRouter è installata? ${e.message}"
                    )
                    // Qui potresti mostrare un messaggio all'utente.
                }
                //addGeopackageTiles()
                //geoPackage()
            }
            /*R.id.layerGPkg -> {
                val directions = MappaFragmentDirections.actionMappaFragmentToGpkgLayer()
                this@MappaFragment.findNavController().navigate(directions)
            }*/
        }
        return false
    }


    @RequiresApi(Build.VERSION_CODES.S)
    private fun creaWayPoint() {
        val nomePoiEditText = EditText(requireActivity())
        nomePoiEditText.hint = "Nome Waypoint"
        nomePoiEditText.setText("WayPoint")

        val descrPoiEditText = EditText(requireActivity())
        descrPoiEditText.hint = "Descrizione"
        descrPoiEditText.setText("Descrizione")

        val btnRegistraAudio = Button(requireContext())
        btnRegistraAudio.text = "Registra Commento Vocale (5s)"

        val builder = AlertDialog.Builder(requireContext(), R.style.AlertDialogCustom)
        val dialogLayout = LinearLayout(requireContext())
        dialogLayout.orientation = LinearLayout.VERTICAL
        dialogLayout.setPadding(40, 40, 40, 40) // Aggiungi un po' di padding

        dialogLayout.addView(nomePoiEditText)
        dialogLayout.addView(descrPoiEditText)

        val params = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        params.topMargin = 20 // Aggiungi un margine sopra il bottone di registrazione
        dialogLayout.addView(btnRegistraAudio, params)

        builder.setTitle("Crea waypoint")
        builder.setView(dialogLayout)

        val alertDialog =
            builder.create() // Crea il dialogo prima per poterlo chiudere dai listener dei bottoni custom

        btnRegistraAudio.setOnClickListener {
            if (isAudioRecording) {
                stopAudioRecording(btnRegistraAudio)
            } else {
                if (isRecordAudioPermissionGranted()) {
                    startAudioRecording(btnRegistraAudio)
                } else {
                    Toast.makeText(
                        requireContext(),
                        "Non concesso permesso di registrare audio",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }

        builder.setPositiveButton("Crea") { _, _ ->
            val nome = nomePoiEditText.text.toString()
            val descr = descrPoiEditText.text.toString()
            // Passa currentAudioFilePath a salvaWayPoint.
            // salvaWayPoint dovrà essere modificato per accettare e usare questo path.
            salvaWayPoint(nome, descr /*, currentAudioFilePath */)
            currentAudioFilePath = null // Resetta dopo aver salvato
            alertDialog.dismiss() // Chiudi il dialogo
        }
        builder.setNegativeButton(android.R.string.cancel) { _, _ ->
            currentAudioFilePath = null // Resetta se si annulla
            // Se è stata fatta una registrazione e si annulla, si potrebbe voler cancellare il file audio
            audioOutputFile?.let {
                if (it.exists()) {
                    it.delete()
                    //Log.d(TAG_AUDIO, "File audio temporaneo cancellato: ${it.absolutePath}")
                }
            }
            audioOutputFile = null
            alertDialog.dismiss() // Chiudi il dialogo
        }
        // Sovrascrivi i bottoni standard per evitare la chiusura automatica se necessario,
        // oppure gestisci la logica e poi chiama dialog.dismiss().
        // Per semplicità, li lasciamo come Positive/Negative per ora,
        // ma per un controllo fine (es. non chiudere se la registrazione è in corso),
        // dovresti implementare i bottoni nel layout custom e gestire tu il dismiss.
        // Invece di builder.show(), usiamo l'istanza creata per poterla gestire.
        // Se si usa setPositive/NegativeButton, non c'è bisogno di creare prima l'alertDialog
        // e poi chiamare show. builder.show() è sufficiente.
        // Però per accedere ad `alertDialog.dismiss()` nei listener dei bottoni del layout custom,
        // serve l'istanza. Qui li manteniamo come bottoni del builder.
        // Mostra il dialogo (il builder viene modificato, quindi ricreiamo o mostriamo direttamente)
        val finalDialog = builder.show() // builder.create() e poi show() è un'alternativa

        // Gestisci il rilascio del media recorder se il dialogo viene chiuso inaspettatamente
        finalDialog.setOnDismissListener {
            if (isAudioRecording) {
                stopAudioRecording(btnRegistraAudio, true) // Forza lo stop e il rilascio
            }
        }
    }


    private fun addMarker(line: Polyline) {
// aggiunge marker inizio e fine percorso su tracce caricate
        val startMarker = Marker(mapView)
        startMarker.icon = requireContext().let {
            AppCompatResources.getDrawable(
                it,
                R.drawable.ic_start
            )
        }
        startMarker.title = "Inizio"
        startMarker.id = "start"
        if (line.actualPoints.isEmpty())
            return
        var punto: GeoPoint = line.actualPoints[0]
        startMarker.position = punto
        viewModel.listaTracce.add(startMarker)
        val endMarker = Marker(mapView)
        endMarker.icon = requireContext().let {
            AppCompatResources.getDrawable(
                it,
                R.drawable.ic_finish
            )
        }
        punto = line.actualPoints[line.actualPoints.size - 1]
        endMarker.position = punto
        endMarker.title = "Fine"
        viewModel.listaTracce.add(endMarker)
    }

    override fun onKey(v: View?, keyCode: Int, event: KeyEvent?): Boolean {
        if (event?.action == KeyEvent.ACTION_DOWN) {
            when (keyCode) {
                KEYCODE_VOLUME_UP -> {
                    mapView.controller.zoomIn()
                    return true // Evento gestito
                }
                KEYCODE_VOLUME_DOWN -> {
                    mapView.controller.zoomOut()
                    return true // Evento gestito
                }
            }
        }
        return false // Non abbiamo gestito questo evento di tasto, lascialo propagare
    }

    // Questa funzione aggiorna la vista in base allo stato ricevuto dal ViewModel.
    private fun updateBtnAllarmeUI(isAttivo: Boolean) {
        if (isAttivo) {
            binding.cruscotto.btnAllarme.text = "Allarme on"
            binding.cruscotto.btnAllarme.backgroundTintList = ColorStateList.valueOf(Color.RED)
        } else {
            binding.cruscotto.btnAllarme.text = "Allarme off"
            binding.cruscotto.btnAllarme.backgroundTintList = ColorStateList.valueOf(Color.GREEN)
        }
    }

    private fun aggiornaUIFabBlocMappa() {
        if (viewModel.bloccaMappa) {
            // Se la mappa è bloccata, mostra l'icona "bloccata" (es. PIN_RED)
            binding.fabBlocMappa.setImageResource(PIN_RED)
        } else {
            // Se la mappa è sbloccata, mostra l'icona "sbloccata" (es. PIN_BLACK)
            binding.fabBlocMappa.setImageResource(PIN_BLACK)
        }
    }

    override fun onSharedPreferenceChanged(p0: SharedPreferences?, p1: String?) {
        when (p1) {
            "MenuMap" -> {
                viewModel.menuMap = p0!!.getInt(p1, 1)
            }

            "setBaro" -> {
                viewModel.setBaro = p0!!.getBoolean(p1, false)
            }
        }
    }

    private fun addGeopackageTiles() {
        try {
            val mediaDir = requireContext().externalMediaDirs
            val documentsDir = mediaDir[0]
            val geoPackageFile = File("$documentsDir/Mappe/parchi.gpkg")
            val manager = GeoPackageFactory.getManager(requireContext())
            val databases = manager.databases()
            val geoPackage = manager.openExternal(geoPackageFile)
            val features = geoPackage.featureTables   //tileTables
            val markerRenderingOptions = MarkerOptions()
            val polylineRenderingOptions = PolylineOptions()
            polylineRenderingOptions.width = 2f
            polylineRenderingOptions.color = Color.argb(100, 255, 0, 0)
            polylineRenderingOptions.title = databases[0] + ":" + features[0]
            val polygonOptions = PolygonOptions()
            polygonOptions.strokeWidth = 2f
            polygonOptions.fillColor = Color.argb(100, 255, 0, 255)
            polygonOptions.strokeColor = Color.argb(100, 0, 0, 255)
            polygonOptions.title = databases[0] + ":" + features[0]
            val converter = OsmMapShapeConverter(
                null,
                markerRenderingOptions,
                polylineRenderingOptions,
                polygonOptions
            )
            val featureTable = "iba"
            val featureDao = geoPackage.getFeatureDao(featureTable)
            val featureCursor = featureDao.queryForAll()
            featureCursor.use { featureCursor ->
                while (featureCursor.moveToNext()) {
                    try {
                        val featureRow = featureCursor.row
                        val geometryData = featureRow.geometry
                        val geometry = geometryData.geometry
                        //Log.d("packgage", "geometry $geometry")
                        converter.addToMap(mapView, geometry)
                    } catch (ex: java.lang.Exception) {
                        ex.printStackTrace()
                    }
                    // ...
                }
            }
            geoPackage.close()
            mapView.invalidate()
        } catch (ex: Exception) {
            Log.d("packgage", "inside geopackage exception " + ex.message)
        }
    }

    private fun createOsmPolygonFromNgaPolygon(
        ngaPolygon: mil.nga.sf.Polygon,
        tableName: String,
        featureRow: FeatureRow,
        colore: String
    ): Polygon {
        val osmdroidPolygon = Polygon(mapView) // Assuming 'map' is accessible
        val exteriorRingPoints = mutableListOf<GeoPoint>()

        val firstRing = ngaPolygon.rings?.firstOrNull() // Controlla se rings è null
        if (firstRing != null && firstRing.points != null) { // Controlla se points è null
            firstRing.points.forEach { ngaPoint ->
                if (ngaPoint != null) { // Controlla se il singolo punto è null
                    exteriorRingPoints.add(GeoPoint(ngaPoint.y, ngaPoint.x))
                } else {
                    Log.w(TAG, "Null point found in exterior ring of polygon.")
                }
            }
        } else {
            Log.w(TAG, "Exterior ring or its points are null for a polygon.")
        }
        osmdroidPolygon.points = exteriorRingPoints


        if (ngaPolygon.rings != null && ngaPolygon.rings.size > 1) {
            val holes = mutableListOf<List<GeoPoint>>()
            ngaPolygon.rings.drop(1).forEach { interiorNgaRing ->
                if (interiorNgaRing != null && interiorNgaRing.points != null) { // Controlli aggiunti
                    val holePath = mutableListOf<GeoPoint>()
                    interiorNgaRing.points.forEach { ngaPoint ->
                        if (ngaPoint != null) { // Controllo aggiunto
                            holePath.add(GeoPoint(ngaPoint.y, ngaPoint.x))
                        } else {
                            Log.w(TAG, "Null point found in an interior ring (hole) of polygon.")
                        }
                    }
                    if (holePath.isNotEmpty()) { // Aggiungi solo se il percorso del buco ha punti
                        holes.add(holePath)
                    }
                } else {
                    Log.w(TAG, "An interior ring (hole) or its points are null for a polygon.")
                }
            }
            if (holes.isNotEmpty()) {
                osmdroidPolygon.holes = holes
            }
        }
        // Opzione A: Crea la label ora e memorizzala (più semplice se la label non è troppo grande)
        val labelForPolygon =
            layerModel.creaLabel(featureRow, tableName)
        osmdroidPolygon.relatedObject = labelForPolygon // Memorizza la stringa della label

        //Devi associare l' OnClickListener a ogni singola istanza di Polygon che crei.
        // Imposta l'OnClickListener
        osmdroidPolygon.setOnClickListener { polygon, map, eventPos ->
            val retrievedLabel = polygon.relatedObject as? String
            if (retrievedLabel != null) {
                mostraAlertDialogSemplice(
                    retrievedLabel
                )
            }
            true // Indica che l'evento è stato gestito
        }
        // Apply styling (assuming polygonOptions is accessible)
        if (colore == "RANDOM")
            osmdroidPolygon.fillColor = layerModel.getRandomIntColor(30)
        else
            osmdroidPolygon.fillColor = layerModel.polygonOptions.fillColor
        osmdroidPolygon.strokeColor = layerModel.polygonOptions.strokeColor
        osmdroidPolygon.strokeWidth = layerModel.polygonOptions.strokeWidth
        osmdroidPolygon.title = layerModel.polygonOptions.title
        return osmdroidPolygon
    }

    private fun mostraAlertDialogSemplice(message: String) {
        if (!isAdded || context == null) {
            Log.w(TAG, "Fragment non attaccato o contesto nullo, impossibile mostrare AlertDialog.")
            return // Esci dalla funzione per evitare il crash
        }
        val builder = AlertDialog.Builder(requireContext()) // 'this' è il Context dell'Activity
        //builder.setTitle(titolo) // Imposta il titolo
        builder.setMessage(message) // Imposta il messaggio
        builder.setPositiveButton("Chiudi") { dialog, which ->
            dialog.dismiss() // Chiude esplicitamente il dialogo (spesso non necessario per setPositiveButton)
        }
        val alertDialog: AlertDialog = builder.create()
        alertDialog.show()
    }

    /*private fun puntiSuMappa(tableName: String, featureInfo: FeatureTableInfo) {
        // Assicurati che usi il GeoPackage dal ViewModel
        val currentGeoPackage = layerModel.geoPackageInstance
        if (currentGeoPackage == null) {
            Log.e(TAG, "GeoPackage is null in puntiSuMappa for table $tableName")
            return
        }
        val colore = featureInfo.colore
        val points = mutableListOf<IGeoPoint>()
        val osmdroidPolygonsToAdd = mutableListOf<Polygon>()
        val lineStringToAdd = mutableListOf<LineStringFeature>()
        // Utilizza tableName passato come argomento
        val featureDao: FeatureDao = currentGeoPackage.getFeatureDao(tableName)
        val featureCursor: FeatureCursor = featureDao.queryForAll()
        try {
            while (featureCursor.moveToNext()) {
                val featureRow: FeatureRow = featureCursor.row
                val geometryData: GeoPackageGeometryData? =
                    featureRow.geometry // Può essere null

                if (geometryData?.geometry == null || geometryData.isEmpty) {
                    Log.w(TAG, "Skipping feature row with null or empty geometry.")
                    continue
                }

                val geometry = geometryData.geometry

                when (geometry.geometryType) {
                    GeometryType.POINT -> processPointGeometry(featureRow, tableName, points)
                    GeometryType.MULTIPOLYGON -> processMultiPolygonGeometry(
                        featureRow,
                        tableName,
                        osmdroidPolygonsToAdd,
                        colore
                    )

                    GeometryType.POLYGON -> processPolygonGeometry(
                        featureRow,
                        tableName,
                        osmdroidPolygonsToAdd,
                        colore
                    )

                    GeometryType.LINESTRING -> processLineStringGeometry(
                        featureRow,
                        tableName,
                        lineStringToAdd
                    )

                    else -> Log.w(
                        TAG,
                        "Geometry type ${geometry.geometryType.name} not yet handled for display."
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing feature cursor for table $tableName", e)
        } finally {
            featureCursor.close()
        }
        // crea e carica i layer se ci sono dati, punti oppure poligoni
        if (points.isNotEmpty()) {
            creaOverlayPunti(points, featureInfo)
        }
        if (lineStringToAdd.isNotEmpty()) {
            creaOverlayLinee(lineStringToAdd, featureInfo)
        }
        if (osmdroidPolygonsToAdd.isNotEmpty()) {
            creaOverlayPoligoni(osmdroidPolygonsToAdd, featureInfo)
        }
        mapView.invalidate()
    }*/
    private fun puntiSuMappa(tableName: String, featureInfo: FeatureTableInfo) {
        // 1. Mostra l'indicatore di caricamento
        _binding?.let {
            it.loadingProgressBar.visibility = View.VISIBLE
        }

        lifecycleScope.launch {
            val processedData = loadAndProcessFeaturesInBackground(tableName, featureInfo)

            // Torna al thread principale per aggiornare la UI
            withContext(Dispatchers.Main) {
                // 2. Nascondi l'indicatore di caricamento
                _binding?.let {
                    it.loadingProgressBar.visibility = View.GONE
                }

                // Crea e carica i layer se ci sono dati
                processedData.points?.let {
                    if (it.isNotEmpty()) {
                        creaOverlayPunti(it, featureInfo)
                    }
                }
                processedData.lineStrings?.let {
                    if (it.isNotEmpty()) {
                        creaOverlayLinee(it, featureInfo)
                    }
                }
                processedData.polygons?.let {
                    if (it.isNotEmpty()) {
                        creaOverlayPoligoni(it, featureInfo)
                    }
                }
                if (_binding != null) { // Controlla se mapView è inizializzata e binding non è nullo
                    mapView.invalidate()
                }
            }
        }
    }

    private fun creaOverlayPoligoni(
        osmdroidPolygonsToAdd: MutableList<Polygon>,
        featureInfo: FeatureTableInfo
    ) {
        val polyOverlay = FolderOverlay()
        osmdroidPolygonsToAdd.forEach {
            polyOverlay.add(it) // 'it' è il nuovo poligono con il nuovo listener
        }
        if (featureInfo.listOverlay == null) {
            featureInfo.listOverlay = mutableListOf()
        }
        featureInfo.listOverlay?.add(polyOverlay) // Aggiungi il nuovo FolderOverlay alla lista (ora pulita o appena inizializzata)
        mapView.overlayManager.add(polyOverlay) // Aggiungi il nuovo FolderOverlay alla mappa
    }

    private fun creaOverlayPunti(
        points: MutableList<IGeoPoint>,
        featureInfo: FeatureTableInfo
    ) {
        val theme = SimplePointTheme(points, false)
        // create label style
        val textStyle = Paint().apply {
            style = Paint.Style.FILL
            color = "#0000ff".toColorInt()
            textAlign = Paint.Align.CENTER
            textSize = 24f
        }
        // create point style
        val PointStyle = Paint().apply {
            style = Paint.Style.FILL
            //color = "#114190".toColorInt()
            color = featureInfo.colore.toColorInt()
            textAlign = Paint.Align.CENTER
            textSize = 24f
        }
        // set some visual options for the overlay
        // we use here MAXIMUM_OPTIMIZATION algorithm, which works well with >100k points
        val opt = SimpleFastPointOverlayOptions.getDefaultStyle()
            //.setAlgorithm(SimpleFastPointOverlayOptions.RenderingAlgorithm.MAXIMUM_OPTIMIZATION)
            .setAlgorithm(SimpleFastPointOverlayOptions.RenderingAlgorithm.NO_OPTIMIZATION)
            .setRadius(7F)
            .setSymbol(SimpleFastPointOverlayOptions.Shape.CIRCLE)
            .setIsClickable(true)
            .setCellSize(15)
            .setPointStyle(PointStyle)
            .setTextStyle(textStyle)
        val sfpo = SimpleFastPointOverlay(theme, opt)
        // 2. Inizializza listOverlay se è null
        if (featureInfo.listOverlay == null) {
            featureInfo.listOverlay = mutableListOf()
        }
        // add overlay
        featureInfo.listOverlay?.add(sfpo)
        sfpo.setOnClickListener { points, point ->
            points[point].toString()
            (points[point] as LabelledGeoPoint).label?.let {
                mostraAlertDialogSemplice(
                    it
                )
            }
        }
        if (!mapView.overlays.contains(sfpo)) {
            mapView.overlays.add(sfpo)
        }
    }

    private fun creaOverlayLinee(
        lineStringToAdd: MutableList<LineStringFeature>, // Questa è una lista di mil.nga.sf.LineString
        featureInfo: FeatureTableInfo
    ) {
        val lineOverlayFolder =
            FolderOverlay() // Questo è l'overlay che deve essere in featureInfo.listOverlay

        lineStringToAdd.forEachIndexed { index, lineFeature ->
            // 1. Crea un Polyline di osmdroid dalla LineString NGA
            val ngaLineString = lineFeature
            val osmdroidPolyline = Polyline(mapView)
            val geoPoints = mutableListOf<GeoPoint>()
            ngaLineString.lineString.points.forEach { point ->
                geoPoints.add(
                    GeoPoint(
                        point.y,
                        point.x
                    )
                ) // Assicurati che l'ordine sia (latitudine, longitudine)
            }
            osmdroidPolyline.setPoints(geoPoints)
            // Puoi personalizzare l'aspetto della Polyline qui (colore, spessore, ecc.)
            osmdroidPolyline.outlinePaint.color = layerModel.getRandomIntColor()
            osmdroidPolyline.outlinePaint.strokeWidth = 8f
            // --- Gestione InfoWindow ---
            osmdroidPolyline.id = "line_${featureInfo.name}_$index" // ID univoco
            osmdroidPolyline.title = ngaLineString.title // Titolo per l'InfoWindow
            osmdroidPolyline.snippet = ngaLineString.description // Snippet/sottotitolo
            osmdroidPolyline.infoWindow = BasicInfoWindow(
                R.layout.bonuspack_bubble, // Layout di default
                mapView
            )
            osmdroidPolyline.setOnClickListener { clickedPolyline, map, eventPosition ->
                //Log.d(TAG, "Polyline da GeoPackage cliccata: ${clickedPolyline.title}")
                clickedPolyline.infoWindowLocation =
                    eventPosition // Usa il punto del click per posizionare l'InfoWindow
                clickedPolyline.showInfoWindow() // Mostra l'InfoWindow
                map.controller.animateTo(eventPosition)
                true // Indica che l'evento è stato gestito
            }
            // 2. Aggiungi la Polyline di osmdroid al FolderOverlay
            lineOverlayFolder.add(osmdroidPolyline)
        }
        if (featureInfo.listOverlay == null) {
            featureInfo.listOverlay = mutableListOf()
        }
        featureInfo.listOverlay?.add(lineOverlayFolder) // AGGIUNGI IL FOLDER OVERLAY ALLA LISTA!

        // Aggiungi il FolderOverlay principale alla mappa (se non l'hai già fatto)
        if (!mapView.overlays.contains(lineOverlayFolder)) {
            mapView.overlays.add(lineOverlayFolder)
        }
    }

    private fun processPointGeometry(
        featureRow: FeatureRow,
        tableName: String,
        pointsToAdd: MutableList<IGeoPoint>
    ) {
        val label = layerModel.creaLabel(featureRow, tableName)
        val geometryData: GeoPackageGeometryData = featureRow.geometry
        pointsToAdd.add(
            LabelledGeoPoint(
                geometryData.geometry.centroid.y,
                geometryData.geometry.centroid.x,
                label
            )
        )
    }

    private fun processPolygonGeometry(
        featureRow: FeatureRow,
        tableName: String,
        osmdroidPolygonsToAdd: MutableList<Polygon>,
        colore: String
    ) {
        val geometryData = featureRow.geometry
        val geometry = geometryData.geometry
        val ngaPolygon = geometry as mil.nga.sf.Polygon
        osmdroidPolygonsToAdd.add(
            createOsmPolygonFromNgaPolygon(
                ngaPolygon,
                tableName,
                featureRow,
                colore
            )
        )
    }

    private fun processMultiPolygonGeometry(
        featureRow: FeatureRow,
        tableName: String,
        osmdroidPolygonsToAdd: MutableList<Polygon>,
        colore: String
    ) {
        val geometryData = featureRow.geometry
        val geometry = geometryData.geometry
        val ngaMultiPolygon = geometry as MultiPolygon
        ngaMultiPolygon.polygons.forEach { ngaPolygon ->
            osmdroidPolygonsToAdd.add(
                createOsmPolygonFromNgaPolygon(
                    ngaPolygon,
                    tableName,
                    featureRow,
                    colore
                )
            )
        }
    }

    private fun processLineStringGeometry(
        featureRow: FeatureRow,
        tableName: String,
        lineStringToAdd: MutableList<LineStringFeature>
    ) {
        // Gestisci la linea stringa qui
        val geometryData = featureRow.geometry
        val geometry = geometryData.geometry
        if (geometry is LineString) {
            val label = layerModel.creaLabel(featureRow, tableName)
            val description = "layer:$tableName"
            lineStringToAdd.add(LineStringFeature(geometry, label, description))
        }
    }

    private suspend fun loadAndProcessFeaturesInBackground(
        tableName: String,
        featureInfo: FeatureTableInfo
    ): ProcessedFeatureData {
        return withContext(Dispatchers.IO) {
            val currentGeoPackage = layerModel.geoPackageInstance
            if (currentGeoPackage == null) {
                Log.e(
                    TAG,
                    "GeoPackage is null in loadAndProcessFeaturesInBackground for table $tableName"
                )
                return@withContext ProcessedFeatureData(null, null, null)
            }

            val colore = featureInfo.colore
            val points = mutableListOf<IGeoPoint>()
            val osmdroidPolygonsToAdd = mutableListOf<Polygon>()
            val lineStringToAdd = mutableListOf<LineStringFeature>()

            try {
                val featureDao: FeatureDao = currentGeoPackage.getFeatureDao(tableName)
                val featureCursor: FeatureCursor = featureDao.queryForAll()

                featureCursor.use { cursor ->
                    while (cursor.moveToNext()) {
                        val featureRow: FeatureRow = cursor.row
                        val geometryData: GeoPackageGeometryData? = featureRow.geometry

                        if (geometryData?.geometry == null || geometryData.isEmpty) {
                            Log.w(TAG, "Skipping feature row with null or empty geometry.")
                            continue
                        }

                        val geometry = geometryData.geometry

                        when (geometry.geometryType) {
                            GeometryType.POINT -> processPointGeometry(
                                featureRow,
                                tableName,
                                points
                            )

                            GeometryType.MULTIPOLYGON -> processMultiPolygonGeometry(
                                featureRow,
                                tableName,
                                osmdroidPolygonsToAdd,
                                colore
                            )

                            GeometryType.POLYGON -> processPolygonGeometry(
                                featureRow,
                                tableName,
                                osmdroidPolygonsToAdd,
                                colore
                            )

                            GeometryType.LINESTRING -> processLineStringGeometry(
                                featureRow,
                                tableName,
                                lineStringToAdd
                            )

                            else -> Log.w(
                                TAG,
                                "Geometry type ${geometry.geometryType.name} not yet handled for display."
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing feature cursor for table $tableName in background", e)
                // In caso di errore, è comunque buona pratica ritornare i dati parzialmente processati
                // o almeno una struttura dati valida (anche se vuota).
                return@withContext ProcessedFeatureData(
                    points,
                    osmdroidPolygonsToAdd,
                    lineStringToAdd
                )
            }
            ProcessedFeatureData(points, osmdroidPolygonsToAdd, lineStringToAdd)
        }
    }


    // --- Inizio Funzioni di Registrazione Audio ---
    @RequiresApi(Build.VERSION_CODES.S)
// In MappaFragment.kt

    private fun startAudioRecording(button: Button) {
        if (isAudioRecording) return

        // This now creates a file with the final, correct name.
        audioOutputFile = createAudioFileInternal()
        if (audioOutputFile == null) {
            Toast.makeText(
                requireContext(),
                "Errore nella creazione del file audio",
                Toast.LENGTH_SHORT
            ).show()
            return
        }
        currentAudioFilePath = audioOutputFile?.absolutePath // Salva il percorso

        mediaRecorder =
            MediaRecorder(requireContext())

        mediaRecorder?.apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
            setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
            setOutputFile(audioOutputFile!!.absolutePath)

            try {
                prepare()
                start()
                isAudioRecording = true
                button.text = "Interrompi Registrazione"
                Toast.makeText(requireContext(), "Registrazione avviata...", Toast.LENGTH_SHORT)
                    .show()

                // Schedules the stop call correctly
                audioHandler.postDelayed({
                    if (isAudioRecording) {
                        stopAudioRecording(button)
                    }
                }, recordingDurationMs)

            } catch (e: IOException) {
                Log.e(TAG_AUDIO, "prepare() failed: ${e.message}")
                Toast.makeText(requireContext(), "Avvio registrazione fallito", Toast.LENGTH_SHORT)
                    .show()
                releaseMediaRecorderInternal(true)
            } catch (e: IllegalStateException) {
                Log.e(TAG_AUDIO, "start() failed: ${e.message}")
                Toast.makeText(
                    requireContext(),
                    "Avvio registrazione fallito (IllegalState)",
                    Toast.LENGTH_SHORT
                ).show()
                releaseMediaRecorderInternal(true)
            }
        }
    }

    private fun stopAudioRecording(button: Button, forceRelease: Boolean = false) {
        if (!isAudioRecording && !forceRelease) return

        if (isAudioRecording) {
            try {
                // THE CRUCIAL MISSING CALL: Stop the recording.
                mediaRecorder?.stop()
                Toast.makeText(
                    requireContext(),
                    "Registrazione salvata: $audioFileName",
                    Toast.LENGTH_LONG
                ).show()
            } catch (e: RuntimeException) {
                Log.e(TAG_AUDIO, "stop() failed: ${e.message}")
                currentAudioFilePath = null // Invalidate the path if stop failed
                audioOutputFile?.delete()    // Delete the potentially corrupt file
                Toast.makeText(
                    requireContext(),
                    "Interruzione registrazione fallita",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        // Always release the recorder and update the state.
        releaseMediaRecorderInternal(false) // Release resources, keep the file if stop was successful
        isAudioRecording = false
        button.text = "Registra Commento Vocale (5s)"
        audioHandler.removeCallbacksAndMessages(null)
    }

    private fun createAudioFileInternal(): File? {
        return try {
            val timeStamp: String =
                SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            audioFileName = "AUD_${timeStamp}.3gp" // This is the final name
            val storageDir: File? = requireContext().getExternalFilesDir("VoiceNotesWaypoints")
            if (storageDir != null && !storageDir.exists()) {
                if (!storageDir.mkdirs()) {
                    Log.e(TAG_AUDIO, "Impossibile creare la directory VoiceNotesWaypoints")
                    return null
                }
            }
            // Create the file directly with its final name. No temp file, no renaming.
            File(storageDir, audioFileName)
        } catch (ex: IOException) {
            Log.e(TAG_AUDIO, "Errore nella creazione del file audio: ${ex.message}")
            null
        }
    }

    private fun releaseMediaRecorderInternal(deleteFileOnError: Boolean) {
        if (deleteFileOnError) {
            audioOutputFile?.let {
                if (it.exists()) it.delete()
                //Log.d(TAG_AUDIO, "File audio cancellato a causa di errore nel rilascio.")
            }
            audioOutputFile = null
            currentAudioFilePath = null
        }
        mediaRecorder?.release()
        mediaRecorder = null
        isAudioRecording = false // Assicurati che lo stato sia consistente
        // Non modificare il testo del bottone qui, perché non abbiamo un riferimento diretto
        // al bottone del dialogo se questo viene chiamato da onStop, per esempio.
        audioHandler.removeCallbacksAndMessages(null)
    }

    // Sovrascrivi onStop per rilasciare il MediaRecorder
    override fun onStop() {
        super.onStop()
        // se collegato rilascia servizio Brouter
        if (isBound) {
            requireContext().unbindService(connection)
            brouterService = null
            isBound = false
            Log.d(TAG, "Disconnessione da BRouterService.")
        }
        if (isAudioRecording) {
            // Potresti voler cambiare il testo del bottone, ma il dialogo potrebbe non essere più visibile.
            // Per semplicità, ci concentriamo sul rilascio delle risorse.
            stopAudioRecording(
                Button(requireContext() /*placeholder, non verrà usato per il testo*/),
                true
            )
        }
        releaseMediaRecorderInternal(false) // Rilascia sempre, non cancellare il file se lo stop è stato regolare
    }

    private fun isRecordAudioPermissionGranted(): Boolean {
        return ContextCompat.checkSelfPermission(
            requireContext(), // Ottieni il contesto dal Fragment
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }
    // --- Fine Funzioni di Registrazione Audio ---

    // funzioni di servizio BRouter
    private fun calculateRoute(startPoint: GeoPoint, endPoint: GeoPoint) {
        if (!isBound || brouterService == null) {
            Log.w(TAG, "Calcolo del percorso annullato: servizio non connesso.")
            return
        }

        // Lancia una coroutine legata al ciclo di vita del fragment.
        // Verrà cancellata automaticamente quando il Fragment viene distrutto.
        viewLifecycleOwner.lifecycleScope.launch {
            Log.d(TAG, "Avvio del calcolo del percorso in background...")

            val params = Bundle().apply {
                putDoubleArray("lons", doubleArrayOf(startPoint.longitude, endPoint.longitude))
                putDoubleArray("lats", doubleArrayOf(startPoint.latitude, endPoint.latitude))
                putString("profile", "trekking")
                putString("trackFormat", "gpx")
            }

            // Esegui la chiamata di rete, il parsing del GPX e la conversione dei punti
            // su un thread in background (Dispatchers.IO).
            val result = withContext(Dispatchers.IO) {
                try {
                    // 1. Chiamata bloccante al servizio
                    val gpxString = brouterService?.getTrackFromParams(params)

                    // 2. Gestione preliminare del risultato
                    if (gpxString == null) {
                        Log.i(TAG, "BRouter ha restituito un risultato nullo.")
                        // Usiamo una classe wrapper per passare sia il messaggio che i dati
                        Result.failure(Exception("BRouter: Nessun percorso restituito."))
                    } else if (gpxString.startsWith("Error")) {
                        Log.e(TAG, "Errore da BRouter: $gpxString")
                        Result.failure(Exception("Errore da BRouter: $gpxString"))
                    } else {
                        Log.d(TAG, "Tracciato GPX ricevuto, inizio parsing...")

                        // 3. Parsing del GPX (operazione potenzialmente pesante)
                        val parser = GpxParser()
                        val inputStream = gpxString.byteInputStream(Charsets.UTF_8)
                        // Esegui il parsing dall'InputStream
                        val gpx = parser.parse(inputStream)

                        val trackPoints = gpx.tracks?.firstOrNull()?.trackPoints
                        if (trackPoints.isNullOrEmpty()) {
                            Log.w(TAG, "Il GPX da BRouter non contiene punti traccia validi.")
                            Result.failure(Exception("BRouter ha restituito un percorso vuoto."))
                        } else {
                            // 4. Conversione dei punti (altra operazione pesante)
                            val geoPoints = trackPoints.map { wayPoint ->
                                GeoPoint(wayPoint.latitude, wayPoint.longitude, wayPoint.elevation ?: 0.0)
                            }
                            // Restituisci i dati pronti per la UI
                            Result.success(geoPoints)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Errore durante il calcolo o il parsing del percorso.", e)
                    Result.failure(Exception("Errore nell'elaborare il percorso: ${e.message}"))
                }
            }

            // Torna sul thread principale per gestire il risultato e aggiornare la UI.
            handleResult(result)
        }
    }

    private fun handleResult(result: Result<List<GeoPoint>>) {
        result.onSuccess { geoPoints ->
            // Se il risultato è un successo, disegna la traccia
            Log.d(TAG, "Disegno della traccia sulla mappa...")
            disegnaTracciaBrouter(geoPoints)
        }.onFailure { exception ->
            // Se c'è stato un errore in qualsiasi punto, mostra un Toast
            Log.e(TAG, "Fallimento nel processare il percorso: ${exception.message}")
            Toast.makeText(requireContext(), exception.message, Toast.LENGTH_LONG).show()
        }

        // Disconnettiti dal servizio in ogni caso (successo o fallimento)
        if (isBound) {
            try {
                requireContext().unbindService(connection)
                brouterService = null
                isBound = false
                Log.d(TAG, "Disconnessione da BRouterService.")
            } catch (_: IllegalArgumentException) {
                Log.w(TAG, "Servizio già disconnesso.")
            }
        }
    }

    /**
     * Disegna sulla mappa la traccia calcolata e ricevuta da BRouter.
     */
    private fun disegnaTracciaBrouter(points: List<GeoPoint>) {
        val brouterPolyline = Polyline(mapView)
        brouterPolyline.setPoints(points)
        brouterPolyline.outlinePaint.color = Color.BLUE // Colore blu per distinguerla
        brouterPolyline.outlinePaint.strokeWidth = 12f
        brouterPolyline.title = "Percorso BRouter"

        // Aggiungi un listener per il click (opzionale)
        setPolylineClickListener(brouterPolyline)

        // Aggiungi la nuova linea al folder delle tracce e aggiorna la mappa
        viewModel.listaTracce.add(brouterPolyline)
        mapView.invalidate()

        // Esegui uno zoom per inquadrare la nuova traccia
        if (points.isNotEmpty()) {
            mapView.post {
                mapView.zoomToBoundingBox(brouterPolyline.bounds.increaseByScale(1.2f), true)
            }
        }
    }

    private fun enterDestinationSelectionMode() {
        isSelectingDestination = true

        // Mostra il pulsante di conferma e cambia l'icona del FAB (opzionale)
        binding.buttonConfirmDestination.visibility = View.VISIBLE
        binding.fabSelectDestination.setImageResource(android.R.drawable.ic_menu_close_clear_cancel) // Icona di annullamento

        // Se il marker non esiste, crealo al centro della mappa
        if (destinationMarker == null) {
            destinationMarker = Marker(mapView).apply {
                // Imposta un'icona personalizzata se vuoi
                // icon = ContextCompat.getDrawable(requireContext(), R.drawable.ic_destination_marker)
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                isDraggable = true // LA PROPRIETÀ CHIAVE!
                title = "Trascina per impostare la destinazione"

                // Listener per quando il trascinamento finisce
                setOnMarkerDragListener(object : Marker.OnMarkerDragListener {
                    override fun onMarkerDrag(marker: Marker) { /* Non serve fare nulla qui */ }
                    override fun onMarkerDragEnd(marker: Marker) {
                        // Puoi aggiornare un'infowindow o fare altro qui se vuoi
                        Log.d("MappaFragment", "Destinazione impostata a: ${marker.position}")
                    }
                    override fun onMarkerDragStart(marker: Marker) { /* Non serve fare nulla qui */ }
                })
            }
            mapView.overlays.add(destinationMarker)
        }

        // Posiziona il marker al centro della vista corrente e rendilo visibile
        destinationMarker?.position = mapView.mapCenter as GeoPoint
        destinationMarker?.isEnabled = true
        mapView.invalidate() // Ridisegna la mappa per mostrare il marker

        Toast.makeText(requireContext(), "Trascina il marker e conferma la destinazione", Toast.LENGTH_LONG).show()
    }

    private fun exitDestinationSelectionMode() {
        isSelectingDestination = false

        // Nascondi il pulsante di conferma e ripristina l'icona del FAB
        binding.buttonConfirmDestination.visibility = View.GONE
        binding.fabSelectDestination.setImageResource(R.drawable.ic_distance) // Icona originale

        // Nascondi il marker (non rimuoverlo, così lo possiamo riutilizzare)
        destinationMarker?.isEnabled = false
        mapView.invalidate()
    }

    // --- ComponentCallbacks2 Implementation ---
    override fun onTrimMemory(level: Int) {
        //super.onTrimMemory(level) // È buona norma chiamare super
        Log.i(TAG, "onTrimMemory called with level: $level")
        if (_binding != null) { // Check if mapView is initialized
            onTrimMemory(level) // Chiama onTrimMemory sull'istanza di mapView
        }
        // You might want to clear other caches here based on the level
        // For example, if level is TRIM_MEMORY_RUNNING_CRITICAL or TRIM_MEMORY_COMPLETE
        if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL) {
            // Aggressively free up resources
            // layerModel.clearCache() // Example
            // viewModel.clearCache() // Example
        }
    }

    override fun onLowMemory() {
        super.onLowMemory() // It's good practice to call super
        Log.w(TAG, "onLowMemory called. System is critically low on memory.")
        if (_binding != null) { // Check if mapView is initialized
            onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_COMPLETE)
        }
        // This is a more severe signal. You should release any resources that are not
        // absolutely essential for the current user experience.
        // layerModel.evictAll() // Example: Clear all caches in LayerViewModel
        // viewModel.evictAll()  // Example: Clear all caches in SentieriViewModel
        // Consider releasing other resources like GeoPackage instances if they can be reopened later.
        layerModel.closeGeoPackage() // Example
    }
    // --- End ComponentCallbacks2 Implementation ---

    inner class RemovableMarker(mapView: MapView) : Marker(mapView) {
        var onMarkerLongClick: ((RemovableMarker) -> Boolean)? = null

        // Override per vedere quando MapView chiama questo specifico marker per un long press
        override fun onLongPress(event: MotionEvent?, mapView: MapView?): Boolean {
            if (mapView == null || event == null) return false
            val wasHit = hitTest(event, mapView) // Controlla se l'evento è DENTRO questo marker
            if (wasHit && this.isEnabled) {
                return onMarkerLongClick?.invoke(this) ?: super.onLongPress(event, mapView)
            }
            // Se non è stato colpito o non è abilitato, restituisce false per far continuare il dispatching
            // o chiama super.onLongPress che farà la sua logica.
            return super.onLongPress(event, mapView)
        }

        // Override per vedere l'hit test in azione
        override fun hitTest(event: MotionEvent, mapView: MapView): Boolean {
            val hit = super.hitTest(event, mapView)
            return hit
        }
    }

}