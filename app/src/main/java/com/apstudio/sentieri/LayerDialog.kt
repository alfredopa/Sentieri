package com.apstudio.sentieri

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.apstudio.sentieri.db.OnLayerClickListener
import com.apstudio.sentieri.db.SentieriDB
import com.apstudio.sentieri.db.SentieriRepo
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.Marker

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
        Log.d("listaTracce", "LayerDialog onViewCreated ${viewModel.layerItems.size}")
        recyclerView.adapter = layerAdapter
        
        layerAdapter.setOnItemClickListener(object : OnLayerClickListener {

            override fun onswcVisibileClick(position: Int, isChecked: Boolean) {
                viewModel.layerItems[position].abilitato = isChecked
                viewModel.requestMapInvalidate()
            }

            override fun onswcDirezioneClick(position: Int, isChecked: Boolean) {
                viewModel.layerItems[position].direzione = isChecked
                viewModel.requestMapInvalidate()
            }

            override fun onswcQuotaPendenzaClick(position: Int, isChecked: Boolean) {
                viewModel.layerItems[position].mostraPendenza = isChecked
                viewModel.requestMapInvalidate()
            }

            override fun onbtnSeguiClick(position: Int) {
                val clickedItem = viewModel.layerItems[position]
                val isTurningOn = !clickedItem.segui

                if (isTurningOn) {
                    viewModel.tracciaDaSeguire = clickedItem.nome
                    // Aggiorna i valori di riferimento per il calcolo dei valori rimanenti
                    viewModel.trackDistanza = clickedItem.distanza
                    viewModel.trackAscesa = clickedItem.ascesa
                    viewModel.trackDiscesa = clickedItem.discesa

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

                    // CERCA IL LAYERITEM PER I PUNTI DEL GRAFICO
                    val targetName = item.nome.trim().lowercase()
                    val layerItem = viewModel.layerItems.find { it.nome.trim().lowercase() == targetName }

                    layerItem?.let { li ->
                        if (li.punti.isNotEmpty()) {
                            val chartEntries = MapUtils.getPuntiInterpolati(li.punti)
                            if (chartEntries.isNotEmpty()) {
                                val lineChart = LineChart(context).apply {
                                    layoutParams = LinearLayout.LayoutParams(
                                        LinearLayout.LayoutParams.MATCH_PARENT,
                                        450 // Altezza per il grafico nel dialogo
                                    ).apply {
                                        topMargin = 20
                                    }
                                    description.isEnabled = false
                                    legend.isEnabled = false
                                    isDragEnabled = false
                                    setScaleEnabled(false)

                                    val textColor = ContextCompat.getColor(context, R.color.grafico_text_color)
                                    
                                    xAxis.apply {
                                        granularity = 1f
                                        this.position = XAxis.XAxisPosition.BOTTOM
                                        this.textColor = textColor
                                        valueFormatter = object : ValueFormatter() {
                                            override fun getFormattedValue(value: Float): String = "${value.toInt()} km"
                                        }
                                    }

                                    axisLeft.apply {
                                        this.textColor = textColor
                                        valueFormatter = object : ValueFormatter() {
                                            override fun getFormattedValue(value: Float): String = "${value.toInt()} m"
                                        }
                                    }
                                    axisRight.isEnabled = false

                                    val dataSet = LineDataSet(chartEntries, "Profilo Altimetrico").apply {
                                        color = Color.RED
                                        setDrawFilled(true)
                                        fillColor = Color.CYAN
                                        mode = LineDataSet.Mode.CUBIC_BEZIER
                                        setDrawCircles(false)
                                        setDrawValues(false)
                                        lineWidth = 2f
                                    }
                                    data = LineData(dataSet)
                                    invalidate()
                                }
                                layout.addView(lineChart)
                            }
                        }
                    }

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
