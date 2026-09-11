package com.apstudio.sentieri

import android.annotation.SuppressLint
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.apstudio.sentieri.db.OnItemClickListener
import com.apstudio.sentieri.db.SentieriDB
import com.apstudio.sentieri.db.SentieriRepo
import net.federicomatera.agpxp.models.WayPoint
import org.osmdroid.util.GeoPoint
import java.io.File
import java.util.Locale


class PoiFragment : Fragment() {
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
    private lateinit var recyclerPoi: RecyclerView

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        // Inflate the layout for this fragment
        val view = inflater.inflate(R.layout.fragment_poi, container, false)

        // Colleziona tutte le foto dai layer abilitati e dalla lista globale, filtrando quelle vuote
        val allPhotos = mutableListOf<Uri>()
        viewModel.layerItems.forEach { item ->
            if (item.abilitato) {
                allPhotos.addAll(item.fotos.filter { it.toString().isNotBlank() })
            }
        }
        allPhotos.addAll(viewModel.fotoList.filter { it.toString().isNotBlank() })
        
        if (allPhotos.isNotEmpty()) {
            // Rimuovi duplicati basandosi sul nome del file e verifica che il file esista effettivamente
            val uniquePhotos = allPhotos.distinctBy { uri -> 
                MapUtils.getFileNameFromUri(requireContext(), uri)
            }.filter { uri -> 
                val fileName = MapUtils.getFileNameFromUri(requireContext(), uri)
                if (fileName.isBlank()) return@filter false
                
                // Se è un URI content://, proviamo ad aprire uno stream per verificare l'esistenza
                if (uri.scheme == "content") {
                    try {
                        requireContext().contentResolver.openAssetFileDescriptor(uri, "r")?.use { true } ?: false
                    } catch (e: Exception) {
                        false
                    }
                } else {
                    // Se è un percorso file, verifichiamo con java.io.File
                    File(uri.path ?: "").exists()
                }
            }
            
            if (uniquePhotos.isNotEmpty()) {
                // Create a recyclerPhoto  object and set the adapter
                val recyclerPhoto = view.findViewById<RecyclerView>(R.id.rv_photo)
                recyclerPhoto.layoutManager =
                    LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
                val fotoAdapter = ImageAdapter(uniquePhotos.toMutableList())
                recyclerPhoto.adapter = fotoAdapter
                fotoAdapter.setOnItemClickListener(object : OnItemClickListener {
                        override fun onItemClick(position: Int) {
                            val uri = uniquePhotos[position]
                            val directions = PoiFragmentDirections.actionPoiFragmentToCameraFragment(uri.toString())
                            findNavController().navigate(directions)
                        }
                })
            }
        }

//       imposta recyclerPoi per i Waypoint
        recyclerPoi = view.findViewById(R.id.rv_poi)
        recyclerPoi.layoutManager =
            LinearLayoutManager(requireContext(), LinearLayoutManager.VERTICAL, false)
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // Chiama displayPoiList, che ora costruirà la lista combinata
        // e gestirà il caso in cui la lista combinata sia vuota.
        displayPoiList()
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun displayPoiList() {
        val waypointsToShow = mutableListOf<WayPoint>()
        // Set per tracciare i waypoint già aggiunti basandosi su coordinate e nome normalizzato
        val seenKeys = mutableSetOf<String>()

        fun addIfUnique(wp: WayPoint) {
            // Arrotondiamo a 6 decimali per evitare duplicati da imprecisioni floating point
            val latStr = String.format(Locale.US, "%.6f", wp.latitude)
            val lonStr = String.format(Locale.US, "%.6f", wp.longitude)
            
            // Normalizziamo il nome: se è nullo, vuoto o il default "WayPoint", usiamo una stringa vuota nella chiave
            val name = wp.name?.trim() ?: ""
            val normalizedName = if (name.isEmpty() || name.equals("WayPoint", ignoreCase = true)) "" else name
            
            val key = "${normalizedName}_${latStr}_${lonStr}"
            if (seenKeys.add(key)) {
                waypointsToShow.add(wp)
            }
        }

        // 1. Aggiungi i waypoint dai layer abilitati nel ViewModel
        viewModel.layerItems.forEach { item ->
            if (item.abilitato) {
                item.waypoints.forEach { addIfUnique(it) }
            }
        }

        // 2. Aggiungi i waypoint preesistenti globali (es. caricati da file)
        viewModel.wayPoint.forEach { addIfUnique(it) }

        // 3. Aggiungi i waypoint da viewModel.poiDBList (sessione corrente)
        viewModel.poiDBList.forEach { poiFromDb ->
            addIfUnique(WayPoint(
                latitude = poiFromDb.Latit,
                longitude = poiFromDb.Longit,
                elevation = poiFromDb.Ele,
                name = poiFromDb.NomePOI,
                description = poiFromDb.DescrPOI,
                src = poiFromDb.UriPath
            ))
        }

        if (waypointsToShow.isEmpty()) {
            val toast = Toast.makeText(requireActivity(), "Nessun waypoint da visualizzare!", Toast.LENGTH_LONG)
            toast.show()
            toast.view?.setBackgroundColor(ContextCompat.getColor(requireActivity(), R.color.purple_500))
            recyclerPoi.adapter = null // Pulisci l'adapter
            return
        }

        val adapter = PoiAdapter(waypointsToShow) // Usa la lista combinata
        recyclerPoi.adapter = adapter
        adapter.setOnItemClickListener(object : OnItemClickListener {
            override fun onItemClick(position: Int) {
                if (position < 0 || position >= waypointsToShow.size) return // Controllo di sicurezza
                val clickedWayPoint = waypointsToShow[position]
                val altitudine = clickedWayPoint.elevation?: 0.0 // Gestisci elevation nullabile
                val destPoi = GeoPoint(
                    clickedWayPoint.latitude,
                    clickedWayPoint.longitude,
                    altitudine
                )

                viewModel.poi = destPoi // viewModel.poi è un GeoPoint, quindi questo è corretto
                val directions = PoiFragmentDirections.actionPoiFragmentToMappaFragment()
                findNavController().navigate(directions)
            }
        })
        // Non è necessario adapter.notifyDataSetChanged() qui perché l'adapter viene sempre ricreato.
    }

}
