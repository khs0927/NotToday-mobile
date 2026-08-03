package com.lifesaver.cardiac.escalation

import com.lifesaver.cardiac.FallCandidateEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EscalationPersistenceTest {
    @Test
    fun acceptedTransitionIsRolledBackWhenDurableWriteFails() {
        val persistence = RecordingPersistence()
        val coordinator = EscalationCoordinator(
            machine = EscalationStateMachine(initialTimestampEpochMs = 0L),
            persistence = persistence,
        )

        val started = coordinator.handleFallCandidate(
            FallCandidateEvent.Started(
                candidateId = "persist-1",
                occurredAtEpochMs = 100L,
                impactGForce = 4.0,
            ),
        )
        assertTrue(started is TransitionResult.Accepted)
        assertEquals(EscalationStage.RECHECK, coordinator.currentSnapshot().stage)
        assertEquals(1, persistence.results.size)

        persistence.shouldPersist = false
        val failed = coordinator.handleFallCandidate(
            FallCandidateEvent.Verified(
                candidateId = "persist-1",
                occurredAtEpochMs = 200L,
                impactGForce = 4.0,
                reason = "VERIFIED",
                sampleCount = 10,
                postImpactStdDevG = 0.02,
            ),
        )

        assertTrue(failed is TransitionResult.Rejected)
        assertEquals(
            "PERSISTENCE_UNAVAILABLE",
            (failed as TransitionResult.Rejected).reason,
        )
        assertEquals("IMPACT_CANDIDATE", coordinator.currentSnapshot().reason)
        assertEquals(EscalationStage.RECHECK, coordinator.currentSnapshot().stage)
        assertEquals(1, persistence.results.size)
    }

    @Test
    fun rejectedTransitionIsAuditedWithoutChangingSnapshot() {
        val persistence = RecordingPersistence()
        val coordinator = EscalationCoordinator(
            machine = EscalationStateMachine(initialTimestampEpochMs = 0L),
            persistence = persistence,
        )

        coordinator.handleFallCandidate(
            FallCandidateEvent.Started(
                candidateId = "persist-2",
                occurredAtEpochMs = 100L,
                impactGForce = 4.0,
            ),
        )

        val rejected = coordinator.handleFallCandidate(
            FallCandidateEvent.Verified(
                candidateId = "different-candidate",
                occurredAtEpochMs = 200L,
                impactGForce = 4.0,
                reason = "VERIFIED",
                sampleCount = 10,
                postImpactStdDevG = 0.02,
            ),
        )

        assertTrue(rejected is TransitionResult.Rejected)
        assertEquals("CANDIDATE_MISMATCH", (rejected as TransitionResult.Rejected).reason)
        assertEquals(EscalationStage.RECHECK, coordinator.currentSnapshot().stage)
        assertEquals(2, persistence.results.size)
        val audited = persistence.results.last() as TransitionResult.Rejected
        assertEquals("CANDIDATE_MISMATCH", audited.reason)
    }

    private class RecordingPersistence : EscalationPersistence {
        var shouldPersist = true
        val results = mutableListOf<TransitionResult>()

        override fun persist(result: TransitionResult): Boolean {
            if (!shouldPersist) return false
            results += result
            return true
        }
    }
}
