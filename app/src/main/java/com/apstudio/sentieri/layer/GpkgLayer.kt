package com.apstudio.sentieri.layer

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

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        recyclerView = view.findViewById(R.id.my_recycler_view)
        val layoutManager = GridLayoutManager(requireContext(), 1)
        recyclerView.layoutManager = layoutManager

        // L'adapter ora prende la lista direttamente dal ViewModel
        // La lista nel ViewModel è la "source of truth"
        // Assicurati che FeatureAdapter sia in grado di gestire una lista vuota inizialmente
        // e si aggiorni quando layerModel.featureList cambia (es. usando LiveData/StateFlow e osservandolo)
        adapter = FeatureAdapter(layerModel.featureList, this)
        recyclerView.adapter = adapter

        // Esempio se featureList fosse un LiveData nel ViewModel:
        // layerModel.featuresLiveData.observe(viewLifecycleOwner) { features ->
        //     adapter.updateData(features) // Dovresti creare un metodo nell'adapter per aggiornare i dati
        // }

        val btnOk: Button = view.findViewById(R.id.btnOk)
        btnOk.setOnClickListener {
            dismiss()
        }

        // Se featureList non è LiveData, potresti dover notificare l'adapter dopo che onCreate
        // ha chiamato openGeoPackageAndLoadConfig e la lista è stata popolata.
        // Questo è un punto delicato se il popolamento è asincrono.
        // Per ora, assumiamo che al momento della creazione dell'adapter, featureList
        // sia già popolata o che l'adapter gestisca una lista vuota e si aggiorni.
        // Se non è così, potresti aver bisogno di aggiornare l'adapter dopo che i dati sono pronti.
        if (layerModel.featureList.isNotEmpty() && adapter.itemCount == 0) {
            adapter.notifyDataSetChanged() // o un modo più specifico per aggiornare
        }
    }

    // ... (onStart, onItemClick, onSwitchCheckedChanged rimangono simili,
    //      ma operano sulla featureList del layerModel) ...

    // La funzione creaGeopackage non è più necessaria qui, è gestita dal ViewModel
    // La funzione loadConfigIfNeeded non è più necessaria qui, è gestita dal ViewModel


    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        Log.d(TAG, "GpkgLayer onDismiss called.")
        parentFragmentManager.setFragmentResult(LAYER_DIALOG_REQUEST_KEY, Bundle.EMPTY)
        // Non è più necessario chiamare closeGeoPackage() qui,
        // il ViewModel lo gestirà in onCleared() se ha scope Application.
        // Se lo scope del ViewModel è legato al Fragment, onCleared verrà chiamato
        // quando il Fragment viene distrutto.
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
        featureInfo.isVisible = isChecked
        Log.d(
            "SwitchDebug",
            "Model isVisible for ${featureInfo.name} UPDATED TO: ${featureInfo.isVisible}"
        )
        // Qui dovresti anche gestire la logica per mostrare/nascondere gli overlay sulla mappa.
        // Ad esempio, chiamando un metodo in MappaFragment o tramite un LiveData condiviso.
        // Se MappaFragment osserva featureList, potrebbe reagire a questo cambiamento.
        layerModel.currentActiveTableName = featureInfo.name
        // Notifica l'adapter che l'item è cambiato per ridisegnarlo (se necessario)
        adapter.notifyItemChanged(position)
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