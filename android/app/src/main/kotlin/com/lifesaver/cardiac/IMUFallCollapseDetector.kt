package com.lifesaver.cardiac

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.util.ArrayDeque
import kotlin.math.abs
import kotlin.math.sqrt

sealed interface FallCandidateEvent {
    val candidateId: String
    val occurredAtEpochMs: Long
    val impactGForce: Double

    data class Started(
        override val candidateId: String,
        override val occurredAtEpochMs: Long,
        override val impactGForce: Double,
    ) : FallCandidateEvent

    data class Verified(
        override val candidateId: String,
        override val occurredAtEpochMs: Long,
        override val impactGForce: Double,
        val reason: String,
        val sampleCount: Int,
        val postImpactStdDevG: Double,
    ) : FallCandidateEvent

    data class Rejected(
        override val candidateId: String,
        override val occurredAtEpochMs: Long,
        override val impactGForce: Double,
        val reason: String,
        val sampleCount: Int,
        val postImpactStdDevG: Double?,
    ) : FallCandidateEvent
}

class IMUFallCollapseDetector(
    context: Context,
    private val isWearerSignalAvailable: () -> Boolean,
    private val onCandidateEvent: (FallCandidateEvent) -> Boolean,
    private val nowEpochMs: () -> Long = { System.currentTimeMillis() },
) : SensorEventListener {

    private val sensorManager =
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val postImpactSamples = ArrayDeque<Double>()

    private var isStarted = false
    private var isImpactDetected = false
    private var impactDetectedAtMs = 0L
    private var impactGForce = 0.0
    private var activeCandidateId: String? = null
    private var candidateSequence = 0L
    private var immobilityCheckRunnable: Runnable? = null

    companion object {
        private const val TAG = "IMUCollapseDetector"

        // These values are provisional engineering defaults, not clinical thresholds.
        private const val IMPACT_THRESHOLD_G = 4.0
        private const val IMMOBILITY_WINDOW_MS = 10_000L
        private const val POST_IMPACT_SETTLE_MS = 1_500L
        private const val MAX_SAMPLE_COUNT = 500
        private const val MIN_SAMPLE_COUNT = 30
        private const val MAX_IMMOBILITY_STDDEV_G = 0.08
        private const val MAX_MEAN_GRAVITY_DEVIATION_G = 0.35
    }

    fun start() {
        if (isStarted) return

        val sensor = accelerometer
        if (sensor == null) {
            Log.e(TAG, "No accelerometer is available on this device.")
            return
        }

        sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME)
        isStarted = true
        Log.i(TAG, "IMU fall-candidate detector started.")
    }

    fun stop() {
        if (!isStarted) return

        sensorManager.unregisterListener(this)
        rejectActiveCandidate("MONITORING_STOPPED")
        resetImpactWindow()
        isStarted = false
        Log.i(TAG, "IMU fall-candidate detector stopped.")
    }

    override fun onSensorChanged(event: SensorEvent?) {
        val sensorEvent = event ?: return
        if (sensorEvent.sensor.type != Sensor.TYPE_ACCELEROMETER) return

        val x = sensorEvent.values[0].toDouble()
        val y = sensorEvent.values[1].toDouble()
        val z = sensorEvent.values[2].toDouble()
        val magnitudeAcceleration = sqrt(x * x + y * y + z * z)
        val gForce = magnitudeAcceleration / SensorManager.GRAVITY_EARTH
        val now = nowEpochMs()

        if (!isImpactDetected) {
            if (gForce >= IMPACT_THRESHOLD_G) {
                proposeImpactWindow(now, gForce)
            }
            return
        }

        if (now - impactDetectedAtMs < POST_IMPACT_SETTLE_MS) return

        if (postImpactSamples.size >= MAX_SAMPLE_COUNT) {
            postImpactSamples.removeFirst()
        }
        postImpactSamples.addLast(gForce)
    }

    private fun proposeImpactWindow(now: Long, gForce: Double) {
        candidateSequence += 1L
        val candidateId = "impact-$now-$candidateSequence"
        val accepted = onCandidateEvent(
            FallCandidateEvent.Started(
                candidateId = candidateId,
                occurredAtEpochMs = now,
                impactGForce = gForce,
            ),
        )

        if (!accepted) {
            Log.i(TAG, "Impact candidate rejected by escalation state machine.")
            return
        }

        isImpactDetected = true
        impactDetectedAtMs = now
        impactGForce = gForce
        activeCandidateId = candidateId
        postImpactSamples.clear()

        immobilityCheckRunnable?.let(mainHandler::removeCallbacks)
        val scheduledCandidateId = candidateId
        immobilityCheckRunnable = Runnable {
            if (activeCandidateId == scheduledCandidateId) {
                evaluateImpactWindow(scheduledCandidateId)
            }
        }
        mainHandler.postDelayed(immobilityCheckRunnable!!, IMMOBILITY_WINDOW_MS)

        Log.w(TAG, "Impact candidate accepted at ${"%.2f".format(gForce)} g.")
    }

    private fun evaluateImpactWindow(expectedCandidateId: String) {
        if (activeCandidateId != expectedCandidateId || !isImpactDetected) return
        val candidateId = expectedCandidateId
        val samples = postImpactSamples.toList()
        val hasEnoughSamples = samples.size >= MIN_SAMPLE_COUNT
        val wearerSignalPresent = isWearerSignalAvailable()

        val mean = if (samples.isEmpty()) 0.0 else samples.average()
        val variance = if (samples.isEmpty()) {
            Double.MAX_VALUE
        } else {
            samples.sumOf { value ->
                val delta = value - mean
                delta * delta
            } / samples.size
        }
        val standardDeviation = sqrt(variance)
        val gravityLooksStationary =
            abs(mean - 1.0) <= MAX_MEAN_GRAVITY_DEVIATION_G
        val lowMotionDetected = standardDeviation <= MAX_IMMOBILITY_STDDEV_G
        val now = nowEpochMs()

        if (hasEnoughSamples && gravityLooksStationary && lowMotionDetected && wearerSignalPresent) {
            Log.w(
                TAG,
                "Fall candidate verified: impact=${"%.2f".format(impactGForce)} g, " +
                    "stddev=${"%.3f".format(standardDeviation)} g.",
            )
            onCandidateEvent(
                FallCandidateEvent.Verified(
                    candidateId = candidateId,
                    occurredAtEpochMs = now,
                    impactGForce = impactGForce,
                    reason = "IMPACT_WITH_POST_EVENT_IMMOBILITY",
                    sampleCount = samples.size,
                    postImpactStdDevG = standardDeviation,
                ),
            )
        } else {
            val reason = when {
                !hasEnoughSamples -> "INSUFFICIENT_SAMPLES"
                !gravityLooksStationary -> "GRAVITY_NOT_STATIONARY"
                !lowMotionDetected -> "POST_IMPACT_MOTION"
                !wearerSignalPresent -> "WEARER_SIGNAL_UNAVAILABLE"
                else -> "UNVERIFIED_CANDIDATE"
            }
            Log.i(
                TAG,
                "Fall candidate rejected: reason=$reason, samples=${samples.size}, " +
                    "stddev=${"%.3f".format(standardDeviation)}.",
            )
            onCandidateEvent(
                FallCandidateEvent.Rejected(
                    candidateId = candidateId,
                    occurredAtEpochMs = now,
                    impactGForce = impactGForce,
                    reason = reason,
                    sampleCount = samples.size,
                    postImpactStdDevG = standardDeviation.takeIf { it.isFinite() },
                ),
            )
        }

        resetImpactWindow()
    }

    private fun rejectActiveCandidate(reason: String) {
        val candidateId = activeCandidateId ?: return
        onCandidateEvent(
            FallCandidateEvent.Rejected(
                candidateId = candidateId,
                occurredAtEpochMs = nowEpochMs(),
                impactGForce = impactGForce,
                reason = reason,
                sampleCount = postImpactSamples.size,
                postImpactStdDevG = null,
            ),
        )
    }

    private fun resetImpactWindow() {
        immobilityCheckRunnable?.let(mainHandler::removeCallbacks)
        immobilityCheckRunnable = null
        postImpactSamples.clear()
        isImpactDetected = false
        impactDetectedAtMs = 0L
        impactGForce = 0.0
        activeCandidateId = null
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
