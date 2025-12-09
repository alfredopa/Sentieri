package com.apstudio.sentieri.layer

import android.content.DialogInterface
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStoreOwner
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.apstudio.sentieri.R

const val LAYER_DIALOG_REQUEST_KEY = "layerDialogRequest"

class GpkgLayer : DialogFragment(), FeatureAdapter.OnItemClickListener {
    private val layerModel: LayerViewModel by lazy {
        // Se LayerViewModel è un AndroidViewModel, il provider è leggermente diverso
        // o se hai un factory personalizzato.
        // Assumendo che il provider di default per AndroidViewModel funzioni:
        //ViewModelProvider(this)[LayerViewModel::class.java]
        // Se prima usavi `requireActivity().application as ViewModelStoreOwner`
        // e vuoi che il ViewModel sia a livello di Application, mantieni quel provider:
         ViewModelProvider(requireActivity().application as ViewModelStoreOwner)[LayerViewModel::class.java]
    }

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: FeatureAdapter // FeatureAdapter deve essere definito

    val TAG = "GpkgLayer" // Già presente

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Chiama il metodo del ViewModel per aprire il GeoPackage e caricare la configurazione
        // Passa il contesto se il tuo ViewModel non è un AndroidViewModel
        Log.d(TAG, "onCreate: Requesting GeoPackage open and config load from ViewModel.")
        layerModel.openGeoPackageAndLoadConfig()
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

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.dlg_gpkg_layer, container, false)
    }

    // In GpkgLayer.kt
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        recyclerView = view.findViewById(R.id.my_recycler_view)    // Potresti aggiungere un ProgressBar al layout dlg_gpkg_layer.xml per un feedback migliore
        // val progressBar = view.findViewById<ProgressBar>(R.id.loading_spinner)
        // progressBar.visibility = View.VISIBLE

        recyclerView.layoutManager = GridLayoutManager(requireContext(), 1)
        recyclerView.adapter = FeatureAdapter(mutableListOf<FeatureTableInfo>(), this) // Inizia con un adapter vuoto

        // Osserva lo stato di preparazione del ViewModel
        layerModel.isReady.observe(viewLifecycleOwner, Observer { isReady ->
            if (isReady) {
                // I dati sono pronti, popola l'adapter
                Log.d(TAG, "ViewModel è pronto. Aggiorno la lista nell'adapter.")
                // progressBar.visibility = View.GONE
                adapter = FeatureAdapter(layerModel.featureList, this)
                recyclerView.adapter = adapter
            } else {
                // Gestisci il caso in cui il caricamento fallisce
                // progressBar.visibility = View.GONE
                if (isAdded) { // Controlla se il fragment è ancora attivo
                    Toast.makeText(context, "Errore nel caricamento dei layer.", Toast.LENGTH_LONG)
                        .show()
                }
            }
        })

        // Chiama il caricamento (ora asincrono) se non è già stato fatto
        layerModel.openGeoPackageAndLoadConfig()

        val btnOk: Button = view.findViewById(R.id.btnOk)
        btnOk.setOnClickListener {
            dismiss()
        }
    }
    // ... (onStart, onItemClick, onSwitchCheckedChanged rimangono simili,
    //      ma operano sulla featureList del layerModel) ...

    // La funzione creaGeopackage non è più necessaria qui, è gestita dal ViewModel
    // La funzione loadConfigIfNeeded non è più necessaria qui, è gestita dal ViewModel


    // Nel file GpkgLayer.kt
    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        // Notifica al ViewModel che il dialogo è stato chiuso.
        layerModel.requestLayerUpdate()
        Log.d("GpkgLayer", "Dialogo chiuso. Inviata richiesta via ViewModel.")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "GpkgLayer onDestroy called.")
        // Nessuna chiusura esplicita del GeoPackage qui.
    }

    // onItemClick e onSwitchCheckedChanged dovrebbero già usare layerModel.featureList,
    // quindi dovrebbero continuare a funzionare, assumendo che featureList sia aggiornata correttamente.
    // Esempio per onSwitchCheckedChanged:
    override fun onSwitchCheckedChanged(position: Int, isChecked: Boolean) {
        if (position < 0 || position >= layerModel.featureList.size) {
            Log.e(TAG, "Invalid position in onSwitchCheckedChanged: $position")
            return
        }
        val featureInfo = layerModel.featureList[position]
        // Aggiorna solo lo stato del modello.
        if (featureInfo.isVisible != isChecked) {
            featureInfo.isVisible = isChecked
            Log.d("SwitchDebug", "Stato 'isVisible' per ${featureInfo.name} aggiornato a: $isChecked nel modello.")
        }
    }


    override fun onItemClick(position: Int) {
        if (position < 0 || position >= layerModel.featureList.size) {
            Log.e(TAG, "Invalid position in onItemClick: $position")
            return
        }
        layerModel.currentActiveTableName = layerModel.featureList[position].name
        // Potresti voler passare dati aggiuntivi o usare Safe Args
        this@GpkgLayer.findNavController().navigate(R.id.action_gpkgLayer_to_featureList)
    }
}
