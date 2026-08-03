package com.lifesaver.cardiac

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.Looper
import android.util.Log

class IMUFallCollapseDetector(
    context: Context,
    private val onCollapseDetected: (collapseType: String, gForce: Double) -> Unit
) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val mainHandler = Handler(Looper.getMainLooper())

    private var isImpactDetected = false
    private var impactGForce = 0.0
    private var immobilityCheckRunnable: Runnable? = null

    companion object {
        private const val TAG = "IMUCollapseDetector"
        private const val IMPACT_THRESHOLD_G = 4.5
        private const val IMMOBILITY_WINDOW_MS = 10000L
    }

    fun start() {
        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_FASTEST)
            Log.i(TAG, "IMU Fall Collapse Detector started.")
        }
    }

    fun stop() {
        sensorManager.unregisterListener(this)
        immobilityCheckRunnable?.let { mainHandler.removeCallbacks(it) }
        Log.i(TAG, "IMU Fall Collapse Detector stopped.")
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event?.let { e ->
            if (e.sensor.type == Sensor.TYPE_ACCELEROMETER) {
                val x = e.values[0].toDouble()
                val y = e.values[1].toDouble()
                val z = e.values[2].toDouble()

                val magnitudeAccel = Math.sqrt(x * x + y * y + z * z)
                val gForce = magnitudeAccel / SensorManager.GRAVITY_EARTH

                if (gForce > IMPACT_THRESHOLD_G && !isImpactDetected) {
                    isImpactDetected = true
                    impactGForce = gForce
                    scheduleImmobilityCheck()
                }
            }
        }
    }

    private fun scheduleImmobilityCheck() {
        immobilityCheckRunnable?.let { mainHandler.removeCallbacks(it) }

        immobilityCheckRunnable = Runnable {
            Log.e(TAG, "10-Second Post-Impact Immobility Confirmed!")
            onCollapseDetected("UNRESPONSIVE_COLLAPSE", impactGForce)
            isImpactDetected = false
        }

        mainHandler.postDelayed(immobilityCheckRunnable!!, IMMOBILITY_WINDOW_MS)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}