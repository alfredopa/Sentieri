package com.apstudio.sentieri

import android.app.Activity
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.activity
import com.apstudio.sentieri.databinding.FragmentAltGrafBinding
import com.apstudio.sentieri.db.SentieriRepo
import com.patrykandpatrick.vico.core.axis.Axis
import com.patrykandpatrick.vico.core.entry.ChartEntryModelProducer
import com.patrykandpatrick.vico.core.entry.FloatEntry
import org.osmdroid.util.GeoPoint
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import androidx.navigation.fragment.navArgs

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
            // entryProducer è già stato impostato sopra
            (bottomAxis as Axis).guideline = null
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        if (isRemoving) {
            activity?.requestedOrientation = originalOrientation
        }
    }
}
