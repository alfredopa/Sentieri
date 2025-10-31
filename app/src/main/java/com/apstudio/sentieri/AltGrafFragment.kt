package com.apstudio.sentieri

import android.content.pm.ActivityInfo
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.navArgs
import com.apstudio.sentieri.databinding.FragmentAltGrafBinding
import com.patrykandpatrick.vico.core.axis.Axis
import com.patrykandpatrick.vico.views.scroll.ChartScrollSpec
import com.patrykandpatrick.vico.core.axis.formatter.DefaultAxisValueFormatter
import com.patrykandpatrick.vico.core.axis.formatter.AxisValueFormatter
import com.patrykandpatrick.vico.core.axis.AxisItemPlacer


class AltGrafFragment : Fragment() {
    private lateinit var viewModel: SentieriViewModel
    private var originalOrientation: Int = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    private lateinit var binding: FragmentAltGrafBinding
    private val args: AltGrafFragmentArgs by navArgs()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = ViewModelProvider(requireActivity().applicationContext as AppSentieri).get(SentieriViewModel::class.java)
        // Salva l'orientamento originale QUI, solo se non è una ricreazione.
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
        if (requireActivity().requestedOrientation != ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE) {
            requireActivity().requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            // Esci immediatamente. La logica del grafico verrà eseguita dopo la ricreazione.
            return
        }
        Log.d("GRAF", "Orientamento corretto. Preparazione grafico.")

        // 1. Collega la ChartView al Producer che vive nel ViewModel
        binding.chartView.entryProducer = viewModel.chartProducer

        // 2. Chiama la funzione di preparazione nel ViewModel.
        //    La logica interna del ViewModel impedirà esecuzioni multiple.
        viewModel.preparaDatiGrafico(args.idTrack)

        // 3. (Opzionale ma consigliato) Forza un aggiornamento della vista
        //    nel caso il Fragment sia stato ricreato dopo una rotazione.
        viewModel.chartProducer.getModel()?.let { model ->
            viewModel.chartProducer.setEntries(model.entries.first())
        }


        val grafico = binding.chartView
        with(grafico) {
            runInitialAnimation = false
            chartScrollSpec = ChartScrollSpec(isScrollEnabled = false)
            // entryProducer è già stato impostato sopra
            (bottomAxis as Axis).guideline = null
            // 1. Crea un formattatore per l'asse Y che converte i float in interi
            val yAxisValueFormatter = AxisValueFormatter<AxisPosition.Horizontal.Bottom> { value, _ ->
                // 'value' è il valore numerico della quota (un Float)
                // Lo convertiamo in un intero e poi in una stringa.
                value.toInt().toString()
            }
            // Configurazione Asse Verticale (Start Axis) - AGGIUNGI LA FORMATTAZIONE
            (startAxis as Axis).apply {
                title = "Altitudine (m)"

                // Questo è il comando chiave:
                // Crea un formattatore che mostra i numeri con 0 cifre decimali.
               // valueFormatter = DefaultAxisValueFormatter(decimals = 0)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        if (isRemoving) {
            activity?.requestedOrientation = originalOrientation
        }
    }
}
