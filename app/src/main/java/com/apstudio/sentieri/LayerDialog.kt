package com.apstudio.sentieri

import android.os.Bundle
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
import com.apstudio.sentieri.db.SentieriRepo
import org.osmdroid.views.overlay.Polyline

// visualizza le tracce caricate nella mappa
class LayerDialog : Fragment() {
    private val viewModel: SentieriViewModel by activityViewModels {
        SentieriFactory(
            SentieriRepo(requireActivity())
        )
    }

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
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        val layerAdapter = LayerAdapter(viewModel.layerItems)
        recyclerView.adapter = layerAdapter
        layerAdapter.setOnItemClickListener(object : OnLayerClickListener {

            override fun onswcVisibileClick(position: Int) {
                viewModel.layerItems[position].abilitato = !viewModel.layerItems[position].abilitato
                viewModel.listaTracce.items.forEach {
                    if (it is Polyline && it.title == viewModel.layerItems[position].nome) {
                        it.isEnabled = !it.isEnabled
                        // attenzione il valore di isEnabled è appena cambiato
                        viewModel.listaTracce.items[position].isEnabled = it.isEnabled
                    }
                }
            }

            override fun onswcDirezioneClick(position: Int) {
                viewModel.layerItems[position].direzione = !viewModel.layerItems[position].direzione
                val viewHolder =
                    recyclerView.findViewHolderForAdapterPosition(position) as LayerAdapter.LayerViewHolder
                val swcDirezione = viewHolder.binding.swcDirezione.isChecked
                viewModel.listaTracce.items.forEach {
                    if (it is Polyline && it.title == viewModel.layerItems[position].nome) {
                        //assegna il valore della direzione alla traccia direttamente
                        it.usePath(swcDirezione)
                    }
                }
            }

            override fun onbtnSeguiClick(position: Int) {
                // camobio traccia da seguire
                viewModel.tracciaDaSeguire = viewModel.layerItems[position].nome
                for (i in 0 until viewModel.layerItems.size) {
                    if (i != position) {
                        viewModel.layerItems[i].segui = false
                    }
                }
                viewModel.layerItems[position].segui = !viewModel.layerItems[position].segui
                recyclerView.adapter?.notifyDataSetChanged()
            }

            override fun onItemLongClick(position: Int) {
                // alertdialog per mostrare i dati della traccia
                val allarme = EditText(requireContext())
                val builder =
                    AlertDialog.Builder(requireContext(), R.style.AlertDialogCustom)
                with(builder)
                {
                    setTitle("Dati traccia\n${viewModel.layerItems[position].nome}")
                    val layout = LinearLayout(context)
                    layout.orientation = LinearLayout.VERTICAL
                    val distanza = String.format("%,d", viewModel.trackDistanza.toInt())
                    val ascesa = String.format("%,d", viewModel.trackAscesa)
                    val discesa = String.format("%,d", viewModel.trackDiscesa)
                    allarme.setText("\nDistanza: $distanza\nAscesa: $ascesa\nDiscesa: $discesa")
                    allarme.setPadding(20, 10, 20, 30) // Aggiungi padding per una migliore leggibilità
                    layout.addView(allarme)
                    // Set the LinearLayout as the view for the dialog
                    builder.setView(layout)

                    setPositiveButton(
                        "Chiudi"
                    ) { _, _ ->}
                    show()
                }
            }
        })

        val closeButton = view.findViewById<Button>(R.id.btnOk)
        closeButton.setOnClickListener {
            val directions = LayerDialogDirections.actionLayerDialogToMappaFragment()
            findNavController().navigate(directions)
        }
    }
}