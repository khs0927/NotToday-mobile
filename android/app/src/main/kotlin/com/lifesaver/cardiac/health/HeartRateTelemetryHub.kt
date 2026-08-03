package com.lifesaver.cardiac.health

import java.util.concurrent.CopyOnWriteArraySet

/**
 * Process-local bridge between Health Services' PassiveListenerService and the
 * foreground Flutter bridge. It deliberately stores only the latest accepted
 * sample in memory.
 */
object HeartRateTelemetryHub {
    private val tracker = HeartRateSignalTracker()
    private val listeners = CopyOnWriteArraySet<(Map<String, Any>) -> Unit>()

    @Volatile
    private var serviceState: String = "IDLE"

    fun addListener(listener: (Map<String, Any>) -> Unit) {
        listeners += listener
        latestTelemetry()?.let(listener)
    }

    fun removeListener(listener: (Map<String, Any>) -> Unit) {
        listeners -= listener
    }

    fun publishServiceState(
        state: String,
        sensorAvailable: Boolean? = null,
        reason: String? = null,
        timestampEpochMs: Long = System.currentTimeMillis(),
    ) {
        serviceState = state.take(MAX_STATE_LENGTH)
        val event = mutableMapOf<String, Any>(
            "healthServiceState" to serviceState,
            "timestamp" to timestampEpochMs,
            "heartRateSource" to "HEALTH_SERVICES_PASSIVE",
        )
        sensorAvailable?.let { event["sensorAvailable"] = it }
        reason?.let { event["healthServiceReason"] = it.take(MAX_REASON_LENGTH) }
        publish(event)
    }

    fun publishReading(
        bpm: Double,
        measuredAtEpochMs: Long,
        receivedAtEpochMs: Long,
        accuracyLabel: String?,
    ): HeartRateSignalResult {
        val result = tracker.accept(
            bpm = bpm,
            measuredAtEpochMs = measuredAtEpochMs,
            receivedAtEpochMs = receivedAtEpochMs,
            accuracyLabel = accuracyLabel,
        )

        when (result) {
            is HeartRateSignalResult.Accepted -> {
                serviceState = "ACTIVE"
                publish(signalToTelemetry(result.signal))
            }
            is HeartRateSignalResult.Rejected -> publish(
                mapOf(
                    "healthServiceState" to serviceState,
                    "heartRateSource" to "HEALTH_SERVICES_PASSIVE",
                    "readingValid" to false,
                    "heartRateRejectionReason" to result.reason,
                    "timestamp" to receivedAtEpochMs,
                ),
            )
        }
        return result
    }

    fun clear(
        reason: String,
        timestampEpochMs: Long = System.currentTimeMillis(),
    ) {
        tracker.clear()
        serviceState = "PERMISSION_LOST"
        publish(
            mapOf(
                "healthServiceState" to serviceState,
                "heartRateSource" to "HEALTH_SERVICES_PASSIVE",
                "readingValid" to false,
                "sensorAvailable" to false,
                "healthServiceReason" to reason.take(MAX_REASON_LENGTH),
                "timestamp" to timestampEpochMs,
            ),
        )
    }

    fun hasRecentValidHeartRate(
        nowEpochMs: Long = System.currentTimeMillis(),
        maxAgeMs: Long = HeartRateSignalTracker.DEFAULT_FRESHNESS_MS,
    ): Boolean = tracker.hasRecentValidSignal(nowEpochMs, maxAgeMs)

    fun latestTelemetry(): Map<String, Any>? =
        tracker.latest()?.let(::signalToTelemetry)

    private fun signalToTelemetry(signal: HeartRateSignal): Map<String, Any> {
        val event = mutableMapOf<String, Any>(
            "heartRate" to signal.bpm,
            "measuredAtEpochMs" to signal.measuredAtEpochMs,
            "timestamp" to signal.receivedAtEpochMs,
            "readingValid" to true,
            "sensorAvailable" to true,
            "healthServiceState" to serviceState,
            "heartRateSource" to "HEALTH_SERVICES_PASSIVE",
        )
        signal.accuracyLabel?.let { event["accuracyLabel"] = it }
        return event
    }

    private fun publish(event: Map<String, Any>) {
        listeners.forEach { listener ->
            runCatching { listener(event) }
        }
    }

    private const val MAX_STATE_LENGTH = 64
    private const val MAX_REASON_LENGTH = 160
}
