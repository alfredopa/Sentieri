package com.apstudio.sentieri

import android.app.Activity
import android.content.pm.ActivityInfo
import android.os.Bundle
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


class AltGrafFragment : Fragment() {
    private lateinit var viewModel: SentieriViewModel
    private var originalOrientation: Int = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    private lateinit var binding: FragmentAltGrafBinding
    //private val chartEntryModelProducer: ChartEntryModelProducer = ChartEntryModelProducer()

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

        // Forza l'orientamento solo la prima volta
        if (savedInstanceState == null) {
            originalOrientation = requireActivity().requestedOrientation
            requireActivity().requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }

        // --- INIZIO DELLA LOGICA CORRETTA ---

        // 1. Collega la ChartView al Producer che vive nel ViewModel
        binding.chartView.entryProducer = viewModel.chartProducer

        // 2. Chiama la funzione di preparazione nel ViewModel.
        //    La logica interna del ViewModel impedirà esecuzioni multiple.
        viewModel.preparaDatiGrafico()

        // 3. (Opzionale ma consigliato) Forza un aggiornamento della vista
        //    se il modello esiste già (caso post-rotazione)
        viewModel.chartProducer.getModel()?.let { model ->
            viewModel.chartProducer.setEntries(model.entries.first())
        }

        // --- FINE DELLA LOGICA CORRETTA ---

        // Configurazione degli Assi
        val bottomAxis = binding.chartView.bottomAxis as Axis
        bottomAxis.title = "Distanza (km)"
        bottomAxis.guideline = null

        val startAxis = binding.chartView.startAxis as Axis
        startAxis.title = "Altitudine (m)"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Quando la vista viene distrutta, resetta l'orientamento
        activity?.requestedOrientation = originalOrientation

        // Quando l'utente esce *definitivamente* dal fragment (es. tasto back),
        // puliamo i dati del grafico nel ViewModel.
        if (isRemoving) {
            viewModel.pulisciDatiGrafico()
        }
    }
}
