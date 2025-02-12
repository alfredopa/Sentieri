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
import android.location.Location
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
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
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.ContextCompat.getColor
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.lifecycleScope
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.navigation.fragment.findNavController
import androidx.preference.PreferenceManager
import com.apstudio.mytestmapsforgegit.URIPathHelper
import com.apstudio.sentieri.MapUtils.dataOraIso8601
import com.apstudio.sentieri.MapUtils.disegnaLine
import com.apstudio.sentieri.MapUtils.getFileNameFromUri
import com.apstudio.sentieri.db.FotoPoi
import com.apstudio.sentieri.db.FotoPoiDao
import com.apstudio.sentieri.db.PoiDB
import com.apstudio.sentieri.db.PoiDao
import com.apstudio.sentieri.db.Sentieri
import com.apstudio.sentieri.db.SentieriDB
import com.apstudio.sentieri.db.SentieriRepo
import com.apstudio.sentieri.db.TrackDao
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import net.federicomatera.agpxp.GpxParser
import net.federicomatera.agpxp.GpxWriter
import net.federicomatera.agpxp.models.Gpx
import net.federicomatera.agpxp.models.GpxMetadata
import net.federicomatera.agpxp.models.Link
import net.federicomatera.agpxp.models.Track
import net.federicomatera.agpxp.models.WayPoint
import org.mapsforge.map.rendertheme.ExternalRenderTheme
import org.mapsforge.map.rendertheme.XmlRenderTheme
import org.osmdroid.api.IMapController
import org.osmdroid.mapsforge.MapsForgeTileProvider
import org.osmdroid.mapsforge.MapsForgeTileSource
import org.osmdroid.tileprovider.MapTileProviderBase
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
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.compass.CompassOverlay
import org.osmdroid.views.overlay.compass.InternalCompassOrientationProvider
import org.osmdroid.views.overlay.infowindow.BasicInfoWindow
import java.io.File
import java.sql.Timestamp
import java.util.Date

class MappaFragment : Fragment(), MenuProvider, SharedPreferences.OnSharedPreferenceChangeListener, View.OnKeyListener {
    val viewModel: SentieriViewModel by activityViewModels {
        SentieriFactory(
            SentieriRepo(requireActivity())
        )
    }

    // viewModel del LocationService con scope Application
    private val gpsViewModel: GpsViewModel by lazy {
        ViewModelProvider(requireActivity().application as ViewModelStoreOwner).get(GpsViewModel::class.java)
    }

    private lateinit var bottomSheetBehavior: BottomSheetBehavior<ConstraintLayout>

    //private lateinit var intent: Intent
    private val SELECT_GPX_FILE = 10
    private val SELECT_MAP_FILE = 20
    private lateinit var mapView: MapView
    private lateinit var gpsMarker: Marker
    private var uri: Uri? = null

    // valori per polyline scalar
    /* private var mMapping: ColorMappingVariationHue? = null
    private var mContainer: ColorMappingForScalarContainer? = null
    private val paintBorder = Paint()
    private val paintMapping = Paint()*/

    private lateinit var dist: TextView
    private lateinit var quota: TextView
    //private lateinit var quotaIpso: TextView
    private lateinit var velo: TextView
    private lateinit var disliv: TextView
    private lateinit var dMeno: TextView
    private lateinit var dislivIpso: TextView
    private lateinit var dMenoIpso: TextView

    private lateinit var tempo: TextView
    private lateinit var blocMappa: FloatingActionButton
    private lateinit var flCamera: FloatingActionButton
    private lateinit var osservaMappa: Observer<Polyline>
    private var alertDialog: AlertDialog? = null
    private lateinit var btnAllarme: Button

    // memorizza istanza del menu per aggiornare icone
    private var menu: Menu? = null

    //gestione preferenze e listener
    private lateinit var preferenze: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // predispone broadcast per servizio aggiornamento posizione
        val filter = IntentFilter(SEND_LOCATION_ACTION)
        LocalBroadcastManager.getInstance(requireContext()).registerReceiver(mReceiver, filter)

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
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        //return inflater.inflate(R.layout.fragment_mappa, container, false)
        val view = inflater.inflate(R.layout.fragment_mappa, container, false)
        // Assegna un listener al fragment per gestire la pressione dei tasti
        view.isFocusableInTouchMode = true
        view.requestFocus()
        view.setOnKeyListener(this)
        //Log.d("Mappa", "onCreateView ")
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        mapView = view.findViewById(R.id.Mapview)
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
                    val uriMappa = Uri.parse(preferenze.getString("URIMappa", "")!!)
                    apreMappa(uriMappa)
                    //viewModel.uriMappa = uriMappa
                    menu?.findItem(0)?.setChecked(true)
                } else
                {
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
        } else
        {
            // nessuna mappa selezionata apre OpenStreetMap
            mapView.isTilesScaledToDpi = true
            mapView.setUseDataConnection(true)
            online(1)
        }

        /*if (!viewModel.isMapOnline) {
            mapView.isTilesScaledToDpi = false
            mapView.setUseDataConnection(false)
            if (preferenze.contains("URIMappa"))
                apreMappa(Uri.parse(preferenze.getString("URIMappa", "")!!))
            else
                MapUtils.setMapOfflineSource(activity, mapView)
        } else {
            mapView.isTilesScaledToDpi = true
            mapView.setUseDataConnection(true)
            //if (preferenze.contains("URLMappa")) {

            //preferenze.getString("URLMappa", "MAPNIK")?.let { online(it)
        }*/

        val menuHost: MenuHost = requireActivity()
        menuHost.addMenuProvider(this, viewLifecycleOwner, Lifecycle.State.RESUMED)
        Log.d("Mappa", "onViewCreated ")
        //aggiunge i folder overlay, listaTracce che conterrà tutte le tracce aggiunte  overlays alla mapview
        // e rectraccia che conterrà la traccia corrente
        if (mapView.overlays.size == 0) {
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
            gpsMarker.icon = resources.getDrawable((R.drawable.punto_gps), null)
            mapView.overlayManager.add(gpsMarker)

            // add compass to map
            val compassOverlay =
                CompassOverlay(context, InternalCompassOrientationProvider(context), mapView)
            compassOverlay.enableCompass()
            compassOverlay.setCompassCenter(36f, 36f)
            mapView.overlayManager.add(compassOverlay)
        }

        mapView.getZoomController()?.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
        mapView.setMultiTouchControls(true)
        mapView.setMinZoomLevel(7.0)
        mapView.setMaxZoomLevel(19.0)
        val mapController: IMapController = MapController(mapView)
        mapController.setCenter(viewModel.ultPosizione)
        mapController.setZoom(viewModel.ultZoom)

        // aggiunge il bottomsheet ed il menu
        val layoutSheet = view.findViewById<ConstraintLayout>(R.id.cruscotto)
        bottomSheetBehavior = BottomSheetBehavior.from(layoutSheet)
        bottomSheetBehavior.isHideable = true
        //bottomSheetBehavior.skipCollapsed = true
        //bottomSheetBehavior.peekHeight = 160
        bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
        // Textview dei valori da visualizzare in Cruscotto
        dist = view.findViewById(R.id.tvDist)
        //distH = view.findViewById(R.id.tvDistH)
        quota = view.findViewById(R.id.tvQuota)
        //quotaIpso = view.findViewById(R.id.tvDistH)
        velo = view.findViewById(R.id.tvVelo)
        disliv = view.findViewById(R.id.tvDPiu)
        dMeno = view.findViewById(R.id.tvDMeno)
        //dislivIpso = view.findViewById(R.id.tvDisIpso)
        //dMenoIpso = view.findViewById(R.id.tvDisMenoIpso)
        tempo = view.findViewById(R.id.tvTempo)
        blocMappa = view.findViewById(R.id.fab)
        flCamera = view.findViewById(R.id.camera)
        btnAllarme = view.findViewById(R.id.button)
        //Log.d("Mappa", "onViewCreated ${bottomSheetBehavior.state}")
        // Bottone per bloccare ancoraggio mappa al gps
        blocMappa.setOnClickListener {
            bloccaMappa()
        }

        // Bottone per attivare la fotocamera
        flCamera.setOnClickListener {
            //if (!viewModel.isRecording) {
            //Log.d("camera", "viemodel ${viewModel.traccia.points.size}")
            val directions =
                MappaFragmentDirections.actionMappaFragmentToCameraFragment()
            this@MappaFragment.findNavController().navigate(directions)
            //}
        }

        btnAllarme.setOnClickListener {
            // Dis/Abilita allarme fuori tracce
            viewModel.alertFuoriTraccia = !viewModel.alertFuoriTraccia
            //Log.d("allarme", "allarme ${viewModel.alertFuoriTraccia}")
            if (viewModel.alertFuoriTraccia) {
                btnAllarme.text = "Allarme on"
                btnAllarme.backgroundTintList = ColorStateList.valueOf(Color.RED)
            } else {
                btnAllarme.text = "Allarme off"
                btnAllarme.backgroundTintList = ColorStateList.valueOf(Color.GREEN)
            }
            btnAllarme.postInvalidate()
        }

        // l'intent contiene il nome del file GPX da caricare da Files
        //intent = requireActivity().intent!!
        val intent = requireActivity().intent
        //if (!viewModel.isRecording) {
        val data: Uri? = intent.data
        if (data != null) {
            caricaGPX(data)
            intent.setData(null)
        }
    }

    // aggiunge il click listener alla polyline per aprire l'info window
    private fun setPolylineClickListener(polyline: Polyline) {
        polyline.setOnClickListener { polyline, mapView, eventPos ->
            // Il layout è stato copiato nelle risorse potrebbe differire dall'originale
            polyline.infoWindow = BasicInfoWindow(R.layout.bonuspack_bubble, mapView)
            polyline.infoWindowLocation = eventPos
            polyline.showInfoWindow()
            false // Ritorna true per indicare che l'evento è stato gestito
        }
    }

    // aggiunge il click listener al marker per aprire l'info window
    private fun setMarkerClickListener(marker: Marker) {
        marker.setOnMarkerClickListener { marker, mapView ->
            // Apri la info window qui, usando eventPos come posizione
            marker.infoWindow = BasicInfoWindow(R.layout.bonuspack_bubble, mapView)
            marker.showInfoWindow()
            mapView.controller.animateTo(marker.position)
            true // Ritorna true per indicare che l'evento è stato gestito
        }
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()

        // Controlli per verificare valori da altri fragment da scheda sentieri e visualizzazione waypoint
        // verifica se è valorizzata line, quindi è stato passsata dal pulsante Segui
        // e lo mostra sulla mappa
        // qui carica traccia dal db con waypoint e lista foto
        if (viewModel.line.actualPoints.size > 0) {
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
            /*val segui: Boolean
            if (viewModel.tracciaDaSeguire == nuovaTraccia.title)
                segui = true
            else
                segui = false
            viewModel.layerItems.add(LayerItem(nuovaTraccia.title, nuovaTraccia.isEnabled, false, segui))*/
            // il post serve a terminare la fase di disegno prima di eseguire lo zoom
            mapView.post {
                mapView.zoomToBoundingBox(mbounds.increaseByScale(1.2f), false)
            }
            viewModel.line.actualPoints.clear()

            // carica i waypoints dalla lista wayPoints da non salvare con traccia
            if (viewModel.wayPoint.size > 0) {
                viewModel.wayPoint.forEach {
                    val poiMarker = Marker(mapView)
                    poiMarker.title = it.name
                    poiMarker.icon = resources.getDrawable((R.drawable.ic_finish), null)
                    poiMarker.position.latitude = it.latitude
                    poiMarker.position.longitude = it.longitude
                    poiMarker.position.altitude = it.elevation ?: 0.0
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
                    mapView.controller.animateTo(alMarker.position, viewModel.ultZoom.toDouble(), 0)
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
            bottomSheetBehavior.isHideable = false
            bottomSheetBehavior.peekHeight = 90
            bottomSheetBehavior.state = viewModel.BottomState
            viewModel.running = true
            startUpdates()
            // riavvia gli observer per aggiornamento dati cruscotto
            avviaObserver()
            val toast =
                Toast.makeText(requireActivity(), "Registrazione in corso", Toast.LENGTH_SHORT)
            toast.view?.setBackgroundColor(getColor(requireActivity(), R.color.purple_500))
            toast.show()
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
        viewModel.BottomState = bottomSheetBehavior.state

        mapView.onPause() //needed for compass, my location overlays, v6.0.0 and up
        if (viewModel.isRecording) {
            stopUpdates()
        }
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

        val maps: Array<File?> = arrayOfNulls(1)
        val f = File(filePath)
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
        var theme: XmlRenderTheme?  = null
        if (f.name.contains(".map")) {
            val folderTema = File(Environment.getExternalStorageDirectory().absolutePath + "/Sentieri/Mappe/4UMaps/4UMaps.xml")
            if (folderTema.exists()) {
                theme = ExternalRenderTheme(
                    Environment.getExternalStorageDirectory().absolutePath +
                            "/Sentieri/Mappe/4UMaps/4UMaps.xml"
                )
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
            viewModel.MappaTileProvider = offlineMappa as MapTileProviderBasic
            val archives = offlineMappa.archives
// importante setIgnoreTileSource consente apertura rapida della mappa evitando il controllo del tipo di sorgente presente nel file tiles
            archives[0].setIgnoreTileSource(true)
        }

        mapView.setTileSource(TileSourceFactory.DEFAULT_TILE_SOURCE)
        viewModel.connessione = (false)
        mapView.setUseDataConnection(false)
        mapView.invalidate()
    }

    private fun online(mappa: Int) {
        var scarica: MapTileProviderBasic? = null
        viewModel.connessione = (true)
        // salvo indice menu selezionato
        viewModel.menuMap = mappa
        mapView.setUseDataConnection(true)
        when (mappa) {
            1 -> scarica = MapTileProviderBasic(context, TileSourceFactory.MAPNIK)  // OpenStreetmap
            2 -> scarica = MapTileProviderBasic(context, TileSourceFactory.OpenTopo) // OpenTopo
            3 -> scarica = MappaMapBox() // MapBox
        }
// salva la mappa scelta nelle preferenze
        preferenze.edit().putInt("MenuMap", mappa).apply()
        mapView.tileProvider = scarica
        mapView.invalidate()
    }

    private fun bloccaMappa() {
// cambio immagine non funziona
        if (viewModel.isRecording) {
            if (!viewModel.bloccaMappa) {
                val icona = ContextCompat.getDrawable(requireContext(), R.drawable.pin_rosso)
                blocMappa.setImageDrawable(icona)
            } else {
                val icona = ContextCompat.getDrawable(requireContext(), R.drawable.pin_nero)
                blocMappa.setImageDrawable(icona)
            }
// blocMappa.show()
            blocMappa.invalidate()
            viewModel.bloccaMappa = !viewModel.bloccaMappa
//Log.d("blocco", "${viewModel.bloccaMappa}")
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
// annulla calibrazione quindi utilizza solo GPS
                viewModel.setBaro = false
                //viewModel.puntiPostFix = 0
                attivaGps()
            }
            dlgBaro.show(requireActivity().supportFragmentManager, "dlgBaro")
        }
    }

    private fun stopGPS() {
// FINE REGISTRAZIONE TRACCIA se non ha fixato non chiede di salvare
        if (!viewModel.isFixed) {
            azzeraCruscotto()
            fermaRecording()
            return
        }

        var builder = AlertDialog.Builder(requireContext(), R.style.AlertDialogCustom)
        val inputEditTextField = EditText(requireActivity())
        inputEditTextField.setText("Traccia")
        with(builder)
        {   // chiede salvataggio traccia
            setTitle("Salva traccia")
            setMessage("Vuoi memorizzare la traccia").setView(inputEditTextField)
            setPositiveButton(
                "Ok"
            ) { _, _ ->
                salvaTraccia(inputEditTextField.text.toString())
                azzeraCruscotto()
                fermaRecording()
            }
            setNegativeButton(android.R.string.cancel) { _, _ ->
                azzeraCruscotto()
                fermaRecording()
            }
            setNeutralButton("Continua") { _, _ -> }
            setCancelable(false) // Impedisce la chiusura tramite tocco esterno o tasto Indietro
            show()
        }
    }

    private fun fermaRecording() {
// ferma aggiornamenti posizione ui e ferma servizio LocationService
        // Log.d("Posizione","Stop servizio")
        requireActivity().stopService(Intent(context, LocationService::class.java))
        stopUpdates()
        gpsMarker.setVisible(false)
// rimuove impostazione schermo sempre acceso
        requireActivity().window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
// azzera valori da visualizzare in Cruscotto
        viewModel.isRecording = false
// aggiunge marker fine percorso
        MapUtils.markInizioFine(requireContext(), viewModel.newPunto, mapView, viewModel.recTraccia, 1)
//setBaro indica se si preferisce usare il sensore barometrico fisico oppure no
// a fine registrazione ripristina preferenza barometro
        if (viewModel.haBaro)
            viewModel.setBaro = true
        bottomSheetBehavior.peekHeight = 0
        bottomSheetBehavior.isHideable = true
        bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
        mapView.invalidate()
    }

    private fun attivaGps() {
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
// crea observer per aggiornamento punti traccia
        osservaMappa = Observer {
            viewModel.traccia.observe(viewLifecycleOwner) { traccia ->
                Log.d("observer", "rectraccia ${viewModel.recTraccia.items.size}")
                traccia.title = "Registrazione"
                viewModel.recTraccia.add(traccia)
            }
        }
        viewModel.traccia.observe(viewLifecycleOwner, osservaMappa)

// crea observer sullo stato del GPS cambia colore icona
//val menuItem = menu?.findItem(R.id.Offline)
//val currentGpsStatus = gpsRepository.gpsStatus

        viewModel.running = true
        startUpdates()
// avvia il servizio per tracciare locazione in background
        requireActivity().startService(Intent(context, LocationService::class.java))
        bottomSheetBehavior.isHideable = false
        bottomSheetBehavior.peekHeight = 90
        bottomSheetBehavior.state = BottomSheetBehavior.STATE_HALF_EXPANDED
// avvia gli observer per aggiornamento dati cruscotto
        avviaObserver()
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == SELECT_GPX_FILE && resultCode == AppCompatActivity.RESULT_OK) {
            if (data != null) {
                uri = data?.data
                caricaGPX(uri!!)
                //uri?.run { caricaGPX(uri!!) }
            }
        }
        if (requestCode == SELECT_MAP_FILE && resultCode == AppCompatActivity.RESULT_OK) {
            if (data != null) {
                uri = data.data
                apreMappa(uri!!)
// salva la mappa offline scelta nelle preferenze
                preferenze.edit().putString("URIMappa", uri.toString()).apply()
                preferenze.edit().putInt("MenuMap", 0).apply()
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

// carica i punti della traccia - da verificare con gpx multisegmento
// usa altinulla per contare gli elementi con altitudine 0
        gpx.tracks.get(0).trackPoints.forEach {
//Log.d("Punto","${it.latitude}${it.longitude}")
// verifica esistenza valore altitudine
            if (it.elevation != null) {
                punto = GeoPoint(it.latitude, it.longitude, it.elevation.toDouble())
// confronta con la precedente altitudine non nulla e verifica se aumenta ascesa oppure discesa
                if (oldPunto?.altitude != null) {
                    if (it.elevation.toDouble() > oldPunto?.altitude!!) {
                        viewModel.trackAscesa += (it.elevation.toInt() - oldPunto!!.altitude.toInt())
                    } else {
                        viewModel.trackDiscesa += (it.elevation.toInt() - oldPunto!!.altitude.toInt())
                    }
                }
            } else {
                punto = GeoPoint(it.latitude, it.longitude)
                altiNulla += 1
            }
// calcola distanza della traccia, da utilizzare se viene seguita per caloolare distanza rimanente
            if (oldPunto != null) {
                val distToPunto = MapUtils.getDistanceInMeters(oldPunto!!, punto)
                viewModel.trackDistanza += distToPunto
            }

            oldPunto = GeoPoint(it.latitude, it.longitude, it.elevation?.toDouble() ?: 0.0)
            line.addPoint(punto)
        }
        Log.d(
            "Punto",
            "${viewModel.trackDistanza}   ${viewModel.trackAscesa}  ${viewModel.trackDiscesa}"
        )
        disegnaLine(line)
        viewModel.listaTracce.add(line)
        addMarker(line)

// carica i waypoints nella lista wayPoints da non salvare con traccia
        gpx.wayPoints?.forEach {
            viewModel.wayPoint.add(it)
            val waymarker = Marker(mapView)
            waymarker.title = it.name
            waymarker.icon = resources.getDrawable((R.drawable.ic_finish), null)
            waymarker.position.latitude = it.latitude
            waymarker.position.longitude = it.longitude
            waymarker.position.altitude = it.elevation ?: 0.0
// carica nel FolderOverlay
            viewModel.listaTracce.add(waymarker)
        }

// verifica il numero dei punti con il valore altinulla se coincide tutti i punti hanno altitudine nulla
        if (gpx.tracks.get(0).trackPoints.size == altiNulla) {
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
//Log.d("caricagpx", mapView.zoomLevel.toString())
// esegue la visualizzazione dopo aver aggiornato lo zoom della mappa
        mapView.post {
            mapView.zoomToBoundingBox(line.bounds.increaseByScale(1.2f), false)
        }
        MapUtils.alertSegui(requireContext(), viewModel, line)
    }

    private fun avviaObserver() {
        viewModel.distanzaMetri.observe(viewLifecycleOwner) {
            dist.text = formattastring(viewModel.distanzaMetri.value!!)
        }
        viewModel.velocita.observe(viewLifecycleOwner) {
            velo.text = viewModel.velocita.value?.toInt().toString() + " km/h"
        }
        viewModel.quota.observe(viewLifecycleOwner) {
            quota.text = viewModel.quota.value.toString()
        }
        //viewModel.quotaIpso.observe(viewLifecycleOwner) {
        //    quotaIpso.text = viewModel.quotaIpso.value.toString()
        //}
        viewModel.dislivPiu.observe(viewLifecycleOwner) {
            disliv.text = viewModel.dislivPiu.value.toString()
        }
        viewModel.dislivMeno.observe(viewLifecycleOwner) {
            dMeno.text = viewModel.dislivMeno.value.toString()
        }
        /*viewModel.dislivPiuIpso.observe(viewLifecycleOwner) {
            dislivIpso.text = viewModel.dislivPiuIpso.value.toString()
        }
        viewModel.dislivMenoIpso.observe(viewLifecycleOwner) {
            dMenoIpso.text = viewModel.dislivMenoIpso.value.toString()
        }*/
        gpsViewModel.gpsStatus.observe(viewLifecycleOwner) { status ->
            val currentGpsStatus = status.toString()
            val menuItem = menu?.findItem(R.id.gps)
            //Log.d("gpsStatus", "observe $currentGpsStatus")
            val icon = when (currentGpsStatus) {
                "started" -> R.drawable.gps_started
                "fixed" -> R.drawable.gps_on
                "stopped" -> R.drawable.gps_off
                else -> R.drawable.gps_off
            }
            menuItem?.setIcon(resources.getDrawable(icon))
        }
    }

    private fun salvaTraccia(nomeTraccia: String) {
        var tracciaDao: TrackDao
        var ultimoID: Int
//var punto = WayPoint()
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
            DataOra = dateString,
            TempMedia = 0.0,
            TempMax = 0.0,
            TempMin = 0.0,
            DataFine = "",
            TempoTot = 0.0,
            TempoInMov = 0.0,
            MediaVel = 0.0
        )

        runBlocking {
            // salva il nuovo record in Tabella Sentiero
            viewModel.run {
                salvaSentiero(sentiero)
            }
            val dao = SentieriDB.getInstance(requireActivity().application).sentieriDao
            ultimoID = dao.ultimoId()
        }

//ciclo caricamento punti GPS in lista punti db
        tracciaDao = SentieriDB.getInstance(requireActivity().application).trackDao
        viewModel.puntiGPS.forEach {
            val trackPoint = com.apstudio.sentieri.db.Track(
                Id = 0,
                Trackid = ultimoID,
                Latit = it.latitude.toFloat(),
                Longit = it.longitude.toFloat(),
                Ele = it.elevation!!.toFloat(),
                Ora = it.time.toString()
            )
            MainScope().launch {
                tracciaDao.insertDB(trackPoint)
                //Log.d("Track","$trackPoint")
            }
        }

// scrive waypoint se inseriti durante registrazione traccia
// la lista è PoiDB
        if (viewModel.poiDBList.size > 0) {
            val poiDao: PoiDao =
                SentieriDB.getInstance(requireActivity().application).poiDao
            viewModel.poiDBList.forEach {
                val poi = PoiDB(
                    Id = 0,
                    Trackid = ultimoID,
                    Latit = it.Latit,
                    Longit = it.Longit,
                    Ele = it.Ele,
                    NomePOI = it.NomePOI,
                    DescrPOI = it.DescrPOI,
                    UriPath = "",
                    Time = it.Time
                )
                MainScope().launch {
                    poiDao.insertDB(poi)
                    //Log.d("Track","$trackPoint")
                }
            }
        }

// memorizza uri e nome file delle foto scattate in regsitrazione traccia
        if (viewModel.fotoInPoiDB.size > 0) {
            val fotoDao: FotoPoiDao =
                SentieriDB.getInstance(requireActivity().application).fotoPoiDao
            viewModel.fotoInPoiDB.forEach {
                val foto = FotoPoi(
                    id = 0,
                    trackid = ultimoID,
                    uriPath = it.toString(),
                    nomeFoto = getFileNameFromUri(requireContext(), it)
                )
                MainScope().launch {
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
            //  esempio di come formare il gpx per scrittura
            /*val gpx = Gpx(
            version = "1",
            creator = "Sentieri",
            tracks = listOf(
                Track(
                    name = "myGpx",
                    trackPoints = listOf(
                        WayPoint(
                            latitude = 30.8,
                            longitude = 9.11,
                            elevation = 430.0
                        ),
                        WayPoint(
                            latitude = 30.9,
                            longitude = 9.12,
                            elevation = 432.0
                        ),
                        WayPoint(
                            latitude = 30.10,
                            longitude = 9.113,
                            elevation = 442.0
                            )
                        )
                    )
                )
            )
            */

            // crea la cartella Documents però nella root
            /*val publicDocumentsDir = requireContext().getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
            if (publicDocumentsDir != null) {
                publicDocumentsDir.mkdirs() // Crea la cartella se non esiste

                publicDocumentsDir.setExecutable(true, false)
                publicDocumentsDir.setReadable(true, false)
                publicDocumentsDir.setWritable(true, false)

                // ... scrivi i file nella cartella publicDocumentsDir ...
            }*/

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
                setTitle("Percorso concluso")
                setMessage("Distanza percorsa ${viewModel.distanzaMetri.value} con d+ ${viewModel.dislivPiu.value}")
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
// azzera i valori del viewModel
// legati alla traccia visualizzati nel cruscotto
        viewModel.distanzaMetri.value = 0
        viewModel.dislivPiu.value = 0
        viewModel.dislivMeno.value = 0
        viewModel.quota.value = 0
        viewModel.dislivPiuIpso.value = 0
        viewModel.dislivMenoIpso.value = 0
    }


    // riceve aggiornamento posizione da servizio in Broadcast
    private val mReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            var loc: Location
            if (Build.VERSION.SDK_INT >= 34) {
                loc = intent.getParcelableExtra("posizione", Location::class.java)!!
            } else {
                @Suppress("DEPRECATION")
                loc = intent.getParcelableExtra("posizione")!!
            }
            var altitudine: Double = intent.getDoubleExtra("altitudine", 0.0)
            var milliBar = intent.getFloatExtra("milliBar", 0.0F)

    // aggiorna posizione ed inserisce nuovo punto
    // riceve il valore in millibar letti da barometro e lo passa al viewModel
    // aggiorna dati nel viewModel
            viewModel.aggiornaDati(loc, altitudine, milliBar)
    //se non è visualizzata la mappa non aggiorna dati cruscotto
            if (!viewModel.running)
                return
    // al primo punto aggiunge il marker d'inizio
            if (viewModel.traccia.value?.actualPoints?.size == 1)
                MapUtils.markInizioFine(requireContext(), viewModel.newPunto, mapView, viewModel.recTraccia, 0)

    //Log.d("BroadcastReceiver ",  "numsat ${gpsViewModel.numSat}")
    // sposta il marker su nuova posizione con animazione
            gpsMarker.position = (viewModel.newPunto)
            if (viewModel.bloccaMappa) {
                mapView.controller?.animateTo(viewModel.newPunto)
            }
    // Orienta display sorgente in OpenMap demo di Osmdroid: Location - SampleHeadingCompassUp
    //Log.d("loc", "${loc.bearing} - ${loc.speed}")
            val gpsbearing = loc.bearing
    //val gpsspeed = loc.speed

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
                    //val puntoDista =  traccia.getCloseTo(viewModel.newPunto, 30.0, mapView)
                    //Log.d("Mappa", "segui $puntoDista")
                    // restituisce se il punto è vicino con una tolleranza di 30 pixel alla posizione corrente della mappa
                    if (!traccia.isCloseTo(viewModel.newPunto, 30.0, mapView)) {
                        //Alert
                        val allarme = EditText(requireActivity())
                        val builder =
                            AlertDialog.Builder(requireContext(), R.style.AlertDialogCustom)
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

    // i Wp sono caricati nelle liste wayPoint per quelli che sono caricati da dB e Gpx
// e in poiDBList per quelli ripresi durante registrazione traccia e devono essere salvati nel db PoiDB
    private fun salvaWayPoint(nome: String, descr: String) {
        val newWayPoint = WayPoint(
            latitude = viewModel.newPunto.latitude,
            longitude = viewModel.newPunto.longitude,
            elevation = viewModel.newPunto.altitude,
            time = Timestamp(System.currentTimeMillis()),
            name = nome,
            comment = descr
        )
        viewModel.wayPoint.add(newWayPoint)
        viewModel.poiDBList.add(
            PoiDB(
                Id = 0,
                Trackid = 0,
                Latit = viewModel.newPunto.latitude.toFloat(),
                Longit = viewModel.newPunto.longitude.toFloat(),
                Ele = viewModel.newPunto.altitude.toFloat(),
                NomePOI = nome,
                DescrPOI = descr,
                UriPath = "",
                Time = Date().toString()
            )
        )

        val waymarker = Marker(mapView)
        waymarker.title = nome
        waymarker.icon = resources.getDrawable((R.drawable.ic_finish), null)
        waymarker.position.latitude = newWayPoint.latitude
        waymarker.position.longitude = newWayPoint.longitude
//mapView.overlays?.add(waymarker)
        viewModel.listaTracce.add(waymarker)
    }


    override fun onDestroy() {
//ondestroy viene richiamato al termine dell'app
        if (!viewModel.isRecording) {
            LocalBroadcastManager.getInstance(requireContext()).unregisterReceiver(mReceiver)
        }
        super.onDestroy()
        preferenze.unregisterOnSharedPreferenceChangeListener(this)
    }

    companion object {
        const val SEND_LOCATION_ACTION = "com.apstudio.sentieri.posizione"
    }

    private fun formattastring(distanza: Int): String {
// visualizza distanza in metri o km
        return if (distanza < 1_000)
            String.format("%d m", distanza)
        else
            String.format("%.1f km", (distanza / 1_000.0).toFloat())

    }

    // coroutine per aggiornamento del tempo di registrazione sul cruscotto
    private fun startUpdates() {
        viewLifecycleOwner.lifecycleScope.launch {
            while (true) {
                if (viewModel.running) {
                    tempo.text = viewModel.tempoTrascorso()
                    delay(1000)
                } else
                    delay(1000)

            }
        }
    }

    private fun stopUpdates() {
        viewModel.running = false
//Log.d("Mappa", "Stop running")
    }

    override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
        menuInflater.inflate(R.menu.main_menu, menu)
// viene richiamata alla creazione del menu, quindi  anche quando si cambia il fragment
        this.menu = menu
// necessario ricaricare icona se il fragment viene messo in pausa e ricreato
        /*if (viewModel.isRecording) {
        val voceMenu = menu.findItem(R.id.gps) as MenuItem
        voceMenu.setIcon(resources.getDrawable(R.drawable.gps_on))
        }*/
    }

    /*override fun onPrepareMenu(menu: Menu) {
    // la funzione viene chiamata ogni volta che viene cambiato il fragment
    // assegna il colore al pulsante GPS
    val menuItem = menu.findItem(R.id.gps)
    if (viewModel.isRecording) {
    menuItem.setIcon(resources.getDrawable(R.drawable.gps_on))
    } else
    menuItem.setIcon(resources.getDrawable(R.drawable.gps_off))

    /* Log.d("onPrepareMenu", currentGpsStatus)
    when (currentGpsStatus) {
    "started" -> menuItem.setIcon(R.drawable.gps_started)
    "fixed" -> menuItem.setIcon(R.drawable.gps_on)
    "stopped" -> menuItem.setIcon(R.drawable.gps_off)
    }*/
    }*/

    override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
// Handle the menu selection
        when (menuItem.itemId) {
            R.id.Offline -> {
                menuItem.isChecked = !menuItem.isChecked
                offline()
            }

            R.id.Online -> {
                menuItem.isChecked = !menuItem.isChecked
                online(1 )
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
// gestione del frammento Lista sentieri se non in registrazione traccia
//if (!viewModel.isRecording) {
                val directions =
                    MappaFragmentDirections.actionMappaFragmentToSentieriFragment()
                this@MappaFragment.findNavController().navigate(directions)
//}
            }

            R.id.gps -> {
// Attiva o disattiva GPS
                if (viewModel.isRecording) {
                    //menuItem.setIcon(resources.getDrawable(R.drawable.gps_off))
                    stopGPS()
                } else {
                    //menuItem.setIcon(resources.getDrawable(R.drawable.gps_on))
                    // se è presente barometro ed è settato per essere usato, verifica poi se è gia calibrato non è necessario
                    if (viewModel.haBaro && viewModel.setBaro) {
                        if (!viewModel.is_Calibrato)
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
        }
        return false
    }

    private fun creaWayPoint() {
        val nomePoi = EditText(requireActivity())
        val descrPoi = EditText(requireActivity())
        nomePoi.setText("Nome")
        descrPoi.setText("Descrizione")

        val builder = AlertDialog.Builder(requireContext(), R.style.AlertDialogCustom)
        with(builder)
        {
            setTitle("Crea waypoint")
//setMessage("Inserire nuovo waypoint").setView(nomePoi)
            val layout = LinearLayout(context)
            layout.orientation = LinearLayout.VERTICAL

            nomePoi.setText("WayPoint")
            layout.addView(nomePoi)

            descrPoi.setText("Descrizione")
            layout.addView(descrPoi)

// Set the LinearLayout as the view for the dialog
            builder.setView(layout)

            setPositiveButton(
                "Crea"
            ) { _, _ ->
                salvaWayPoint(nomePoi.text.toString(), descrPoi.text.toString())
//Log.d("Camera", "positivo")
            }
            setNegativeButton(android.R.string.cancel) { _, _ -> }
//create()
            show()
        }
    }

    private fun addMarker(line: Polyline) {
// aggiunge marker inizio e fine percorso
        val startMarker = Marker(mapView)
        startMarker.icon = requireContext().let {
            AppCompatResources.getDrawable(
                it,
                R.drawable.ic_start
            )
        }
        startMarker.title = "Inizio"
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
        if (keyCode == KEYCODE_VOLUME_UP) {
            mapController?.zoomIn()
        }
        if (keyCode == KEYCODE_VOLUME_DOWN) {
            mapController?.zoomOut()
        }
        return true
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
}

/*----------------------------------------------------------------------------------------------------
//  filtra i punti basandosi sulla velocità verticale
fun filterPoints(points: List<Point>): List<Point> {
val filteredPoints = mutableListOf<Point>()
var previousPoint: Point? = null
for (point in points) {
if (previousPoint != null) {
val verticalSpeed = (point.altitude - previousPoint.altitude) / (point.timestamp - previousPoint.timestamp)
if (Math.abs(verticalSpeed) <= 10.0) {
    filteredPoints.add(point)
}
}
previousPoint = point
}
return filteredPoints
}

@Override
fun onOrientationChanged(orientationToMagneticNorth: Float, source: IOrientationProvider?) {
//note, on devices without a compass this never fires...
//only use the compass bit if we aren't moving, since gps is more accurate when we are moving
if (gpsspeed < 0.01) {
var gf: GeomagneticField? = GeomagneticField(lat, lon, alt, timeOfFix)
trueNorth = orientationToMagneticNorth + gf!!.declination
gf = null
synchronized(trueNorth) {
if (trueNorth > 360.0f) {
    trueNorth = trueNorth - 360.0f
}
var actualHeading = 0f

//this part adjusts the desired map rotation based on device orientation and compass heading
var t: Float = 360 - trueNorth - this.deviceOrientation
if (t < 0) {
    t += 360f
}
if (t > 360) {
    t -= 360f
}
actualHeading = t
//help smooth everything out
t = t.toInt().toFloat()
t = t / 5
t = t.toInt().toFloat()
t = t * 5
mMapView.setMapOrientation(t)
}
}
}
*/