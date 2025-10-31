// File: AltGrafFragment.kt

package com.apstudio.sentieri

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.navArgs
import com.apstudio.sentieri.databinding.FragmentAltGrafBinding
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import kotlinx.coroutines.launch

class AltGrafFragment : Fragment() {
    private lateinit var viewModel: SentieriViewModel
    private lateinit var binding: FragmentAltGrafBinding
    private val args: AltGrafFragmentArgs by navArgs()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = ViewModelProvider(requireActivity().applicationContext as AppSentieri).get(SentieriViewModel::class.java)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentAltGrafBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // La gestione della rotazione non è più necessaria qui perché MPAndroidChart è più robusto

        // Avvia una coroutine per caricare i dati e popolare il grafico
        viewLifecycleOwner.lifecycleScope.launch {
            // 1. Carica i dati dal ViewModel
            val chartEntries = viewModel.preparaDatiGrafico(args.idTrack)

            // Se non ci sono dati, non fare nulla
            if (chartEntries.isEmpty()) return@launch

            // 2. Crea un "DataSet" con i punti
            val dataSet = LineDataSet(chartEntries, "Profilo Altimetrico")

            // 3. Personalizza l'aspetto del DataSet (linea, colori, riempimento)
            dataSet.color = Color.RED
            dataSet.valueTextColor = Color.BLACK
            dataSet.setDrawFilled(true) // Abilita il riempimento sotto la linea
            dataSet.fillColor = Color.CYAN
            dataSet.mode = LineDataSet.Mode.CUBIC_BEZIER // Linea curva
            dataSet.setDrawCircles(false) // Non disegnare i punti
            dataSet.setDrawValues(false) // Non scrivere i valori sopra i punti

            // 4. Crea l'oggetto "LineData" da dare al grafico
            val lineData = LineData(dataSet)

            // 5. Configura e popola il grafico
            binding.lineChart.apply {
                description.isEnabled = false // Rimuove la descrizione in basso a destra
                legend.isEnabled = false // Rimuove la legenda

                // Asse X (in basso)
                xAxis.granularity = 1f // Imposta lo step minimo a 1 (1 km)
                xAxis.position = com.github.mikephil.charting.components.XAxis.XAxisPosition.BOTTOM
                xAxis.valueFormatter = object : ValueFormatter() {
                    override fun getFormattedValue(value: Float): String {
                        return "${value.toInt()} km" // Formatta le etichette come "5 km"
                    }
                }

                // Asse Y (sinistro)
                axisLeft.valueFormatter = object : ValueFormatter() {
                    override fun getFormattedValue(value: Float): String {
                        return "${value.toInt()} m" // Formatta le etichette come "200 m"
                    }
                }
                axisRight.isEnabled = false // Disabilita l'asse Y destro

                // Popola il grafico con i dati
                data = lineData
                invalidate() // Ridisegna il grafico
            }
        }
    }
}
