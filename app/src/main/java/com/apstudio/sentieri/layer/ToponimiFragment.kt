package com.apstudio.sentieri.layer

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.SearchView
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.apstudio.sentieri.R
import com.apstudio.sentieri.databinding.FragmentToponimiListBinding
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

    // Variabile per memorizzare l'ultima query
    private var lastQuery: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            columnCount = it.getInt(ARG_COLUMN_COUNT)
        }
        // Potremmo voler ripristinare lastQuery da savedInstanceState qui se necessario
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
            // Imposta il testo nella SearchView senza sottomettere la query
            // (onQueryTextSubmit verrà chiamato dal submit manuale o da cercaRecord)
            binding.searchView.setQuery(lastQuery, false)
            // Esegui la ricerca
            cercaRecord(lastQuery)
        }
    }

    private fun creaGeopackage(context: Context) {
         if (openedGeoPackage != null && featureDao != null) {
             //Log.d("ToponimiFragment", "GeoPackage already open.")
             return
         }

        val databaseName = "Toponimi.gpkg"
        val dataDir = context.getDatabasePath(databaseName).parentFile
        val geoPackageFile = File(dataDir, databaseName)

        if (!geoPackageFile.exists()) {
            //Log.e("ToponimiFragment", "GeoPackage file does not exist at: ${geoPackageFile.absolutePath}")
            return
        }

        val geoPackageManager: GeoPackageManager = GeoPackageFactory.getManager(context)
        try {
            openedGeoPackage = geoPackageManager.openExternal(geoPackageFile)
            if (openedGeoPackage == null) {
                //Log.e("ToponimiFragment", "Errore durante apertura del GeoPackage: ${geoPackageFile.name}")
                return
            }
            featureDao = openedGeoPackage?.getFeatureDao("Toponimi")
            if (featureDao == null) {
                //Log.e("ToponimiFragment", "FeatureDao 'Toponimi' non trovato nel GeoPackage.")
            }
        } catch (e: Exception) {
            //Log.e("ToponimiFragment", "Eccezione durante apertura GeoPackage: ${e.message}", e)
        }
    }

    private fun cercaRecord(query: String?) {
        if (featureDao == null) {
            //Log.e("ToponimiFragment", "FeatureDao è null, impossibile eseguire la query.")
            toponimiAdapter.updateData(emptyList())
            return
        }
        // Aggiorna lastQuery anche qui, nel caso cercaRecord sia chiamato da altrove
        // o per consistenza, anche se onQueryTextSubmit dovrebbe già farlo.
        // Se query è null/blank da qui, e lastQuery non lo era, dovremmo pulire lastQuery?
        // Per ora, assumiamo che se cercaRecord è chiamato con null/blank, è intenzionale pulire.
        if (query.isNullOrBlank()) {
            //Log.d("ToponimiFragment", "Query è nulla o vuota, pulizia dei risultati.")
            // lastQuery = null // Considera se vuoi pulire lastQuery anche qui
            toponimiAdapter.updateData(emptyList())
            return
        }

        // Se siamo qui, la query non è blank, quindi la consideriamo la "lastQuery" valida
        lastQuery = query
        // E aggiorniamo anche la SearchView nel caso questa chiamata non origini dalla SearchView stessa
        // ma solo se il testo attuale della SearchView è diverso, per evitare loop o comportamenti imprevisti.
        if (binding.searchView.query.toString() != query) {
             binding.searchView.setQuery(query, false)
        }


        val risultatiRicerca = mutableListOf<PlaceholderItem>()
        val condizioneWhere = "toponimo LIKE ?"
        val argomentiWhere = arrayOf("%$query%")

        //Log.d("ToponimiFragment", "Eseguo query con condizione: $condizioneWhere e argomenti: ${argomentiWhere.joinToString()}")

        try {
            val featureCursor = featureDao!!.query(condizioneWhere, argomentiWhere)
            featureCursor?.use { cursor ->
                val itemCount = cursor.count
                //Log.d("ToponimiFragment", "La query ha restituito $itemCount elementi per '$query'.")

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
                                //Log.d("ToponimiFragment", "Toponimo: $valoreToponimo, Lat: $lat, Lon: $lon")
                            } else {
                                //Log.w("ToponimiFragment", "Geometria per $valoreToponimo non è un punto: ${geometry?.geometryType}")
                            }
                        } else {
                            //Log.w("ToponimiFragment", "Dati geometrici mancanti per $valoreToponimo")
                        }
                        risultatiRicerca.add(PlaceholderItem(valoreToponimo, "Dettagli per $valoreToponimo", lat, lon))
                    } while (cursor.moveToNext())
                } else {
                    //Log.d("ToponimiFragment", "Nessun risultato trovato per la query: '$query'")
                }
            }
        } catch (e: Exception) {
            //Log.e("ToponimiFragment", "Errore durante l'esecuzione della query: ${e.message}", e)
        }
        toponimiAdapter.updateData(risultatiRicerca)
    }

    override fun onItemClick(position: Int) {
        val clickedItem = toponimiAdapter.getItem(position)
        if (clickedItem != null) {
            //Log.d("ToponimiFragment", "Clicked item: ${clickedItem.content}, Lat: ${clickedItem.latitude}, Lon: ${clickedItem.longitude}")
            if (clickedItem.latitude != null && clickedItem.longitude != null) {
                val bundle = Bundle().apply {
                    putDouble("latitude", clickedItem.latitude)
                    putDouble("longitude", clickedItem.longitude)
                    putString("toponimo_name", clickedItem.content)
                }
                findNavController().navigate(R.id.action_toponimiFragment_to_mappaFragment, bundle)
            } else {
                //Log.w("ToponimiFragment", "Coordinate non disponibili per ${clickedItem.content}. Navigazione standard.")
                findNavController().navigate(R.id.action_toponimiFragment_to_mappaFragment)
            }
        } else {
            //Log.e("ToponimiFragment", "Invalid position or item not found in onItemClick: $position")
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        onSaveInstanceState(Bundle())
        // Non resettare lastQuery qui, altrimenti non verrà ripristinato al ritorno.
        // Verrà resettato solo se l'utente cancella la query o ne inizia una nuova.
        _binding = null
        // La chiusura del GeoPackage e il null-check di featureDao sono corretti qui
        try {
            openedGeoPackage?.close()
        } catch (e: Exception) {
            //Log.e("ToponimiFragment", "Errore chiusura GeoPackage: ${e.message}", e)
        }
        openedGeoPackage = null
        featureDao = null
    }

    companion object {
        const val ARG_COLUMN_COUNT = "column-count"
    }
}