package com.apstudio.sentieri

import android.app.Activity
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
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
    private val viewModel: SentieriViewModel by activityViewModels {
        SentieriFactory(
            SentieriRepo(requireActivity())
        )
    }
    private var originalOrientation: Int = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    private lateinit var binding: FragmentAltGrafBinding
    private val chartEntryModelProducer: ChartEntryModelProducer = ChartEntryModelProducer()

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

        //  utilizza PointReducer (algoritmo douglasPeuckerReduction) in osmdroid.util per la riduzione del numero di punti
        val listEle: ArrayList<GeoPoint> =
            douglasPeucker(viewModel.geoPuntiPercorso as ArrayList<GeoPoint>, 200.0)
        var quota: Float
        var punto: FloatEntry

        listEle.forEach {
            quota = it.altitude.toFloat()
            punto = FloatEntry(listEle.indexOf(it).toFloat(), quota)
            listPunti.add(punto)
        }
        return listPunti
    }

    // riduzione dei punti
    private fun douglasPeucker(points: ArrayList<GeoPoint>, epsilon: Double): ArrayList<GeoPoint> {
        if (points.size < 3) return points

        // Trova il punto con la massima distanza dalla linea
        var dmax = 0.0
        var index = 0
        val end = points.size - 1
        for (i in 1 until end) {
            val d = perpendicularDistance(points[i], points[0], points[end])
            if (d > dmax) {
                index = i
                dmax = d
            }
        }

        // Se la massima distanza è maggiore di epsilon, ricorsivamente semplifica
        if (dmax > epsilon) {
            val recResults1 = douglasPeucker(ArrayList(points.subList(0, index + 1)), epsilon)
            val recResults2= douglasPeucker(ArrayList(points.subList(index, end + 1)), epsilon)

            // Costruisci la lista dei risultati
            val result = ArrayList<GeoPoint>(recResults1.subList(0, recResults1.size - 1))
            result.addAll(recResults2)
            return result
        } else {
            // Restituisci solo il primo e l'ultimo punto
            return arrayListOf(points[0], points[end])
        }
    }

    // Calcola la distanza perpendicolare da un punto a una linea
    private fun perpendicularDistance(pt: GeoPoint, lineStart: GeoPoint, lineEnd: GeoPoint): Double {
        val dx = lineEnd.longitude - lineStart.longitude
        val dy = lineEnd.latitude - lineStart.latitude

        val mag = sqrt(dx * dx + dy * dy)
        if (mag > 0.0) {
            val u = ((pt.longitude - lineStart.longitude) * dx + (pt.latitude - lineStart.latitude) * dy) / (mag * mag)

            if (u <= 0.0)return distance(pt, lineStart)
            if (u >= 1.0)
                return distance(pt, lineEnd)

            val intersection = GeoPoint(
                lineStart.latitude + u * dy,
                lineStart.longitude + u * dx
            )
            return distance(pt, intersection)
        }
        return 0.0
    }

    // Calcola la distanza tra due punti
    private fun distance(pt1: GeoPoint, pt2: GeoPoint): Double {
        val lat1 = Math.toRadians(pt1.latitude)
        val lon1 = Math.toRadians(pt1.longitude)
        val lat2 = Math.toRadians(pt2.latitude)
        val lon2 = Math.toRadians(pt2.longitude)

        val dLat = lat2 - lat1
        val dLon = lon2 - lon1

        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(lat1) * cos(lat2) *
                sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))

        val r = 6371e3 // Raggio medio della Terra in metri
        return r * c
    }

    override fun onDestroyView() {
        super.onDestroyView()
        activity?.requestedOrientation = originalOrientation
    }
}

