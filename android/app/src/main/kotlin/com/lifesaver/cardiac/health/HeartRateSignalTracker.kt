package com.lifesaver.cardiac.health

data class HeartRateSignal(
    val bpm: Double,
    val measuredAtEpochMs: Long,
    val receivedAtEpochMs: Long,
    val accuracyLabel: String?,
)

sealed interface HeartRateSignalResult {
    data class Accepted(val signal: HeartRateSignal) : HeartRateSignalResult
    data class Rejected(val reason: String) : HeartRateSignalResult
}

/**
 * Pure signal-quality and freshness gate shared by Health Services callbacks
 * and the fall-candidate detector. No raw samples are persisted.
 */
class HeartRateSignalTracker(
    private val minimumBpm: Double = MINIMUM_BPM,
    private val maximumBpm: Double = MAXIMUM_BPM,
    private val defaultFreshnessMs: Long = DEFAULT_FRESHNESS_MS,
) {
    private var latestSignal: HeartRateSignal? = null

    init {
        require(minimumBpm > 0.0)
        require(maximumBpm > minimumBpm)
        require(defaultFreshnessMs > 0L)
    }

    @Synchronized
    fun accept(
        bpm: Double,
        measuredAtEpochMs: Long,
        receivedAtEpochMs: Long,
        accuracyLabel: String?,
    ): HeartRateSignalResult {
        if (!bpm.isFinite() || bpm !in minimumBpm..maximumBpm) {
            return HeartRateSignalResult.Rejected("HEART_RATE_OUT_OF_RANGE")
        }
        if (measuredAtEpochMs < 0L || receivedAtEpochMs < measuredAtEpochMs) {
            return HeartRateSignalResult.Rejected("INVALID_HEART_RATE_TIMESTAMP")
        }
        val previous = latestSignal
        if (previous != null && measuredAtEpochMs < previous.measuredAtEpochMs) {
            return HeartRateSignalResult.Rejected("OUT_OF_ORDER_HEART_RATE")
        }

        val accepted = HeartRateSignal(
            bpm = bpm,
            measuredAtEpochMs = measuredAtEpochMs,
            receivedAtEpochMs = receivedAtEpochMs,
            accuracyLabel = accuracyLabel?.take(MAX_ACCURACY_LABEL_LENGTH),
        )
        latestSignal = accepted
        return HeartRateSignalResult.Accepted(accepted)
    }

    @Synchronized
    fun latest(): HeartRateSignal? = latestSignal

    @Synchronized
    fun hasRecentValidSignal(
        nowEpochMs: Long,
        maxAgeMs: Long = defaultFreshnessMs,
    ): Boolean {
        require(maxAgeMs > 0L)
        val signal = latestSignal ?: return false
        if (nowEpochMs < signal.measuredAtEpochMs) return false
        return nowEpochMs - signal.measuredAtEpochMs <= maxAgeMs
    }

    @Synchronized
    fun clear() {
        latestSignal = null
    }

    companion object {
        const val MINIMUM_BPM = 25.0
        const val MAXIMUM_BPM = 240.0
        const val DEFAULT_FRESHNESS_MS = 30_000L
        private const val MAX_ACCURACY_LABEL_LENGTH = 80
    }
}
