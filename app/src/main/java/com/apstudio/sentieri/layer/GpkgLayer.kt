package com.apstudio.sentieri.layer

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStoreOwner
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.apstudio.sentieri.R
import com.google.android.material.bottomsheet.BottomSheetBehavior
import mil.nga.geopackage.GeoPackageFactory
import mil.nga.geopackage.GeoPackageManager
import org.osmdroid.gpkg.overlay.features.PolygonOptions
import java.io.File

class GpkgLayer: DialogFragment(), FeatureAdapter.OnItemClickListener {
    private val layerModel: LayerViewModel by lazy {
        ViewModelProvider(requireActivity().application as ViewModelStoreOwner)[LayerViewModel::class.java]
    }
    private lateinit var recyclerView: RecyclerView

    //private lateinit var adapter: DynamicColumnAdapter
    private lateinit var adapter: FeatureAdapter
    private var labelConfig = mutableMapOf<String, List<Pair<String, Boolean>>>()

    private lateinit var bottomSheetBehavior: BottomSheetBehavior<ConstraintLayout>

    // PARAMETRI per Polyline e Polygon
    val polygonOptions = PolygonOptions().apply {
        strokeWidth = 2f
        fillColor = Color.argb(100, 255, 0, 255)
        strokeColor = Color.argb(100, 0, 0, 255)
    }
    val TAG = "GpkgLayer"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Crea e gestisce il GeoPackage.
        // il geopackage non viene importato ma letto dalla cartella dati interni data/data
        val databaseName = "Layers.gpkg"
        val dataDir = requireContext().getDatabasePath(databaseName).parentFile
        val geoPackageFile = File(dataDir, databaseName)
        val geoPackageManager: GeoPackageManager = GeoPackageFactory.getManager(requireContext())
        val openedGeoPackage = geoPackageManager.openExternal(geoPackageFile) // Non assegnare direttamente a layerModel.DATABASE_NAME

        if (openedGeoPackage == null) {
            Log.d(TAG, "Errore durante apertura del GeoPackage")
            // Gestisci errore e return
            return
        }
        // Imposta il GeoPackage nel ViewModel. Questo scatenerà il caricamento
        // dei feature names la prima volta.
        layerModel.geoPackageInstance = openedGeoPackage

        // Carica il file di configurazione Xml
        val pathGeoPackage = geoPackageFile.toString()
        val configurator = DatabaseSchemaConfigurator(requireContext(), pathGeoPackage)
        // Prima, potresti voler generare il file se non esiste o se è la prima esecuzione
        if (!File(requireContext().filesDir, "db_schema_config.xml").exists()) {
            configurator.generateAndWriteConfigFile()
        }
        (configurator.loadConfigFromFile() as MutableMap<String, List<Pair<String, Boolean>>>).also {
            labelConfig = it
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.dlg_gpkg_layer, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // Crea il RecyclerView
        recyclerView = view.findViewById(R.id.my_recycler_view)
        // Crea e imposta l'adapter
        val layoutManager = GridLayoutManager(requireContext(), 1)
        recyclerView.layoutManager = layoutManager
        // L'adapter ora prende la lista direttamente dal ViewModel
        // La lista nel ViewModel è la "source of truth"
        adapter = FeatureAdapter(layerModel.featureList, this)
        recyclerView.adapter = adapter
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.apply {
            val width = (resources.displayMetrics.widthPixels * 0.95).toInt() // 90% della larghezza schermo
            val height = (resources.displayMetrics.heightPixels * 0.60).toInt() // 75% dell'altezza schermo
            // Oppure usa ViewGroup.LayoutParams.MATCH_PARENT o WRAP_CONTENT
            // val height = ViewGroup.LayoutParams.WRAP_CONTENT
            setLayout(width, height)
            // Puoi anche impostare la gravità, se necessario
            // attributes.gravity = Gravity.CENTER
        }
    }
    override fun onItemClick(position: Int) {
        // Gestisci qui il click sull'item
        // Ad esempio, mostra dettagli, naviga, ecc.
        Log.d("GpkgLayer", "Item clicked: $position}")
        // Fai qualcosa con l'oggetto 'feature' cliccato
    }

    override fun onSwitchCheckedChanged(position: Int, isChecked: Boolean) {
        TODO("Not yet implemented")
    }
}