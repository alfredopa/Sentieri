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
    // private var querySearch: String? = null // Non più necessario come variabile di istanza se gestito localmente
    private lateinit var toponimiAdapter: ToponimiRecyclerViewAdapter // Riferimento all'adapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            columnCount = it.getInt(ARG_COLUMN_COUNT)
        }
        creaGeopackage(requireContext())
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View { // Rimosso ? da View se il binding è garantito non nullo qui
        _binding = FragmentToponimiListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Setup RecyclerView
        toponimiAdapter = ToponimiRecyclerViewAdapter(this, mutableListOf()) // Inizia con una lista vuota
        binding.list.apply { // Assumendo che l'ID del RecyclerView nel layout sia 'list'
            layoutManager = when {
                columnCount <= 1 -> LinearLayoutManager(context)
                else -> GridLayoutManager(context, columnCount)
            }
            adapter = toponimiAdapter
        }

        binding.searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                cercaRecord(query)
                binding.searchView.clearFocus() // Nasconde la tastiera
                return true // Evento gestito
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                // Potresti voler implementare una ricerca dinamica qui (con debounce)
                // Oppure lasciare che la ricerca avvenga solo al submit
                if (newText.isNullOrEmpty()) {
                    toponimiAdapter.updateData(emptyList()) // Pulisce i risultati se la query è vuota
                }
                return true // Evento gestito
            }
        })
    }

    private fun creaGeopackage(context: Context) {
        val databaseName = "Toponimi.gpkg"
        // Assicurati che il file esista e sia copiato correttamente dalla cartella assets
        // (come fatto in MainActivity) prima di tentare di aprirlo.
        // Questo frammento assume che il file sia già nel posto giusto.
        val dataDir = context.getDatabasePath(databaseName).parentFile
        val geoPackageFile = File(dataDir, databaseName)

        if (!geoPackageFile.exists()) {
            Log.e("ToponimiFragment", "GeoPackage file does not exist at: ${geoPackageFile.absolutePath}")
            // Mostra un messaggio all'utente o gestisci l'errore
            return
        }

        val geoPackageManager: GeoPackageManager = GeoPackageFactory.getManager(context)
        try {
            openedGeoPackage = geoPackageManager.openExternal(geoPackageFile)
            if (openedGeoPackage == null) {
                Log.e("ToponimiFragment", "Errore durante apertura del GeoPackage: ${geoPackageFile.name}")
                return
            }
            featureDao = openedGeoPackage?.getFeatureDao("Toponimi")
            if (featureDao == null) {
                Log.e("ToponimiFragment", "FeatureDao 'Toponimi' non trovato nel GeoPackage.")
            }
        } catch (e: Exception) {
            Log.e("ToponimiFragment", "Eccezione durante apertura GeoPackage: ${e.message}", e)
        }
    }

    private fun cercaRecord(query: String?) {
        if (featureDao == null) {
            Log.e("ToponimiFragment", "FeatureDao è null, impossibile eseguire la query.")
            toponimiAdapter.updateData(emptyList()) // Pulisce i risultati se DAO non è disponibile
            return
        }
        if (query.isNullOrBlank()) {
            Log.d("ToponimiFragment", "Query è nulla o vuota, pulizia dei risultati.")
            toponimiAdapter.updateData(emptyList())
            return
        }

        val risultatiRicerca = mutableListOf<PlaceholderItem>()
        // Usiamo '?' per un argomento sicuro. La colonna dei toponimi deve chiamarsi 'toponimo'.
        val condizioneWhere = "toponimo LIKE ?"
        val argomentiWhere = arrayOf("%$query%")

        Log.d("ToponimiFragment", "Eseguo query con condizione: $condizioneWhere e argomenti: ${argomentiWhere.joinToString()}")

        try {
            val featureCursor = featureDao!!.query(condizioneWhere, argomentiWhere)
            featureCursor?.use { cursor ->
                val itemCount = cursor.count
                Log.d("ToponimiFragment", "La query ha restituito $itemCount elementi per '$query'.")

                if (cursor.moveToFirst()) {
                    var recordIdCounter = 1 // Per generare un ID progressivo se la PK non è usata
                    do {
                        val featureRow = cursor.row
                        val valoreToponimo = featureRow.getValue("toponimo")?.toString() ?: "N/D"

                        // Ottieni l'ID della chiave primaria, se disponibile, altrimenti usa un contatore
                        val pkColumnName = featureRow.pkColumn.name // pkColumn è deprecato, usa pkColumnName
                        val idValore = if (pkColumnName != null) {
                            featureRow.getValue(pkColumnName).toString()
                        } else {
                            recordIdCounter++.toString()
                        }

                        risultatiRicerca.add(PlaceholderItem( valoreToponimo, "Dettagli per $valoreToponimo"))
                    } while (cursor.moveToNext())
                } else {
                    Log.d("ToponimiFragment", "Nessun risultato trovato per la query: '$query'")
                }
            }
        } catch (e: Exception) {
            Log.e("ToponimiFragment", "Errore durante l'esecuzione della query: ${e.message}", e)
        }
        // Aggiorna l'adapter del RecyclerView con i risultati
        toponimiAdapter.updateData(risultatiRicerca)
    }

    // In ToponimiFragment.kt
    override fun onItemClick(position: Int) {
        val clickedItem = toponimiAdapter.getItem(position) // Usa il nuovo metodo
        if (clickedItem != null) {
            Log.d("ToponimiFragment", "Clicked item: ${clickedItem.content} ")
            // Naviga o esegui altre azioni con i dati dell'item cliccato
            findNavController().navigate(R.id.action_toponimiFragment_to_mappaFragment)
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
        // Non è necessario un newInstance se gli argomenti vengono passati tramite il Navigation Component
        // o se non ci sono argomenti obbligatori per la creazione.
    }
}
