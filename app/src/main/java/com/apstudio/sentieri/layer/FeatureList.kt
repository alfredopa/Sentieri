package com.apstudio.sentieri.layer

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStoreOwner
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.apstudio.sentieri.R
import mil.nga.geopackage.features.user.FeatureCursor
import mil.nga.sf.Point

class FeatureList : Fragment() {
    private val layerModel: LayerViewModel by lazy {
        ViewModelProvider(requireActivity().application as ViewModelStoreOwner)[LayerViewModel::class.java]
    }
    private lateinit var recyclerView: RecyclerView
    private var featureCursor: FeatureCursor? = null // Per chiuderlo in onDestroyView

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_feature_list_list, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // 1. Inizializza il RecyclerView
        recyclerView = view.findViewById(R.id.list)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        // 2. Ottieni il nome del layer dal ViewModel
        val currentLayerName = layerModel.currentActiveTableName
        if (currentLayerName == null) {
            Toast.makeText(requireContext(), "Layer attivo non specificato.", Toast.LENGTH_LONG).show()
            recyclerView.adapter = null // Assicurati che non ci sia un adapter vecchio
            // Considera di tornare indietro o mostrare una UI di errore più esplicita
            return
        }

        // 3. Accedi al GeoPackage e carica i dati
        val geoPackage = layerModel.geoPackageInstance
        if (geoPackage == null) {
            Toast.makeText(requireContext(), "GeoPackage non disponibile.", Toast.LENGTH_LONG).show()
            recyclerView.adapter = null
            return
        }

        val featureDao = geoPackage.getFeatureDao(currentLayerName)
        if (featureDao == null) {
            Toast.makeText(requireContext(), "DAO non trovato per il layer: $currentLayerName", Toast.LENGTH_LONG).show()
            recyclerView.adapter = null
            // layerModel gestisce la chiusura del geoPackage
            return
        }

        // Tieni traccia del cursore per chiuderlo in onDestroyView
        featureCursor = featureDao.queryForAll()

        if (featureCursor == null || featureCursor!!.count == 0) {
            Toast.makeText(requireContext(), "Nessun dato trovato per il layer: $currentLayerName", Toast.LENGTH_LONG).show()
            recyclerView.adapter = null
            // featureCursor?.close() // Verrà chiuso in onDestroyView
            return
        }

        // 4. Prepara i dati per il DynamicFeatureAdapter
        val fieldConfig = layerModel.labelConfig[currentLayerName]
        if (fieldConfig == null) {
            Toast.makeText(requireContext(), "Configurazione campi non trovata per: $currentLayerName", Toast.LENGTH_LONG).show()
            recyclerView.adapter = null
            // featureCursor?.close() // Verrà chiuso in onDestroyView
            return
        }

        val featuresData = mutableListOf<Map<String, Any?>>()
        // È importante resettare il cursore prima di iterare se è già stato usato,
        // o se l'adapter si aspetta di partire dall'inizio.
        // featureCursor!!.moveToFirst() // Assicurati che il cursore sia all'inizio
        try {
            featureCursor!!.use { cursor -> // 'use' chiude il cursore dopo questo blocco
                if (cursor.moveToFirst()) {
                    do {
                        val row = cursor.row // Ottieni la FeatureRow corrente
                        val columnNames = row.columnNames
                        val values = row.values // Questo è Array<Any?>
                        // Crea una nuova mappa kotlin in modo esplicito
                        val currentFeatureMap = mutableMapOf<String, Any?>()
                        // Assicurati che il numero di nomi di colonna corrisponda al numero di valori
                        if (columnNames.size == values.size) {
                            for (i in columnNames.indices) {
                                currentFeatureMap[columnNames[i]] = values[i]
                            }
                        } else {
                            Log.e("FeatureList", "Discordanza tra numero di colonne e valori per la riga.")
                        }
                        // --- Inizio estrazione coordinate ---
                        var featureLatitude: Double? = null
                        var featureLongitude: Double? = null
                        var featureElevation: Double? = null

                        val geometryColumnIndex = row.geometryColumnIndex
                        if (geometryColumnIndex != -1) { // Controlla se l'indice è valido
                            val geometryData = row.getGeometry()
                            if (geometryData != null && !geometryData.isEmpty) {
                                val geometry = geometryData.geometry
                                if (geometry is Point) { // mil.nga.sf.Point
                                    featureLongitude = geometry.x
                                    featureLatitude = geometry.y
                                    if (geometry.hasZ()) {
                                        featureElevation = geometry.z
                                    }
                                } else { // Per altre geometrie, usa il centroide
                                    val centroid = geometry.centroid
                                    // Il centroide potrebbe non essere un Point se la geometria è degenere
                                    if (centroid is Point) {
                                        featureLongitude = centroid.x
                                        featureLatitude = centroid.y
                                        if (centroid.hasZ()) {
                                            featureElevation = centroid.z
                                        }
                                    }
                                }
                            }
                        }
                        // Aggiungi le coordinate alla mappa (anche se null, l'adapter le gestirà)
                        currentFeatureMap[DynamicFeatureAdapter.KEY_INTERNAL_LATITUDE] = featureLatitude
                        currentFeatureMap[DynamicFeatureAdapter.KEY_INTERNAL_LONGITUDE] = featureLongitude
                        currentFeatureMap[DynamicFeatureAdapter.KEY_INTERNAL_ELEVATION] = featureElevation
                        // --- Fine estrazione coordinate ---

                        featuresData.add(currentFeatureMap)
                    } while (cursor.moveToNext())
                }
            }
            // Dato che featureCursor.use lo chiude, se devi riutilizzare featureCursor
            // per un altro adapter o logica, dovresti ri-eseguire la query.
            // Per ora, assumiamo che featuresData sia sufficiente per DynamicFeatureAdapter.
            // Se usi un adapter che prende direttamente il FeatureCursor, gestisci diversamente.
            // Il featureCursor membro della classe verrà chiuso in onDestroyView,
            // ma quello specifico qui è già stato chiuso da 'use'.
            // Per semplicità, annulliamo il riferimento al featureCursor membro se 'use' è stato impiegato.
            this.featureCursor = null // Annulla il riferimento al cursore membro se è stato chiuso da .use{}
            // Altrimenti, se non usi .use{} per popolare featuresData,
            // assicurati che il cursore membro sia gestito correttamente.


        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Errore durante la lettura dei dati: ${e.message}", Toast.LENGTH_LONG).show()
            recyclerView.adapter = null
            //this.featureCursor?.close() // Chiudi il cursore membro in caso di errore qui se non usi 'use'
            //this.featureCursor = null
            return
        }


        if (featuresData.isNotEmpty()) {
            val adapter = DynamicFeatureAdapter(featuresData, fieldConfig)
            adapter.setOnItemClickListener(object : DynamicFeatureAdapter.OnItemClickListener {
                override fun onItemClicked(latitude: Double, longitude: Double, elevation: Double?) {
                    val bundle = Bundle().apply {
                        putDouble("clicked_latitude", latitude)
                        putDouble("clicked_longitude", longitude)
                        elevation?.let { putDouble("clicked_elevation", it) }
                    }
                    // Usa una chiave univoca per il risultato
                    parentFragmentManager.setFragmentResult("feature_click_request", bundle)
                    // Torna al fragment precedente (che dovrebbe essere MappaFragment)
                    findNavController().popBackStack()
                }
            })
            recyclerView.adapter = adapter
        } else {
            Toast.makeText(requireContext(), "Nessun dato processato.", Toast.LENGTH_LONG).show()
            recyclerView.adapter = null
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Chiudi il cursore se è ancora aperto e referenziato dalla variabile membro
        featureCursor?.close()
        featureCursor = null // Pulisci il riferimento
        // Pulisci l'adapter per evitare memory leak e liberare risorse
        if (::recyclerView.isInitialized) { // Controlla se recyclerView è stata inizializzata
            recyclerView.adapter = null
        }
    }
}
