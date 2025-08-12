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
    private val chartEntryModelProducer: ChartEntryModelProducer = ChartEntryModelProducer()

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
        originalOrientation = activity?.requestedOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        chartEntryModelProducer.setEntries(getpunti())
        val grafico = binding.chartView
        with(grafico) {
            runInitialAnimation = false
            entryProducer = chartEntryModelProducer
            (bottomAxis as Axis).guideline = null
        }
    }

    private fun getpunti(): MutableList<FloatEntry> {
        val listPunti = mutableListOf<FloatEntry>()

        //  utilizza PointReducer (algoritmo douglasPeuckerReduction da osmdroid.util) per la riduzione del numero di punti
        val listEle: ArrayList<GeoPoint> =
            MapUtils.douglasPeucker(viewModel.geoPuntiPercorso as ArrayList<GeoPoint>, 200.0)
        var quota: Float
        var punto: FloatEntry

        listEle.forEach {
            quota = it.altitude.toFloat()
            punto = FloatEntry(listEle.indexOf(it).toFloat(), quota)
            listPunti.add(punto)
        }
        return listPunti
    }

    override fun onDestroyView() {
        super.onDestroyView()
        activity?.requestedOrientation = originalOrientation
    }
}

