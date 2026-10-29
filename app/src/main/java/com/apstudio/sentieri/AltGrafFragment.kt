// File: AltGrafFragment.kt

package com.apstudio.sentieri

import android.content.pm.ActivityInfo
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.apstudio.sentieri.databinding.FragmentAltGrafBinding
import com.apstudio.sentieri.db.SentieriDB
import com.apstudio.sentieri.db.SentieriRepo
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import kotlinx.coroutines.launch
import androidx.core.graphics.toColorInt

class AltGrafFragment : Fragment() {
    private val viewModel: SentieriViewModel by activityViewModels {
        val application = requireActivity().application
        val database = SentieriDB.getInstance(application)
        val repository = SentieriRepo(
            sentieriDao = database.sentieriDao(),
            trackDao = database.trackDao(),
            poiDao = database.poiDao(),
            fotoPoiDao = database.fotoPoiDao()
        )
        SentieriFactory(repository, application)
    }
    private lateinit var binding: FragmentAltGrafBinding
    private var originalOrientation: Int = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED

    private var currentChartType = SentieriViewModel.ChartType.ALTITUDE
    private val activeEbikeSeries = mutableSetOf<SentieriViewModel.TelemetrySeries>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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

        requireActivity().requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE

        (activity as? AppCompatActivity)?.supportActionBar?.hide()

        val window = requireActivity().window
        WindowInsetsControllerCompat(window, window.decorView).let { controller ->
            controller.hide(WindowInsetsCompat.Type.statusBars())
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        setupControls()
        updateChart()
    }

    private fun setupControls() {
        binding.chartToggleGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                currentChartType = when (checkedId) {
                    R.id.btnAltitudine -> SentieriViewModel.ChartType.ALTITUDE
                    R.id.btnEbike -> SentieriViewModel.ChartType.EBIKE
                    R.id.btnCuore -> SentieriViewModel.ChartType.HEART_RATE
                    else -> SentieriViewModel.ChartType.ALTITUDE
                }

                binding.seriesChipGroup.visibility = if (currentChartType == SentieriViewModel.ChartType.EBIKE) View.VISIBLE else View.GONE

                if (currentChartType == SentieriViewModel.ChartType.EBIKE && activeEbikeSeries.isEmpty()) {
                    activeEbikeSeries.add(SentieriViewModel.TelemetrySeries.BATTERY)
                    activeEbikeSeries.add(SentieriViewModel.TelemetrySeries.ASSIST)
                    activeEbikeSeries.add(SentieriViewModel.TelemetrySeries.POWER)
                    activeEbikeSeries.add(SentieriViewModel.TelemetrySeries.CADENCE)
                }

                updateChart()
            }
        }

        val seriesChips = mapOf(
            R.id.chipBattery to SentieriViewModel.TelemetrySeries.BATTERY,
            R.id.chipAssist to SentieriViewModel.TelemetrySeries.ASSIST,
            R.id.chipPower to SentieriViewModel.TelemetrySeries.POWER,
            R.id.chipCadence to SentieriViewModel.TelemetrySeries.CADENCE
        )

        seriesChips.forEach { (id, series) ->
            binding.root.findViewById<View>(id).setOnClickListener {
                val chip = it as com.google.android.material.chip.Chip
                if (chip.isChecked) {
                    activeEbikeSeries.add(series)
                } else {
                    activeEbikeSeries.remove(series)
                }
                updateChart()
            }
        }
    }

    private fun updateChart() {
        viewLifecycleOwner.lifecycleScope.launch {
            val dataMap = viewModel.preparaDatiGraficoTelemetry(currentChartType, activeEbikeSeries.toList())

            if (dataMap.isEmpty()) {
                Log.w("GRAF", "Nessun dato da visualizzare nel grafico.")
                return@launch
            }

            val dataSets = mutableListOf<LineDataSet>()

            dataMap.forEach { (series, entries) ->
                val label = when (series) {
                    SentieriViewModel.TelemetrySeries.ALTITUDE -> "Profilo Altimetrico"
                    SentieriViewModel.TelemetrySeries.BATTERY -> "Batteria %"
                    SentieriViewModel.TelemetrySeries.ASSIST -> "Assist"
                    SentieriViewModel.TelemetrySeries.POWER -> "Potenza (W)"
                    SentieriViewModel.TelemetrySeries.CADENCE -> "Cadenza"
                    SentieriViewModel.TelemetrySeries.HR -> "Battito Cardiaco"
                }
                val color = when (series) {
                    SentieriViewModel.TelemetrySeries.ALTITUDE -> Color.RED
                    SentieriViewModel.TelemetrySeries.BATTERY -> Color.GREEN
                    SentieriViewModel.TelemetrySeries.ASSIST -> Color.BLUE
                    SentieriViewModel.TelemetrySeries.POWER -> Color.MAGENTA
                    SentieriViewModel.TelemetrySeries.CADENCE -> Color.CYAN
                    SentieriViewModel.TelemetrySeries.HR -> "#FF00FF".toColorInt()
                }

                val dataSet = LineDataSet(entries, label).apply {
                    this.color = color
                    setDrawFilled(series == SentieriViewModel.TelemetrySeries.ALTITUDE)
                    fillColor = if (series == SentieriViewModel.TelemetrySeries.ALTITUDE) Color.CYAN else Color.TRANSPARENT
                    mode = LineDataSet.Mode.CUBIC_BEZIER
                    setDrawCircles(false)
                    setDrawValues(false)
                    lineWidth = 2f
                }
                dataSets.add(dataSet)
            }

            val lineData = LineData(*dataSets.toTypedArray())

            binding.lineChart.apply {
                description.isEnabled = false
                legend.isEnabled = true
                isDragEnabled = false
                setScaleEnabled(false)

                val textColor = ContextCompat.getColor(requireContext(), R.color.grafico_text_color)
                xAxis.apply {
                    granularity = 1f
                    position = com.github.mikephil.charting.components.XAxis.XAxisPosition.BOTTOM
                    this.textColor = textColor
                    valueFormatter = object : ValueFormatter() {
                        override fun getFormattedValue(value: Float): String {
                            return "${value.toInt()} km"
                        }
                    }
                }

                axisLeft.apply {
                    this.textColor = textColor
                    valueFormatter = object : ValueFormatter() {
                        override fun getFormattedValue(value: Float): String {
                            val unit = when (currentChartType) {
                                SentieriViewModel.ChartType.ALTITUDE -> " m"
                                SentieriViewModel.ChartType.HEART_RATE -> " bpm"
                                SentieriViewModel.ChartType.EBIKE -> " val"
                            }
                            return "${value.toInt()}$unit"
                        }
                    }
                }
                axisRight.isEnabled = false
                data = lineData
                invalidate()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        (activity as? AppCompatActivity)?.supportActionBar?.show()

        val window = requireActivity().window
        WindowInsetsControllerCompat(window, window.decorView).show(WindowInsetsCompat.Type.statusBars())

        if (isRemoving) {
            activity?.requestedOrientation = originalOrientation
        }
    }
}
