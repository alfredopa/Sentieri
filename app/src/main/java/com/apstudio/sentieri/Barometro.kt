package com.apstudio.sentieri

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.location.OnNmeaMessageListener
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.apstudio.sentieri.databinding.FragmentBarometroBinding
import kotlin.math.pow

// TODO: Rename parameter arguments, choose names that match
// the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
private const val ARG_PARAM1 = "param1"
private const val ARG_PARAM2 = "param2"

/**
 * A simple [Fragment] subclass.
 * Use the [Barometro.newInstance] factory method to
 * create an instance of this fragment.
 */
class Barometro : Fragment() , SensorEventListener {
    // TODO: Rename and change types of parameters
    private var param1: String? = null
    private var param2: String? = null
    private lateinit var viewModel: SentieriViewModel

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
        viewModel = ViewModelProvider(requireActivity().applicationContext as AppSentieri)[SentieriViewModel::class.java]
        arguments?.let {
            param1 = it.getString(ARG_PARAM1)
            param2 = it.getString(ARG_PARAM2)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Inflate the layout for this fragment
        binding = FragmentBarometroBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        sensorManager = requireActivity().getSystemService(Context.SENSOR_SERVICE) as SensorManager
        pressure = sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE)
        // Il valore NORMAL_PRESSURE viene calibrato, se inserito valore a livello mare si utilizza direttamente
        // se inserita altitudine si calcola la pressione a livello mare
        // pulsante imposta pressione alla quota 0 click listener
        binding.btnPress.setOnClickListener {
            NORMAL_PRESSURE = binding.EditPressione.text.toString().toFloat()
            viewModel.NORMAL_PRESSURE = NORMAL_PRESSURE
            //Log.d("barometro","pressione ${viewModel.NORMAL_PRESSURE}")
            viewModel.baroCalibrato(true)
        }
        // pulsante Altitudine conosciuta  click listener calcola equivalente pressione a livello mare
        binding.btnAlti.setOnClickListener {
            NORMAL_PRESSURE = MapUtils.getSealevelPressure(binding.EditAltitud.text.toString().toFloat(), millibarsOfPressure )
            binding.EditPressione.setText(NORMAL_PRESSURE. toString())
            viewModel.NORMAL_PRESSURE = NORMAL_PRESSURE
            //Log.d("barometro","pressione ${viewModel.NORMAL_PRESSURE}")
            viewModel.baroCalibrato(true)
        }
        // Crea un LocationListener
        locationListener = LocationListener { p0 -> datiGPS(p0) }

        // Crea e registra NMEA listener
        nmeaListener = OnNmeaMessageListener { message, timestamp ->
            // Do something with NMEA message $GPGGA
            loggaNMEA(message)
        }

    }

    companion object {
        private const val MIN_DIST = 0
        private const val MIN_PERIOD = 10000
        /**
         * Use this factory method to create a new instance of
         * this fragment using the provided parameters.
         *
         * @param param1 Parameter 1.
         * @param param2 Parameter 2.
         * @return A new instance of fragment Barometro.
         */
        // TODO: Rename and change types and number of parameters
        @JvmStatic
        fun newInstance(param1: String, param2: String) =
            Barometro().apply {
                arguments = Bundle().apply {
                    putString(ARG_PARAM1, param1)
                    putString(ARG_PARAM2, param2)
                }
            }
    }

    override fun onSensorChanged(p0: SensorEvent?) {
        if (p0 != null) {
            if (p0.sensor.type == Sensor.TYPE_PRESSURE) {
                millibarsOfPressure = p0.values[0]
                binding.tvPress.text = millibarsOfPressure.toString()
                // utilizzo formula ipsometrica
                val altiRif = ( BAROMETRIC_CONSTANT * (1 - (millibarsOfPressure / NORMAL_PRESSURE).pow(
                    EXPONENTIAL_COEFFICIENT )))
                binding.tvTemp.text = MapUtils.formatDecimal(altiRif)

                // utilizzo  formula con gradiente barometrico
                //val altiRif = MapUtils.calcolaAltitudine(millibarsOfPressure.toFloat(), viewModel.NORMAL_PRESSURE)
                //binding.tvTemp.text = MapUtils.formatDecimal(altiRif.toFloat())
            }
        }
    }

    override fun onAccuracyChanged(p0: Sensor?, p1: Int) {
        // test
    }

    override fun onResume() {
        // Register a listener for the sensor.
        super.onResume()
        sensorManager.registerListener(this, pressure, letturatime)

        locationManager = requireActivity().getSystemService(AppCompatActivity.LOCATION_SERVICE) as LocationManager
        if (!locationManager!!.isProviderEnabled(providerId)) {
            val gpsOptionsIntent = Intent(
                Settings.ACTION_LOCATION_SOURCE_SETTINGS
            )
            startActivity(gpsOptionsIntent)
        } else if (ActivityCompat.checkSelfPermission(
                requireActivity(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                requireActivity(),
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        binding.tvMsl.text = "In attesa del GPS"
        locationManager!!.requestLocationUpdates(
            providerId,
            MIN_PERIOD.toLong(),
            MIN_DIST.toFloat(),
            locationListener
        )
        locationManager!!.addNmeaListener(nmeaListener)
    }

    override fun onPause() {
        // Be sure to unregister the sensor when the activity pauses.
        super.onPause()
        sensorManager.unregisterListener(this)
        locationManager!!.removeNmeaListener(nmeaListener)
        locationManager!!.removeUpdates(locationListener)
    }

    fun datiGPS(p0: Location) {
        "GPS " + MapUtils.formatDecimal(p0.altitude.toFloat())
        //binding.tvGPS.text = altiGPS
    }

    private fun loggaNMEA(message : String) {
        //  $GPGGA,113951.00,3913.488983,N,00906.041103,E,1,03,1.6,0.0,M,46.8,M,,*6A
        if (message.startsWith('$'+"GPGGA") or message.startsWith('$'+"GNGGA")) {
            val nmeaSplit = message.split(",")
            val fixQuality = nmeaSplit[6]
            val mslAltitude = nmeaSplit[9].toDoubleOrNull()
            if (fixQuality == "1" && mslAltitude != null) {
                binding.tvMsl.text = mslAltitude.toString()
            }
        }
        //Log.d("NMEA", message)
    }
}