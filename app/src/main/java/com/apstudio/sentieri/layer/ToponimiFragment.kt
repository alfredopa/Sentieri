package com.apstudio.sentieri.layer

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.SearchView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.apstudio.sentieri.R
import com.apstudio.sentieri.SentieriFactory
import com.apstudio.sentieri.SentieriViewModel
import com.apstudio.sentieri.databinding.FragmentToponimiListBinding
import com.apstudio.sentieri.db.SentieriDB
import com.apstudio.sentieri.db.SentieriRepo
import com.apstudio.sentieri.db.TopoMarkerData
import com.apstudio.sentieri.layer.placeholder.PlaceholderContent.PlaceholderItem
import mil.nga.geopackage.GeoPackage
import mil.nga.geopackage.GeoPackageFactory
import mil.nga.geopackage.GeoPackageManager
import mil.nga.geopackage.features.user.FeatureDao
import mil.nga.sf.Point
import java.io.File

/**
 * A fragment representing a list of Items.
 */
class ToponimiFragment : Fragment(), ToponimiRecyclerViewAdapter.OnItemClickListener {
    private var columnCount = 1
    private var _binding: FragmentToponimiListBinding? = null
    private val binding get() = _binding!!
    private var openedGeoPackage: GeoPackage? = null
    private var featureDao: FeatureDao? = null
    private lateinit var toponimiAdapter: ToponimiRecyclerViewAdapter
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

    // Variabile per memorizzare l'ultima query
    private var lastQuery: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            columnCount = it.getInt(ARG_COLUMN_COUNT)
        }
        savedInstanceState?.getString("last_query")?.let { lastQuery = it }
    }


    // Se vuoi una persistenza più robusta (es. attraverso la distruzione del processo)
    // dovresti salvare e ripristinare lastQuery in onSaveInstanceState.
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (!lastQuery.isNullOrBlank()) {
            outState.putString("last_query", lastQuery)
        }
    }


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentToponimiListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        creaGeopackage(requireContext())
        toponimiAdapter = ToponimiRecyclerViewAdapter(this, mutableListOf())
        binding.list.apply {
            layoutManager = when {
                columnCount <= 1 -> LinearLayoutManager(context)
                else -> GridLayoutManager(context, columnCount)
            }
            adapter = toponimiAdapter
            if (!lastQuery.isNullOrBlank()) {
                cercaRecord(lastQuery)
            }
        }

        binding.searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                lastQuery = query // Salva l'ultima query
                cercaRecord(query)
                binding.searchView.clearFocus()
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                if (newText.isNullOrEmpty() && !binding.searchView.isIconified) {
                    // Se l'utente cancella manualmente il testo, pulisci i risultati e lastQuery
                    lastQuery = null
                    toponimiAdapter.updateData(emptyList())
                }
                return true
            }
        })

        // Ripristina l'ultima ricerca se presente
        if (!lastQuery.isNullOrBlank()) {
            binding.searchView.setQuery(lastQuery, false)
            cercaRecord(lastQuery)
        }
    }

    private fun creaGeopackage(context: Context) {
         if (openedGeoPackage != null && featureDao != null) {
             return
         }

        val databaseName = "Toponimi.gpkg"
        val dataDir = context.getDatabasePath(databaseName).parentFile
        val geoPackageFile = File(dataDir, databaseName)

        if (!geoPackageFile.exists()) {
            return
        }

        val geoPackageManager: GeoPackageManager = GeoPackageFactory.getManager(context)
        try {
            openedGeoPackage = geoPackageManager.openExternal(geoPackageFile)
            if (openedGeoPackage == null) {
                return
            }
            featureDao = openedGeoPackage?.getFeatureDao("Toponimi")
        } catch (e: Exception) {
            Log.e("ToponimiFragment", "Eccezione durante apertura GeoPackage: ${e.message}", e)
        }
    }

    private fun cercaRecord(query: String?) {
        if (featureDao == null) {
            toponimiAdapter.updateData(emptyList())
            return
        }
        if (query.isNullOrBlank()) {
            toponimiAdapter.updateData(emptyList())
            return
        }

        lastQuery = query
        if (binding.searchView.query.toString() != query) {
             binding.searchView.setQuery(query, false)
        }

        val risultatiRicerca = mutableListOf<PlaceholderItem>()
        val condizioneWhere = "toponimo LIKE ?"
        val argomentiWhere = arrayOf("%$query%")

        try {
            val featureCursor = featureDao!!.query(condizioneWhere, argomentiWhere)
            featureCursor?.use { cursor ->
                if (cursor.moveToFirst()) {
                    do {
                        val featureRow = cursor.row
                        val valoreToponimo = featureRow.getValue("toponimo")?.toString() ?: "N/D"
                        var lat: Double? = null
                        var lon: Double? = null
                        val geometryData = featureRow.geometry
                        if (geometryData != null && !geometryData.isEmpty) {
                            val geometry = geometryData.geometry
                            if (geometry is Point) {
                                lon = geometry.x
                                lat = geometry.y
                            }
                        }
                        risultatiRicerca.add(PlaceholderItem(valoreToponimo, "Dettagli per $valoreToponimo", lat, lon))
                    } while (cursor.moveToNext())
                }
            }
        } catch (e: Exception) {
            Log.e("ToponimiFragment", "Errore durante l'esecuzione della query: ${e.message}", e)
        }
        toponimiAdapter.updateData(risultatiRicerca)
    }

    override fun onItemClick(position: Int) {
        val clickedItem = toponimiAdapter.getItem(position)
        if (clickedItem != null) {
            if (clickedItem.latitude != null && clickedItem.longitude != null) {
                // Create TopoMarkerData and add to ViewModel
                val toponimoData = TopoMarkerData(
                    name = clickedItem.content,
                    latitude = clickedItem.latitude,
                    longitude = clickedItem.longitude
                )
                // Add to a list in ViewModel, ensuring no duplicates if needed by ID (if TopoMarkerData had a unique ID from source)
                if (!viewModel.toponimiSelezionati.any { it.name == toponimoData.name && it.latitude == toponimoData.latitude && it.longitude == toponimoData.longitude }) {
                    viewModel.toponimiSelezionati.add(toponimoData)
                }

                val bundle = Bundle().apply {
                    putDouble("latitude", clickedItem.latitude)
                    putDouble("longitude", clickedItem.longitude)
                    putString("toponimo_name", clickedItem.content)
                }

                findNavController().navigate(R.id.action_toponimiFragment_to_mappaFragment, bundle)
            } else {
                Log.w("ToponimiFragment", "Coordinate non disponibili per ${clickedItem.content}. Navigazione standard.")
                findNavController().navigate(R.id.action_toponimiFragment_to_mappaFragment) // Or show a toast
            }
        } else {
            Log.e("ToponimiFragment", "Invalid position or item not found in onItemClick: $position")
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        try {
            openedGeoPackage?.close()
        } catch (e: Exception) {
            Log.e("ToponimiFragment", "Errore chiusura GeoPackage: ${e.message}", e)
        }
        openedGeoPackage = null
        featureDao = null
    }

    companion object {
        const val ARG_COLUMN_COUNT = "column-count"
    }
}