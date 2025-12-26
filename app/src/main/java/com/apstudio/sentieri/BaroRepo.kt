package com.apstudio.sentieri

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.lifecycle.MutableLiveData

class BaroRepo (context: Context) {
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE)
    private val letturatime = 1_500_000 // 1,5 secondi in microsecondi
    val baroData = MutableLiveData<Float>()

    fun startSensorUpdates() {
        // imposta un tempo di lettura più corto
        sensorManager.registerListener(sensorEventListener, sensor, letturatime)
    }

    fun stopSensorUpdates() {
        sensorManager.unregisterListener(sensorEventListener)
    }

    fun getLatestPressure(): Float? {
        return if (baroData.isInitialized) baroData.value else null
    }

    private val sensorEventListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent?) {
            if (event != null) {
                baroData.value = event.values[0]
                //Log.d("Barorepo", "pressione  ${baroData.value}")
            }
        }
        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
            //Log.d("Barorepo", "accur  $accuracy")
        }
    }
}