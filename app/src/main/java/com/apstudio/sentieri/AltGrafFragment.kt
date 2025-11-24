// File: AltGrafFragment.kt

package com.apstudio.sentieri

import android.content.pm.ActivityInfo
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.navArgs
import com.apstudio.sentieri.databinding.FragmentAltGrafBinding
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import kotlinx.coroutines.launch

class AltGrafFragment : Fragment() {
    private lateinit var viewModel: SentieriViewModel
    private lateinit var binding: FragmentAltGrafBinding
    private val args: AltGrafFragmentArgs by navArgs()
    private var originalOrientation: Int = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = ViewModelProvider(requireActivity().applicationContext as AppSentieri)[SentieriViewModel::class.java]

        // Salva l'orientamento originale solo alla prima creazione del fragment
        if (savedInstanceState == null) {
            originalOrientation = requireActivity().requestedOrientation
        }
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

        // Forza l'orientamento orizzontale
        requireActivity().requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE

        // --- INIZIO DELLA SOLUZIONE FINALE ---

        // Avvia una coroutine per caricare i dati e configurare il grafico
        viewLifecycleOwner.lifecycleScope.launch {
            // 1. Carica i dati dal ViewModel
            val chartEntries = viewModel.preparaDatiGrafico(args.idTrack)

            // Se non ci sono dati, non fare nulla
            if (chartEntries.isEmpty()) {
                Log.w("GRAF", "Nessun dato da visualizzare nel grafico.")
                return@launch
            }

            // 2. Crea un "DataSet" con i punti
            val dataSet = LineDataSet(chartEntries, "Profilo Altimetrico")

            // 3. Personalizza l'aspetto del DataSet (linea, colori, riempimento)
            dataSet.color = Color.RED
            dataSet.setDrawFilled(true)
            dataSet.fillColor = Color.CYAN
            dataSet.mode = LineDataSet.Mode.CUBIC_BEZIER
            dataSet.setDrawCircles(false)
            dataSet.setDrawValues(false)
            dataSet.lineWidth = 2f

            // 4. Crea l'oggetto "LineData" da dare al grafico
            val lineData = LineData(dataSet)

            // 5. CONFIGURA E POPOLA IL GRAFICO TUTTO INSIEME
            binding.lineChart.apply {
                description.isEnabled = false
                legend.isEnabled = false
                isDragEnabled = false
                setScaleEnabled(false)

                //val textColor = Color.DKGRAY // Un grigio scuro è quasi sempre leggibile.
                val textColor = ContextCompat.getColor(requireContext(), R.color.grafico_text_color)
                // Configura Asse X (in basso)
                xAxis.apply {
                    granularity = 1f
                    position = com.github.mikephil.charting.components.XAxis.XAxisPosition.BOTTOM
                    this.textColor = textColor // Applica colore
                    valueFormatter = object : ValueFormatter() {
                        override fun getFormattedValue(value: Float): String {
                            return "${value.toInt()} km"
                        }
                    }
                }

                // Configura Asse Y (sinistro)
                axisLeft.apply {
                    this.textColor = textColor // Applica colore
                    valueFormatter = object : ValueFormatter() {
                        override fun getFormattedValue(value: Float): String {
                            return "${value.toInt()} m"
                        }
                    }
                }
                axisRight.isEnabled = false

                // Assegna i dati al grafico
                data = lineData

                // Infine, ridisegna il grafico
                invalidate()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Ripristina l'orientamento originale solo quando il fragment viene rimosso permanentemente
        if (isRemoving) {
            activity?.requestedOrientation = originalOrientation
        }
    }
}
