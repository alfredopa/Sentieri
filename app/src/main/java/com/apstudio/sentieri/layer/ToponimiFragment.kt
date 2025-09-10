package com.apstudio.sentieri.layer

import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.SearchView
import com.apstudio.sentieri.databinding.FragmentToponimiListBinding
import com.apstudio.sentieri.layer.placeholder.PlaceholderContent
import mil.nga.geopackage.GeoPackageFactory
import mil.nga.geopackage.GeoPackageManager
import java.io.File

/**
 * A fragment representing a list of Items.
 */
class ToponimiFragment : Fragment() {
    private var columnCount = 1
    private var _binding: FragmentToponimiListBinding? = null
    private val binding get() = _binding!!

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
    ): View? {
        _binding = FragmentToponimiListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (view is RecyclerView) {
            with(view) {
                layoutManager = when {
                    columnCount <= 1 -> LinearLayoutManager(context)
                    else -> GridLayoutManager(context, columnCount)
                }
                adapter = ToponimiRecyclerViewAdapter(PlaceholderContent.ITEMS)
            }
        }
        binding.searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                // Azione quando l'utente preme "cerca" sulla tastiera
                // filterList(query)
                return false // true se l'evento è stato gestito qui
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                // Azione mentre l'utente digita
                // filterList(newText)
                // adapter.filter.filter(newText) // Se il tuo adapter implementa Filterable
                return true // true se l'evento è stato gestito qui
            }
        })
    }

    fun creaGeopackage(context: Context) {
        // Crea e gestisce il GeoPackage.
        // il geopackage non viene importato ma letto dalla cartella dati interni data/data
        val databaseName = "Toponimi.gpkg"
        val dataDir = context.getDatabasePath(databaseName).parentFile
        val geoPackageFile = File(dataDir, databaseName)
        val geoPackageManager: GeoPackageManager = GeoPackageFactory.getManager(context)
        val openedGeoPackage = geoPackageManager.openExternal(geoPackageFile) // Non assegnare direttamente a layerModel.DATABASE_NAME

        if (openedGeoPackage == null) {
            Log.d("toponimiFragment", "Errore durante apertura del GeoPackage")
            // Gestisci errore e return
            return
        }

    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
    companion object {

        // TODO: Customize parameter argument names
        const val ARG_COLUMN_COUNT = "column-count"

    }
}