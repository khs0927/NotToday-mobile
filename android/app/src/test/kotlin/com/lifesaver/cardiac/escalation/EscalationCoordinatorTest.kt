package com.lifesaver.cardiac.escalation

import com.lifesaver.cardiac.FallCandidateEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EscalationCoordinatorTest {
    @Test
    fun detectorLifecycleMapsToRecheckAndSafetyCheck() {
        val coordinator = EscalationCoordinator(
            EscalationStateMachine(initialTimestampEpochMs = 0L),
        )

        val started = coordinator.handleFallCandidate(
            FallCandidateEvent.Started(
                candidateId = "impact-100-1",
                occurredAtEpochMs = 100L,
                impactGForce = 4.2,
            ),
        )
        assertAccepted(started, EscalationStage.RECHECK)

        val verified = coordinator.handleFallCandidate(
            FallCandidateEvent.Verified(
                candidateId = "impact-100-1",
                occurredAtEpochMs = 10_100L,
                impactGForce = 4.2,
                reason = "IMPACT_WITH_POST_EVENT_IMMOBILITY",
                sampleCount = 100,
                postImpactStdDevG = 0.04,
            ),
        )
        assertAccepted(verified, EscalationStage.SAFETY_CHECK)
    }

    @Test
    fun detectorRejectionResolvesTheActiveCandidate() {
        val coordinator = EscalationCoordinator(
            EscalationStateMachine(initialTimestampEpochMs = 0L),
        )
        coordinator.handleFallCandidate(
            FallCandidateEvent.Started(
                candidateId = "impact-200-1",
                occurredAtEpochMs = 200L,
                impactGForce = 4.4,
            ),
        )

        val rejected = coordinator.handleFallCandidate(
            FallCandidateEvent.Rejected(
                candidateId = "impact-200-1",
                occurredAtEpochMs = 10_200L,
                impactGForce = 4.4,
                reason = "POST_IMPACT_MOTION",
                sampleCount = 120,
                postImpactStdDevG = 0.22,
            ),
        )

        assertAccepted(rejected, EscalationStage.RESOLVED)
        assertEquals("POST_IMPACT_MOTION", coordinator.currentSnapshot().reason)
    }

    @Test
    fun mismatchedVerifiedCandidateCannotOpenSafetyCheck() {
        val coordinator = EscalationCoordinator(
            EscalationStateMachine(initialTimestampEpochMs = 0L),
        )
        coordinator.handleFallCandidate(
            FallCandidateEvent.Started(
                candidateId = "impact-active",
                occurredAtEpochMs = 100L,
                impactGForce = 4.1,
            ),
        )

        val result = coordinator.handleFallCandidate(
            FallCandidateEvent.Verified(
                candidateId = "impact-other",
                occurredAtEpochMs = 10_100L,
                impactGForce = 4.1,
                reason = "IMPACT_WITH_POST_EVENT_IMMOBILITY",
                sampleCount = 100,
                postImpactStdDevG = 0.03,
            ),
        )

        assertTrue(result is TransitionResult.Rejected)
        assertEquals("CANDIDATE_MISMATCH", (result as TransitionResult.Rejected).reason)
        assertEquals(EscalationStage.RECHECK, coordinator.currentSnapshot().stage)
    }

    @Test
    fun dialerRequiresAcceptedRequestBeforeOutcome() {
        val coordinator = EscalationCoordinator(
            EscalationStateMachine(initialTimestampEpochMs = 0L),
        )
        coordinator.handleFallCandidate(
            FallCandidateEvent.Started(
                candidateId = "impact-300-1",
                occurredAtEpochMs = 100L,
                impactGForce = 4.5,
            ),
        )
        coordinator.handleFallCandidate(
            FallCandidateEvent.Verified(
                candidateId = "impact-300-1",
                occurredAtEpochMs = 10_100L,
                impactGForce = 4.5,
                reason = "IMPACT_WITH_POST_EVENT_IMMOBILITY",
                sampleCount = 100,
                postImpactStdDevG = 0.02,
            ),
        )

        val requested = coordinator.requestDialer(
            source = DialerRequestSource.USER,
            occurredAtEpochMs = 10_200L,
        )
        assertAccepted(requested, EscalationStage.DIALER_READY)

        val opened = coordinator.recordDialerOpened(10_300L)
        assertAccepted(opened, EscalationStage.RESOLVED)
    }

    private fun assertAccepted(
        result: TransitionResult,
        expectedStage: EscalationStage,
    ) {
        assertTrue(result is TransitionResult.Accepted)
        assertEquals(expectedStage, (result as TransitionResult.Accepted).current.stage)
    }
}
