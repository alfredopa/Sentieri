package com.apstudio.sentieri

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.icu.text.DecimalFormat
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.Switch
import android.widget.Toast
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.ContextCompat.getColor
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.apstudio.sentieri.MapUtils.alertVerificaSegui
import com.apstudio.sentieri.databinding.FragmentSchedaBinding
import com.apstudio.sentieri.db.LayerItem
import com.apstudio.sentieri.db.PoiDB
import com.apstudio.sentieri.db.SentieriRepo
import com.apstudio.sentieri.db.prnDiscesa
import com.apstudio.sentieri.db.prnDislivello
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import net.federicomatera.agpxp.GpxWriter
import net.federicomatera.agpxp.models.Gpx
import net.federicomatera.agpxp.models.GpxMetadata
import net.federicomatera.agpxp.models.Link
import net.federicomatera.agpxp.models.Track
import net.federicomatera.agpxp.models.WayPoint
import org.mapsforge.map.rendertheme.ExternalRenderTheme
import org.mapsforge.map.rendertheme.XmlRenderTheme
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
import org.osmdroid.views.MapController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import java.io.File
import java.io.FileOutputStream
import java.util.Date
import androidx.core.net.toUri
import androidx.lifecycle.ViewModelProvider

// Fragment che visualizza il dettaglio della traccia selezionata dall'elenco delle tracce
// su una mappa ridotta e principali dati di riepilogo
class SchedaFragment : Fragment(), MenuProvider {

    private val args: SchedaFragmentArgs by navArgs()
    private lateinit var viewModel: SentieriViewModel

    private lateinit var binding: FragmentSchedaBinding
    private lateinit var mapView: MapView
    private var mapController: MapController? = null
    private val poiDBList = mutableListOf<PoiDB>()
    private lateinit var percorso: Polyline

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = ViewModelProvider(requireActivity().applicationContext as AppSentieri).get(SentieriViewModel::class.java)
        setHasOptionsMenu(true) // Abilita le icone nel menu
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Inflate the layout for this fragment
        binding = FragmentSchedaBinding.inflate(inflater, container, false)
        return binding.root
    }
    override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
        menuInflater.inflate(R.menu.scheda_menu, menu)
    }

    override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
        when (menuItem.itemId) {
            R.id.salvaGpx -> {
                // esporta file gpx
                val scriviGpx = GpxWriter()
                val alink = Link("", "")
                val time = Date()
                val points = percorso.points
                val trackPoints = mutableListOf<WayPoint>()
                val poiGpx = mutableListOf<WayPoint>()
                var puntoGps: WayPoint
                val gpx = Gpx(
                    xmlns = "http://www.topografix.com/GPX/1/1",
                    version = "1.1",
                    creator = "Sentieri",
                    metadata = (GpxMetadata(alink, time)),
                    // attenzione i poi non vengono caricati nel file gpx !!!!!!!
                    wayPoints = poiGpx,
                    tracks = listOf(
                        Track(
                            name = binding.txNome.text.toString(),
                            trackPoints = trackPoints
                        )
                    )
                )
                points.forEach {
                    puntoGps = WayPoint(
                        longitude = it.longitude,
                        latitude = it.latitude,
                        elevation = it.altitude
                    )
                    trackPoints.add(puntoGps)
                }
                poiDBList.forEach {
                    puntoGps = WayPoint(
                        longitude = it.Longit.toDouble(),
                        latitude = it.Latit.toDouble(),
                        elevation = it.Ele.toDouble(),
                        name = it.NomePOI,
                        description = it.DescrPOI
                    )
                    poiGpx.add(puntoGps)
                }
                //Log.d("over", "${mapView!!.overlays[0]} ")

                val downloadFolder =
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                //val file = File(downloadFolder, "Traccia.gpx")
                val file = File(downloadFolder, binding.txNome.text as String + ".gpx")
                val fileOutputStream = FileOutputStream(file)
                scriviGpx.write(gpx, fileOutputStream)
                fileOutputStream.close()
                // Invia un broadcast intent per notificare al sistema del nuovo file
                val mediaScanIntent = Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE)
                mediaScanIntent.data = Uri.fromFile(file)
                requireContext().sendBroadcast(mediaScanIntent)
                val toast = Toast.makeText(
                    requireActivity(),
                    "Scrittura di ${file.name} eseguita nella cartella Download",
                    Toast.LENGTH_LONG
                )
                toast.view?.setBackgroundColor(getColor(requireActivity(), R.color.purple_500))
                toast.show()

            }
            R.id.eliminaSentiero -> {
                // chiede conferma cancellazione
                val builder = AlertDialog.Builder(requireContext(), R.style.AlertDialogCustom)
                with(builder)
                {   // chiede salvataggio traccia
                    setTitle("Eliminazione percorso")
                    setMessage("Sei sicuro di voler eliminare il percorso?")
                    setPositiveButton(
                        "Confermo"
                    ) { _, _ ->
                        // elimina sentiero con transazione?
                        viewModel.cancellaSentiero(args.idSentiero)
                    }
                    setNegativeButton(android.R.string.cancel) { _, _ ->}
                    setCancelable(false) // Impedisce la chiusura tramite tocco esterno o tasto Indietro
                    show()
                }
                requireActivity().onBackPressedDispatcher.onBackPressed()
            }
        }
        return false
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val menuHost: MenuHost = requireActivity()
        menuHost.addMenuProvider(this, viewLifecycleOwner, Lifecycle.State.RESUMED)
        val idSentiero: Int = args.idSentiero
        val swcSegui: Switch = binding.swtchSegui
        viewModel.trovaSentiero(idSentiero).observe(this.viewLifecycleOwner) {
            binding.txNome.text = it.nome
            binding.txDistanza.text = MapUtils.formattastring(it.lunghezza.toInt())
            //binding.txDistanza.text = it.prnLunghezza()
            binding.txDislivello.text = it.prnDislivello()
            binding.txDiscesa.text = it.prnDiscesa()
            binding.tDataInizioText.text = MapUtils.prnDataFromUtc(it.DataOra)
            binding.tDataFineText.text = MapUtils.prnDataFromUtc(it.DataFine)
            binding.tvTempoTot.text = MapUtils.formatSeconds(it.TempoTot.toLong())
            binding.tvTMov.text = MapUtils.formatSeconds(it.TempoInMov.toLong())
            binding.HrMedText.text = it.HrMed.toString()
            binding.HrMaxText.text = it.HrMax.toString()
            binding.tVelMedText.text = DecimalFormat("##.#").format(it.MediaVel)
            //binding.tMediaText.text = DecimalFormat("##.#").format(it.TempMedia)
            //binding.tMaxText.text = DecimalFormat("##.#").format(it.TempMax)
            //binding.tMinText.text = DecimalFormat("##.#").format(it.TempMin)
            // valorizza le variabili che serviranno se viene caricato il percorso
            viewModel.trackDistanza = it.lunghezza.toFloat()
            viewModel.trackAscesa = it.dislivello
            viewModel.trackDiscesa = it.discesa
        }
        mapView = binding.Mapview
        mapController = MapController(mapView)
        mapView.setUseDataConnection(false)

        // DA VERIFICARE cartella Mappa usa quella di default Osmdroid
        if (viewModel.menuMap ==0 )
            apreMappa(viewModel.uriMappa)
        else
            online(viewModel.menuMap)
        //MapUtils.setMapOfflineSource(activity, mapView)
        mapView.setMultiTouchControls(true)
        mapView.minZoomLevel = 9.0
        mapView.maxZoomLevel = 19.0
        mapController!!.setZoom(19.0)
        // carica punti percorso ed eventuali waypoint
        percorso = viewModel.leggiTrack(this, idSentiero, poiDBList)

        if (percorso.actualPoints.isNotEmpty()) {
            // aggiunge marker inizio e fine percorso
            val startMarker = Marker(mapView)
            startMarker.icon = requireContext().let {
                AppCompatResources.getDrawable(
                    it,
                    R.drawable.ic_start
                )
            }
            startMarker.title = "Inizio"
            var punto: GeoPoint = percorso.actualPoints[0]
            startMarker.position = punto
            mapView.overlays?.add(startMarker)
            val endMarker = Marker(mapView)
            endMarker.icon = requireContext().let {
                AppCompatResources.getDrawable(
                    it,
                    R.drawable.ic_finish
                )
            }
            punto = percorso.actualPoints[percorso.actualPoints.size - 1]
            endMarker.position = punto
            endMarker.title = "Fine"
            mapView.overlays?.add(endMarker)
            mapView.overlays.add(percorso)
        }

        // il post serve per la corretta visualizzazione al termine del caricamento
        mapView.post {
            mapView.zoomToBoundingBox(percorso.bounds.increaseByScale(1.2f), false)
        }

        // switch per visualizzazione percorso con frecce direzionali
        val swtchFrecce = binding.swtchFrecce
        swtchFrecce.setOnClickListener {
            if (swtchFrecce.isChecked)
                percorso.usePath(true)
            else
                percorso.usePath(false)
            mapView.invalidate()
        }

        val btnSegui: Button = binding.btnSegui
        btnSegui.setOnClickListener {
            // scrive i punti su polilinea d'appoggio in viewmodel
            viewModel.line.title = binding.txNome.text.toString()
            viewModel.line.setPoints(percorso.actualPoints)
            // carica i waypoint nella viewmodel da visualizzare sulla mappa
            poiDBList.forEach {
                viewModel.wayPoint.add(
                    WayPoint(
                        latitude = it.Latit.toDouble(),
                        longitude = it.Longit.toDouble(),
                        elevation = it.Ele.toDouble(),
                        name = it.NomePOI,
                        description = it.DescrPOI,
                        src = it.UriPath
                    )
                )
            }
            // necessario un thread per la lettura dal db delle foto della traccia
            MainScope().launch(Dispatchers.IO) {
                // aggiunge elenco foto traccia
                viewModel.listaFotoId(idSentiero).forEach {
                    viewModel.fotoList.add(
                        it.uriPath.toUri()
                    )
                }
            }
            // verifica se traccia da seguire e se esiste già una traccia da seguire
            if (swcSegui.isChecked) {
                if (viewModel.tracciaDaSeguire != "") {
                    alertVerificaSegui(requireContext()) { segui ->
                        if (segui) {
                            // resetta tracce con flag segui true
                            viewModel.layerItems.forEach {
                                it.segui = false
                            }
                            // aggiunge traccia con flag segui true alla lista layerItems e imposta alert
                            viewModel.layerItems.add(LayerItem(viewModel.line.title, viewModel.line.isEnabled, false, true,
                                viewModel.trackDistanza, viewModel.trackAscesa, viewModel.trackDiscesa))
                            viewModel.tracciaDaSeguire = viewModel.line.title
                            viewModel.alertFuoriTraccia = true
                            // L'utente ha premuto "Segui"
                            // Esegui le azioni per seguire la traccia
                        } else {
                            // aggiunge traccia con flag segui false alla lista layerItems
                            viewModel.layerItems.add(LayerItem(viewModel.line.title, viewModel.line.isEnabled, false, false,
                                viewModel.trackDistanza, viewModel.trackAscesa, viewModel.trackDiscesa))
                            // L'utente ha premuto "Annulla"
                            // Esegui le azioni per annullare l'operazione
                        }
                    }
                } else {
                    viewModel.layerItems.add(
                        LayerItem(
                            viewModel.line.title,
                            viewModel.line.isEnabled,
                            false,
                            true,
                            viewModel.trackDistanza, viewModel.trackAscesa, viewModel.trackDiscesa
                        )
                    )
                    viewModel.tracciaDaSeguire = viewModel.line.title
                    viewModel.alertFuoriTraccia = true
                }
            } else
                viewModel.layerItems.add(LayerItem(viewModel.line.title, viewModel.line.isEnabled, false, false,
                    viewModel.trackDistanza, viewModel.trackAscesa, viewModel.trackDiscesa))

            findNavController().navigate(R.id.action_schedaFragment_to_mappaFragment)
        }

            val btnAltimetria: Button = binding.btnAltimetria
            btnAltimetria.setOnClickListener {
                val directions =
                    SchedaFragmentDirections.actionSchedaFragmentToAltGrafFragment(idSentiero)
                findNavController().navigate(directions)
            }
        }

    // apertura mappa offline locale da Uri
    private fun apreMappa(uri: Uri) {
        val uriPathHelper = URIPathHelper()
        val filePath = uriPathHelper.getPath(requireContext(), uri)
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
        var theme: XmlRenderTheme?  = null
        if (f.name.contains(".map")) {
            val documentsDir =
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS).absolutePath
            val folderTema = File("$documentsDir/Sentieri/Mappe/4UMaps/4UMaps.xml")
            if (folderTema.exists()) {
                theme = ExternalRenderTheme("$documentsDir/Sentieri/Mappe/4UMaps/4UMaps.xml")
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

        mapView.setTileSource(TileSourceFactory.DEFAULT_TILE_SOURCE)
        viewModel.connessione = false
        mapView.setUseDataConnection(false)
        mapView.invalidate()
    }

    private fun online(mappa: Int) {
        var scarica: MapTileProviderBasic? = null
        viewModel.connessione = true
        // salvo indice menu selezionato
        viewModel.menuMap = mappa
        mapView.setUseDataConnection(true)
        when (mappa) {
            1 -> scarica = MapTileProviderBasic(context, TileSourceFactory.MAPNIK)  // OpenStreetmap
            2 -> scarica = MapTileProviderBasic(context, TileSourceFactory.OpenTopo) // OpenTopo
            3 -> scarica = MappaMapBox() // MapBox
        }
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
        TileSourceFactory.addTileSource(MAPBOXSATELLITELABELLED)
        val bitmapProvider = MapTileProviderBasic(requireContext(), MAPBOXSATELLITELABELLED)
        return bitmapProvider
    }

}