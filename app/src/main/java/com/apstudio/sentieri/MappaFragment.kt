package com.apstudio.sentieri

import android.Manifest
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Paint
import android.icu.text.SimpleDateFormat
import android.location.Location
import android.location.LocationManager
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
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
import androidx.core.content.ContextCompat.getColor
import androidx.core.content.edit
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.toColorInt
import androidx.core.net.toUri
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewModelScope
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.navigation.fragment.findNavController
import androidx.preference.PreferenceManager
import com.apstudio.sentieri.MapUtils.convertMillisToISO8601JavaTime
import com.apstudio.sentieri.MapUtils.dataOraIso8601
import com.apstudio.sentieri.MapUtils.disegnaLine
import com.apstudio.sentieri.MapUtils.formatSeconds
import com.apstudio.sentieri.MapUtils.getFileNameFromUri
import com.apstudio.sentieri.databinding.FragmentMappaBinding
import com.apstudio.sentieri.db.FotoPoi
import com.apstudio.sentieri.db.FotoPoiDao
import com.apstudio.sentieri.db.PoiDB
import com.apstudio.sentieri.db.PoiDao
import com.apstudio.sentieri.db.Sentieri
import com.apstudio.sentieri.db.SentieriDB
import com.apstudio.sentieri.db.TrackDao
import com.apstudio.sentieri.layer.FeatureTableInfo
import com.apstudio.sentieri.layer.LAYER_DIALOG_REQUEST_KEY
import com.apstudio.sentieri.layer.LayerViewModel
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import mil.nga.geopackage.GeoPackageFactory
import mil.nga.geopackage.features.user.FeatureCursor
import mil.nga.geopackage.features.user.FeatureDao
import mil.nga.geopackage.features.user.FeatureRow
import mil.nga.geopackage.geom.GeoPackageGeometryData
import mil.nga.sf.GeometryType
import mil.nga.sf.MultiPolygon
import net.federicomatera.agpxp.GpxParser
import net.federicomatera.agpxp.GpxWriter
import net.federicomatera.agpxp.models.Gpx
import net.federicomatera.agpxp.models.GpxMetadata
import net.federicomatera.agpxp.models.Link
import net.federicomatera.agpxp.models.Track
import net.federicomatera.agpxp.models.WayPoint
import org.mapsforge.map.rendertheme.ExternalRenderTheme
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
import java.sql.Timestamp
import java.text.DecimalFormat
import java.text.NumberFormat
import java.util.Date
import java.util.Locale

private const val TAG = "MappaFragment"

class MappaFragment : Fragment(), MenuProvider, SharedPreferences.OnSharedPreferenceChangeListener,
    View.OnKeyListener {
    private lateinit var viewModel: SentieriViewModel
    private val METERS_IN_A_KILOMETER = 1000.0 // Changed from Int to Double for precision
    private val SECONDS_IN_AN_HOUR = 3600.0 // Changed from Int to Double for precision

    // viewModel del LocationService con scope Application
    private val gpsViewModel: GpsViewModel by lazy {
        ViewModelProvider(requireActivity().application as ViewModelStoreOwner)[GpsViewModel::class.java]
    }
    val layerModel: LayerViewModel by lazy {
        ViewModelProvider(requireActivity().application as ViewModelStoreOwner)[LayerViewModel::class.java]
    }

    private var _binding: FragmentMappaBinding? = null
    private val binding get() = _binding!!

    private lateinit var database: SentieriDB
    private lateinit var bottomSheetBehavior: BottomSheetBehavior<ConstraintLayout>

    private val SELECT_GPX_FILE = 10
    private val SELECT_MAP_FILE = 20
    private lateinit var gpsMarker: Marker

    private var uri: Uri? = null

    private lateinit var osservaMappa: Observer<Polyline>
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

    // Variabili per la registrazione audio
    private var audioFileName: String? = null
    private var mediaRecorder: MediaRecorder? = null
    private var audioOutputFile: File? = null
    private var currentAudioFilePath: String? =
        null // Per salvare il percorso dell'ultima registrazione
    private var isAudioRecording = false
    private val recordingDurationMs: Long = 5000 // 5 secondi
    private val audioHandler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel =
            ViewModelProvider(requireActivity().applicationContext as AppSentieri)[SentieriViewModel::class.java]
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
        // predispone broadcast per servizio aggiornamento posizione
        val filter = IntentFilter(SEND_LOCATION_ACTION)
        LocalBroadcastManager.getInstance(requireContext()).registerReceiver(mReceiver, filter)

        // Imposta il listener per il risultato da GpkgLayer
        parentFragmentManager.setFragmentResultListener(
            LAYER_DIALOG_REQUEST_KEY,
            this
        ) { requestKey, bundle ->
            // Questo blocco viene eseguito quando GpkgLayer invia un risultato
            // con la LAYER_DIALOG_REQUEST_KEY specificata.
            if (requestKey == LAYER_DIALOG_REQUEST_KEY) {
                layerModel.featureList.forEach { featureInfo ->
                    //if (featureInfo.isVisible)
                    onReturnFromLayerDialog(featureInfo)
                }
                // Puoi recuperare dati dal bundle se GpkgLayer li ha inviati
                // val userAction = bundle.getString("userAction")
            }
        }
    }

    fun onReturnFromLayerDialog(featureInfo: FeatureTableInfo) {
        // Metti qui la logica che vuoi eseguire al ritorno da GpkgLayer
        // Ad esempio, aggiornare la mappa, ricaricare dei dati, etc.
        Log.d("MappaFragment", "Funzione onReturnFromLayerDialog chiamata!")
        if (featureInfo.isVisible) {
            if (!featureInfo.readData || featureInfo.listOverlay.isNullOrEmpty()) {
                layerModel.geoPackageInstance?.let { // Usa l'istanza dal ViewModel
                    // Passa il geoPackage corretto e tableName
                    puntiSuMappa(
                        featureInfo.name,
                        featureInfo
                    ) // Assicurati che puntiSuMappa usi layerModel.geoPackageInstance
                    featureInfo.readData = true
                } ?: run {
                    Log.e(
                        "SwitchDebug",
                        "geoPackageInstance in ViewModel is null. Cannot load points."
                    )
                }
            } else {
                featureInfo.listOverlay?.forEach { tabOverlay ->
                    if (!mapView.overlays.contains(tabOverlay)) {
                        mapView.overlays.add(tabOverlay)
                    }
                }
            }
        } else {
            featureInfo.listOverlay?.forEach { tabOverlay ->
                mapView.overlays.remove(tabOverlay)
                featureInfo.readData = false
            }
        }
        // Esempio: viewModel.refreshMapLayers()
        mapView.invalidate()
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
        // verifica se sono passati argomenti
        arguments?.getString("gpx_file_uri")?.let { uriString ->
            val gpxUri = uriString.toUri()
            caricaGPX(gpxUri)
            arguments?.remove("gpx_file_uri")
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

        val menuHost: MenuHost = requireActivity()
        menuHost.addMenuProvider(this, viewLifecycleOwner, Lifecycle.State.RESUMED)
        //Log.d("Mappa", "onViewCreated ")
        //aggiunge i folder overlay, listaTracce che conterrà tutte le tracce aggiunte  overlays alla mapview
        // e rectraccia che conterrà la traccia corrente
        if (mapView.overlays.isEmpty()) {
            //val overlayManager = mapView.overlayManager
            mapView.overlayManager.add(viewModel.listaTracce)
            mapView.overlayManager.add(viewModel.recTraccia)
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
        mapController.setZoom(viewModel.ultZoom)

        // Bottone per bloccare ancoraggio mappa al gps
        binding.fabBlocMappa.setOnClickListener {
            bloccaMappa()
        }

        // Bottone per attivare la fotocamera
        binding.camera.setOnClickListener {
            //Log.d("camera", "viemodel ${viewModel.traccia.points.size}")
            val directions =
                MappaFragmentDirections.actionMappaFragmentToCameraFragment()
            this@MappaFragment.findNavController().navigate(directions)
        }

        binding.cruscotto.btnAllarme.setOnClickListener {
            // Dis/Abilita allarme fuori tracce
            viewModel.alertFuoriTraccia = !viewModel.alertFuoriTraccia
            btnAllarme()
            //binding.cruscotto.btnAllarme.postInvalidate()
        }

        // avvia gli observer per aggiornamento dati cruscotto
        val numberFormat = NumberFormat.getNumberInstance(Locale.getDefault())
        viewModel.distanzaMetri.observe(viewLifecycleOwner) { distanzaMetri ->
            binding.cruscotto.tvDist.text = MapUtils.formattastring(distanzaMetri)
        }
        gpsViewModel.velocita.observe(viewLifecycleOwner) { velocita ->
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
        // determina se l'altitudine deve essere barometrica o dal GPS
        // setta il flag is_Calibrato nel gpsViewModel, utilizzato da LocationService
        viewModel.isCalibrato.observe(viewLifecycleOwner) {
            if (it) {
                binding.cruscotto.tvCalcQuota.text = "BARO"
                gpsViewModel.usaBaro = true
            } else {
                binding.cruscotto.tvCalcQuota.text = "GPS"
                gpsViewModel.usaBaro = false
            }
        }
        gpsViewModel.gpsStatus.observe(viewLifecycleOwner) { status ->
            updateGpsIcon(status)
            //Log.d("gps", "status $status")
        }

    }

    // aggiunge il click listener alla polyline per aprire l'info window
    private fun setPolylineClickListener(polyline: Polyline) {
        polyline.setOnClickListener { mpolyline, mapView, eventPos ->
            // Il layout è stato copiato nelle risorse potrebbe differire dall'originale
            mpolyline.infoWindow = BasicInfoWindow(R.layout.bonuspack_bubble, mapView)
            mpolyline.infoWindowLocation = eventPos
            mpolyline.showInfoWindow()
            false // Ritorna true per indicare che l'evento è stato gestito
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
        Log.d("Mappa", "MappaFragment onResume ")
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
                    poiMarker.position.latitude = it.Latit.toDouble()
                    poiMarker.position.longitude = it.Longit.toDouble()
                    poiMarker.position.altitude = it.Ele.toDouble()
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
            gpsMarker.position = (viewModel.newPunto)
            gpsMarker.setVisible(true)
            btnAllarme()
            bottomSheetBehavior.isHideable = false
            bottomSheetBehavior.peekHeight = 120
            bottomSheetBehavior.state = viewModel.bottomState

            val toast =
                Toast.makeText(requireActivity(), "Registrazione in corso", Toast.LENGTH_SHORT)
            toast.view?.setBackgroundColor(getColor(requireActivity(), R.color.purple_500))
            toast.show()
        }

        // ridisegna eventuali layer aggiunti da GpkgLayer
        layerModel.featureList.forEach { featureInfo ->
            if (featureInfo.isVisible)
                onReturnFromLayerDialog(featureInfo)
        }
    }

    override fun onPrepareMenu(menu: Menu) {
        super.onPrepareMenu(menu)
        // soluzione per aggiornare icona gps dopo cambio fragment in quanto observer non aggiorna
        if (viewModel.isRecording) {
            gpsViewModel.updateGpsStatus(gpsViewModel.gpsStatus.value!!)
        }
    }

    override fun onPause() {
        super.onPause()
        // memorizza valori per ripristinare la mappa
        //if (viewModel.isRecording)  gpsMarker.setVisible(false)
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
            Log.e("AppTestPlayStore", "Errore inizializzazione OsmDroid", e)
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
        var theme: XmlRenderTheme? = null
        if (f.name.contains(".map")) {
            val mediaDir = requireContext().externalMediaDirs
            val documentsDir = mediaDir[0]
            val folderTema = File("$documentsDir/Mappe/4UMaps/4UMaps.xml")
            if (folderTema.exists()) {
                theme = ExternalRenderTheme("$documentsDir/Mappe/4UMaps/4UMaps.xml")
            }
            val fromFiles = MapsForgeTileSource.createFromFiles(maps, theme, null)
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

    private fun bloccaMappa() {
        if (viewModel.isRecording) {
            val iconResId = if (viewModel.bloccaMappa) PIN_BLACK else PIN_RED
            val icon = ContextCompat.getDrawable(requireContext(), iconResId)

            binding.fabBlocMappa.apply {
                setImageDrawable(icon)
            }
            viewModel.bloccaMappa = !viewModel.bloccaMappa
        }
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

    private fun stopGPS() {
// FINE REGISTRAZIONE TRACCIA
        // se non ha fixato non chiede di salvare
        if (!viewModel.isFixed) {
            azzeraCruscotto()
            fermaRecording(false)
            stopObserver() // Arresta gli observer
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
        gpsViewModel.updateGpsStatus("stopped")
// rimuove impostazione schermo sempre acceso
        requireActivity().window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
// aggiunge marker fine percorso
        if (fine) {
            MapUtils.markInizioFine(
                requireContext(),
                viewModel.newPunto,
                mapView,
                viewModel.recTraccia,
                1
            )
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
        gpsViewModel.gpsStatus.removeObservers(viewLifecycleOwner)
    }

    private fun attivaGps() {
        var locationManager =
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
        viewModel.isFixed = false
// Cambia stato GPS ON
        gpsMarker.setVisible(true)
// imposta schermo sempre acceso
        requireActivity().window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
// inizio registrazione posizione
        viewModel.isRecording = true
        viewModel.oraInizio = System.currentTimeMillis()
        //viewModel.oraInizio = SystemClock.elapsedRealtime()
// crea observer per aggiornamento punti traccia
        osservaMappa = Observer {
            viewModel.traccia.observe(viewLifecycleOwner) { traccia ->
                //Log.d("observer", "rectraccia ${viewModel.recTraccia.items.size}")
                traccia.title = "Registrazione"
                viewModel.recTraccia.add(traccia)
            }
        }
        viewModel.traccia.observe(viewLifecycleOwner, osservaMappa)
        viewModel.startUpdates()
// avvia il servizio per tracciare locazione in background
        requireActivity().startService(Intent(context, LocationService::class.java))
        bottomSheetBehavior.isHideable = false
        bottomSheetBehavior.peekHeight = 120
        bottomSheetBehavior.halfExpandedRatio = 0.5f
        bottomSheetBehavior.state = BottomSheetBehavior.STATE_HALF_EXPANDED
        gpsViewModel.updateGpsStatus("started")
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
            //Log.d(
            //    "Punto",
            //    "${viewModel.trackDistanza}   ${viewModel.trackAscesa}  ${viewModel.trackDiscesa}"
            //)
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

    private fun updateGpsIcon(status: String) {
        val iconRes = when (status) {
            "started" -> R.drawable.gps_started
            "fixed" -> R.drawable.gps_on
            "stopped" -> R.drawable.gps_off
            else -> R.drawable.gps_off
        }
        menu?.findItem(R.id.gps)?.setIcon(iconRes)
        //Log.d("GpsView", "gps status $status")
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
            descrizione = "prova",
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
                SentieriDB.getInstance(requireActivity().application).trackDao

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
                    SentieriDB.getInstance(requireActivity().application).poiDao
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
                    SentieriDB.getInstance(requireActivity().application).fotoPoiDao
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
                //put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Sentieri")
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


    // riceve aggiornamento posizione da servizio in Broadcast
    private
    val mReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val loc: Location
            if (Build.VERSION.SDK_INT >= 34) {
                loc = intent.getParcelableExtra("posizione", Location::class.java)!!
            } else {
                @Suppress("DEPRECATION")
                loc = intent.getParcelableExtra("posizione")!!
            }
            val altitudine: Double = intent.getDoubleExtra("altitudine", 0.0)
            val milliBar = intent.getFloatExtra("milliBar", 0.0F)
            //Log.d("Receiver", "alti $altitudine mb $milliBar")
            // aggiorna posizione ed inserisce nuovo punto
            // riceve il valore in millibar letti da barometro e lo passa al viewModel
            // aggiorna dati nel viewModel
            //SimpleFileLogger.log("BroadcastReceiver", "altitudine $altitudine  millibar $milliBar")
            viewModel.aggiornaDati(loc, altitudine, milliBar)
            //in debug visualizza altitudine su mappa
            //binding.cruscotto.tvCalcQuota.text = altitudine.toString()
            //se non è visualizzata la mappa non aggiorna dati cruscotto
            if (!isFragmentVisibleAndActive())
                return
            // al primo punto aggiunge il marker d'inizio
            if (viewModel.traccia.value?.actualPoints?.size == 1)
                MapUtils.markInizioFine(
                    requireContext(),
                    viewModel.newPunto,
                    mapView,
                    viewModel.recTraccia,
                    0
                )
            // sposta il marker su nuova posizione con animazione
            gpsMarker.position = (viewModel.newPunto)
            if (viewModel.bloccaMappa) {
                mapView.controller?.animateTo(viewModel.newPunto)
            }
            // Orienta display sorgente in OpenMap demo di Osmdroid: Location - SampleHeadingCompassUp
            //Log.d("loc", "${loc.bearing} - ${loc.speed}")
            val gpsbearing = loc.bearing

            //use gps bearing instead of the compass
            var t: Float = 360 - gpsbearing
            if (t < 0) {
                t += 360f
            }
            if (t > 360) {
                t -= 360f
            }
//help smooth everything out
            t = t.toInt().toFloat()
            t /= 5
            t = t.toInt().toFloat()
            t *= 5
// DA riabilitare rotazione solo in movimento?
//if (gpsspeed >= 0.01) {
            mapView.mapOrientation = t
//otherwise let the compass take over
//}

// Controllo del fuori traccia basato sulla distanza dai punti della traccia da seguire
            if (viewModel.alertFuoriTraccia && viewModel.tracciaDaSeguire != "") {
//  verifica che non sia già visualizzato l'alert fuori traccia altrimenti lo aprirebbe ogni seoondo
                if (!isAlertDialogShowing()) {
                    //Log.d("Mappa", "segui traccia ${viewModel.listaTracce.items.size}")
                    var indice = 0
                    viewModel.listaTracce.items.forEachIndexed { index, it ->
                        if (it is Polyline && it.title == viewModel.tracciaDaSeguire) {
                            indice = index
                        }
                    }
                    val traccia = viewModel.listaTracce.items[indice] as Polyline
                    // restituisce se il punto è vicino con una tolleranza di 30 pixel alla posizione corrente della mappa
                    if (!traccia.isCloseTo(viewModel.newPunto, 30.0, mapView)) {
                        val allarme = EditText(requireActivity())
                        val builder =
                            AlertDialog.Builder(
                                requireContext(),
                                R.style.AlertDialogCustom
                            )
                        with(builder)
                        {
                            setTitle("Fuori traccia")
                            val layout = LinearLayout(context)
                            layout.orientation = LinearLayout.VERTICAL
                            allarme.setText("ATTENZIONE SEI FUORI TRACCIA")
                            layout.addView(allarme)
                            // Set the LinearLayout as the view for the dialog
                            builder.setView(layout)
                            /*setPositiveButton(
                                "Disabilita allarme"
                            ) { _, _ ->
                                //viewModel.alertFuoriTraccia = false
                                // simula pressione pulsante allarme per cambiare stato allarme
                                viewModel.alertDialogMostrato = false
                                btnAllarme.text = "Allarme off"
                                btnAllarme.backgroundTintList = ColorStateList.valueOf(Color.GREEN)
                                btnAllarme.postInvalidate()
                            }*/
                            setNegativeButton(android.R.string.cancel) { _, _ ->
                            }
                            alertDialog = create()
                            alertDialog?.show()
                        }
                    }
                }
            }
        }
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

// ... altre parti di MappaFragment ...

    private fun salvaWayPoint(nome: String, descr: String /*, audioPath: String? */) {
        // NON aggiungere il nuovo waypoint a viewModel.wayPoint
        // viewModel.wayPoint.add(...) // ASSICURATI CHE QUESTA RIGA SIA RIMOSSA O COMMENTATA SE ESISTEVA

        // Aggiungi il nuovo waypoint SOLO a viewModel.poiDBList come PoiDB
        viewModel.poiDBList.add(
            PoiDB(
                Id = 0, // Gestisci l'ID come appropriato per i nuovi record
                Trackid = 0, // Gestisci il TrackId come appropriato
                Latit = viewModel.newPunto.latitude.toFloat(),
                Longit = viewModel.newPunto.longitude.toFloat(),
                Ele = viewModel.newPunto.altitude.toFloat(),
                NomePOI = nome,
                DescrPOI = descr,
                UriPath = currentAudioFilePath ?: "",
                Time = Date().toString() // Considera di usare un formato di data/ora più standard o un Long
            )
        )

        // La logica per visualizzare il marker sulla mappa può rimanere,
        // creando un'istanza temporanea di WayPoint se necessario per il marker,
        // ma NON aggiungerla a viewModel.wayPoint.
        val markerDisplayWayPoint = net.federicomatera.agpxp.models.WayPoint(
            latitude = viewModel.newPunto.latitude,
            longitude = viewModel.newPunto.longitude,
            elevation = viewModel.newPunto.altitude,
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
        // viewModel.listaTracce.add(waymarker) // Se questa è una lista separata per i marker sulla mappa
    }

    override fun onDestroy() {
//ondestroy viene richiamato al termine dell'app
        if (!viewModel.isRecording) {
            LocalBroadcastManager.getInstance(requireContext()).unregisterReceiver(mReceiver)
        }
        super.onDestroy()
        preferenze.unregisterOnSharedPreferenceChangeListener(this)
        database.close()
    }

    companion object {
        const val SEND_LOCATION_ACTION = "com.apstudio.sentieri.posizione"
        private const val TAG_AUDIO = "AudioRecording" // Tag per log audio
    }


    override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
        menuInflater.inflate(R.menu.main_menu, menu)
// viene richiamata alla creazione del menu, quindi  anche quando si cambia il fragment
        this.menu = menu
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
                addGeopackageTiles()
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
                    Log.d(TAG_AUDIO, "File audio temporaneo cancellato: ${it.absolutePath}")
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
        val mapController = mapView.controller
        when (keyCode) {
            KEYCODE_VOLUME_UP -> {
                // Solo se l'azione è KEY_DOWN per evitare doppie chiamate
                if (event?.action == KeyEvent.ACTION_DOWN) {
                    mapController?.zoomIn()
                }
                return true // Evento volume gestito
            }

            KEYCODE_VOLUME_DOWN -> {
                // Solo se l'azione è KEY_DOWN
                if (event?.action == KeyEvent.ACTION_DOWN) {
                    mapController?.zoomOut()
                }
                return true // Evento volume gestito
            }
        }
        return false // Non abbiamo gestito questo evento di tasto, lascialo propagare
    }

    private fun btnAllarme() {
        if (!viewModel.alertFuoriTraccia) {
            binding.cruscotto.btnAllarme.text = "Allarme on"
            binding.cruscotto.btnAllarme.backgroundTintList = ColorStateList.valueOf(Color.RED)
        } else {
            binding.cruscotto.btnAllarme.text = "Allarme off"
            binding.cruscotto.btnAllarme.backgroundTintList =
                ColorStateList.valueOf(Color.GREEN)
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

    /*private fun geoPackage() {
        val mapFiles: MutableSet<File> = HashSet()
        mapFiles.add(File(Environment.getExternalStorageDirectory().absolutePath + "/Sentieri/Mappe/prova.gpkg"))
        //var maps: Array<File?> = arrayOfNulls(mapFiles.size)
        var maps: Array<File?> = arrayOfNulls(1)

        if (Build.VERSION.SDK_INT >= 34) {
            maps = mapFiles.toArray(maps)
        }
        val geoPackageProvider = GeoPackageProvider(maps, this)
        val sources = geoPackageProvider.geoPackageMapTileModuleProvider().tileSources

        val tileSource: XYTileSource = geoPackageProvider.getTileSource(
            sources[0].database,
            sources[0].tableDao
        )
        val bbox: BoundingBox = sources[0].bounds
        mapView.setTileProvider(geoPackageProvider)
        mapView.setTileSource(tileSource)


        //-----------------------------------------------------------------------------------------------------------------------
        // altra soluzione
        //val currentSource: XYTileSource? = null
        val geoMappa =
            File(Environment.getExternalStorageDirectory().absolutePath + "/Sentieri/Mappe")
        // val f = activity?.getExternalFilesDir(null)

        if (geoMappa.exists()) {
            val list = geoMappa.listFiles()
            if (list != null) {
                for (aList in list) {
                    if (aList.isDirectory) {
                        continue
                    }
                    aList.name.lowercase(Locale.getDefault())
                    if (aList.name.contains(".gpkg")) {
                        val maps: Array<File?> = arrayOfNulls(1)
                        maps[0] = aList
                        val geoPackageProvider = GeoPackageProvider(maps, requireContext())
                        val sources =
                            geoPackageProvider.geoPackageMapTileModuleProvider().tileSources
                        val tileSource: XYTileSource = geoPackageProvider.getTileSource(
                            sources[0].database,
                            sources[0].tableDao
                        )
                        val bbox: BoundingBox = sources[0].bounds
                        mapView.setTileProvider(geoPackageProvider)
                        mapView.setTileSource(tileSource)
                        val mapController: IMapController = MapController(mapView)
                        mapController.setCenter(viewModel.ultPosizione)
                        mapView.invalidate()

                        /*mapView.setTileProvider(geoPackageProvider)
                        //get the list of sources
                       // val tileSources = geoPackageProvider.geoPackageMapTileModuleProvider().tileSources
                        var sourceSet = false
                        val tileSources = geoPackageProvider.geoPackageMapTileModuleProvider().tileSources
                        if (!tileSources.isEmpty()) {
                            mapView.setTileSource(tileSources[0])
                            mapView.zoomToBoundingBox(tileSources[0].bounds, true)
                            mapView.getController().setZoom(tileSources[0].maximumZoomLevel)
                            sourceSet = true

                        }*/

                    }
                }
            }
        }
    }*/

    private fun addGeopackageTiles() {
        try {
            val mediaDir = requireContext().externalMediaDirs
            val documentsDir = mediaDir[0]
            val geoPackageFile = File("$documentsDir/Mappe/parchi.gpkg")
            val manager = GeoPackageFactory.getManager(requireContext())

            /*try {
                val imported = manager.importGeoPackage(geoPackageFile)
                //Log.d("packgage", "is imported ? $imported")
            } catch (ex: Exception) {
                //Log.d("packgage", "import exception " + ex.message)
            }*/
            val databases = manager.databases()
            //val geoPackage = manager.open(databases[0])
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
            //val featureTable: String = features[1]
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

            /*if (features != null) {
                // Layer di Tiles
                for (tableName in features) {
                    val tileDao = geoPackage.getTileDao(tableName)

                    val boundingBox = tileDao.boundingBox

                    Log.d("packgage",tileDao.projection.crs.name + " , " + tileDao.projection.code)
                    //Log.d("packgage","isGoogleTiles " + tileDao.isGoogleTiles)

                    var projection = ProjectionFactory.getProjection(
                        tileDao.projection.authority,
                        tileDao.projection.code
                    )
                    //var bbox = transform(boundingBox, projection)

                    val bounds = BoundingBox(
                        boundingBox.maxLatitude,
                        boundingBox.maxLongitude,
                        boundingBox.minLatitude,
                        boundingBox.minLongitude
                    )
                    val geopackageRasterTileSource = GeopackageRasterTileSource(
                        databases[0],
                        tableName,
                        tileDao.minZoom.toInt(),
                        tileDao.maxZoom.toInt(),
                        bounds
                    )

                    Log.d("packgage","absolutepath " + geoPackageFile.absolutePath)

                    val geoPackageProvider = GeoPackageProvider(arrayOf(geoPackageFile), requireContext())
                    geoPackageProvider.tileSource = geopackageRasterTileSource


                    val tilesOverlay = TilesOverlay(geoPackageProvider, requireContext())

                    mapView.overlayManager.add(tilesOverlay)
                    mapView.minZoomLevel = tileDao.minZoom.toDouble()
                    mapView.maxZoomLevel = tileDao.maxZoom.toDouble()
                    mapView.zoomToBoundingBox(bounds, true)
                    mapView.controller.setZoom(tileDao.minZoom.toDouble())
                    mapView.invalidate()
                }
            }*/
        } catch (ex: Exception) {
            Log.d("packgage", "inside geopackage exception " + ex.message)
        }
    }

    private fun createOsmPolygonFromNgaPolygon(
        ngaPolygon: mil.nga.sf.Polygon,
        featureRow: FeatureRow,
        colore: String
    ): Polygon {
        //val osmdroidPolygon = Polygon(map) // Assuming 'map' is accessible
        val osmdroidPolygon = Polygon() // Assuming 'map' is accessible
        val exteriorRingPoints = mutableListOf<GeoPoint>()
        ngaPolygon.rings.firstOrNull()?.points?.forEach { ngaPoint ->
            exteriorRingPoints.add(GeoPoint(ngaPoint.y, ngaPoint.x))
        }
        osmdroidPolygon.points = exteriorRingPoints

        if (ngaPolygon.rings.size > 1) {
            val holes = mutableListOf<List<GeoPoint>>()
            ngaPolygon.rings.drop(1).forEach { interiorNgaRing ->
                val holePath = mutableListOf<GeoPoint>()
                interiorNgaRing.points.forEach { ngaPoint ->
                    holePath.add(GeoPoint(ngaPoint.y, ngaPoint.x))
                }
                holes.add(holePath)
            }
            osmdroidPolygon.holes = holes
        }
        // Opzione A: Crea la label ora e memorizzala (più semplice se la label non è troppo grande)
        val labelForPolygon =
            layerModel.creaLabel(featureRow, layerModel.currentActiveTableName!!)
        osmdroidPolygon.relatedObject = labelForPolygon // Memorizza la stringa della label

        //Devi associare l' OnClickListener a ogni singola istanza di Polygon che crei.
        // Imposta l'OnClickListener
        osmdroidPolygon.setOnClickListener { polygon, map, eventPos ->
            // 'polygon' è l'istanza di org.osmdroid.views.overlay.Polygon che è stata cliccata
            // 'mapView' è la mappa
            // 'eventPos' è il GeoPoint del click
            // Puoi aprire un InfoWindow personalizzato o eseguire altre azioni
            // Per l'InfoWindow di default (se hai titolo e snippet):
            /*if (polygon.isInfoWindowOpen) {
                InfoWindow.closeAllInfoWindowsOn(mapView)
            } else {
                // Chiudi altri InfoWindow prima di aprirne uno nuovo
                InfoWindow.closeAllInfoWindowsOn(mapView)
                polygon.showInfoWindow() // Mostra l'InfoWindow nel punto cliccato
            }*/
            val retrievedLabel = polygon.relatedObject as? String
            if (retrievedLabel != null) {
                val featureInfo =
                    layerModel.featureList.find { it.name == layerModel.currentActiveTableName } // Trova la FeatureTableInfo corretta per il titolo
                // DA CREARE UN LIVEDATA?
                mostraAlertDialogSemplice(
                    retrievedLabel,
                    featureInfo?.descrTabella ?: "Dettagli Feature"
                )
            }
            true // Indica che l'evento è stato gestito
        }
        // Apply styling (assuming polygonOptions is accessible)
        if (colore == "RANDOM")
            osmdroidPolygon.fillColor = layerModel.getRandomHexColor().toColorInt()
        else
            osmdroidPolygon.fillColor = layerModel.polygonOptions.fillColor
        osmdroidPolygon.strokeColor = layerModel.polygonOptions.strokeColor
        osmdroidPolygon.strokeWidth = layerModel.polygonOptions.strokeWidth
        osmdroidPolygon.title = layerModel.polygonOptions.title
        return osmdroidPolygon
    }

    private fun mostraAlertDialogSemplice(message: String, titolo: String) {
        val builder = AlertDialog.Builder(requireContext()) // 'this' è il Context dell'Activity
        builder.setTitle(titolo) // Imposta il titolo
        builder.setMessage(message) // Imposta il messaggio
        builder.setPositiveButton("Chiudi") { dialog, which ->
            dialog.dismiss() // Chiude esplicitamente il dialogo (spesso non necessario per setPositiveButton)
        }
        val alertDialog: AlertDialog = builder.create()
        alertDialog.show()
    }

    private fun puntiSuMappa(tableName: String, featureInfo: FeatureTableInfo) {
        // Assicurati che usi il GeoPackage dal ViewModel
        val currentGeoPackage = layerModel.geoPackageInstance
        if (currentGeoPackage == null) {
            Log.e(TAG, "GeoPackage is null in puntiSuMappa for table $tableName")
            return
        }
        val colore = featureInfo.colore
        val points = mutableListOf<IGeoPoint>()
        val osmdroidPolygonsToAdd = mutableListOf<Polygon>()
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
                        osmdroidPolygonsToAdd,
                        colore
                    )

                    GeometryType.POLYGON -> processPolygonGeometry(
                        featureRow,
                        osmdroidPolygonsToAdd,
                        colore
                    )
                    // ... other types
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

        if (osmdroidPolygonsToAdd.isNotEmpty()) {
            creaOverlayPoligoni(osmdroidPolygonsToAdd, featureInfo)
        }
        mapView.invalidate()
    }

    private fun creaOverlayPoligoni(
        osmdroidPolygonsToAdd: MutableList<Polygon>,
        featureInfo: FeatureTableInfo // Questo è l'oggetto che deve contenere la lista degli overlay
    ) {
        val polyOverlay =
            FolderOverlay() // Questo è l'overlay che deve essere in featureInfo.listOverlay
        osmdroidPolygonsToAdd.forEach {
            polyOverlay.add(it) // Aggiungi i singoli poligoni al FolderOverlay
        }
        if (featureInfo.listOverlay == null) {
            featureInfo.listOverlay = mutableListOf()
        }
        featureInfo.listOverlay?.add(polyOverlay) // AGGIUNGI IL FOLDER OVERLAY ALLA LISTA!
        // Aggiungi il FolderOverlay principale alla mappa (se non l'hai già fatto)
        // Se lo aggiungi qui, assicurati di non aggiungerlo due volte se lo fai anche fuori
        if (!mapView.overlays.contains(polyOverlay)) {
            mapView.overlays.add(polyOverlay)
        }
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
                    it,
                    featureInfo.descrTabella
                )
            }
        }
        if (!mapView.overlays.contains(sfpo)) {
            mapView.overlays.add(sfpo)
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
        osmdroidPolygonsToAdd: MutableList<Polygon>,
        colore: String
    ) {
        val geometryData = featureRow.geometry
        val geometry = geometryData.geometry
        val ngaPolygon = geometry as mil.nga.sf.Polygon
        osmdroidPolygonsToAdd.add(
            createOsmPolygonFromNgaPolygon(
                ngaPolygon,
                featureRow,
                colore
            )
        )
    }

    private fun processMultiPolygonGeometry(
        featureRow: FeatureRow,
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
                    featureRow,
                    colore
                )
            )
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun startAudioRecording(button: Button) {
        if (isAudioRecording) return

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
            MediaRecorder(requireContext()) // Usa il costruttore con Context se API >= 31, altrimenti il default
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
        if (!isAudioRecording && !forceRelease) return // Se non sta registrando e non è un rilascio forzato, non fare nulla

        try {
            if (isAudioRecording) { // Solo se stava registrando
                // NON chiamare createAudioFileInternal() qui di nuovo.
                // Usa la variabile membro this.audioOutputFile che contiene il file registrato.
                val finalAudioTargetName =
                    audioFileName // Assumendo che audioFileName contenga il nome finale desiderato (es. AUD_timestamp.3gp)
                if (finalAudioTargetName != null && this.audioOutputFile != null) {
                    val storageDir = requireContext().getExternalFilesDir("VoiceNotesWaypoints")
                    val finalAudioFile = File(storageDir, finalAudioTargetName)
                    if (this.audioOutputFile!!.renameTo(finalAudioFile)) {
                        // Rinomina riuscita
                        currentAudioFilePath =
                            finalAudioFile.absolutePath // Aggiorna il percorso al nuovo file
                        Toast.makeText(
                            requireContext(),
                            "Registrazione salvata: ${finalAudioFile.name}",
                            Toast.LENGTH_LONG
                        ).show()
                    } else {
                        // Rinomina fallita, il file rimane con il nome temporaneo
                        Toast.makeText(
                            requireContext(),
                            "Salvataggio con nome finale fallito. File: ${this.audioOutputFile!!.name}",
                            Toast.LENGTH_LONG
                        ).show()
                        // currentAudioFilePath punterebbe ancora al file temporaneo se non lo aggiorni
                    }
                } else {
                    // audioFileName o this.audioOutputFile erano null, gestisci l'errore
                    Toast.makeText(
                        requireContext(),
                        "Errore: impossibile definire il nome finale del file.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        } catch (e: RuntimeException) {
            Log.e(TAG_AUDIO, "stop() failed: ${e.message}")
            if (isAudioRecording) { // Mostra errore solo se l'utente si aspettava uno stop normale
                Toast.makeText(
                    requireContext(),
                    "Interruzione registrazione fallita",
                    Toast.LENGTH_SHORT
                ).show()
            }
            // In alcuni casi, il file potrebbe essere corrotto o parziale
            // audioOutputFile?.delete() // Considera se cancellare
            currentAudioFilePath = null // La registrazione non è valida
        } finally {
            isAudioRecording = false
            button.text = "Registra Commento Vocale (5s)"
            audioHandler.removeCallbacksAndMessages(null)
            if (forceRelease) {
                releaseMediaRecorderInternal(false) // Non cancellare il file se lo stop è forzato ma riuscito
            }
        }
    }

    private fun createAudioFileInternal(): File? {
        return try {
            val timeStamp: String =
                SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            audioFileName = "AUD_${timeStamp}.3gp"
            val storageDir: File? = requireContext().getExternalFilesDir("VoiceNotesWaypoints")
            if (storageDir != null && !storageDir.exists()) {
                if (!storageDir.mkdirs()) {
                    Log.e(TAG_AUDIO, "Impossibile creare la directory VoiceNotesWaypoints")
                    return null
                }
            }
            File.createTempFile(audioFileName, ".3gp", storageDir).also {
                Log.d(TAG_AUDIO, "File audio creato: ${it.absolutePath}")
            }
        } catch (ex: IOException) {
            Log.e(TAG_AUDIO, "Errore nella creazione del file audio: ${ex.message}")
            null
        }
    }

    private fun releaseMediaRecorderInternal(deleteFileOnError: Boolean) {
        if (deleteFileOnError) {
            audioOutputFile?.let {
                if (it.exists()) it.delete()
                Log.d(TAG_AUDIO, "File audio cancellato a causa di errore nel rilascio.")
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


}