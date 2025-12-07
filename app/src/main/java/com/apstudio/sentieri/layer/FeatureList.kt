package com.apstudio.sentieri.layer

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStoreOwner
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.apstudio.sentieri.R
import com.apstudio.sentieri.db.FieldSchemaInfo
import mil.nga.geopackage.features.user.FeatureCursor
import mil.nga.geopackage.features.user.FeatureDao
import mil.nga.sf.Point

// MODIFICATO: Aggiunta implementazione per MenuProvider
class FeatureList : Fragment(), MenuProvider {
    private val layerModel: LayerViewModel by lazy {
        ViewModelProvider(requireActivity().application as ViewModelStoreOwner)[LayerViewModel::class.java]
    }
    private lateinit var recyclerView: RecyclerView
    private var featureCursor: FeatureCursor? = null

    // Memorizza i dati necessari per la ricerca e il caricamento
    private var currentLayerName: String? = null
    private var featureDao: FeatureDao? = null
    private var fieldConfig: List<FieldSchemaInfo>? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_feature_list_list, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        recyclerView = view.findViewById(R.id.list)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())

        // NUOVO: Aggiunge il provider del menu alla Action Bar
        val menuHost: MenuHost = requireActivity()
        menuHost.addMenuProvider(this, viewLifecycleOwner, Lifecycle.State.RESUMED)

        setup()
    }

    // NUOVA FUNZIONE: Imposta le variabili iniziali e carica i dati
    private fun setup() {
        currentLayerName = layerModel.currentActiveTableName
        if (currentLayerName == null) {
            Toast.makeText(requireContext(), "Layer attivo non specificato.", Toast.LENGTH_LONG).show()
            return
        }

        val geoPackage = layerModel.geoPackageInstance
        if (geoPackage == null) {
            Toast.makeText(requireContext(), "GeoPackage non disponibile.", Toast.LENGTH_LONG).show()
            return
        }

        featureDao = geoPackage.getFeatureDao(currentLayerName)
        if (featureDao == null) {
            Toast.makeText(requireContext(), "DAO non trovato per il layer: $currentLayerName", Toast.LENGTH_LONG).show()
            return
        }

        fieldConfig = layerModel.labelConfig[currentLayerName]
        if (fieldConfig == null) {
            Toast.makeText(requireContext(), "Configurazione campi non trovata.", Toast.LENGTH_LONG).show()
            return
        }

        // Carica i dati iniziali (senza filtro)
        loadFeatures()
    }

    // NUOVA FUNZIONE (refactoring): Carica le feature con filtri opzionali
    private fun loadFeatures(whereClause: String? = null, selectionArgs: Array<String>? = null) {
        if (featureDao == null || fieldConfig == null) {
            Log.e("FeatureList", "DAO o Config non inizializzati, impossibile caricare i dati.")
            recyclerView.adapter = null
            return
        }

        val featuresDataForAdapter = mutableListOf<Map<String, Any?>>()
        val extrasMap = mutableMapOf<String, Pair<String, String>>()

        try {
            // Esegue la query (filtrata o completa)
            featureCursor = featureDao!!.query(whereClause, selectionArgs, null, null, null)

            featureCursor?.use { cursor ->
                if (cursor.moveToFirst()) {
                    do {
                        val row = cursor.row
                        val currentFeatureMap = mutableMapOf<String, Any?>()
                        row.columnNames.forEachIndexed { index, name ->
                            currentFeatureMap[name] = row.values[index]
                        }
                        featuresDataForAdapter.add(currentFeatureMap)

                        // Estrai dati per la mappa ausiliaria (per il click)
                        val geometry = row.geometry?.geometry
                        if (geometry != null) {
                            val centroid = geometry.centroid
                            if (centroid is Point) {
                                val lat = centroid.y
                                val lon = centroid.x
                                val key = "$lat:$lon"
                                val uniqueId = "${currentLayerName}_${row.id}"
                                val label = layerModel.creaLabel(row, currentLayerName!!)
                                extrasMap[key] = Pair(uniqueId, label)

                                // Popola anche i campi richiesti dall'adapter per la gestione del click
                                currentFeatureMap[DynamicFeatureAdapter.KEY_INTERNAL_LATITUDE] = lat
                                currentFeatureMap[DynamicFeatureAdapter.KEY_INTERNAL_LONGITUDE] = lon
                            }
                        }
                    } while (cursor.moveToNext())
                }
            }
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Errore durante la lettura dei dati: ${e.message}", Toast.LENGTH_LONG).show()
            recyclerView.adapter = null
            return
        } finally {
            featureCursor?.close()
            featureCursor = null
        }

        if (featuresDataForAdapter.isEmpty()) {
            Toast.makeText(requireContext(), "Nessun dato trovato.", Toast.LENGTH_SHORT).show()
        }

        // Configura l'adapter con i dati (filtrati o completi)
        val adapter = DynamicFeatureAdapter(featuresDataForAdapter, fieldConfig!!)
        adapter.setOnItemClickListener(object : DynamicFeatureAdapter.OnItemClickListener {
            override fun onItemClicked(latitude: Double, longitude: Double, elevation: Double?) {
                // Comunica le coordinate al ViewModel condiviso
                layerModel.requestNavigationToPoint(latitude, longitude)
                findNavController().popBackStack()
            }
        })
        recyclerView.adapter = adapter
    }

    // NUOVA FUNZIONE: Mostra il dialogo di ricerca dinamico
    private fun showSearchDialog() {
        if (fieldConfig == null) {
            Toast.makeText(requireContext(), "Configurazione non disponibile per la ricerca.", Toast.LENGTH_SHORT).show()
            return
        }

        val dialogView = ScrollView(requireContext())
        val layout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            val padding = (16 * resources.displayMetrics.density).toInt()
            setPadding(padding, padding, padding, padding)
        }
        dialogView.addView(layout)

        val fieldsToSearch = fieldConfig!!.filter { it.isVisible }
        val editTextMap = mutableMapOf<String, EditText>()

        fieldsToSearch.forEach { field ->
            val editText = EditText(requireContext()).apply {
                hint = field.description // Usa la descrizione come hint (più leggibile)
            }
            layout.addView(editText)
            editTextMap[field.name] = editText
        }

        AlertDialog.Builder(requireContext())
            .setTitle("Filtra Dati")
            .setView(dialogView)
            .setPositiveButton("Cerca") { _, _ ->
                val whereClauseParts = mutableListOf<String>()
                val selectionArgs = mutableListOf<String>()

                editTextMap.forEach { (fieldName, editText) ->
                    val searchText = editText.text.toString().trim()
                    if (searchText.isNotEmpty()) {
                        // Usa LIKE per ricerche parziali e previene SQL Injection
                        whereClauseParts.add("$fieldName LIKE ?")
                        selectionArgs.add("%$searchText%")
                    }
                }

                if (whereClauseParts.isNotEmpty()) {
                    val finalWhereClause = whereClauseParts.joinToString(" AND ")
                    loadFeatures(finalWhereClause, selectionArgs.toTypedArray())
                } else {
                    Toast.makeText(requireContext(), "Nessun criterio inserito.", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Annulla", null)
            .setNeutralButton("Reset") { _, _ ->
                // Ricarica tutti i dati senza filtri
                loadFeatures()
            }
            .show()
    }

    // --- Implementazione MenuProvider ---
    override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
        menuInflater.inflate(R.menu.feature_list_menu, menu)
    }

    override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
        return when (menuItem.itemId) {
            R.id.action_search -> {
                showSearchDialog()
                true
            }
            else -> false
        }
    }
    // --- Fine Implementazione ---


    override fun onDestroyView() {
        super.onDestroyView()
        featureCursor?.close()
        featureCursor = null
        if (::recyclerView.isInitialized) {
            recyclerView.adapter = null
        }
    }
}