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
import com.patrykandpatrick.vico.core.axis.AxisPosition
import com.patrykandpatrick.vico.core.axis.horizontal.createHorizontalAxis
import com.patrykandpatrick.vico.core.axis.vertical.createVerticalAxis

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
        val grafico = binding.chartView
        grafico.bottomAxis = createHorizontalAxis()
        grafico.startAxis = createVerticalAxis()
        with (grafico) {
            (startAxis as Axis).apply {
                title = "Altitudine (m)"
                //valueFormatter = yAxisValueFormatter
        }
        // 2. Collega il Producer
        grafico.entryProducer = viewModel.chartProducer

        // 3. Avvia la preparazione dei dati
        viewModel.preparaDatiGrafico(args.idTrack)

        // 4. CONFIGURA GLI ASSI IN SICUREZZA DENTRO AL POST
        //    Ora che gli assi esistono, possiamo configurarli tranquillamente
        //    dopo che la vista è stata misurata.
        grafico.post {
            with(grafico) {
                runInitialAnimation = false
                chartScrollSpec = ChartScrollSpec(isScrollEnabled = false)

                // Configurazione asse X
                (bottomAxis as Axis).apply {
                    //title = "Distanza (km)"
                    guideline = null
                    //valueFormatter = DefaultAxisValueFormatter(decimals = 0)
                    //itemPlacer = AxisItemPlacer.Horizontal.default(spacing = 1)
                }


                }
                (startAxis as Axis).apply {
                    title = "Altitudine (m)"
                    //valueFormatter = yAxisValueFormatter
                }
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
