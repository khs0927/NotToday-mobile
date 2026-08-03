package com.lifesaver.cardiac.escalation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EscalationStateMachineTest {
    @Test
    fun verifiedCandidateCanResolveThroughSafeConfirmation() {
        val machine = EscalationStateMachine(initialTimestampEpochMs = 1_000L)

        assertAccepted(
            machine.handle(
                EscalationEvent.RecheckStarted(
                    candidateId = "candidate-1",
                    reason = "IMPACT_CANDIDATE",
                    occurredAtEpochMs = 1_100L,
                ),
            ),
            EscalationStage.RECHECK,
        )
        assertAccepted(
            machine.handle(
                EscalationEvent.SafetyCheckRequired(
                    candidateId = "candidate-1",
                    reason = "LOW_POST_IMPACT_MOTION",
                    occurredAtEpochMs = 2_000L,
                ),
            ),
            EscalationStage.SAFETY_CHECK,
        )
        assertAccepted(
            machine.handle(EscalationEvent.UserConfirmedSafe(2_500L)),
            EscalationStage.RESOLVED,
        )

        assertEquals("USER_CONFIRMED_SAFE", machine.currentSnapshot().reason)
    }

    @Test
    fun promptTimeoutOnlyPreparesDialerAndDoesNotOpenIt() {
        val machine = EscalationStateMachine(initialTimestampEpochMs = 0L)
        machine.handle(
            EscalationEvent.SafetyCheckRequired(
                candidateId = "candidate-2",
                reason = "VERIFIED_BY_DETECTOR",
                occurredAtEpochMs = 100L,
            ),
        )

        val requested = machine.handle(
            EscalationEvent.DialerRequested(
                source = DialerRequestSource.PROMPT_TIMEOUT,
                occurredAtEpochMs = 15_100L,
            ),
        )

        assertAccepted(requested, EscalationStage.DIALER_READY)
        assertEquals(
            "DIALER_REQUESTED_PROMPT_TIMEOUT",
            machine.currentSnapshot().reason,
        )

        val opened = machine.handle(EscalationEvent.DialerOpened(15_200L))
        assertAccepted(opened, EscalationStage.RESOLVED)
    }

    @Test
    fun restoringDialerReadyRequiresFreshUserAction() {
        val persisted = EscalationSnapshot(
            stage = EscalationStage.DIALER_READY,
            sequence = 4L,
            activeCandidateId = "candidate-3",
            stageEnteredAtEpochMs = 4_000L,
            updatedAtEpochMs = 4_000L,
            reason = "DIALER_REQUESTED_USER",
            cooldownUntilEpochMs = null,
        )

        val restored = EscalationStateMachine.restorePersisted(
            persisted = persisted,
            restoredAtEpochMs = 5_000L,
        )

        assertEquals(EscalationStage.SAFETY_CHECK, restored.currentSnapshot().stage)
        assertEquals(
            "RESTORED_REQUIRES_FRESH_USER_ACTION",
            restored.currentSnapshot().reason,
        )
    }

    @Test
    fun outOfOrderEventIsRejected() {
        val machine = EscalationStateMachine(initialTimestampEpochMs = 1_000L)

        val result = machine.handle(
            EscalationEvent.SafetyCheckRequired(
                candidateId = "candidate-4",
                reason = "VERIFIED_BY_DETECTOR",
                occurredAtEpochMs = 999L,
            ),
        )

        assertRejected(result, "OUT_OF_ORDER_EVENT")
        assertEquals(EscalationStage.NORMAL, machine.currentSnapshot().stage)
    }

    @Test
    fun candidateMismatchCannotResolveActiveRecheck() {
        val machine = EscalationStateMachine(initialTimestampEpochMs = 0L)
        machine.handle(
            EscalationEvent.RecheckStarted(
                candidateId = "candidate-active",
                reason = "IMPACT_CANDIDATE",
                occurredAtEpochMs = 100L,
            ),
        )

        val result = machine.handle(
            EscalationEvent.CandidateRejected(
                candidateId = "candidate-other",
                reason = "MOTION_RESUMED",
                occurredAtEpochMs = 200L,
            ),
        )

        assertRejected(result, "CANDIDATE_MISMATCH")
        assertEquals(EscalationStage.RECHECK, machine.currentSnapshot().stage)
    }

    @Test
    fun cooldownRejectsImmediateDuplicateButAcceptsLaterCandidate() {
        val machine = EscalationStateMachine(
            cooldownMs = 30_000L,
            initialTimestampEpochMs = 0L,
        )
        machine.handle(
            EscalationEvent.SafetyCheckRequired(
                candidateId = "candidate-5",
                reason = "VERIFIED_BY_DETECTOR",
                occurredAtEpochMs = 100L,
            ),
        )
        machine.handle(EscalationEvent.UserConfirmedSafe(200L))

        val immediate = machine.handle(
            EscalationEvent.RecheckStarted(
                candidateId = "candidate-6",
                reason = "IMPACT_CANDIDATE",
                occurredAtEpochMs = 10_000L,
            ),
        )
        assertRejected(immediate, "COOLDOWN_ACTIVE")

        val later = machine.handle(
            EscalationEvent.RecheckStarted(
                candidateId = "candidate-6",
                reason = "IMPACT_CANDIDATE",
                occurredAtEpochMs = 30_200L,
            ),
        )
        assertAccepted(later, EscalationStage.RECHECK)
    }

    @Test
    fun failedDialerDoesNotRetryAutomatically() {
        val machine = EscalationStateMachine(initialTimestampEpochMs = 0L)
        machine.handle(
            EscalationEvent.SafetyCheckRequired(
                candidateId = "candidate-7",
                reason = "VERIFIED_BY_DETECTOR",
                occurredAtEpochMs = 100L,
            ),
        )
        machine.handle(
            EscalationEvent.DialerRequested(
                source = DialerRequestSource.USER,
                occurredAtEpochMs = 200L,
            ),
        )

        val failed = machine.handle(
            EscalationEvent.DialerFailed(
                reason = "NO_ACTIVITY_HANDLER",
                occurredAtEpochMs = 300L,
            ),
        )

        assertAccepted(failed, EscalationStage.RESOLVED)
        assertTrue(machine.currentSnapshot().reason.startsWith("DIALER_FAILED:"))
    }

    private fun assertAccepted(
        result: TransitionResult,
        expectedStage: EscalationStage,
    ) {
        assertTrue(result is TransitionResult.Accepted)
        val accepted = result as TransitionResult.Accepted
        assertEquals(expectedStage, accepted.current.stage)
    }

    private fun assertRejected(
        result: TransitionResult,
        expectedReason: String,
    ) {
        assertTrue(result is TransitionResult.Rejected)
        val rejected = result as TransitionResult.Rejected
        assertEquals(expectedReason, rejected.reason)
    }
}
