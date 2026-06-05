package com.apstudio.sentieri

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.apstudio.sentieri.db.OnLayerClickListener
import com.apstudio.sentieri.db.SentieriDB
import com.apstudio.sentieri.db.SentieriRepo
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.milestones.MilestoneManager

// visualizza le tracce caricate nella mappa
class LayerDialog : Fragment() {
    private val viewModel: SentieriViewModel by activityViewModels {
        val application = requireActivity().application
        // 1. Ottieni una singola istanza del database
        val database = SentieriDB.getInstance(application)
        // 2. Crea il repository passando TUTTI i DAO richiesti
        val repository = SentieriRepo(
            sentieriDao = database.sentieriDao(),
            trackDao = database.trackDao(),
            poiDao = database.poiDao(),
            fotoPoiDao = database.fotoPoiDao()
        )
        // 3. Crea la factory con il repository e l'applicazione
        SentieriFactory(repository, application)
    }

    private var rcvLayer: RecyclerView? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = layoutInflater.inflate(R.layout.dialog_layer, container, false)
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val recyclerView = view.findViewById<RecyclerView>(R.id.rcvLayer)
        rcvLayer = recyclerView
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        val layerAdapter = LayerAdapter(viewModel.layerItems)
        recyclerView.adapter = layerAdapter
        
        layerAdapter.setOnItemClickListener(object : OnLayerClickListener {

            override fun onswcVisibileClick(position: Int, isChecked: Boolean) {
                val item = viewModel.layerItems[position]
                item.abilitato = isChecked
                val targetName = item.nome.trim().lowercase()
                
                viewModel.listaTracce.items.forEach { overlay ->
                    if (overlay is Polyline && (overlay.title ?: "").trim().lowercase() == targetName) {
                        overlay.isEnabled = isChecked
                    }
                }
                viewModel.requestMapInvalidate()
            }

            override fun onswcDirezioneClick(position: Int, isChecked: Boolean) {
                val item = viewModel.layerItems[position]
                item.direzione = isChecked
                val targetName = item.nome.trim().lowercase()

                viewModel.listaTracce.items.forEach { overlay ->
                    if (overlay is Polyline && (overlay.title ?: "").trim().lowercase() == targetName) {
                        if (isChecked) {
                            MapUtils.applicaFrecceDirezione(overlay)
                        } else {
                            overlay.setMilestoneManagers(ArrayList())
                        }
                    }
                }
                viewModel.requestMapInvalidate()
            }

            override fun onswcQuotaPendenzaClick(position: Int, isChecked: Boolean) {
                val item = viewModel.layerItems[position]
                item.mostraPendenza = isChecked
                val targetName = item.nome.trim().lowercase()
                
                viewModel.listaTracce.items.forEach { overlay ->
                    if (overlay is Polyline && (overlay.title ?: "").trim().lowercase() == targetName) {
                        if (isChecked) {
                            val pendenze = MapUtils.calcolaPendenzeSmussate(overlay, 8)
                            MapUtils.disegnaPercorsoColorato(overlay, pendenze)
                        } else {
                            MapUtils.disegnaPercorsoColorato(overlay)
                        }
                    }
                }
                viewModel.requestMapInvalidate()
            }

            override fun onbtnSeguiClick(position: Int) {
                val clickedItem = viewModel.layerItems[position]
                val isTurningOn = !clickedItem.segui

                if (isTurningOn) {
                    viewModel.tracciaDaSeguire = clickedItem.nome
                    clickedItem.segui = true
                    for (i in 0 until viewModel.layerItems.size) {
                        if (i != position) viewModel.layerItems[i].segui = false
                    }
                } else {
                    viewModel.tracciaDaSeguire = ""
                    clickedItem.segui = false
                }
                recyclerView.adapter?.notifyDataSetChanged()
            }

            override fun onItemLongClick(position: Int) {
                val item = viewModel.layerItems[position]
                val builder = AlertDialog.Builder(requireContext(), R.style.AlertDialogCustom)
                with(builder) {
                    setTitle("Dati traccia\n${item.nome}")
                    val layout = LinearLayout(context).apply {
                        orientation = LinearLayout.VERTICAL
                        setPadding(40, 20, 40, 20)
                    }
                    
                    val infoText = android.widget.TextView(context).apply {
                        text = "\nDistanza: ${String.format("%,d", item.distanza.toInt())} m" +
                               "\nAscesa: ${String.format("%,d", item.ascesa)} m" +
                               "\nDiscesa: ${String.format("%,d", item.discesa)} m"
                        textSize = 16f
                    }
                    layout.addView(infoText)
                    setView(layout)
                    setPositiveButton("Chiudi", null)
                    show()
                }
            }
        })

        val closeButton = view.findViewById<Button>(R.id.btnOk)
        closeButton.setOnClickListener {
            // Segnaliamo al MappaFragment che deve ridisegnare tutto
            // Se hai un LiveData per questo, usalo. Altrimenti il NavController farà il pop.
            findNavController().popBackStack()
        }
    }
}
