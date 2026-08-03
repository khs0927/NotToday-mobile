package com.lifesaver.cardiac.escalation

/**
 * Produces epoch timestamps that never move backwards within a process.
 *
 * Epoch milliseconds remain useful for persisted audit records, while the
 * in-process clamp prevents wall-clock corrections from creating false
 * OUT_OF_ORDER_EVENT rejections.
 */
class MonotonicEpochClock(
    initialEpochMs: Long = Long.MIN_VALUE,
) {
    private var lastEpochMs = initialEpochMs

    @Synchronized
    fun now(): Long {
        val wallClockNow = System.currentTimeMillis()
        val nextEpochMs = maxOf(wallClockNow, lastEpochMs)
        lastEpochMs = nextEpochMs
        return nextEpochMs
    }

    @Synchronized
    fun observe(epochMs: Long) {
        if (epochMs > lastEpochMs) {
            lastEpochMs = epochMs
        }
    }
}
