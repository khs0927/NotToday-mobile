package com.lifesaver.cardiac.health

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HeartRateSignalTrackerTest {
    @Test
    fun validReadingIsAcceptedAndFresh() {
        val tracker = HeartRateSignalTracker()

        val result = tracker.accept(
            bpm = 72.0,
            measuredAtEpochMs = 1_000L,
            receivedAtEpochMs = 1_100L,
            accuracyLabel = "HIGH",
        )

        assertTrue(result is HeartRateSignalResult.Accepted)
        assertTrue(tracker.hasRecentValidSignal(nowEpochMs = 31_000L))
        assertFalse(tracker.hasRecentValidSignal(nowEpochMs = 31_001L))
    }

    @Test
    fun outOfRangeReadingDoesNotBecomeWearerSignal() {
        val tracker = HeartRateSignalTracker()

        val result = tracker.accept(
            bpm = 5.0,
            measuredAtEpochMs = 1_000L,
            receivedAtEpochMs = 1_000L,
            accuracyLabel = null,
        )

        assertEquals(
            HeartRateSignalResult.Rejected("HEART_RATE_OUT_OF_RANGE"),
            result,
        )
        assertFalse(tracker.hasRecentValidSignal(nowEpochMs = 1_000L))
        assertNull(tracker.latest())
    }

    @Test
    fun outOfOrderReadingIsRejectedWithoutReplacingLatest() {
        val tracker = HeartRateSignalTracker()
        tracker.accept(70.0, 2_000L, 2_000L, null)

        val result = tracker.accept(80.0, 1_999L, 2_100L, null)

        assertEquals(
            HeartRateSignalResult.Rejected("OUT_OF_ORDER_HEART_RATE"),
            result,
        )
        assertEquals(70.0, tracker.latest()?.bpm ?: 0.0, 0.0)
    }

    @Test
    fun futureTimestampIsNeverConsideredFresh() {
        val tracker = HeartRateSignalTracker()
        tracker.accept(70.0, 5_000L, 5_000L, null)

        assertFalse(tracker.hasRecentValidSignal(nowEpochMs = 4_999L))
    }

    @Test
    fun clearRemovesWearerSignal() {
        val tracker = HeartRateSignalTracker()
        tracker.accept(70.0, 1_000L, 1_000L, null)

        tracker.clear()

        assertNull(tracker.latest())
        assertFalse(tracker.hasRecentValidSignal(nowEpochMs = 1_100L))
    }
}
