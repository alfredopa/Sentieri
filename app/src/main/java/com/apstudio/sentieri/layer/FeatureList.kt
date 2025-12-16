package com.apstudio.sentieri.layer

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Button
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

class FeatureList : Fragment(), MenuProvider {
    private val layerModel: LayerViewModel by lazy {
        ViewModelProvider(requireActivity().application as ViewModelStoreOwner)[LayerViewModel::class.java]
    }
    private lateinit var recyclerView: RecyclerView
    private var featureCursor: FeatureCursor? = null

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

        val menuHost: MenuHost = requireActivity()
        menuHost.addMenuProvider(this, viewLifecycleOwner, Lifecycle.State.RESUMED)

        setup()
    }

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

        loadFeatures()
    }

    private fun loadFeatures(whereClause: String? = null, selectionArgs: Array<String>? = null) {
        if (featureDao == null || fieldConfig == null) {
            Log.e("FeatureList", "DAO o Config non inizializzati, impossibile caricare i dati.")
            recyclerView.adapter = null
            return
        }

        val featuresDataForAdapter = mutableListOf<Map<String, Any?>>()

        try {
            featureCursor = featureDao!!.query(whereClause, selectionArgs, null, null, null)
            featureCursor?.use { cursor ->
                while (cursor.moveToNext()) {
                    val row = cursor.row
                    val currentFeatureMap = mutableMapOf<String, Any?>()
                    row.columnNames.forEachIndexed { index, name ->
                        currentFeatureMap[name] = row.values[index]
                    }

                    val geometry = row.geometry?.geometry
                    if (geometry != null) {
                        val centroid = geometry.centroid
                        if (centroid is Point) {
                            currentFeatureMap[DynamicFeatureAdapter.KEY_INTERNAL_LATITUDE] = centroid.y
                            currentFeatureMap[DynamicFeatureAdapter.KEY_INTERNAL_LONGITUDE] = centroid.x
                        }
                    }
                    featuresDataForAdapter.add(currentFeatureMap)
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

        val showDetailsButton = currentLayerName == "Sentieri CAI"

        val adapter = DynamicFeatureAdapter(featuresDataForAdapter, fieldConfig!!, showDetailsButton)
        adapter.setOnItemClickListener(object : DynamicFeatureAdapter.OnItemClickListener {
            override fun onItemClicked(latitude: Double, longitude: Double, elevation: Double?) {
                layerModel.requestNavigationToPoint(latitude, longitude)
                findNavController().popBackStack()
            }

            override fun onDetailsButtonClicked(itemData: Map<String, Any?>) {
                // 1. Estrai l'URL dal campo 'website'.
                val url = itemData["website"] as? String

                // 2. Controlla se l'URL è valido e non vuoto.
                if (!url.isNullOrBlank()) {
                    try {
                        // 3. Crea un Intent per aprire l'URL nel browser.
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                        startActivity(intent)
                    } catch (e: Exception) {
                        // Gestisci il caso in cui l'URL non sia valido o non ci sia un browser.
                        Toast.makeText(requireContext(), "Impossibile aprire il link: $url", Toast.LENGTH_LONG).show()
                        Log.e("FeatureList", "Errore nell'aprire l'URL: $url", e)
                    }
                } else {
                    // Se il campo 'website' è vuoto o mancante.
                    Toast.makeText(requireContext(), "Nessun sito web disponibile per questo elemento.", Toast.LENGTH_SHORT).show()
                }
            }
        })
        recyclerView.adapter = adapter
    }

    private fun showSearchDialog() {
        if (fieldConfig == null) {
            Toast.makeText(requireContext(), "Configurazione non disponibile per la ricerca.", Toast.LENGTH_SHORT).show()
            return
        }

        // 1. Infla il nuovo layout personalizzato
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_search_layout, null)
        val fieldsContainer = dialogView.findViewById<LinearLayout>(R.id.dialog_fields_container)

        // 2. Trova i pulsanti nel layout
        val resetButton: Button = dialogView.findViewById(R.id.button_reset)
        val cancelButton: Button = dialogView.findViewById(R.id.button_annulla)
        val searchButton: Button = dialogView.findViewById(R.id.button_cerca)

        // Popola gli EditText come prima
        val editTextMap = mutableMapOf<String, EditText>()
        fieldConfig!!.filter { it.isVisible }.forEach { field ->
            val editText = EditText(requireContext()).apply {
                hint = field.description
            }
            fieldsContainer.addView(editText)
            editTextMap[field.name] = editText
        }

        // 3. Costruisci il dialogo SENZA i pulsanti di default
        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .create()

        // Rendi trasparente lo sfondo della finestra per mostrare la CardView
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        // 4. Imposta i listener per i pulsanti personalizzati
        searchButton.setOnClickListener {
            val whereClauseParts = mutableListOf<String>()
            val selectionArgs = mutableListOf<String>()
            editTextMap.forEach { (fieldName, editText) ->
                val searchText = editText.text.toString().trim()
                if (searchText.isNotEmpty()) {
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

            dialog.dismiss() // Chiudi il dialogo
        }

        cancelButton.setOnClickListener {
            dialog.dismiss() // Chiudi semplicemente il dialogo
        }

        resetButton.setOnClickListener {
            loadFeatures() // Resetta i filtri e ricarica tutto
            dialog.dismiss() // Chiudi il dialogo
        }

        dialog.show()
    }

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

    override fun onDestroyView() {
        super.onDestroyView()
        featureCursor?.close()
        featureCursor = null
        if (::recyclerView.isInitialized) {
            recyclerView.adapter = null
        }
    }
}
