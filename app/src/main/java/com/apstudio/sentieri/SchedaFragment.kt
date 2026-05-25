package com.apstudio.sentieri

import com.google.android.material.dialog.MaterialAlertDialogBuilder
import android.content.ContentValues
import android.graphics.Color
import android.icu.text.DecimalFormat
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.apstudio.sentieri.MapUtils.alertVerificaSegui
import com.apstudio.sentieri.MapUtils.apreMappa
import com.apstudio.sentieri.MapUtils.online
import com.apstudio.sentieri.databinding.FragmentSchedaBinding
import com.apstudio.sentieri.db.LayerItem
import com.apstudio.sentieri.db.PoiDB
import com.apstudio.sentieri.db.Sentieri
import com.apstudio.sentieri.db.SentieriDB
import com.apstudio.sentieri.db.SentieriRepo
import com.apstudio.sentieri.db.prnDiscesa
import com.apstudio.sentieri.db.prnDislivello
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import net.federicomatera.agpxp.GpxWriter
import net.federicomatera.agpxp.models.Gpx
import net.federicomatera.agpxp.models.GpxMetadata
import net.federicomatera.agpxp.models.Link
import net.federicomatera.agpxp.models.Track
import net.federicomatera.agpxp.models.WayPoint
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import java.util.Date

// Fragment che visualizza il dettaglio della traccia selezionata dall'elenco delle tracce
// su una mappa ridotta e principali dati di riepilogo
class SchedaFragment : Fragment(), MenuProvider {

    private val args: SchedaFragmentArgs by navArgs()
    private val viewModel: SentieriViewModel by activityViewModels {
        val application = requireActivity().application
        // 1. Ottieni una singola istanza del database
        val database = SentieriDB.getInstance(application)
        // 2. Crea il repository passando TUTTI i DAO richiesti
        val repository = SentieriRepo(
            sentieriDao = database.sentieriDao(),
            trackDao = database.trackDao(),
            poiDao = database.poiDao(),
            fotoPoiDao = database.fotoPoiDao()
        )
        // 3. Crea la factory con il repository e l'applicazione
        SentieriFactory(repository, application)
    }
    private lateinit var binding: FragmentSchedaBinding
    private lateinit var mapView: MapView
    private var mapController: MapController? = null
    private val poiDBList = mutableListOf<PoiDB>()
    private lateinit var percorso: Polyline
    private var puntiOriginali: List<GeoPoint> = emptyList()

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
                val points = percorso.actualPoints
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
                        longitude = it.Longit,
                        latitude = it.Latit,
                        elevation = it.Ele,
                        name = it.NomePOI,
                        description = it.DescrPOI
                    )
                    poiGpx.add(puntoGps)
                }

                val fileName = "${binding.txNome.text}.gpx"
                var success = false

                try {
                    val resolver = requireContext().contentResolver
                    val contentValues = ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                        put(MediaStore.MediaColumns.MIME_TYPE, "application/gpx+xml")
                        put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                    }

                    val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                    if (uri != null) {
                        resolver.openOutputStream(uri)?.use { outputStream ->
                            scriviGpx.write(gpx, outputStream)
                            success = true
                        }
                    }
                } catch (e: Exception) {
                    Log.e("SchedaFragment", "Errore durante il salvataggio del file GPX", e)
                    success = false
                }

                val message = if (success) {
                    "Scrittura di $fileName eseguita nella cartella Download"
                } else {
                    "Errore durante il salvataggio del file"
                }

                val snackbar = Snackbar.make(binding.root, message,
                    Snackbar.LENGTH_LONG)
                if (success) {
                    snackbar.setBackgroundTint(ContextCompat.getColor(requireContext(), R.color.purple_500))
                    snackbar.setTextColor(Color.WHITE)
                }
                snackbar.show()
            }
            R.id.rinominaSentiero -> {
                val inputEditTextField = android.widget.EditText(requireActivity())
                inputEditTextField.setText(binding.txNome.text)
                
                MaterialAlertDialogBuilder(requireContext(), R.style.AlertDialogCustom)
                    .setTitle("Rinomina percorso")
                    .setMessage("Inserisci il nuovo nome:")
                    .setView(inputEditTextField)
                    .setPositiveButton("Rinomina") { _, _ ->
                        val nuovoNome = inputEditTextField.text.toString()
                        if (nuovoNome.isNotBlank()) {
                            viewModel.rinominaSentiero(args.idSentiero, nuovoNome)
                        }
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
            }
            R.id.eliminaSentiero -> {
                MaterialAlertDialogBuilder(requireContext(), R.style.AlertDialogCustom)
                    .setTitle("Eliminazione percorso")
                    .setMessage("Sei sicuro di voler eliminare il percorso?")
                    .setPositiveButton("Confermo") { _, _ ->
                        viewModel.cancellaSentiero(args.idSentiero)
                        findNavController().popBackStack()
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
            }
        }
        return false
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val menuHost: MenuHost = requireActivity()
        menuHost.addMenuProvider(this, viewLifecycleOwner, Lifecycle.State.RESUMED)
        viewModel.puntiDaSeguire.clear()

        val idSentiero: Int = args.idSentiero
        mapView = binding.Mapview
        mapController = MapController(mapView)
        setupMappaIniziale()
        // configurazione eventi switch e pulsanti
        val swcSegui: MaterialSwitch = binding.swtchSegui
        // switch per visualizzazione percorso con quota o pendenza
        binding.swtchQuota.isChecked = viewModel.mostraPendenza
        binding.swtchQuota.setOnCheckedChangeListener { _, isChecked ->
            viewModel.mostraPendenza = isChecked
            //Log.d("SchedaFragment", "checked aggiornaMappaPercorso")
            aggiornaMappaPercorso()
        }

        val btnSegui: Button = binding.btnSegui
        btnSegui.setOnClickListener {
            // scrive i punti su polilinea d'appoggio in viewmodel
            viewModel.puntiDaSeguire = puntiOriginali.toMutableList()
            viewModel.titoloTracciaDaSeguire = binding.txNome.text.toString()
            viewModel.mostraPendenza = binding.swtchQuota.isChecked
            
            // Se NON è attiva la pendenza, azzeriamo la lista colori affinché la MappaFragment usi l'altitudine
            if (!viewModel.mostraPendenza) {
                viewModel.coloriPuntiDaSeguire = null
            }
            // NOTA: Se mostraPendenza è true, coloriPuntiDaSeguire è già stato correttamente 
            // popolato con le PENDENZE dalla funzione aggiornaMappaPercorso().

            // carica i waypoint nella viewmodel da visualizzare sulla mappa
            poiDBList.forEach {
                viewModel.wayPoint.add(
                    WayPoint(
                        latitude = it.Latit,
                        longitude = it.Longit,
                        elevation = it.Ele,
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
                            viewModel.layerItems.add(
                                LayerItem(
                                    viewModel.titoloTracciaDaSeguire,
                                    true,
                                    direzione = false,
                                    segui = true,
                                    distanza = viewModel.trackDistanza,
                                    ascesa = viewModel.trackAscesa,
                                    discesa = viewModel.trackDiscesa
                                )
                            )
                            viewModel.tracciaDaSeguire = viewModel.titoloTracciaDaSeguire
                            viewModel.alertFuoriTraccia = true
                            // L'utente ha premuto "Segui"
                            // Esegui le azioni per seguire la traccia
                        } else {
                            // aggiunge traccia con flag segui false alla lista layerItems
                            viewModel.layerItems.add(
                                LayerItem(
                                    viewModel.titoloTracciaDaSeguire,
                                    true,
                                    direzione = false,
                                    segui = false,
                                    distanza = viewModel.trackDistanza,
                                    ascesa = viewModel.trackAscesa,
                                    discesa = viewModel.trackDiscesa
                                )
                            )
                            // L'utente ha premuto "Annulla"
                            // Esegui le azioni per annullare l'operazione
                        }
                    }
                } else {
                    viewModel.layerItems.add(
                        LayerItem(
                            viewModel.titoloTracciaDaSeguire,
                            true,
                            direzione = false,
                            segui = true,
                            distanza = viewModel.trackDistanza,
                            ascesa = viewModel.trackAscesa,
                            discesa = viewModel.trackDiscesa
                        )
                    )
                    viewModel.tracciaDaSeguire = viewModel.titoloTracciaDaSeguire
                    viewModel.alertFuoriTraccia = true
                }
            } else
                viewModel.layerItems.add(
                    LayerItem(
                        viewModel.titoloTracciaDaSeguire,
                        true,
                        direzione = false,
                        segui = false,
                        distanza = viewModel.trackDistanza,
                        ascesa = viewModel.trackAscesa,
                        discesa = viewModel.trackDiscesa
                    )
                )

            findNavController().navigate(R.id.action_schedaFragment_to_mappaFragment)
        }

        val btnAltimetria: Button = binding.btnAltimetria
        btnAltimetria.setOnClickListener {
            val directions =
                SchedaFragmentDirections.actionSchedaFragmentToAltGrafFragment(idSentiero)
            findNavController().navigate(directions)
        }

        viewModel.trovaSentiero(idSentiero).observe(this.viewLifecycleOwner) { sentiero ->
            sentiero?.let { aggiornaTestiScheda(it) }
        }

        // carica punti percorso ed eventuali waypoint
        viewLifecycleOwner.lifecycleScope.launch {
            // Chiama la nuova suspend fun. Il codice aspetterà qui finché
            // leggiTrack non avrà finito di caricare TUTTI i punti.
            val percorsoCaricato = viewModel.leggiTrack(idSentiero, poiDBList)
            // Usiamo una nuova variabile locale per chiarezza e sicurezza.
            percorso = percorsoCaricato
            
            // Verifichiamo se ci sono punti, altrimenti la mappa non ha senso
            if (percorsoCaricato.actualPoints.isNotEmpty()) {
                puntiOriginali = percorsoCaricato.actualPoints.map {
                    GeoPoint(
                        it.latitude,
                        it.longitude,
                        it.altitude
                    )
                }

                // Assicuriamoci che la mappa sia pronta prima di zoomare
                percorso.bounds?.let { bounds ->
                    mapView.zoomToBoundingBox(bounds.increaseByScale(1.4f), false)
                }
                aggiornaMappaPercorso()
            }
            //Log.d("SchedaFragment", "chiamato aggiornaMappaPercorso, ${percorso.actualPoints.size}")
        }
    }

    private fun aggiornaTestiScheda(sentiero: Sentieri) {
        binding.txNome.text = sentiero.nome
        binding.txDistanza.text = MapUtils.formattastring(sentiero.lunghezza.toInt())
        //binding.txDistanza.text = it.prnLunghezza()
        binding.txDislivello.text = sentiero.prnDislivello()
        binding.txDiscesa.text = sentiero.prnDiscesa()
        binding.tDataInizioText.text = MapUtils.prnDataFromUtc(sentiero.DataOra)
        binding.tDataFineText.text = MapUtils.prnDataFromUtc(sentiero.DataFine)
        binding.tvTempoTot.text = MapUtils.formatSeconds(sentiero.TempoTot.toLong())
        binding.tvTMov.text = MapUtils.formatSeconds(sentiero.TempoInMov.toLong())
        binding.HrMedText.text = sentiero.HrMed.toString()
        binding.HrMaxText.text = sentiero.HrMax.toString()
        binding.tVelMedText.text = DecimalFormat("##.#").format(sentiero.MediaVel)
        //binding.tMediaText.text = DecimalFormat("##.#").format(it.TempMedia)
        //binding.tMaxText.text = DecimalFormat("##.#").format(it.TempMax)
        //binding.tMinText.text = DecimalFormat("##.#").format(it.TempMin)
        // valorizza le variabili che serviranno se viene caricato il percorso
        viewModel.trackDistanza = sentiero.lunghezza.toFloat()
        viewModel.trackAscesa = sentiero.dislivello
        viewModel.trackDiscesa = sentiero.discesa
    }

    private fun setupMappaIniziale() {
        if (viewModel.menuMap == 0)
            apreMappa(requireContext(), mapView, viewModel, viewModel.uriMappa)
        else
            online(requireContext(), mapView, viewModel, viewModel.menuMap)
        
        mapView.setMultiTouchControls(true)
        mapView.minZoomLevel = 4.0 // Ridotto per essere sicuri di vedere qualcosa
        mapView.maxZoomLevel = 22.0
        mapController?.setZoom(15.0) // Valore iniziale sensato
    }

    private fun aggiornaMappaPercorso() {
        if (!::percorso.isInitialized || puntiOriginali.isEmpty()) return
        // 1. Pulizia Overlay Mappa
        mapView.overlays.clear()
        // 2. Pulizia TOTALE della Polyline
        percorso.outlinePaintLists.clear() // Rimuove tutti i colori precedenti
        percorso.setMilestoneManagers(ArrayList()) // Rimuove le frecce (se presenti)
        viewModel.coloriPuntiDaSeguire = null
        // Disegna bordo per tutti i punti percorso
        MapUtils.disegnaLineaSfondo(percorso)
        mapView.overlays.add(percorso)
        if (viewModel.mostraPendenza) {
            val pendenze = MapUtils.calcolaPendenzeSmussate(percorso, 8)

            val percorsoFrecce = Polyline(mapView).apply {
                setPoints(percorso.actualPoints)
                isVisible = true
            }
            // SALVA LE PENDENZE NEL VIEWMODEL
            viewModel.coloriPuntiDaSeguire = pendenze.toMutableList()
            MapUtils.disegnaPercorsoColorato(percorsoFrecce, pendenze)
            mapView.overlays.add(percorsoFrecce)
        } else {
            // USA LA NUOVA FUNZIONE UNIFICATA (senza secondo parametro per l'altitudine)
            MapUtils.disegnaPercorsoColorato(percorso)
            mapView.overlays.add(percorso)
            viewModel.coloriPuntiDaSeguire = null
        }
        // 3. Riaggiungi i Marker (altrimenti spariscono col clear)
        aggiungiMarkerInizioFine()
        mapView.invalidate()
    }

    private fun aggiungiMarkerInizioFine() {
        // aggiunge marker inizio e fine percorso
        val startMarker = Marker(mapView)
        context?.let { startMarker.icon = AppCompatResources.getDrawable(it, R.drawable.ic_start) }
        startMarker.title = "Inizio"
        var punto = percorso.actualPoints[0]
        startMarker.position = punto
        mapView.overlays?.add(startMarker)
        val endMarker = Marker(mapView)
        context?.let { endMarker.icon = AppCompatResources.getDrawable(it, R.drawable.ic_finish) }
        punto = percorso.actualPoints[percorso.actualPoints.size - 1]
        endMarker.position = punto
        endMarker.title = "Fine"
        mapView.overlays?.add(endMarker)
    }

}
