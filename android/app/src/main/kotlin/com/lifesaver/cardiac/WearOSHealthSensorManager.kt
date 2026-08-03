package com.lifesaver.cardiac

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log

class WearOSHealthSensorManager(
    context: Context,
    private val onTelemetryListener: (Map<String, Any>) -> Unit,
) : SensorEventListener {

    private val sensorManager =
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val heartRateSensor: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE)

    @Volatile
    private var lastValidHeartRateAtMs: Long = 0L

    private var isMonitoring = false

    companion object {
        private const val TAG = "WearOSHealthSensorMgr"
        private const val MIN_VALID_BPM = 25f
        private const val MAX_VALID_BPM = 240f
    }

    fun startMonitoring() {
        if (isMonitoring) return

        val sensor = heartRateSensor
        if (sensor == null) {
            Log.e(TAG, "No heart-rate sensor is available on this device.")
            onTelemetryListener(
                mapOf(
                    "sensorAvailable" to false,
                    "timestamp" to System.currentTimeMillis(),
                ),
            )
            return
        }

        sensorManager.registerListener(
            this,
            sensor,
            SensorManager.SENSOR_DELAY_NORMAL,
        )
        isMonitoring = true

        onTelemetryListener(
            mapOf(
                "sensorAvailable" to true,
                "timestamp" to System.currentTimeMillis(),
            ),
        )
    }

    fun stopMonitoring() {
        if (!isMonitoring) return
        sensorManager.unregisterListener(this)
        isMonitoring = false
    }

    fun hasRecentValidHeartRate(maxAgeMs: Long = 30_000L): Boolean {
        val measuredAt = lastValidHeartRateAtMs
        return measuredAt > 0L && System.currentTimeMillis() - measuredAt <= maxAgeMs
    }

    override fun onSensorChanged(event: SensorEvent?) {
        val sensorEvent = event ?: return
        if (sensorEvent.sensor.type != Sensor.TYPE_HEART_RATE || sensorEvent.values.isEmpty()) {
            return
        }

        val bpm = sensorEvent.values[0]
        val isValidReading = bpm in MIN_VALID_BPM..MAX_VALID_BPM
        if (isValidReading) {
            lastValidHeartRateAtMs = System.currentTimeMillis()
        }

        onTelemetryListener(
            mapOf(
                "heartRate" to bpm,
                "accuracy" to sensorEvent.accuracy,
                "readingValid" to isValidReading,
                "timestamp" to System.currentTimeMillis(),
            ),
        )
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
