package com.apstudio.sentieri.layer

import android.content.Context
import android.content.DialogInterface
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStoreOwner
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.apstudio.sentieri.R
import mil.nga.geopackage.GeoPackageFactory
import mil.nga.geopackage.GeoPackageManager
import java.io.File

const val LAYER_DIALOG_REQUEST_KEY = "layerDialogRequest"

class GpkgLayer: DialogFragment(), FeatureAdapter.OnItemClickListener {
    private val layerModel: LayerViewModel by lazy {
        ViewModelProvider(requireActivity().application as ViewModelStoreOwner)[LayerViewModel::class.java]
    }

    private lateinit var recyclerView: RecyclerView

    //private lateinit var adapter: DynamicColumnAdapter
    private lateinit var adapter: FeatureAdapter

    val TAG = "GpkgLayer"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        creaGeopackage(requireContext())
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
        val btnOk: Button = view.findViewById(R.id.btnOk)
        btnOk.setOnClickListener {
            dismiss()
        }
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
        Log.d(TAG, "Item clicked: $position}")
        // Fai qualcosa con l'oggetto 'feature' cliccato
    }

    override fun onSwitchCheckedChanged(position: Int, isChecked: Boolean) {
        val featureInfo = layerModel.featureList[position]
        // 1. Aggiorna lo stato nel ViewModel. Questa è la "source of truth".
        featureInfo.isVisible = isChecked // L'oggetto featureInfo è quello dentro layerModel.featureList
        Log.d("SwitchDebug", "Model isVisible for ${featureInfo.name} UPDATED TO: ${featureInfo.isVisible}")
        layerModel.currentActiveTableName = featureInfo.name
        adapter.notifyItemChanged(position)
    }


    fun creaGeopackage(context: Context) {
        // Crea e gestisce il GeoPackage.
        // il geopackage non viene importato ma letto dalla cartella dati interni data/data
        val databaseName = "Layers.gpkg"
        val dataDir = context.getDatabasePath(databaseName).parentFile
        val geoPackageFile = File(dataDir, databaseName)
        val geoPackageManager: GeoPackageManager = GeoPackageFactory.getManager(context)
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
        val configurator = DatabaseSchemaConfigurator(context, pathGeoPackage)
        // Prima, potresti voler generare il file se non esiste o se è la prima esecuzione
        if (!File(context.filesDir, "db_schema_config.xml").exists()) {
            configurator.generateAndWriteConfigFile()
        }
        (configurator.loadConfigFromFile() as MutableMap<String, List<Pair<String, Boolean>>>).also {
            layerModel.labelConfig = it
        }
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        // Invia un risultato generico quando il dialogo viene chiuso.
        // Puoi anche passare un Bundle con dati specifici se necessario.
        //parentFragmentManager.setFragmentResult(LAYER_DIALOG_REQUEST_KEY, bundleOf("userAction" to "closed"))
        parentFragmentManager.setFragmentResult(LAYER_DIALOG_REQUEST_KEY, Bundle.EMPTY) // Invia un bundle vuoto se non ci sono dati specifici
        Log.d(TAG, "GpkgLayer dismissed, result sent.")
    }

}