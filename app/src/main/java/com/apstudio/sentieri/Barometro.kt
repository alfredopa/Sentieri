package com.apstudio.sentieri

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.LocationListener
import android.location.LocationManager
import android.location.OnNmeaMessageListener
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import com.apstudio.sentieri.databinding.FragmentBarometroBinding
import com.apstudio.sentieri.db.LocationRepository
import kotlin.math.pow

private const val ARG_PARAM1 = "param1"
private const val ARG_PARAM2 = "param2"

class Barometro : Fragment(), SensorEventListener {
    private var param1: String? = null
    private var param2: String? = null

    private val locationModel = LocationRepository

    private lateinit var binding: FragmentBarometroBinding
    private lateinit var sensorManager: SensorManager
    private var pressure: Sensor? = null
    private var millibarsOfPressure = 0.0F
    private var NORMAL_PRESSURE = 1013.25F
    private val BAROMETRIC_CONSTANT = 44330.0F
    private val EXPONENTIAL_COEFFICIENT = 1 / 5.256F
    private val providerId = LocationManager.GPS_PROVIDER
    private var locationManager: LocationManager? = null
    private lateinit var locationListener: LocationListener
    private val letturatime = 1500000 // 1,5 secondi in microsecondi
    private lateinit var nmeaListener: OnNmeaMessageListener

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            param1 = it.getString(ARG_PARAM1)
            param2 = it.getString(ARG_PARAM2)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentBarometroBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        sensorManager = requireActivity().getSystemService(Context.SENSOR_SERVICE) as SensorManager
        pressure = sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE)

        binding.btnPress.setOnClickListener {
            val input = binding.EditPressione.text.toString()
            if (input.isNotEmpty()) {
                NORMAL_PRESSURE = input.toFloat()
                locationModel.baroCalibrato(true)
            }
        }

        binding.btnAlti.setOnClickListener {
            val input = binding.EditAltitud.text.toString()
            if (input.isNotEmpty()) {
                NORMAL_PRESSURE = MapUtils.getSealevelPressure(input.toFloat(), millibarsOfPressure)
                binding.EditPressione.setText(NORMAL_PRESSURE.toString())
                locationModel.baroCalibrato(true)
            }
        }

        locationListener = LocationListener { _ -> datiGPS() }

        nmeaListener = OnNmeaMessageListener { message, _ ->
            loggaNMEA(message)
        }
    }

    override fun onSensorChanged(p0: SensorEvent?) {
        if (p0 != null && p0.sensor.type == Sensor.TYPE_PRESSURE) {
            millibarsOfPressure = p0.values[0]
            binding.tvPress.text = millibarsOfPressure.toString()
            val altiRif = (BAROMETRIC_CONSTANT * (1 - (millibarsOfPressure / NORMAL_PRESSURE).pow(EXPONENTIAL_COEFFICIENT)))
            binding.tvTemp.text = MapUtils.formatDecimal(altiRif)
        }
    }

    override fun onAccuracyChanged(p0: Sensor?, p1: Int) {}

    override fun onResume() {
        super.onResume()
        sensorManager.registerListener(this, pressure, letturatime)

        locationManager = requireActivity().getSystemService(Context.LOCATION_SERVICE) as LocationManager
        if (!locationManager!!.isProviderEnabled(providerId)) {
            val gpsOptionsIntent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            startActivity(gpsOptionsIntent)
        } else if (ActivityCompat.checkSelfPermission(requireActivity(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(requireActivity(), Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        binding.tvMsl.text = "In attesa del GPS"
        locationManager!!.requestLocationUpdates(providerId, MIN_PERIOD.toLong(), MIN_DIST.toFloat(), locationListener)
        @Suppress("DEPRECATION")
        locationManager!!.addNmeaListener(nmeaListener)
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
        locationManager?.removeNmeaListener(nmeaListener)
        locationManager?.removeUpdates(locationListener)
    }

    fun datiGPS() {
        // Implementation if needed
    }

    private fun loggaNMEA(message: String) {
        if (message.startsWith($$"$GPGGA") || message.startsWith($$"$GNGGA")) {
            val nmeaSplit = message.split(",")
            if (nmeaSplit.size > 9) {
                val fixQuality = nmeaSplit[6]
                val mslAltitude = nmeaSplit[9].toDoubleOrNull()
                if (fixQuality == "1" && mslAltitude != null) {
                    binding.tvMsl.text = mslAltitude.toString()
                }
            }
        }
    }

    companion object {
        private const val MIN_DIST = 0
        private const val MIN_PERIOD = 10000
    }
}
