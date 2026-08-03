package com.lifesaver.cardiac

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log

class WearOSHealthSensorManager(
    context: Context,
    private val onTelemetryListener: (Map<String, Any>) -> Unit
) : SensorEventListener {

    private val sensorManager: SensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private var heartRateSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE)
    private var isMonitoring = false

    companion object {
        private const val TAG = "WearOSHealthSensorMgr"
    }

    fun startMonitoring() {
        if (isMonitoring) return
        heartRateSensor?.let { sensor ->
            sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_FASTEST)
        }
        isMonitoring = true
    }

    fun stopMonitoring() {
        if (!isMonitoring) return
        sensorManager.unregisterListener(this)
        isMonitoring = false
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event?.let { e ->
            if (e.sensor.type == Sensor.TYPE_HEART_RATE && e.values.isNotEmpty()) {
                val bpm = e.values[0]
                val telemetryData = mapOf(
                    "heartRate" to bpm,
                    "accuracy" to e.accuracy,
                    "timestamp" to System.currentTimeMillis()
                )
                onTelemetryListener(telemetryData)
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}