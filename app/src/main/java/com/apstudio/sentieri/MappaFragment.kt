package com.apstudio.sentieri

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentCallbacks2
import android.content.ComponentName
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.hardware.Sensor
import android.hardware.SensorManager
import android.icu.text.SimpleDateFormat
import android.location.LocationManager
import android.media.MediaRecorder
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.MediaStore
import android.telephony.TelephonyManager
import android.util.Log
import android.view.KeyEvent
import android.view.KeyEvent.KEYCODE_VOLUME_DOWN
import android.view.KeyEvent.KEYCODE_VOLUME_UP
import android.view.LayoutInflater
import android.view.Menu
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.widget.TooltipCompat
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.toColorInt
import androidx.core.net.toUri
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import androidx.navigation.fragment.findNavController
import androidx.preference.PreferenceManager
import btools.routingapp.IBRouterService
import com.apstudio.sentieri.MapUtils.apreMappa
import com.apstudio.sentieri.MapUtils.convertMillisToISO8601JavaTime
import com.apstudio.sentieri.MapUtils.dataOraIso8601
import com.apstudio.sentieri.MapUtils.disegnaLineaSfondo
import com.apstudio.sentieri.MapUtils.disegnaPercorsoColorato
import com.apstudio.sentieri.MapUtils.formatSeconds
import com.apstudio.sentieri.MapUtils.getFileNameFromUri
import com.apstudio.sentieri.MapUtils.online
import com.apstudio.sentieri.MapUtils.showCustomSnackbar
import com.apstudio.sentieri.databinding.FragmentMappaBinding
import com.apstudio.sentieri.db.FotoPoi
import com.apstudio.sentieri.db.FotoPoiDao
import com.apstudio.sentieri.db.LocationRepository
import com.apstudio.sentieri.db.PoiDB
import com.apstudio.sentieri.db.PoiDao
import com.apstudio.sentieri.db.Sentieri
import com.apstudio.sentieri.db.SentieriDB
import com.apstudio.sentieri.db.SentieriRepo
import com.apstudio.sentieri.db.TrackDao
import com.apstudio.sentieri.layer.FeatureTableInfo
import com.apstudio.sentieri.layer.GeologiaFeatureTiles
import com.apstudio.sentieri.layer.LayerViewModel
import com.apstudio.sentieri.layer.LineStringFeature
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.navigation.NavigationView
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import mil.nga.geopackage.GeoPackage
import mil.nga.geopackage.features.index.FeatureIndexManager
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
import org.osmdroid.api.IGeoPoint
import org.osmdroid.api.IMapController
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.MapTileProviderArray
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.FolderOverlay
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.ScaleBarOverlay
import org.osmdroid.views.overlay.TilesOverlay
import org.osmdroid.views.overlay.compass.CompassOverlay
import org.osmdroid.views.overlay.compass.InternalCompassOrientationProvider
import org.osmdroid.views.overlay.gestures.RotationGestureOverlay
import org.osmdroid.views.overlay.infowindow.BasicInfoWindow
import org.osmdroid.views.overlay.infowindow.InfoWindow
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

class MappaFragment : Fragment(), SharedPreferences.OnSharedPreferenceChangeListener,
    View.OnKeyListener, ComponentCallbacks2 {

    companion object {
        private const val TAG_AUDIO = "AudioRecording" // Tag per log audio

        // Il nome del package dell'app BRouter e il nome del servizio (dal manifest di BRouter)
        private const val BROUTER_PACKAGE = "btools.routingapp"
        private const val BROUTER_SERVICE_CLASS = "btools.routingapp.BRouterService"

        /**
         * Verifica se il servizio BRouter è installato sul dispositivo.
         */
        fun isBRouterInstalled(context: Context): Boolean {
            return try {
                val packageManager = context.packageManager
                packageManager.getPackageInfo(BROUTER_PACKAGE, 0)
                true
            } catch (_: PackageManager.NameNotFoundException) {
                false
            }
        }
    }

    // Flag per tracciare se la vista è stata appena ricreata (onViewCreated).
    private var isViewRecreated = false

    // Struttura dati per contenere i risultati dell'elaborazione in background
    private data class ProcessedFeatureData(
        val points: MutableList<IGeoPoint>?,
        val polygons: MutableList<Polygon>?,
        val lineStrings: MutableList<LineStringFeature>?
    )

    private val viewModel: SentieriViewModel by activityViewModels(
        factoryProducer = {
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
    )

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

    private lateinit var gpsMarker: Marker
    private val displayedTopoMarkers = mutableListOf<Marker>()
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

    // Variabile per memorizzare una richiesta di centraggio mappa in sospeso.
    private var initialCenterPoint: GeoPoint? = GeoPoint(40.0587, 9.1122)

    // Ottimizzazioni per evitare ricaricamenti ridondanti
    private var lastSyncedToponimi: List<com.apstudio.sentieri.db.TopoMarkerData> = emptyList()
    private var layersSyncedThisResume = false

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

    // 1. Registra il launcher per ricevere il risultato dell'attività
    private val mapFileSelectorLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                apreMappa(requireContext(), mapView, viewModel, uri)
            }
        }
    }

    // 2. Launcher per la selezione dei file .gpx
    private val gpxFileSelectorLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                // Logica che prima era in onActivityResult per SELECT_GPX_FILE
                caricaGPX(uri)
            }
        }
    }

    // Launcher per i permessi in primo piano (FINE_LOCATION e, se serve, FOREGROUND_SERVICE_LOCATION)
    private val requestFineLocationLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            // Questo callback viene eseguito DOPO che l'utente ha risposto al dialogo di sistema.
            // Controlla se tutti i permessi sono stati concessi.
            if (permissions.all { it.value }) {
                // Permessi concessi. Mostra un messaggio all'utente per informarlo.
                Toast.makeText(
                    requireContext(),
                    "Permesso di posizione concesso. Premi di nuovo per avviare la registrazione.",
                    Toast.LENGTH_LONG
                ).show()
            } else {
                // Uno o più permessi sono stati negati.
                Toast.makeText(
                    requireContext(),
                    "Senza il permesso di posizione, la mappa non può mostrare la tua localizzazione.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

    // Launcher per il permesso in background
    private val requestBackgroundLocationLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                // Permesso concesso. Informa l'utente che ora può avviare la registrazione.
                Toast.makeText(
                    requireContext(),
                    "Permesso per il background concesso. Ora puoi avviare la registrazione.",
                    Toast.LENGTH_LONG
                ).show()
            } else {
                // L'utente ha negato.
                Toast.makeText(
                    requireContext(),
                    "La registrazione funzionerà solo con l'app aperta.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            brouterService = IBRouterService.Stub.asInterface(service)
            isBound = true
            //Log.d(TAG, "BRouterService connesso con successo.")

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
            //Log.d(TAG, "BRouterService disconnesso.")
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        /*try {
            // Assicurati che AppSentieri sia il nome corretto della tua classe Application
            // e che il ViewModelProvider sia configurato correttamente.
            viewModel =
                ViewModelProvider(requireActivity().application as AppSentieri)[SentieriViewModel::class.java]
        } catch (e: Exception) {
            Log.e(TAG, "FATALE: Errore durante l'inizializzazione del ViewModel in onCreate!", e)
            // Considera di gestire questo errore in modo più drastico se l'app non può funzionare senza viewModel
        }*/
        // Inizializza le preferenze e registra il listener
        preferenze = PreferenceManager.getDefaultSharedPreferences(requireContext())
        // Legge se esiste SENSORE BAROMETRO da Preferences
        //haBaro indica se esiste sensore barometrico fisico
        viewModel.haBaro = preferenze.getBoolean("haBaro", false)
        viewModel.setBaro = preferenze.getBoolean("setBaro", false)

        // Get the database instance (using the singleton)
        database = SentieriDB.getInstance(requireContext())
    }

    private fun onReturnFromLayerDialog() {
        if (_binding == null) {
            Log.w(TAG, "onReturnFromLayerDialog chiamato ma la vista è nulla. Interruzione.")
            return
        }
        //Log.d(TAG, "Eseguo onReturnFromLayerDialog: Sincronizzazione stato overlay...")

        // Itera su tutti i layer e sincronizza il loro stato con la mappa attuale.
        layerModel.featureList.forEach { featureInfo ->
            // Rimuovi sempre i vecchi overlay dalla mappa, se esistono, per evitare duplicati.
            featureInfo.listOverlay?.let { existingOverlays ->
                if (existingOverlays.isNotEmpty()) {
                    mapView.overlays.removeAll(existingOverlays.toSet())
                }
            }

            if (featureInfo.isVisible) {
                // Se è il layer geologico, lo ricreiamo SEMPRE per evitare il blocco dei thread del TileProvider
                // che si verifica dopo il detach della MapView precedente (es. quando si torna da un altro Fragment).
                if (featureInfo.name == "area_geologica") {
                    featureInfo.listOverlay?.clear()
                }

                // Se il layer deve essere visibile...
                if (featureInfo.listOverlay.isNullOrEmpty() || featureInfo.name == "area_geologica") {
                    // ...e non abbiamo gli overlay in memoria (o è il layer geologico che ha context leaks), caricali.
                    //Log.d(TAG,"Il layer ${featureInfo.name} è visibile e non caricato. Avvio caricamento.")
                    puntiSuMappa(featureInfo.name, featureInfo)
                } else {
                    // ...e abbiamo già gli overlay in memoria, semplicemente ri-aggiungili alla NUOVA mapView.
                    //Log.d(TAG,"Il layer ${featureInfo.name} è già caricato. Ri-aggiungo ${featureInfo.listOverlay!!.size} overlay alla mappa.")
                    featureInfo.listOverlay!!.forEach { overlay ->
                        reattachListenersToOverlay(overlay)
                    }
                    mapView.overlays.addAll(featureInfo.listOverlay!!)
                }
            } else {
                // Se il layer non deve essere visibile, assicurati che la sua lista di overlay sia vuota.
                featureInfo.listOverlay?.clear()
            }
        }

        //mapView.invalidate()      // Forza un singolo ridisegno alla fine di tutte le operazioni.
        //Log.d(TAG, "invalidate onReturnFromLayerDialog.")
        layersSyncedThisResume = true
    }

    // Helper function to re-attach listeners
    private fun reattachListenersToOverlay(overlay: org.osmdroid.views.overlay.Overlay) {
        if (!isAdded) return // Controllo di sicurezza
        when (overlay) {
            is FolderOverlay -> {
                // Applica ricorsivamente ai figli
                overlay.items?.forEach { reattachListenersToOverlay(it) }
            }

            is SimpleFastPointOverlay -> {
                //Log.d("ListenerDebug", "Ri-attacco OnPointClickListener per SimpleFastPointOverlay")
                // Accediamo alle opzioni dell'overlay esistente
                // Impostiamo un nuovo OnPointClickListener.
                // Nota: l'overlay contiene già i punti (IGeoPoint), qui gestiamo solo il tocco.
                overlay.setOnClickListener { points, point ->
                    // Recuperiamo il punto cliccato
                    val clickedPoint = points?.get(point!!)
                    if (clickedPoint is LabelledGeoPoint) {
                        // Se i punti hanno delle etichette (label), le mostriamo
                        val label = clickedPoint.label
                        if (!label.isNullOrEmpty()) {
                            mostraAlertDialogSemplice(label)
                        }
                    } //else if (clickedPoint is GeoPoint) {
                        // Se non c'è una label, mostriamo almeno le coordinate o un messaggio generico
                        //Log.d("ListenerDebug","Punto cliccato: ${clickedPoint.latitude}, ${clickedPoint.longitude}")
                    //}
                }
            }

            is Polygon -> {
                //Log.d("ListenerDebug", "Ri-attacco Listener SEMPLICE per Poligono")
                // Anche qui, il listener si limita a mostrare la stringa pre-calcolata.
                overlay.setOnClickListener { polygon, _, _ ->
                    (polygon.relatedObject as? String)?.let { label ->
                        mostraAlertDialogSemplice(label)
                    }
                    true // Evento gestito
                }
            }

            is Polyline -> {
                //Log.d("ListenerDebug", "Ri-attacco InfoWindow per Polyline")
                // Recuperiamo i dati e verifichiamo se è il layer CAI
                val lineFeature = overlay.relatedObject as? LineStringFeature
                overlay.setOnClickListener { clickedPolyline, map, eventPosition ->
                    val iw = if (clickedPolyline.id?.contains("Sentieri CAI") == true) {
                        WebsiteInfoWindow(lineFeature!!, map)
                    } else {
                        BasicInfoWindow(R.layout.bonuspack_bubble, map)
                    }
                    clickedPolyline.infoWindow = iw
                    clickedPolyline.infoWindowLocation = eventPosition
                    clickedPolyline.showInfoWindow()
                    map.controller.animateTo(eventPosition)
                    true
                }
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentMappaBinding.inflate(inflater, container, false)
        return binding.root
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        TooltipCompat.setTooltipText(binding.btnMenu, "Apre il menu")
        TooltipCompat.setTooltipText(binding.camera, "Scatta una foto")
        TooltipCompat.setTooltipText(binding.fabBlocMappa,"Abilita riposizionamento della mappa automatico")
        TooltipCompat.setTooltipText(binding.fabSelectDestination, "Calcola percorso")

        isViewRecreated = true
        //Log.d(TAG, "Ripristino degli overlay dal ViewModel sulla nuova MapView.")
        // 1. Pulisci la mappa per sicurezza (anche se dovrebbe essere già vuota)
        mapView.overlayManager.clear()
        val mRotationGestureOverlay = RotationGestureOverlay(context, mapView)
        mRotationGestureOverlay.setEnabled(true)
        mapView.setMultiTouchControls(true)
        mapView.overlays.add(mRotationGestureOverlay)
        view.isFocusableInTouchMode = true
        view.requestFocus()
        view.setOnKeyListener(this)

        mapView.setOnTouchListener { v, event ->
            if (event.action == MotionEvent.ACTION_UP) {
                val density = resources.displayMetrics.density
                // le coordinate devono corrispondere a quelle del posizionamento dell'overlay bussola
                val compassX = 36f * density
                val compassY = 164f * density
                val dx = event.x - compassX
                val dy = event.y - compassY
                val radius = 50f * density

                if (dx * dx + dy * dy <= radius * radius) {
                    // 1. Chiamiamo performClick() per soddisfare i requisiti di accessibilità
                    v.performClick()

                    // 2. Eseguiamo la nostra logica
                    mapView.controller.animateTo(mapView.mapCenter, mapView.zoomLevelDouble, 500L, 0f)
                    return@setOnTouchListener true
                }
            }
            // Restituiamo false negli altri casi per non interferire con il drag della mappa
            false
        }
        // Imposta l'orientamento salvato nel ViewModel
        mapView.mapOrientation = viewModel.mapRotation
        
        // Osserva le richieste di ridisegno forzato della mappa
        viewModel.mapInvalidateRequest.observe(viewLifecycleOwner, Observer { event ->
            event.getContentIfNotHandled()?.let {
                mapView.invalidate()
            }
        })

        // 1. Osserva le richieste di aggiornamento dei layer (da GpkgLayer).
        layerModel.layerUpdateRequest.observe(viewLifecycleOwner, Observer { event ->
            event.getContentIfNotHandled()?.let {
                //Log.d(TAG, "Ricevuta richiesta di aggiornamento layer via ViewModel. Sincronizzo.")
                onReturnFromLayerDialog()
            }
        })

        // 2. Osserva le richieste di navigazione (da FeatureList).
        layerModel.navigateToPointRequest.observe(viewLifecycleOwner, Observer { event ->
            event.getContentIfNotHandled()?.let { clickedPoint ->
                val animationDuration = 1000L
                Toast.makeText(requireContext(), "Spostamento in corso...", Toast.LENGTH_SHORT).show()

                mapView.controller.animateTo(clickedPoint, 12.5, animationDuration)

                val highlightMarker = Marker(mapView).apply {
                    position = clickedPoint
                    icon = ContextCompat.getDrawable(requireContext(), R.drawable.gps_on)
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                }
                mapView.overlays.add(highlightMarker)
                mapView.invalidate()

                // Una sola coroutine gestisce entrambi i "delayed" in sequenza,
                // niente più controlli manuali su _binding: se la view viene distrutta,
                // viewLifecycleOwner.lifecycleScope cancella automaticamente la coroutine.
                viewLifecycleOwner.lifecycleScope.launch {
                    delay(animationDuration + 200)
                    mapView.invalidate()

                    delay(6000 - (animationDuration + 200)) // completa il totale di 6s dall'inizio
                    mapView.overlays.remove(highlightMarker)
                    mapView.invalidate()
                }
            }
        })

        // Osserva il cambiamento della traccia da seguire
        viewModel.tracciaDaSeguireLiveData.observe(viewLifecycleOwner) {
            updateRemainingVisibility()
        }

        arguments?.getString("gpx_file_uri")?.let { uriString ->
            val gpxUri = uriString.toUri()
            caricaGPX(gpxUri)
            arguments?.remove("gpx_file_uri")
        }

        arguments?.let { bundle ->
            val latitude = bundle.getDouble("latitude", Double.NaN)
            val longitude = bundle.getDouble("longitude", Double.NaN)
            if (!latitude.isNaN() && !longitude.isNaN()) {
                initialCenterPoint = GeoPoint(latitude, longitude)
            }
            //arguments?.clear()
        }

        bottomSheetBehavior = BottomSheetBehavior.from(binding.cruscotto.root)
        bottomSheetBehavior.isHideable = true
        bottomSheetBehavior.skipCollapsed = false
        
        // Imposta lo stato iniziale in base alla registrazione
        if (viewModel.isRecording) {
            bottomSheetBehavior.peekHeight = 120
        } else {
            bottomSheetBehavior.peekHeight = 0
            bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
        }

        binding.cruscotto.root.post {
            if (isAdded && !viewModel.isRecording) {
                bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
            }
        }

        binding.btnOpenDrawer.setOnClickListener {
            (requireActivity() as? MainActivity)?.openDrawer()
        }

        // Aggiungi gli overlay della bussola (solo se il dispositivo ha i sensori necessari) e scala.
        val dm = resources.displayMetrics
        if (hasCompass()) {
            val compassOverlay =
                CompassOverlay(context, InternalCompassOrientationProvider(context), mapView)
            compassOverlay.enableCompass()
            compassOverlay.setCompassCenter(35F, 165F)
            mapView.overlays.add(compassOverlay)
        }
// Calcola l'offset basato sulla densità per evitare la bussola
        val scaleBarOverlay = ScaleBarOverlay(mapView)
// Spostiamo di 100dp a destra (per saltare la bussola) e 90dp in basso
        scaleBarOverlay.setScaleBarOffset((90 * dm.density).toInt(), (50 * dm.density).toInt())
// Centra il testo sopra la barra per un look più pulito
        scaleBarOverlay.setCentred(true)
        mapView.overlays.add(scaleBarOverlay)

        // Assicurati che gli overlay di stato siano sani. Se l'activity è stata ricreata 
        // dopo un onDetach() accidentale, l'OverlayManager interno di FolderOverlay 
        // potrebbe essere nullo, causando NPE. In tal caso li reinizializziamo.
        try { viewModel.listaTracce.items } catch (_: Exception) { viewModel.listaTracce = SafeFolderOverlay() }
        try { viewModel.recTraccia.items } catch (_: Exception) { viewModel.recTraccia = SafeFolderOverlay() }
        try { viewModel.topoLayer.items } catch (_: Exception) { viewModel.topoLayer = SafeFolderOverlay() }

        // Assicurati che gli overlay di stato siano presenti
        // NOTA: Se l'activity è stata ricreata, questi overlay sono conservati nel ViewModel.
        // Verifichiamo se sono già presenti nella nuova MapView prima di aggiungerli.
        
        viewModel.listaTracce.let { 
            if (!mapView.overlays.contains(it)) {
                mapView.overlays.add(it)
            }
        }
        viewModel.recTraccia.let { 
            if (!mapView.overlays.contains(it)) {
                mapView.overlays.add(it)
            }
        }
        if (!mapView.overlays.contains(viewModel.topoLayer)) {
            mapView.overlays.add(viewModel.topoLayer)
        }

        viewModel.listaTracce.items?.forEach { overlay ->
            if (overlay is Polyline) {
                setPolylineClickListener(overlay)
            }
            if (overlay is Marker) {
                setMarkerClickListener(overlay)
            }
        }

        // 2. Inizializza la Polyline e il Marker GPS per la NUOVA MapView.
        // Re-creiamo gli oggetti UI ogni volta che la vista viene creata per evitare
        // problemi di stato interno legati alla MapView precedente.
        currentTrackPolyline = Polyline() 
        currentTrackPolyline.outlinePaint.color = coloreTraccia
        currentTrackPolyline.outlinePaint.strokeWidth = 10f
        
        gpsMarker = Marker(mapView)
        gpsMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
        gpsMarker.title = "Gps"
        gpsMarker.icon = ResourcesCompat.getDrawable(
            requireContext().resources,
            R.drawable.punto_gps,
            requireContext().theme
        )
        gpsMarker.setVisible(false)

        // Aggiungi alla mappa
        mapView.overlays.add(currentTrackPolyline)
        mapView.overlays.add(gpsMarker)

        mapView.isFocusableInTouchMode = true
        mapView.requestFocus()
        mapView.setOnKeyListener(this)
        // mapView.setDestroyMode(false) // Lasciamo che osmdroid gestisca il cleanup se possibile, o gestiamolo noi in onDestroyView

        // Inizializza lo stato del ViewModel basato sulle preferenze.
        // Il caricamento effettivo avverrà in ripristinaStatoMappa() tramite onFirstLayoutListener.
        val savedMenuMap = preferenze.getInt("MenuMap", 1)
        val uriString = preferenze.getString("URIMappa", "") ?: ""
        val uriMappa = uriString.toUri()

        viewModel.menuMap = savedMenuMap
        if (savedMenuMap == 0 && uriString.isNotEmpty()) {
            viewModel.uriMappa = uriMappa
        }

        mapView.apply {
            isTilesScaledToDpi = (viewModel.menuMap != 0)
            setUseDataConnection(viewModel.menuMap != 0)
        }

        val defaultColorArgb = ContextCompat.getColor(requireContext(), R.color.red)
        coloreTraccia = preferenze.getInt("colore_traccia", defaultColorArgb)
        /*val coloreDefault = R.color.black
        coloreTraccia = if (preferenze.contains("colore_traccia")) {
            preferenze.getInt("colore_traccia", coloreDefault)
        } else {
            coloreDefault
        }*/
        mapView.zoomController?.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
        mapView.setMultiTouchControls(true)
        mapView.minZoomLevel = 7.0
        mapView.maxZoomLevel = 19.0
        val mapController: IMapController = MapController(mapView)
        mapController.setCenter(viewModel.ultPosizione)
        mapController.setZoom(viewModel.ultZoom.toDouble())

        aggiornaUIFabBlocMappa(showToast = false)

        binding.btnMenu.setOnClickListener { _ ->
            showMenuBottomSheet()
        }

        binding.fabStopRec.setOnClickListener {
            stopGPS()
            /*if (viewModel.isRecording) {
                stopGPS()
            } else {
                avviaLogicaRegistrazione()
            }*/
        }

        binding.fabBlocMappa.setOnClickListener {
            viewModel.bloccaMappa = !viewModel.bloccaMappa
            aggiornaUIFabBlocMappa()
        }

        binding.camera.setOnClickListener {
            val directions =
                MappaFragmentDirections.actionMappaFragmentToCameraFragment()
            this@MappaFragment.findNavController().navigate(directions)
        }

        binding.cruscotto.btnAllarme.setOnClickListener {
            viewModel.toggleAllarmeState()
        }

        binding.fabSelectDestination.setOnClickListener {
            if (viewModel.isRecording && viewModel.isFixed) {
                if (!isSelectingDestination) {
                    enterDestinationSelectionMode()
                } else {
                    exitDestinationSelectionMode()
                }
            } else {
                Toast.makeText(
                    requireContext(),
                    "Calcolo percorso solo con registrazione avviata",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        binding.buttonConfirmDestination.setOnClickListener {
            val destinationPoint = destinationMarker?.position
            if (destinationPoint == null) {
                Toast.makeText(
                    requireContext(),
                    "Posizione di destinazione non valida.",
                    Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }

            startPointForRouting =
                gpsMarker.position ?: currentTrackPolyline.actualPoints.firstOrNull()
            endPointForRouting = destinationPoint

            if (!isBound) {
                val intent = Intent().apply {
                    component = ComponentName(
                        BROUTER_PACKAGE,
                        BROUTER_SERVICE_CLASS
                    )
                }
                try {
                    requireContext().bindService(intent, connection, Context.BIND_AUTO_CREATE)
                    Toast.makeText(
                        requireContext(),
                        "Calcolo percorso in corso...",
                        Toast.LENGTH_SHORT
                    ).show()
                } catch (_: SecurityException) {
                    Toast.makeText(
                        requireContext(),
                        "Impossibile avviare BRouter.",
                        Toast.LENGTH_LONG
                    ).show()
                    startPointForRouting = null
                    endPointForRouting = null
                }
            } else {
                calculateRoute(startPointForRouting!!, endPointForRouting!!)
                startPointForRouting = null
                endPointForRouting = null
            }
            exitDestinationSelectionMode()
        }

        val numberFormat = NumberFormat.getNumberInstance(Locale.getDefault())
        viewModel.distanzaMetri.observe(viewLifecycleOwner) { distanzaMetri ->
            binding.cruscotto.tvDist.text = MapUtils.formattastring(distanzaMetri)
        }
        viewModel.velocita.observe(viewLifecycleOwner) { velocita ->
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
        viewModel.pendenza.observe(viewLifecycleOwner) { pendenza ->
            // Aggiorna il TextView con il valore della pendenza, aggiungendo il simbolo %
            binding.cruscotto.tvPendenza.text = "$pendenza %"
        }
        viewModel.isAllarmeAttivo.observe(viewLifecycleOwner) { isAttivo ->
            updateBtnAllarmeUI(isAttivo)
        }
        viewModel.isCalibrato.observe(viewLifecycleOwner) { isCalibrated ->
            if (isCalibrated) {
                binding.cruscotto.tvCalcQuota.text = "BARO"
                LocationRepository.usaBaro = true
                // Forza un ricaricamento se il sensore è già attivo nel service
            } else {
                binding.cruscotto.tvCalcQuota.text = "GPS"
                LocationRepository.usaBaro = false
            }
        }
        LocationRepository.gpsStatus.observe(viewLifecycleOwner) { status ->
            updateGpsIcon(status)
        }
        viewModel.locationData.observe(viewLifecycleOwner) { locationData ->
            if (!isAdded) return@observe

            val newGeoPoint = locationData.geoPoint
            if (newGeoPoint.latitude == 0.0 && newGeoPoint.longitude == 0.0) {
                return@observe
            }
            if (::gpsMarker.isInitialized) {
                gpsMarker.position = newGeoPoint
            }

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

            // Osserva i valori rimanenti se segue traccia
            viewModel.remainingDist.observe(viewLifecycleOwner) { remDist ->
                binding.cruscotto.tvRemainingDist.text = MapUtils.formattastring(remDist)
            }
            viewModel.remainingDPiu.observe(viewLifecycleOwner) { remDPiu ->
                binding.cruscotto.tvRemainingDPiu.text = numberFormat.format(remDPiu.toInt())
            }
            viewModel.remainingDMeno.observe(viewLifecycleOwner) { remDMeno ->
                binding.cruscotto.tvRemainingDMeno.text = numberFormat.format(remDMeno.toInt())
            }

            // MODIFICA PRINCIPALE: Gestione della rotazione e del centraggio
            if (viewModel.bloccaMappa) {
                val gpsbearing = locationData.bearing
                var t: Float = 360 - gpsbearing
                if (t < 0) t += 360f
                if (t > 360) t -= 360f
                t = (t.toInt() / 5 * 5).toFloat()

                mapView.mapOrientation = t
                // --- CORREZIONE: Usa setCenter per un'esperienza fluida senza cambiare zoom ---
                mapView.controller?.setCenter(newGeoPoint)
            }

            // segue traccia con una coroutine
            if (viewModel.alertFuoriTraccia && viewModel.tracciaDaSeguire.isNotEmpty()) {
                if (!isAlertDialogShowing()) {
                    // Avviamo una coroutine per non bloccare la UI
                    viewLifecycleOwner.lifecycleScope.launch {
                        val fuoriTraccia = withContext(Dispatchers.Default) {
                            // 1. Cerchiamo la traccia (operazione veloce)
                            val traccia = viewModel.listaTracce.items?.find {
                                it is Polyline && it.title == viewModel.tracciaDaSeguire
                            } as? Polyline

                            // 2. Calcoliamo la distanza (operazione pesante, ora in background)
                            // Nota: usiamo i punti già caricati per evitare di toccare la UI troppo spesso
                            traccia?.let {
                                !it.isCloseTo(newGeoPoint, 30.0, mapView)
                            } ?: false
                        }

                        // 3. Torniamo sul Main Thread per mostrare il dialogo
                        if (fuoriTraccia && isAdded) {
                            mostraAllarmeFuoriTraccia()
                        }
                    }
                }
            }
        }

        LocationRepository.trackPoints.observe(viewLifecycleOwner) { fullTrack ->
            currentTrackPolyline.setPoints(fullTrack)
            //bringRecordingToFront()
            // Usiamo post per garantire che il ridisegno avvenga dopo 
            // che tutti i layer (es. GeoPackage) hanno finito di aggiungersi
            //mapView.post {
            //    if (_binding != null) mapView.invalidate()
            //}
        }
        LocationRepository.newTrackPoint.observe(viewLifecycleOwner) { newPoint ->
            currentTrackPolyline.addPoint(newPoint)
            //bringRecordingToFront()
            //mapView.post {
            //    if (_binding != null) mapView.invalidate()
            //}
        }
        ripristinaStatoMappa()
        mapView.invalidate()
        //Log.d(TAG, "ripristinaStatoMappa invalidate")
    }

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
            mapView.controller.setCenter(eventPos)
            //mapView.controller.animateTo(eventPos)
            true // Ritorna true per indicare che l'evento è stato gestito
        }
    }

    // aggiunge il click listener al marker per aprire l'info window
    private fun setMarkerClickListener(marker: Marker) {
        marker.setOnMarkerClickListener { mMarker, mapView ->
            // Apri la info window qui, usando eventPos come posizione
            mMarker.infoWindow = BasicInfoWindow(R.layout.bonuspack_bubble, mapView)
            mMarker.showInfoWindow()
            mapView.controller.setCenter(marker.position)
            //mapView.controller.animateTo(marker.position)
            true // Ritorna true per indicare che l'evento è stato gestito
        }
    }

    override fun onResume() {
        super.onResume()
        WindowInsetsControllerCompat(requireActivity().window, requireActivity().window.decorView).isAppearanceLightStatusBars = true
        mapView.onResume()
        preferenze.registerOnSharedPreferenceChangeListener(this)
        viewModel.setBaro = preferenze.getBoolean("setBaro", false)
        //Log.d("onResume", "${viewModel.recTraccia})")

        if (viewModel.isRecording) {
            // Se stiamo registrando, forziamo l'aggiornamento dell'icona GPS e la visibilità
            LocationRepository.gpsStatus.value?.let { updateGpsIcon(it) }
            accendiSchermo()

            viewModel.locationData.value?.geoPoint?.let { gpsMarker.position = it }
            gpsMarker.setVisible(true)
            binding.fabBlocMappa.isVisible = true

            bottomSheetBehavior.isHideable = false
            bottomSheetBehavior.peekHeight = 120

            val stateToRestore = viewModel.bottomState
            if (stateToRestore == BottomSheetBehavior.STATE_COLLAPSED ||
                stateToRestore == BottomSheetBehavior.STATE_EXPANDED ||
                stateToRestore == BottomSheetBehavior.STATE_HALF_EXPANDED
            ) {
                bottomSheetBehavior.state = stateToRestore
                if (stateToRestore == BottomSheetBehavior.STATE_HALF_EXPANDED || stateToRestore == BottomSheetBehavior.STATE_EXPANDED) {
                    //Log.d(TAG, "Ripristinato su HALF_EXPANDED. Ricarico i dati visivi.")
                    // Forza l'aggiornamento di TUTTI gli elementi del cruscotto forzando
                    // un nuovo emission sul LiveData (se possibile) o un aggiornamento manuale.
                    val numberFormat = NumberFormat.getNumberInstance(Locale.getDefault())
                    // Aggiornamento manuale forzato dei valori noti al ViewModel:
                    viewModel.quota.value?.let {
                        binding.cruscotto.tvQuota.text = numberFormat.format(it)
                    }
                    viewModel.dislivPiu.value?.let {
                        binding.cruscotto.tvDPiu.text = numberFormat.format(it.toInt())
                    }
                    viewModel.dislivMeno.value?.let {
                        binding.cruscotto.tvDMeno.text = numberFormat.format(it.toInt())
                    }
                    // Ridisegna la view per essere sicuro
                    //binding.cruscotto.root.invalidate()
                    binding.cruscotto.root.post {
                        if (isAdded) {
                            // Ripristina lo stato qui dentro
                            bottomSheetBehavior.state = viewModel.bottomState
                            // Forza il ricalcolo dei margini
                            binding.cruscotto.root.requestLayout()
                        }
                    }
                }
            } else {
                Log.w(
                    TAG,
                    "Stato BottomSheet non valido ($stateToRestore). Imposto collassato di default."
                )
                bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
            }
        } else {
            // Se la registrazione è finita ma c'erano punti, assicurati che il BottomSheet sia nascosto
            bottomSheetBehavior.isHideable = true
            bottomSheetBehavior.peekHeight = 0
            bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
        }

        // la traccia è stata selezionata da schedafragment
        if (viewModel.puntiDaSeguire.isNotEmpty()) {
            if (viewModel.titoloTracciaDaSeguire.isNotEmpty()) {
                (activity as AppCompatActivity).supportActionBar?.title =
                    viewModel.titoloTracciaDaSeguire
            }
            //Log.d("onResume", "Aggiungo nuova traccia")
            val nuovaTraccia = Polyline(mapView)
            nuovaTraccia.title = viewModel.titoloTracciaDaSeguire
            nuovaTraccia.setPoints(viewModel.puntiDaSeguire)
            val mbounds = nuovaTraccia.bounds
            disegnaLineaSfondo(nuovaTraccia)
            // Aggiungi solo alla listaTracce, dato che listaTracce è già in mapView.overlays
            viewModel.listaTracce.add(nuovaTraccia)
            Log.d("layerItems", "onresume ${viewModel.layerItems.size}")
            val percorsoColorato = Polyline(mapView).apply {
                setPoints(viewModel.puntiDaSeguire)
                isVisible = true
            }
            // Applica la colorazione basata sullo stato salvato per pendenza 2 polyline
            percorsoColorato.title = viewModel.titoloTracciaDaSeguire
            if (viewModel.coloriPuntiDaSeguire?.isNotEmpty() == true) {
                disegnaPercorsoColorato(percorsoColorato, viewModel.coloriPuntiDaSeguire)
            } else {
                disegnaPercorsoColorato(percorsoColorato)
            }
            // Aggiungi solo alla listaTracce
            viewModel.listaTracce.add(percorsoColorato)
            Log.d("layerItems", "onresume ${viewModel.layerItems.size}")

            // Se la direzione era attiva nel ViewModel (anche se non dovrebbe esserlo qui, per sicurezza)
            // o se vogliamo applicarla subito se il layer item corrispondente la ha attiva
            // Al momento SchedaFragment aggiunge un nuovo LayerItem con direzione = false

            //applicaFrecceDirezione(percorsoColorato)
            viewModel.puntiDaSeguire = mutableListOf()
            setPolylineClickListener(nuovaTraccia)
            addMarker(nuovaTraccia)
            mapView.post {
                mapView.zoomToBoundingBox(mbounds.increaseByScale(1.2f), false)
            }

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
            viewModel.listaTracce.items?.find { it is Marker && it.position == viewModel.poi }?.let { overlay ->
                val alMarker = overlay as Marker
                alMarker.infoWindow = BasicInfoWindow(R.layout.bonuspack_bubble, mapView)
                alMarker.showInfoWindow()
                mapView.controller.animateTo(
                    alMarker.position,
                    viewModel.ultZoom.toDouble(),
                    0
                )
            }
            viewModel.poi = GeoPoint(0.0, 0.0, 0.0)
        }

        // 1. Sincronizzazione Toponimi (Ottimizzata)
        if (viewModel.toponimiSelezionati != lastSyncedToponimi) {
            syncToponimiMarkers()
            lastSyncedToponimi = viewModel.toponimiSelezionati.toList()
        }

        // 2. Sincronizzazione Layer GeoPackage (Ottimizzata)
        if (!layersSyncedThisResume) {
            onReturnFromLayerDialog()
        }

        val hasRecordedPoints = LocationRepository.trackPointsList.isNotEmpty()
        if (hasRecordedPoints) {
            // --- RIPRISTINO STATO TRACCIA REGISTRATA ---
            val fullTrackSnapshot = LocationRepository.getFullTrackSnapshot()
            currentTrackPolyline.setPoints(fullTrackSnapshot)
            gpsMarker.setVisible(viewModel.isRecording) 
            
            // Se stiamo registrando, assicuriamoci che il marker GPS sia nella posizione corretta
            viewModel.locationData.value?.geoPoint?.let { gpsMarker.position = it }

            // Se la registrazione era iniziata, ricarichiamo il marker di inizio nel FolderOverlay recTraccia
            if (viewModel.isRecording && fullTrackSnapshot.isNotEmpty()) {
                val startPoint = fullTrackSnapshot.first()
                // Evitiamo duplicati nel FolderOverlay recTraccia
                viewModel.recTraccia.items?.clear()
                MapUtils.markInizioFine(
                    requireContext(),
                    startPoint,
                    mapView,
                    viewModel.recTraccia,
                    0 // 0 = Inizio
                )
            }

            //bringRecordingToFront()
        }

        // Forza un ridisegno posticipato per risolvere eventuali race condition
        // causate dallo spostamento programmatico della mappa (toponimi/waypoint)
        /*viewLifecycleOwner.lifecycleScope.launch {
            delay(500)
            val fullTrackSnapshot = LocationRepository.getFullTrackSnapshot()
            if (fullTrackSnapshot.isNotEmpty()) {
                currentTrackPolyline.setPoints(fullTrackSnapshot)
            }
            bringRecordingToFront()
            mapView.invalidate()
        }*/
        /*mapView.postDelayed({
            if (_binding != null) {
                // Ri-sincronizza un'ultima volta per sicurezza
                val fullTrackSnapshot = LocationRepository.getFullTrackSnapshot()
                if (fullTrackSnapshot.isNotEmpty()) {
                    currentTrackPolyline.setPoints(fullTrackSnapshot)
                }
                bringRecordingToFront()
                mapView.invalidate()
            }
        }, 500) // 500ms sono sufficienti per superare le animazioni di spostamento*/
        //Log.d(TAG, "onResume invalidate")
    }

    override fun onPause() {
        super.onPause()
        WindowInsetsControllerCompat(requireActivity().window, requireActivity().window.decorView).isAppearanceLightStatusBars = false
        preferenze.unregisterOnSharedPreferenceChangeListener(this)
        // memorizza valori per ripristinare la mappa
        viewModel.ultZoom = mapView.zoomLevelDouble.toInt()
        viewModel.ultPosizione = mapView.mapCenter as GeoPoint
        //memorizza stato del bottomSheet
        if (::bottomSheetBehavior.isInitialized)
            viewModel.bottomState = bottomSheetBehavior.state
        mapView.onPause() //needed for compass, my location overlays, v6.0.0 and up
        layerModel.recordCurrentLayerVisibility()
        layerModel.loadingStatus.clear()
        layersSyncedThisResume = false
        //Log.d(TAG, "onPause: Stato di caricamento dei layer resettato.")
    }

    private fun offline() {
        // Crea l'intent per selezionare un file generico.
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            // Aggiunge i flag necessari per garantire i permessi di lettura dell'URI restituito.
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            // Specifica che il file deve essere apribile.
            addCategory(Intent.CATEGORY_OPENABLE)
            // Imposta il tipo MIME per accettare qualsiasi tipo di file.
            // Puoi essere più specifico se cerchi solo file .map, ma questo è più generico.
            type = "application/octet-stream"
        }
        // Avvia l'attività di selezione file usando il nuovo launcher.
        // Il risultato verrà gestito nel callback definito in mapFileSelectorLauncher.
        mapFileSelectorLauncher.launch(intent)
    }

    /**
     * Controlla se il dispositivo è dotato dei sensori necessari per la bussola.
     * @return True se sono presenti sia l'accelerometro che il magnetometro, altrimenti False.
     */
    private fun hasCompass(): Boolean {
        val sensorManager =
            requireContext().getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
        return accelerometer != null && magnetometer != null
    }

    private fun ripristinaStatoMappa() {
        val menuMap = preferenze.getInt("MenuMap", 1)
        val uriString = preferenze.getString("URIMappa", null)

        mapView.addOnFirstLayoutListener { _, _, _, _, _ ->
            //Log.d(TAG, "OnFirstLayoutListener eseguito. La mappa è pronta.")

            // --- GESTIONE RITORNO DA TOPONIMIFRAGMENT ---
            // Recupera eventuali coordinate passate tramite bundle (Navigation Component)
            val bundleLat = arguments?.getDouble("latitude", Double.NaN) ?: Double.NaN
            val bundleLon = arguments?.getDouble("longitude", Double.NaN) ?: Double.NaN
            if (!bundleLat.isNaN() && !bundleLon.isNaN()) {
                // Se sono presenti nel bundle, hanno la priorità assoluta
                val targetPoint = GeoPoint(bundleLat, bundleLon)
                //Log.d(TAG, "Navigazione da bundle: $targetPoint")
                mapView.controller.setZoom(17.0) // Zoom ravvicinato per il toponimo
                mapView.controller.setCenter(targetPoint)
                mapView.controller.animateTo(targetPoint)
                // Opzionale: pulisci i parametri per evitare ri-centramgi indesiderati al prossimo layout
                arguments?.remove("latitude")
                arguments?.remove("longitude")
                initialCenterPoint = null 
            } else if (initialCenterPoint != null) {
                // Gestione vecchia variabile di supporto
                mapView.controller.setZoom(14.0)
                mapView.controller.setCenter(initialCenterPoint)
                initialCenterPoint = null
            } else {
                // Ripristino posizione precedente salvata nel ViewModel
                mapView.controller.setZoom(viewModel.ultZoom.toDouble())
                mapView.controller.setCenter(viewModel.ultPosizione)
            }

            // Caricamento layer mappa
            if (menuMap == 0 && uriString != null) {
                apreMappa(requireContext(), mapView, viewModel, uriString.toUri())
            } else {
                online(requireContext(), mapView, viewModel, menuMap)
            }
            bringRecordingToFront()
            mapView.postInvalidate()
        }
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
                salvaTraccia(inputEditTextField.text.toString())
                azzeraCruscotto()
            }
            setNegativeButton("Elimina") { _, _ ->
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
        // //Log.d("Posizione","Stop servizio")
        requireActivity().stopService(Intent(context, LocationService::class.java))
        viewModel.stopUpdates()
        viewModel.isRecording = false
        binding.fabBlocMappa.isVisible = false
        gpsMarker.setVisible(false)
        binding.fabSelectDestination.isVisible = false
        binding.fabStopRec.isVisible = false
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

    /*private fun stopObserver() {
        viewModel.distanzaMetri.removeObservers(viewLifecycleOwner)
        viewModel.velocita.removeObservers(viewLifecycleOwner)
        viewModel.quota.removeObservers(viewLifecycleOwner)
        viewModel.dislivPiu.removeObservers(viewLifecycleOwner)
        viewModel.dislivMeno.removeObservers(viewLifecycleOwner)
        viewModel.secondiMovimento.removeObservers(viewLifecycleOwner)
        //gpsViewModel.gpsStatus.removeObservers(viewLifecycleOwner)
    }*/

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
        // pulizia totale prima di iniziare
        viewModel.isRecording = false
        viewModel.resetCruscotto() // Questo chiama internamente LocationRepository.clearTrack()
        LocationRepository.clearTrack() // Chiamata esplicita per sicurezza
        viewModel.recTraccia.items?.clear()
        viewModel.isFixed = false

// Svuota esplicitamente la polilinea grafica
        if (::currentTrackPolyline.isInitialized) {
            currentTrackPolyline.setPoints(mutableListOf()) // USA setPoints invece di actualPoints.clear()
        }

// imposta schermo sempre acceso
        accendiSchermo()
// inizio registrazione posizione
        viewModel.isRecording = true
        viewModel.oraInizio = System.currentTimeMillis()
        // legge preferenze per il tipo di attività
        val activityType = preferenze.getString("activity_type", "mtb")
        viewModel.startUpdates()

        if (::currentTrackPolyline.isInitialized) {
            mapView.overlays.remove(currentTrackPolyline)
        }
        // 2. Invece di ricreare la polyline, la svuotiamo e la riconfiguriamo.
        currentTrackPolyline.actualPoints.clear()
        currentTrackPolyline.outlinePaint.color = coloreTraccia // Usa il colore delle preferenze
        currentTrackPolyline.outlinePaint.strokeWidth = 10f
        // 3. Aggiungi la nuova polyline alla mappa. Aggiungendola per ultima,
        //    sarà disegnata SOPRA tutti gli altri overlay già presenti (come la traccia GPX).
        mapView.overlays.add(currentTrackPolyline)
        mapView.overlays.remove(gpsMarker)
        // 2. Ri-aggiungi il marker alla fine della lista.
        //    Questo garantisce che venga disegnato sopra a tutto il resto.
        gpsMarker.setVisible(true)
        mapView.overlays.add(gpsMarker)
        binding.fabBlocMappa.isVisible = true
        // Mostra o nascondi il bottone del menu BRouter in base all'installazione
        binding.fabSelectDestination.isVisible = isBRouterInstalled(requireContext())

        // 4. Invalida la mappa per forzare un ridisegno immediato.
        mapView.invalidate()


        // avvia il servizio per tracciare locazione in background
        //requireActivity().startService(Intent(context, LocationService::class.java))
        val serviceIntent = Intent(requireContext(), LocationService::class.java)
        // 3. Aggiungi il tipo di attività come "extra" all'Intent.
        serviceIntent.putExtra("ACTIVITY_TYPE", activityType)
        // 4. Avvia il servizio usando l'Intent modificato.
        ContextCompat.startForegroundService(requireContext(), serviceIntent)
        // predispone cruscotto
        // 3. IMPOSTA LA VISIBILITÀ DELLA PENDENZA IN BASE AL TIPO DI ATTIVITÀ
        if (activityType != "mtb") {
            // Se l'attività NON è "Mountain Bike", nascondi gli elementi della pendenza
            binding.cruscotto.iconPendenza.visibility = View.GONE
            binding.cruscotto.tvPendenza.visibility = View.GONE
        } else {
            // Altrimenti, assicurati che siano visibili
            binding.cruscotto.iconPendenza.visibility = View.VISIBLE
            binding.cruscotto.tvPendenza.visibility = View.VISIBLE
        }
        bottomSheetBehavior.isHideable = false
        bottomSheetBehavior.peekHeight = 120
        bottomSheetBehavior.halfExpandedRatio = 0.5f
        bottomSheetBehavior.state = BottomSheetBehavior.STATE_HALF_EXPANDED
        LocationRepository.updateGpsStatus("started")
        binding.fabStopRec.isVisible = true
        showCustomSnackbar(binding.root, "Registrazione in corso")
    }

    private fun caricaGPX(uri: Uri) {
        if (!isAdded) return // Esci se il fragment non è collegato
        val trackPointsOriginali: MutableList<GeoPoint> = mutableListOf()
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


// Carica i punti della traccia se esistono- da verificare con gpx multisegmento
        // 2. POPOLAMENTO DELLA LISTA: Leggi i punti e aggiungili alla nostra lista.
        gpx.tracks.firstOrNull()?.trackPoints?.forEach { trackPoint ->
            val punto =
                GeoPoint(trackPoint.latitude, trackPoint.longitude, trackPoint.elevation ?: 0.0)
            trackPointsOriginali.add(punto)

            // Calcola statistiche (distanza, ascesa, discesa)
            if (trackPoint.elevation == null) altiNulla += 1

            if (oldPunto != null) {
                viewModel.trackDistanza += MapUtils.getDistanceInMeters(oldPunto, punto)
                val dislivello = (punto.altitude) - (oldPunto.altitude)
                if (dislivello > 0) {
                    viewModel.trackAscesa += dislivello.toInt()
                } else {
                    viewModel.trackDiscesa += dislivello.toInt()
                }
            }
            oldPunto = punto
        }

        // nome file (da Maputils)
        val nome = getFileNameFromUri(requireContext(), uri)
        // 1. Crea la Polyline per lo SFONDO (gradiente di colore)
        val polylineColore = Polyline(mapView).apply {
            title = nome
            setPoints(trackPointsOriginali)
            isVisible = true
        }

        val polylineFrecce = Polyline(mapView).apply {
            title = nome
            setPoints(trackPointsOriginali)
            isVisible = true
        }
        // *** CALCOLO PENDENZE OTTIMIZZATO (chiamata singola) ***
        val pendenze = MapUtils.calcolaPendenzeSmussate(polylineColore, 8)

        // 4. Crea una NUOVA lista di punti con la pendenza "iniettata" nel campo altitudine.
        val puntiConPendenza = trackPointsOriginali.mapIndexed { index, geoPoint ->
            val pendenza = if (index < pendenze.size) pendenze[index].toDouble() else 0.0
            GeoPoint(geoPoint.latitude, geoPoint.longitude, pendenza)
        }

        // 5. AGGIORNA i punti della polyline di sfondo per usare quelli con la pendenza.
        //    Questo è il passaggio chiave per "ingannare" il sistema di colorazione.
        polylineColore.setPoints(puntiConPendenza)
        // 3. Applica gli stili specifici, PASSANDO la lista delle pendenze
        disegnaLineaSfondo(polylineColore)
        disegnaPercorsoColorato(polylineFrecce, pendenze)

        // Aggiungi SOLO alla listaTracce del ViewModel. 
        // listaTracce è già in mapView.overlays.
        viewModel.listaTracce.add(polylineColore)
        viewModel.listaTracce.add(polylineFrecce)
        
        // Aggiungiamo anche il LayerItem se non presente
        /*if (viewModel.layerItems.none { it.nome == nome }) {
            viewModel.layerItems.add(
                LayerItem(
                    nome = nome,
                    abilitato = true,
                    direzione = false,
                    segui = false,
                    distanza = viewModel.trackDistanza,
                    ascesa = viewModel.trackAscesa,
                    discesa = viewModel.trackDiscesa
                )
            )
        }*/

        // 6. Aggiungi i marker di inizio/fine (associandoli alla polilinea di sfondo)
        addMarker(polylineColore)
        //------------------------------------------------------------------------------------------
        // questo usato per disegno traccia con altitudine
        //disegnaLine(line)
        //viewModel.listaTracce.add(line)
        //addMarker(line)
        //------------------------------------------------------------------------------------------
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
            waymarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            waymarker.position = GeoPoint(it.latitude, it.longitude, it.elevation ?: 0.0)
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
                mapView.zoomToBoundingBox(polylineColore.bounds.increaseByScale(1.2f), false)
            }
            MapUtils.alertSegui(requireContext(), viewModel, polylineColore)
        }
        if (!mapView.overlays.contains(viewModel.listaTracce)) {
            mapView.overlays.add(viewModel.listaTracce)
        }
        if (viewModel.isRecording) {
            //Log.d(TAG, "caricaGPX: Ri-ordino gli overlay per portare la registrazione in primo piano.")
            // Rimuovi e ri-aggiungi la traccia della registrazione IN USO
            mapView.overlays.remove(currentTrackPolyline)
            mapView.overlays.add(currentTrackPolyline)

            // Rimuovi e ri-aggiungi il marker GPS
            mapView.overlays.remove(gpsMarker)
            mapView.overlays.add(gpsMarker)
            gpsMarker.setVisible(true)
            //Log.d("caricagpx", "gpsMarker visibile")
        }
        //Log.d("layerItems", "caricagpx ${viewModel.layerItems.size}")
        // Infine, ridisegna la mappa
        mapView.invalidate()
    }

    private fun updateGpsIcon(status: String?) {
        when (status) {
            "started" -> {binding.fabStopRec.backgroundTintList = ContextCompat.getColorStateList(requireContext(), R.color.orange) // O l'icona che usi per "ricerca GPS"
                        binding.fabStopRec.isVisible = true}
            "fixed" -> {binding.fabStopRec.backgroundTintList = ContextCompat.getColorStateList(requireContext(), R.color.red)// Icona per GPS fixato
                        binding.fabStopRec.isVisible = true}
            "stopped" -> {binding.fabStopRec.backgroundTintList = ContextCompat.getColorStateList(requireContext(), R.color.white)   // Icona per GPS spento/non attivo
                        binding.fabStopRec.isVisible = false}
            else -> {binding.fabStopRec.backgroundTintList = ContextCompat.getColorStateList(requireContext(), R.color.white)      // Default a spento se lo stato è null o non riconosciuto
                        binding.fabStopRec.isVisible = false}
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
        viewModel.mapRotation = mapView.mapOrientation
        alertDialog?.dismiss()
        alertDialog = null
        if (_binding != null) {
            mapView.onPause() // Per sicurezza, assicuriamoci che sia in pausa
            
            // Per evitare il crash NPE in FolderOverlay.onTouchEvent, NON chiamiamo 
            // mapView.onDetach() perché questo causerebbe il detach ricorsivo di tutti 
            // gli overlay. In osmdroid, FolderOverlay.onDetach() distrugge il suo 
            // OverlayManager interno (null), rendendolo inutilizzabile per il ViewModel.
            // Detacciamo invece solo il tileProvider per fermare i thread di caricamento.
            mapView.tileProvider.detach()
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

    private fun avviaLogicaRegistrazione() {
        // FASE 1: Controlla se hai i permessi in PRIMO PIANO.
        if (!isFineLocationPermissionGranted()) {
            // NON HAI I PERMESSI DI BASE.
            // Richiedili. Il flusso si ferma qui. L'utente dovrà premere di nuovo dopo averli concessi.
            val permissionsToRequest = mutableListOf<String>()
            permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                permissionsToRequest.add(Manifest.permission.FOREGROUND_SERVICE_LOCATION)
            }
            requestFineLocationLauncher.launch(permissionsToRequest.toTypedArray())
            Toast.makeText(
                requireContext(),
                "È richiesto il permesso per la posizione.",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        // FASE 2: Controlla se hai i permessi in BACKGROUND (se necessari).
        if (!isBackgroundLocationPermissionGranted()) {
            // HAI I PERMESSI DI BASE, ma ti manca quello in background.
            // Mostra la tua spiegazione. Il flusso si ferma qui. La richiesta di sistema verrà
            // attivata dal pulsante "Continua" del tuo dialogo.
            showBackgroundLocationDisclosure()
            return
        }

        // FASE 3: HAI TUTTI I PERMESSI NECESSARI.
        // Procedi con la logica di avvio della registrazione.
        //Log.d(TAG, "Tutti i permessi sono stati concessi. Avvio registrazione.")
        if (viewModel.haBaro && viewModel.setBaro) {
            if (!viewModel.isCalibrato.value!!) {
                altDaBaro()
            } else {
                attivaGps()
            }
        } else {
            attivaGps()
        }
    }

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

    private fun addMarker(polyline: Polyline) {
        if (polyline.actualPoints.isEmpty()) return

        // Marker di INIZIO
        val startMarker = Marker(mapView)
        startMarker.position = polyline.actualPoints.first()
        startMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        startMarker.icon = AppCompatResources.getDrawable(
            requireContext(),
            R.drawable.ic_start
        ) // Icona di partenza
        startMarker.title = "Partenza"

        // Marker di FINE
        val endMarker = Marker(mapView)
        endMarker.position = polyline.actualPoints.last()
        endMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        endMarker.icon = AppCompatResources.getDrawable(
            requireContext(),
            R.drawable.ic_finish
        ) // Icona di arrivo
        endMarker.title = "Arrivo"

        // CORREZIONE: Aggiungi i marker al FolderOverlay nel ViewModel
        viewModel.listaTracce.add(startMarker)
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

    private fun aggiornaUIFabBlocMappa(showToast: Boolean = true) {
        if (viewModel.bloccaMappa) {
            // Se la mappa è bloccata, mostra l'icona "bloccata" (es. PIN_RED)
            binding.fabBlocMappa.setImageResource(PIN_RED)
            if (showToast) Toast.makeText(requireContext(), "Mappa ancorata punto GPS", Toast.LENGTH_SHORT).show()
        } else {
            // Se la mappa è sbloccata, mostra l'icona "sbloccata" (es. PIN_BLACK)
            binding.fabBlocMappa.setImageResource(PIN_BLACK)
            if (showToast) Toast.makeText(requireContext(), "Mappa libera", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Garantisce che gli elementi della registrazione attiva (traccia, marker inizio/fine e GPS)
     * siano sempre gli ultimi overlay nella lista, venendo così disegnati SOPRA ogni altro layer.
     */
    private fun bringRecordingToFront() {
        if (_binding == null) return
        
        // 1. Sposta il FolderOverlay che contiene i marker di inizio/fine
        if (mapView.overlays.contains(viewModel.recTraccia)) {
            mapView.overlays.remove(viewModel.recTraccia)
            mapView.overlays.add(viewModel.recTraccia)
        }
        
        // 2. Sposta la Polyline del percorso corrente
        if (::currentTrackPolyline.isInitialized && mapView.overlays.contains(currentTrackPolyline)) {
            mapView.overlays.remove(currentTrackPolyline)
            mapView.overlays.add(currentTrackPolyline)
        }
        
        // 3. Sposta il marker della posizione GPS (deve essere il più alto in assoluto)
        if (::gpsMarker.isInitialized && mapView.overlays.contains(gpsMarker)) {
            mapView.overlays.remove(gpsMarker)
            mapView.overlays.add(gpsMarker)
        }
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String?) {
        when (key) {
            "setBaro" -> {
                // Aggiorna il ViewModel quando la preferenza 'setBaro' cambia.
                viewModel.setBaro = sharedPreferences.getBoolean(key, false)
                //Log.d(TAG, "Preferenza 'setBaro' aggiornata a: ${viewModel.setBaro}")
            }

            "haBaro" -> {
                // Anche se questo valore non dovrebbe cambiare, è buona norma gestirlo.
                viewModel.haBaro = sharedPreferences.getBoolean(key, false)
            }

            "colore_traccia" -> { // NUOVA LOGICA QUI
                // 1. Rileva il cambiamento del colore traccia
                val defaultColorArgb = ContextCompat.getColor(requireContext(), R.color.red)
                val newColorArgb = sharedPreferences.getInt("colore_traccia", defaultColorArgb)
                // 3. Aggiorna la variabile di classe
                coloreTraccia = newColorArgb
                // 4. Applica immediatamente il nuovo colore
                /*if (::currentTrackPolyline.isInitialized) {
                    currentTrackPolyline.outlinePaint.color = coloreTraccia
                    mapView.invalidate()
                }*/
            }
        }
    }

    /*private fun addGeopackageTiles() {
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
            //Log.d("packgage", "inside geopackage exception " + ex.message)
        }
    }*/

    private fun createOsmPolygonFromNgaPolygon(
        ngaPolygon: mil.nga.sf.Polygon,
        tableName: String,
        featureRow: FeatureRow,
        colore: String
    ): Polygon {
        //1. Usa il costruttore vuoto. Questo ha risolto il crash (NullPointerException).
        val osmdroidPolygon = Polygon()
        val exteriorRingPoints = mutableListOf<GeoPoint>()

        val firstRing = ngaPolygon.rings?.firstOrNull()
        if (firstRing != null && firstRing.points != null) {
            firstRing.points.forEach { ngaPoint ->
                if (ngaPoint != null) {
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
                if (interiorNgaRing != null && interiorNgaRing.points != null) {
                    val holePath = mutableListOf<GeoPoint>()
                    interiorNgaRing.points.forEach { ngaPoint ->
                        if (ngaPoint != null) {
                            holePath.add(GeoPoint(ngaPoint.y, ngaPoint.x))
                        } else {
                            Log.w(TAG, "Null point found in an interior ring (hole) of polygon.")
                        }
                    }
                    if (holePath.isNotEmpty()) {
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


// 2. Pre-calcola l'etichetta ORA, sul thread in background, una sola volta.
        val finalLabel = layerModel.creaLabel(featureRow, tableName)
        osmdroidPolygon.relatedObject = finalLabel // Salva la stringa finale nell'oggetto

// 3. Imposta un listener "stupido" che si limita a mostrare la stringa già pronta.
        osmdroidPolygon.setOnClickListener { polygon, _, _ ->
            (polygon.relatedObject as? String)?.let { label ->
                mostraAlertDialogSemplice(label)
            }
            true // Evento gestito
        }

        // Controlla se la stringa del colore non è vuota o "RANDOM"
        if (colore.isNotBlank() && colore != "RANDOM") {
            try {
                val parsedColor = colore.toColorInt()
                val semiTransparentColor = Color.argb(
                    80,
                    Color.red(parsedColor),
                    Color.green(parsedColor),
                    Color.blue(parsedColor)
                )
                osmdroidPolygon.fillPaint.color = semiTransparentColor
            } catch (_: IllegalArgumentException) {
                Log.w(TAG, "Colore non valido nel database: '$colore'. Uso un colore casuale.")
                osmdroidPolygon.fillPaint.color = layerModel.getRandomIntColor(80)
            }
        } else {
            osmdroidPolygon.fillPaint.color = layerModel.getRandomIntColor(80)
        }

        osmdroidPolygon.outlinePaint.color = layerModel.polygonOptions.strokeColor
        osmdroidPolygon.outlinePaint.strokeWidth = layerModel.polygonOptions.strokeWidth
        osmdroidPolygon.title = layerModel.polygonOptions.title
        return osmdroidPolygon
    }

    private fun mostraAlertDialogSemplice(messaggio: String) {
        if (!isAdded) return //
        // 1. Infla il nuovo layout personalizzato
        val dialogView =
            LayoutInflater.from(requireContext()).inflate(R.layout.dialog_details_layout, null)
        // 2. Trova le View all'interno del nostro layout
        val messageTextView: TextView = dialogView.findViewById(R.id.dialog_message_text)
        val closeButton: Button = dialogView.findViewById(R.id.dialog_close_button)
        // 3. Imposta il testo del messaggio
        messageTextView.text = messaggio
        // 4. Costruisci il dialogo SENZA i pulsanti di default
        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .create()
        // 5. Rendi trasparente lo sfondo della finestra del dialogo
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        // 6. Imposta le listener per il nostro pulsante personalizzato
        closeButton.setOnClickListener {
            dialog.dismiss()
        }
        // Mostra il dialogo
        dialog.show()
    }

    /**
     * Sincronizza i marker dei toponimi sulla mappa con la lista nel ViewModel.
     * Rimuove solo quelli necessari o ricrea la lista se cambiata significativamente.
     */
    private fun syncToponimiMarkers() {
        if (!isAdded || _binding == null) return

        // Per semplicità e robustezza, in questo caso svuotiamo e ricostruiamo,
        // ma la chiamata è protetta dal controllo 'viewModel.toponimiSelezionati != lastSyncedToponimi' in onResume
        displayedTopoMarkers.forEach { mapView.overlays.remove(it) }
        displayedTopoMarkers.clear()

        if (viewModel.toponimiSelezionati.isNotEmpty()) {
            viewModel.toponimiSelezionati.forEach { topoData ->
                val newMarker = RemovableMarker(mapView).apply {
                    id = topoData.id
                    position = GeoPoint(topoData.latitude, topoData.longitude)
                    title = topoData.name
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    icon = ResourcesCompat.getDrawable(
                        requireContext().resources,
                        R.drawable.pin_rosso, requireContext().theme
                    )
                    setOnMarkerClickListener { marker, mv ->
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
                    onMarkerLongClick = { markerInstance ->
                        val toponimoDataToRemove = viewModel.toponimiSelezionati.find {
                            it.id == markerInstance.id
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
                        }
                        true
                    }
                }
                displayedTopoMarkers.add(newMarker)
                mapView.overlays.add(newMarker)
            }
        }
    }

    // Attorno alla riga 2297
    private fun puntiSuMappa(tableName: String, featureInfo: FeatureTableInfo) {
        // Controllo di blocco anti-concorrenza
        if (layerModel.loadingStatus[tableName] == true) {
            Log.w(TAG, "Caricamento per il layer $tableName già in corso. Chiamata ignorata.")
            return
        }

        if (featureInfo.listOverlay != null && featureInfo.listOverlay!!.isNotEmpty()) {
            //Log.d(TAG,"Pulizia di ${featureInfo.listOverlay!!.size} overlay esistenti per il layer: $tableName")
            // 2. Rimuovi tutti gli overlay precedentemente associati a questo layer dalla mappa.
            mapView.overlays.removeAll(featureInfo.listOverlay!!.toSet()) // .toSet() è più sicuro
            // 3. Svuota la lista di overlay salvata nel modello dati.
            featureInfo.listOverlay!!.clear()
        }
        // 2. CASO SPECIALE: Layer Geologico (Raster/Tiles)
        if (tableName == "area_geologica") {
            val geoPackage = layerModel.geoPackageInstance ?: return
            val featureDao = geoPackage.getFeatureDao(tableName)

            // Creiamo la nostra classe custom con i colori Sardegna
            val featureTiles = GeologiaFeatureTiles(requireContext(), geoPackage, featureDao)
            featureTiles.isFillPolygon = true
            featureTiles.setStylePaintCacheSize(1)

            val tileProvider = object : MapTileProviderArray(TileSourceFactory.DEFAULT_TILE_SOURCE, null) {
                init {
                    // Svuota i moduli di default per evitare di scaricare mattonelle online duplicando la mappa
                    mTileProviderList.clear()
                    mTileProviderList.add(GeoPackageFeatureTileModule(featureTiles))
                }
                override fun getMinimumZoomLevel(): Int = 12
            }

            val tilesOverlay = object : TilesOverlay(tileProvider, requireContext()) {
                override fun draw(c: Canvas, pProjection: org.osmdroid.views.Projection) {
                    if (pProjection.zoomLevel < 12.0) return
                    super.draw(c, pProjection)
                }

                override fun onDetach(mapView: MapView?) {
                    super.onDetach(mapView)
                    tileProvider.detach()
                }
            }.apply {
                // Rendi trasparente lo sfondo di caricamento per non nascondere la mappa sottostante
                loadingBackgroundColor = Color.TRANSPARENT
                loadingLineColor = Color.TRANSPARENT
            }

            // Interrogazione (onClick)
            val eventsReceiver = object : MapEventsReceiver {
                override fun singleTapConfirmedHelper(p: GeoPoint): Boolean {
                    val mBinding = _binding ?: return false
                    if (mBinding.Mapview.zoomLevelDouble < 12.0) return false
                    interrogaGeologia(p, geoPackage, "area_geologica")
                    return true
                }
                override fun longPressHelper(p: GeoPoint): Boolean = false
            }
            val mapEventsOverlay = MapEventsOverlay(eventsReceiver)

            // IMPORTANTE: Salviamo gli overlay in listOverlay per poterli rimuovere col checkbox
            val overlays = mutableListOf(tilesOverlay, mapEventsOverlay)
            featureInfo.listOverlay = overlays
            mapView.overlays.addAll(overlays)

            mapView.invalidate()
            return // Usciamo, non serve procedere col caricamento vettoriale
        }

        // Controllo di blocco anti-concorrenza
        if (layerModel.loadingStatus[tableName] == true) {
            Log.w(TAG, "Caricamento per il layer $tableName già in corso. Chiamata ignorata.")
            return
        }

        // Controllo di sicurezza per la vista all'inizio
        if (_binding == null) {
            Log.w(TAG, "puntiSuMappa chiamato ma la vista è nulla. Interruzione.")
            return
        }

        layerModel.loadingStatus[tableName] = true
        binding.loadingProgressBar.visibility = View.VISIBLE

        // Usa viewLifecycleOwner.lifecycleScope per legare la coroutine alla VISTA
        viewLifecycleOwner.lifecycleScope.launch {
            //Log.d(TAG, "Avvio coroutine per layer: $tableName")

            val processedData = withContext(Dispatchers.IO) {
                loadAndProcessFeaturesInBackground(tableName, featureInfo)
            }

            // --- CONTROLLO DI SICUREZZA POST-COROUTINE ---
            // Ricontrolla se la vista è ancora valida PRIMA di toccare la UI
            if (_binding == null) {
                Log.w(TAG, "Vista distrutta dopo caricamento per $tableName. Annullamento.")
                layerModel.loadingStatus[tableName] = false // Rilascia il blocco
                return@launch
            }
            // ---------------------------------------------

            binding.loadingProgressBar.visibility = View.GONE

            val createdOverlays = mutableListOf<org.osmdroid.views.overlay.Overlay>()
            processedData.points?.let { createdOverlays.addAll(creaOverlayPunti(it, featureInfo)) }
            processedData.polygons?.let {
                createdOverlays.addAll(
                    creaOverlayPoligoni(
                        it,
                        featureInfo
                    )
                )
            }
            processedData.lineStrings?.let {
                createdOverlays.addAll(
                    creaOverlayLinee(
                        it,
                        featureInfo
                    )
                )
            }

            // Salva e aggiungi i nuovi overlay
            featureInfo.listOverlay = createdOverlays
            mapView.overlays.addAll(createdOverlays)
            
            // Riporta la registrazione in primo piano
            bringRecordingToFront()

            mapView.invalidate()
            //Log.d(TAG, "Aggiunti ${createdOverlays.size} nuovi overlay per $tableName")

            // Rilascia il blocco alla fine
            layerModel.loadingStatus[tableName] = false
        }
    }


    private fun creaOverlayPoligoni(
        osmdroidPolygonsToAdd: MutableList<Polygon>, featureInfo: FeatureTableInfo
    ): List<org.osmdroid.views.overlay.Overlay> {
        // Creiamo un singolo FolderOverlay per contenere tutti i poligoni.
        val folder = SafeFolderOverlay()
        // Assegnare un nome è utile per il debug.
        folder.name = featureInfo.name

        // Aggiungiamo tutti i poligoni al folder in un'unica operazione.
        folder.items?.addAll(osmdroidPolygonsToAdd)

        // Restituiamo una lista che contiene SOLO il FolderOverlay.
        // L'aggiornamento della mappa diventerà un'operazione atomica (aggiungi/rimuovi un solo oggetto).
        return listOf(folder)
    }

    private fun creaOverlayPunti(
        points: MutableList<IGeoPoint>,
        featureInfo: FeatureTableInfo
    ): List<org.osmdroid.views.overlay.Overlay> { // <-- MODIFICA CHIAVE
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
            textAlign = Paint.Align.CENTER
            textSize = 24f
            try {
                color = featureInfo.colore.toColorInt()
            } catch (_: IllegalArgumentException) {
                Log.w(
                    TAG,
                    "Colore non valido per il layer di punti '${featureInfo.name}'. Uso il colore di default."
                )
                color = Color.BLUE // Imposta un colore di fallback
            }
        }

        // usa  NO_OPTIMIZATION algorithm, per ridisegno ruotando mappa
        val opt = SimpleFastPointOverlayOptions.getDefaultStyle()
            .setAlgorithm(SimpleFastPointOverlayOptions.RenderingAlgorithm.NO_OPTIMIZATION)
            .setRadius(7F)
            .setSymbol(SimpleFastPointOverlayOptions.Shape.CIRCLE)
            .setIsClickable(true)
            .setCellSize(15)
            .setPointStyle(PointStyle)
            .setTextStyle(textStyle)
        val sfpo = SimpleFastPointOverlay(theme, opt)

        sfpo.setOnClickListener { points, point ->
            (points[point] as? LabelledGeoPoint)?.label?.let {
                mostraAlertDialogSemplice(it)
            }
        }
        return listOf(sfpo)
    }

    private fun creaOverlayLinee(
        lineStringToAdd: MutableList<LineStringFeature>,
        featureInfo: FeatureTableInfo
    ): List<org.osmdroid.views.overlay.Overlay> { // <-- MODIFICA CHIAVE
        val lineOverlayFolder = SafeFolderOverlay()
        lineOverlayFolder.name = featureInfo.name //

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
            osmdroidPolyline.id = "line_${featureInfo.name}_$index"
            osmdroidPolyline.relatedObject = lineFeature
            osmdroidPolyline.title = ngaLineString.title
            // Imposta lo snippet, servirà alla BasicInfoWindow
            osmdroidPolyline.snippet = lineFeature.description

            // IMPOSTA UN LISTENER "INTELLIGENTE" CHE CREA LA FINESTRA AL MOMENTO DEL CLICK
            osmdroidPolyline.setOnClickListener { clickedPolyline, map, eventPosition ->
                // 1. Crea la InfoWindow "just-in-time" usando la 'map' VALIDA fornita dal listener
                val iw = if (featureInfo.name == "Sentieri CAI") {
                    WebsiteInfoWindow(lineFeature, map)
                } else {
                    BasicInfoWindow(R.layout.bonuspack_bubble, map)
                }

                // 2. Assegna e mostra la InfoWindow appena creata
                clickedPolyline.infoWindow = iw
                clickedPolyline.infoWindowLocation = eventPosition
                clickedPolyline.showInfoWindow()

                // 3. Centra la mappa sul punto
                map.controller.animateTo(eventPosition)
                true // Evento gestito
            }
            // 2. Aggiungi la Polyline di osmdroid al FolderOverlay
            lineOverlayFolder.add(osmdroidPolyline)
        }
        return listOf(lineOverlayFolder) // Restituisci il FolderOverlay creato
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
        featureRow: FeatureRow, tableName: String,
        lineStringToAdd: MutableList<LineStringFeature>
    ) {
        val geometryData = featureRow.geometry
        val geometry = geometryData.geometry
        if (geometry is LineString) {
            val label = layerModel.creaLabel(featureRow, tableName)
            //val description = "layer:$tableName"
            val description = ""

            // Estrae l'URL dal campo 'website', se esiste
            val websiteColumnIndex = featureRow.getColumnIndex("website")
            val website = if (websiteColumnIndex != -1) {
                featureRow.getValue(websiteColumnIndex) as? String
            } else {
                null
            }

            lineStringToAdd.add(LineStringFeature(geometry, label, description, website))
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

                            GeometryType.MULTIPOINT -> processPointGeometry(
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

    private fun interrogaGeologia(punto: GeoPoint, geoPackage: GeoPackage, tableName: String) {
        val featureDao = geoPackage.getFeatureDao(tableName)
        val indexManager = FeatureIndexManager(requireContext(), geoPackage, featureDao)
        var results: mil.nga.geopackage.features.index.FeatureIndexResults? = null

        try {
            if (!indexManager.isIndexed) {
                Toast.makeText(context, "Indice spaziale non trovato.", Toast.LENGTH_SHORT).show()
                return
            }

            // 1. Crea una bounding box minuscola intorno al punto
            val tolerance = 0.00001 // Ridotta per maggiore precisione (circa 1 metro)
            val queryBox = mil.nga.geopackage.BoundingBox(
                punto.longitude - tolerance, punto.longitude + tolerance,
                punto.latitude - tolerance, punto.latitude + tolerance
            )

            // 2. Esegui la query sull'indice
            results = indexManager.query(queryBox)

            var foundRow: FeatureRow? = null

            // Trasformiamo il GeoPoint di osmdroid in un punto della libreria SF
            val clickPoint = mil.nga.sf.Point(punto.longitude, punto.latitude)

            // 3. Iteriamo su tutti i poligoni trovati per trovare quello che CONTIENE il punto
            for (featureRow in results) {
                val geometryData = featureRow.geometry
                if (geometryData != null && !geometryData.isEmpty) {
                    // Test di inclusione preciso (Point-in-Polygon)
                    val isInside = when (val geometry = geometryData.geometry) {
                        is mil.nga.sf.Polygon -> {
                            mil.nga.sf.util.GeometryUtils.pointInPolygon(clickPoint, geometry)
                        }

                        is MultiPolygon -> {
                            // Per i MultiPolygon, controlliamo se il punto è in almeno uno dei poligoni componenti
                            geometry.polygons.any { poly ->
                                mil.nga.sf.util.GeometryUtils.pointInPolygon(clickPoint, poly)
                            }
                        }

                        else -> false
                    }

                    if (isInside) {
                        // Trovato! Poiché potrebbero esserci sovrapposizioni, l'ultimo trovato
                        // nel ciclo (solitamente il più "giovane" o specifico) sarà quello salvato.
                        foundRow = featureRow
                    }
                }
            }


            if (foundRow != null) {
                val tipoUnit = foundRow.getValueString("TIPOUNIT_1") ?: "N/D"
                val unitaGer = foundRow.getValueString("UNITAGER_1") ?: "N/D"
                mostraDettagliGeologia(tipoUnit, unitaGer)
            } else {
                // Se non trovi nulla con precisione chirurgica, prova a mostrare il primo risultato della box
                results?.firstOrNull()?.let {
                    mostraDettagliGeologia(it.getValueString("TIPOUNIT_1") ?: "N/D", it.getValueString("UNITAGER_1") ?: "N/D")
                }
            }
        } catch (e: Exception) {
            Log.e("GEO", "Errore interrogazione", e)
        } finally {
            results?.close()
            indexManager.close() // Chiudi l'indexManager per rilasciare la connessione al DB metadata
        }
    }
    private fun mostraDettagliGeologia(tipo: String, unita: String) {
        val messaggio = "TIPO UNITÀ: $tipo\n\nUNITÀ GERARCHICA: $unita"

        // Se hai già mostraAlertDialogSemplice implementata nel fragment, usala:
        mostraAlertDialogSemplice(messaggio)
    }


    // --- Inizio Funzioni di Registrazione Audio ---
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
            // 1. Crea il nome del file e salvalo in una variabile locale non-null.
            val finalFileName = "AUD_${timeStamp}.3gp"
            // 2. Assegna il nome del file alla variabile di classe per usi futuri.
            this.audioFileName = finalFileName
            val storageDir: File? = requireContext().getExternalFilesDir("VoiceNotesWaypoints")
            if (storageDir != null && !storageDir.exists()) {
                if (!storageDir.mkdirs()) {
                    Log.e(TAG_AUDIO, "Impossibile creare la directory VoiceNotesWaypoints")
                    return null
                }
            }
            // 3. Usa la variabile locale 'finalFileName', che è garantita non-null.
            File(storageDir, finalFileName)
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
            //Log.d(TAG, "Disconnessione da BRouterService.")
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
            //Log.d(TAG, "Avvio del calcolo del percorso in background...")

            val params = Bundle().apply {
                putDoubleArray("lons", doubleArrayOf(startPoint.longitude, endPoint.longitude))
                putDoubleArray("lats", doubleArrayOf(startPoint.latitude, endPoint.latitude))
                putString("profile", "mtb")
                putString("trackFormat", "gpx")
                //putString("turnInstructionMode", "3") opzione per generare le istruzioni turn by turn
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
                        //Log.d(TAG, "Tracciato GPX ricevuto, inizio parsing...")

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
                                GeoPoint(
                                    wayPoint.latitude,
                                    wayPoint.longitude,
                                    wayPoint.elevation ?: 0.0
                                )
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
            //Log.d(TAG, "Disegno della traccia sulla mappa...")
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
                //Log.d(TAG, "Disconnessione da BRouterService.")
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
                    override fun onMarkerDrag(marker: Marker) { /* Non serve fare nulla qui */
                    }

                    override fun onMarkerDragEnd(marker: Marker) {
                        // Puoi aggiornare un'infowindow o fare altro qui se vuoi
                        //Log.d("MappaFragment", "Destinazione impostata a: ${marker.position}")
                    }

                    override fun onMarkerDragStart(marker: Marker) { /* Non serve fare nulla qui */
                    }
                })
            }
            mapView.overlays.add(destinationMarker)
        }
        // Posiziona il marker al centro della vista corrente e rendilo visibile
        destinationMarker?.position = mapView.mapCenter as GeoPoint
        destinationMarker?.isEnabled = true
        mapView.invalidate() // Ridisegna la mappa per mostrare il marker
        Toast.makeText(
            requireContext(),
            "Trascina il marker e conferma la destinazione",
            Toast.LENGTH_LONG
        ).show()
    }

    private fun showMenuBottomSheet() {
        val bottomSheetDialog = BottomSheetDialog(requireContext())
        val view = layoutInflater.inflate(R.layout.menu_bottom_sheet, null)
        bottomSheetDialog.setContentView(view)

        val navigationView = view.findViewById<NavigationView>(R.id.navigation_view)
        navigationView.itemIconTintList = null
        val menu = navigationView.menu
        val sosItem = menu.findItem(R.id.menu_sos)

        // 1. Colora il Testo di Rosso usando SpannableString
        val spanString = android.text.SpannableString(sosItem.title.toString())
        spanString.setSpan(
            android.text.style.ForegroundColorSpan(Color.RED),
            0,
            spanString.length,
            0
        )
        sosItem.title = spanString

        // 2. Colora l'Icona di Rosso tramite Tint
        sosItem.icon?.let { icon ->
            val wrappedDrawable = androidx.core.graphics.drawable.DrawableCompat.wrap(icon)
            androidx.core.graphics.drawable.DrawableCompat.setTint(wrappedDrawable, Color.RED)
            sosItem.icon = wrappedDrawable
        }

        navigationView.setNavigationItemSelectedListener { menuItem ->

            when (menuItem.itemId) {
                R.id.menu_lista -> {
                    val directions = MappaFragmentDirections.actionMappaFragmentToSentieriFragment()
                    findNavController().navigate(directions)
                    bottomSheetDialog.dismiss()
                }

                R.id.menu_gps -> {
                    if (viewModel.isRecording) stopGPS() else avviaLogicaRegistrazione()
                    bottomSheetDialog.dismiss()
                }

                R.id.menu_gpx -> {
                    val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                        type = "application/octet-stream"
                        //val mimeTypes = arrayOf("application/gpx+xml", "application/xml", "text/xml")
                        //putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes)
                        addCategory(Intent.CATEGORY_OPENABLE)
                    }
                    bottomSheetDialog.dismiss()
                    gpxFileSelectorLauncher.launch(intent)
                }

                R.id.menu_poi -> {
                    if (viewModel.isRecording && viewModel.isFixed) creaWayPoint()
                    else Toast.makeText(
                        requireContext(),
                        "Waypoint solo in registrazione traccia",
                        Toast.LENGTH_SHORT
                    ).show()
                    bottomSheetDialog.dismiss()
                }

                R.id.menu_mappa -> {
                    // Chiudiamo prima il BottomSheet per liberare l'interfaccia
                    bottomSheetDialog.dismiss()
                    selezionaMappa()
                }

                R.id.menu_sos -> {
                    handleSosClick()
                    bottomSheetDialog.dismiss()
                }
            }
            true
        }
        bottomSheetDialog.show()
    }

    private fun selezionaMappa() {
        val bottomSheetDialog = BottomSheetDialog(requireContext())
        val view = layoutInflater.inflate(R.layout.mappa_bottom_sheet, null)
        bottomSheetDialog.setContentView(view)
        val navigationView = view.findViewById<NavigationView>(R.id.mappa_navigation)
        navigationView.setNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.Offline -> {
                    menuItem.isChecked = !menuItem.isChecked
                    offline()
                    bottomSheetDialog.dismiss()
                }

                R.id.Online, R.id.Mapquest, R.id.MapBox -> {
                    menuItem.isChecked = !menuItem.isChecked
                    when (menuItem.itemId) {
                        R.id.Online -> online(requireContext(), mapView, viewModel, 1)
                        R.id.Mapquest -> online(requireContext(), mapView, viewModel, 2)
                        R.id.MapBox -> online(requireContext(), mapView, viewModel, 3)
                    }
                    bottomSheetDialog.dismiss()
                }
            }
            true
        }
        bottomSheetDialog.show()
    }


    private fun isCellularCoverageAvailable(): Boolean {
        val telephonyManager = requireContext().getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

        // Controlla se il modem è pronto o se c'è un operatore registrato
        val hasOperator = telephonyManager.networkOperatorName.isNotEmpty()
        val isPhoneReady = telephonyManager.phoneType != TelephonyManager.PHONE_TYPE_NONE

        // Verifica se c'è connettività dati cellulare
        val cm = requireContext().getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNet = cm.activeNetwork
        val caps = cm.getNetworkCapabilities(activeNet)
        val hasCellularData = caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ?: false

        return hasOperator || isPhoneReady || hasCellularData
    }

    private fun isSatelliteModeAvailable(): Boolean {
        // TRANSPORT_SATELLITE (9) è disponibile da API 35 (Android 15)
        val TRANSPORT_SATELLITE = 9
        val cm = requireContext().getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNet = cm.activeNetwork
        val caps = cm.getNetworkCapabilities(activeNet) ?: return false

        return if (Build.VERSION.SDK_INT >= 35) {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_SATELLITE)
        } else {
            caps.hasTransport(TRANSPORT_SATELLITE)
        }
    }

    private fun handleSosClick() {
        val currentLoc = viewModel.locationData.value?.geoPoint
        if (currentLoc == null || (currentLoc.latitude == 0.0 && currentLoc.longitude == 0.0)) {
            Toast.makeText(requireContext(), "Posizione GPS non ancora disponibile", Toast.LENGTH_LONG).show()
            return
        }

        // Formattazione coordinate
        val latStr = String.format("%.6f", currentLoc.latitude)
        val lonStr = String.format("%.6f", currentLoc.longitude)
        val coordsString = "LAT: $latStr\nLON: $lonStr"

        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_sos_info, null)
        val tvCoords = dialogView.findViewById<TextView>(R.id.sos_coordinates)
        val btnCopy = dialogView.findViewById<Button>(R.id.btn_copy_coords)
        val btnCall = dialogView.findViewById<Button>(R.id.btn_call_emergency)

        tvCoords.text = coordsString

        // Verifica stato rete
        val hasCellular = isCellularCoverageAvailable()
        val hasSatellite = isSatelliteModeAvailable()

        if (!hasCellular && hasSatellite) {
            btnCall.text = "MOSTRA DATI PER SOS SATELLITARE"
            btnCall.setBackgroundColor(Color.BLUE)
        } else if (!hasCellular) {
            btnCall.text = "EMERGENZA (NO SEGNALE)"
        }

        val alertDialog = AlertDialog.Builder(requireContext(), R.style.AlertDialogCustom)
            .setView(dialogView)
            .create()

        btnCopy.setOnClickListener {
            val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Coordinate SOS", "SOS Sentieri: $latStr, $lonStr")
            clipboard.setPrimaryClip(clip)
            Toast.makeText(requireContext(), "Coordinate copiate! Incolla nell'SMS di emergenza", Toast.LENGTH_LONG).show()
        }

        btnCall.setOnClickListener {
            val dialIntent = Intent(Intent.ACTION_DIAL).apply {
                data = "tel:112".toUri()
            }
            startActivity(dialIntent)
            alertDialog.dismiss()
        }

        alertDialog.show()
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
        Log.i(TAG, "onTrimMemory called with level: $level")
        // Pulisci la cache della mappa se la pressione sulla memoria è moderata o superiore.
        if (level >= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) {
            _binding?.Mapview?.tileProvider?.clearTileCache()
        }
        // Se la situazione è critica (livello RUNNING_CRITICAL o superiore), libera anche altre risorse.
        // TRIM_MEMORY_RUNNING_CRITICAL è il sostituto moderno di TRIM_MEMORY_COMPLETE.
        if (level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND) {
            Log.w(TAG, "Critical memory pressure (level: $level). Closing GeoPackage.")
            layerModel.closeGeoPackage()
        }
    }

    // Gestione visibilità in base alla traccia da seguire
// Puoi aggiungere un observer su tracciaDaSeguire se lo rendi LiveData,
// oppure aggiornarlo quando carichi/scarichi una traccia.
// Se tracciaDaSeguire non è LiveData, puoi aggiungere questo controllo dove necessario:
    fun updateRemainingVisibility() {
        val isFollowing = viewModel.tracciaDaSeguire.isNotEmpty()
        binding.cruscotto.groupRemaining.visibility = if (isFollowing) View.VISIBLE else View.GONE
    }

    override fun onLowMemory() {
        super.onLowMemory()
        Log.w(TAG, "onLowMemory called. System is critically low on memory.")
        // Invece di chiamare onTrimMemory, esegui direttamente le azioni più drastiche
        // perché onLowMemory() è il segnale più severo.
        if (_binding != null) {
            // Pulisci la cache delle mattonelle della mappa.
            mapView.tileProvider.clearTileCache()
        }
        // Chiudi risorse pesanti come il GeoPackage.
        layerModel.closeGeoPackage()
    }

    /** Controlla se i permessi per la posizione in primo piano sono stati concessi. */
    private fun isFineLocationPermissionGranted(): Boolean {
        val fineLocationGranted = ContextCompat.checkSelfPermission(
            requireContext(), Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        // Per Android 14+ (API 34+), controlla anche il permesso per il servizio in foreground
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val foregroundServiceLocationGranted = ContextCompat.checkSelfPermission(
                requireContext(), Manifest.permission.FOREGROUND_SERVICE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
            fineLocationGranted && foregroundServiceLocationGranted // Devono essere concessi entrambi
        } else {
            fineLocationGranted // Per le versioni precedenti, basta il permesso standard
        }
    }

    /** Controlla se il permesso per la posizione in background è stato concesso. */
    private fun isBackgroundLocationPermissionGranted(): Boolean {
        return ContextCompat.checkSelfPermission(
            requireContext(), Manifest.permission.ACCESS_BACKGROUND_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    /** Mostra un dialogo che spiega perché l'app necessita della posizione in background. */
    private fun showBackgroundLocationDisclosure() {
        AlertDialog.Builder(requireContext(), R.style.AlertDialogCustom)
            .setTitle("Accesso alla Posizione in Background")
            .setMessage(
                "\"Sentieri\" raccoglie dati sulla posizione, per cui ha bisogno di accedere alla tua posizione in background per garantire il corretto funzionamento della registrazione delle tracce. I dati vengono usati solo da questa applicazione\n" +
                        "Seleziona: <consenti sempre> nella finestra successiva per registrare il tuo percorso anche quando lo schermo è spento o stai usando un'altra applicazione.\n"
            )
            .setPositiveButton("Capito, continua") { _, _ ->
                // L'utente ha capito. Ora richiedi il permesso di sistema usando il launcher.
                requestBackgroundLocationLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            }
            .setNegativeButton("Annulla") { dialog, _ ->
                dialog.dismiss()
                Toast.makeText(
                    requireContext(),
                    "La registrazione in background non sarà disponibile.",
                    Toast.LENGTH_LONG
                ).show()
            }
            .setCancelable(false)
            .show()
    }


    inner class RemovableMarker(mapView: MapView) : Marker(mapView) {
        var onMarkerLongClick: ((RemovableMarker) -> Boolean)? = null

        // Override per vedere quando MapView chiama questo specifico marker per un long press
        override fun onLongPress(event: MotionEvent?, mapView: MapView?): Boolean {
            if (mapView == null || event == null) return false
            val wasHit =
                hitTest(event, mapView) // Controlla se l'evento è DENTRO questo marker
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

    inner class WebsiteInfoWindow(
        private val lineFeature: LineStringFeature,
        mapView: MapView
    ) : InfoWindow(R.layout.custom_info_window, mapView) {

        override fun onOpen(item: Any?) {
            // Chiude altre finestre eventualmente aperte
            closeAllInfoWindowsOn(mapView)

            val titleView: TextView = mView.findViewById(R.id.bubble_title)
            val descriptionView: TextView = mView.findViewById(R.id.bubble_description)
            val websiteButton: Button = mView.findViewById(R.id.bubble_website_button)
            val chiduButton: Button = mView.findViewById(R.id.dialog_close_button)
            // Popola i dati
            titleView.text = lineFeature.title
            descriptionView.text = lineFeature.description

            // Imposta la visibilità e il listener per il pulsante del sito web
            if (!lineFeature.website.isNullOrBlank()) {
                websiteButton.visibility = View.VISIBLE
                websiteButton.setOnClickListener {
                    try {
                        val intent = Intent(Intent.ACTION_VIEW, lineFeature.website.toUri())
                        // Usa il contesto del fragment per avviare l'activity
                        context?.startActivity(intent)
                    } catch (e: Exception) {
                        Toast.makeText(
                            context,
                            "Impossibile aprire il link",
                            Toast.LENGTH_SHORT
                        )
                            .show()
                        Log.e(TAG, "Errore nell'aprire il link dall'InfoWindow", e)
                    }
                }
            } else {
                websiteButton.visibility = View.GONE
            }
            // Imposta un OnClickListener sulla vista principale dell'infowindow
            // per chiuderla quando viene toccata.
            chiduButton.setOnClickListener {
                close() // Chiude questa InfoWindow
            }
        }

        override fun onClose() {
            // Rimuovi i listener per prevenire memory leak, se necessario.
            // In questo caso, non è strettamente richiesto perché i listener
            // vengono ricreati ogni volta che la finestra si apre.
            mView.setOnClickListener(null)
        }
    }
}