package com.lifesaver.cardiac.escalation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EscalationStateMachineTest {
    @Test
    fun verifiedCandidateCanResolveThroughSafeConfirmation() {
        val machine = EscalationStateMachine(initialTimestampEpochMs = 1_000L)
        startCandidate(machine, "candidate-1", 1_100L)

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
            machine.handle(
                EscalationEvent.UserConfirmedSafe(
                    candidateId = "candidate-1",
                    occurredAtEpochMs = 2_500L,
                ),
            ),
            EscalationStage.RESOLVED,
        )

        assertEquals("USER_CONFIRMED_SAFE", machine.currentSnapshot().reason)
        assertNull(machine.currentSnapshot().activeCandidateId)
    }

    @Test
    fun promptTimeoutOnlyPreparesDialerAndDoesNotOpenIt() {
        val machine = EscalationStateMachine(initialTimestampEpochMs = 0L)
        startCandidate(machine, "candidate-2", 100L)
        machine.handle(
            EscalationEvent.SafetyCheckRequired(
                candidateId = "candidate-2",
                reason = "VERIFIED_BY_DETECTOR",
                occurredAtEpochMs = 10_000L,
            ),
        )

        val requested = machine.handle(
            EscalationEvent.DialerRequested(
                candidateId = "candidate-2",
                source = DialerRequestSource.PROMPT_TIMEOUT,
                occurredAtEpochMs = 15_100L,
            ),
        )

        assertAccepted(requested, EscalationStage.DIALER_READY)
        assertEquals(
            "DIALER_REQUESTED_PROMPT_TIMEOUT",
            machine.currentSnapshot().reason,
        )

        val opened = machine.handle(
            EscalationEvent.DialerOpened(
                candidateId = "candidate-2",
                occurredAtEpochMs = 15_200L,
            ),
        )
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
        assertEquals("candidate-3", restored.currentSnapshot().activeCandidateId)
    }

    @Test
    fun restoringRecheckResolvesWithoutResumingMeasurement() {
        val persisted = EscalationSnapshot(
            stage = EscalationStage.RECHECK,
            sequence = 2L,
            activeCandidateId = "candidate-recheck",
            stageEnteredAtEpochMs = 4_000L,
            updatedAtEpochMs = 4_000L,
            reason = "IMPACT_CANDIDATE",
            cooldownUntilEpochMs = null,
        )

        val restored = EscalationStateMachine.restorePersisted(
            persisted = persisted,
            restoredAtEpochMs = 5_000L,
        )

        assertEquals(EscalationStage.RESOLVED, restored.currentSnapshot().stage)
        assertEquals(
            "RESTORED_UNVERIFIED_RECHECK",
            restored.currentSnapshot().reason,
        )
        assertNull(restored.currentSnapshot().activeCandidateId)
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
    fun verifiedEventCannotSkipRecheck() {
        val machine = EscalationStateMachine(initialTimestampEpochMs = 0L)

        val result = machine.handle(
            EscalationEvent.SafetyCheckRequired(
                candidateId = "candidate-skip",
                reason = "VERIFIED_BY_DETECTOR",
                occurredAtEpochMs = 100L,
            ),
        )

        assertRejected(result, "EVENT_NOT_ALLOWED_FROM_NORMAL")
        assertEquals(EscalationStage.NORMAL, machine.currentSnapshot().stage)
    }

    @Test
    fun candidateMismatchCannotResolveActiveRecheck() {
        val machine = EscalationStateMachine(initialTimestampEpochMs = 0L)
        startCandidate(machine, "candidate-active", 100L)

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
        startCandidate(machine, "candidate-5", 100L)
        machine.handle(
            EscalationEvent.SafetyCheckRequired(
                candidateId = "candidate-5",
                reason = "VERIFIED_BY_DETECTOR",
                occurredAtEpochMs = 200L,
            ),
        )
        machine.handle(
            EscalationEvent.UserConfirmedSafe(
                candidateId = "candidate-5",
                occurredAtEpochMs = 200L,
            ),
        )

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
        startCandidate(machine, "candidate-7", 100L)
        machine.handle(
            EscalationEvent.SafetyCheckRequired(
                candidateId = "candidate-7",
                reason = "VERIFIED_BY_DETECTOR",
                occurredAtEpochMs = 200L,
            ),
        )
        machine.handle(
            EscalationEvent.DialerRequested(
                candidateId = "candidate-7",
                source = DialerRequestSource.USER,
                occurredAtEpochMs = 300L,
            ),
        )

        val failed = machine.handle(
            EscalationEvent.DialerFailed(
                candidateId = "candidate-7",
                reason = "NO_ACTIVITY_HANDLER",
                occurredAtEpochMs = 400L,
            ),
        )

        assertAccepted(failed, EscalationStage.RESOLVED)
        assertTrue(machine.currentSnapshot().reason.startsWith("DIALER_FAILED:"))
        assertNull(machine.currentSnapshot().activeCandidateId)
    }

    @Test
    fun staleUserConfirmationCannotResolveAnotherCandidate() {
        val machine = EscalationStateMachine(
            cooldownMs = 30_000L,
            initialTimestampEpochMs = 0L,
        )
        startCandidate(machine, "candidate-old", 100L)
        machine.handle(
            EscalationEvent.SafetyCheckRequired(
                candidateId = "candidate-old",
                reason = "VERIFIED",
                occurredAtEpochMs = 200L,
            ),
        )
        machine.handle(
            EscalationEvent.UserConfirmedSafe(
                candidateId = "candidate-old",
                occurredAtEpochMs = 300L,
            ),
        )
        startCandidate(machine, "candidate-new", 30_400L)
        machine.handle(
            EscalationEvent.SafetyCheckRequired(
                candidateId = "candidate-new",
                reason = "VERIFIED",
                occurredAtEpochMs = 30_500L,
            ),
        )

        val stale = machine.handle(
            EscalationEvent.UserConfirmedSafe(
                candidateId = "candidate-old",
                occurredAtEpochMs = 30_600L,
            ),
        )

        assertRejected(stale, "CANDIDATE_MISMATCH")
        assertEquals(EscalationStage.SAFETY_CHECK, machine.currentSnapshot().stage)
    }

    @Test
    fun staleDialerOutcomeCannotResolveAnotherCandidate() {
        val machine = EscalationStateMachine(
            cooldownMs = 30_000L,
            initialTimestampEpochMs = 0L,
        )
        startCandidate(machine, "candidate-old-dialer", 100L)
        machine.handle(
            EscalationEvent.SafetyCheckRequired(
                candidateId = "candidate-old-dialer",
                reason = "VERIFIED",
                occurredAtEpochMs = 200L,
            ),
        )
        machine.handle(
            EscalationEvent.DialerRequested(
                candidateId = "candidate-old-dialer",
                source = DialerRequestSource.USER,
                occurredAtEpochMs = 300L,
            ),
        )
        machine.handle(
            EscalationEvent.DialerFailed(
                candidateId = "candidate-old-dialer",
                reason = "DIALER_UNAVAILABLE",
                occurredAtEpochMs = 400L,
            ),
        )
        startCandidate(machine, "candidate-new-dialer", 30_500L)
        machine.handle(
            EscalationEvent.SafetyCheckRequired(
                candidateId = "candidate-new-dialer",
                reason = "VERIFIED",
                occurredAtEpochMs = 30_600L,
            ),
        )
        machine.handle(
            EscalationEvent.DialerRequested(
                candidateId = "candidate-new-dialer",
                source = DialerRequestSource.USER,
                occurredAtEpochMs = 30_700L,
            ),
        )

        val stale = machine.handle(
            EscalationEvent.DialerOpened(
                candidateId = "candidate-old-dialer",
                occurredAtEpochMs = 30_800L,
            ),
        )

        assertRejected(stale, "CANDIDATE_MISMATCH")
        assertEquals(EscalationStage.DIALER_READY, machine.currentSnapshot().stage)
    }

    @Test
    fun restoreDoesNotMoveTimestampBackwards() {
        val persisted = EscalationSnapshot(
            stage = EscalationStage.NORMAL,
            sequence = 2L,
            activeCandidateId = null,
            stageEnteredAtEpochMs = 1_000L,
            updatedAtEpochMs = 10_000L,
            reason = "INITIALIZED",
            cooldownUntilEpochMs = null,
        )

        val restored = EscalationStateMachine.restorePersisted(
            persisted = persisted,
            restoredAtEpochMs = 5_000L,
        )

        assertEquals(10_000L, restored.currentSnapshot().updatedAtEpochMs)
    }

    @Test
    fun malformedSafetyCheckWithoutCandidateCannotBeRestored() {
        assertRestoreRejected(
            EscalationSnapshot(
                stage = EscalationStage.SAFETY_CHECK,
                sequence = 4L,
                activeCandidateId = null,
                stageEnteredAtEpochMs = 4_000L,
                updatedAtEpochMs = 4_000L,
                reason = "CORRUPTED",
                cooldownUntilEpochMs = null,
            ),
        )
    }

    @Test
    fun malformedResolvedSnapshotWithCandidateCannotBeRestored() {
        assertRestoreRejected(
            EscalationSnapshot(
                stage = EscalationStage.RESOLVED,
                sequence = 5L,
                activeCandidateId = "stale-candidate",
                stageEnteredAtEpochMs = 5_000L,
                updatedAtEpochMs = 5_000L,
                reason = "CORRUPTED",
                cooldownUntilEpochMs = 35_000L,
            ),
        )
    }

    private fun assertRestoreRejected(snapshot: EscalationSnapshot) {
        try {
            EscalationStateMachine.restorePersisted(
                persisted = snapshot,
                restoredAtEpochMs = 6_000L,
            )
            throw AssertionError("Expected malformed snapshot to be rejected")
        } catch (_: IllegalArgumentException) {
            // Expected fail-closed restore.
        }
    }

    private fun startCandidate(
        machine: EscalationStateMachine,
        candidateId: String,
        occurredAtEpochMs: Long,
    ) {
        assertAccepted(
            machine.handle(
                EscalationEvent.RecheckStarted(
                    candidateId = candidateId,
                    reason = "IMPACT_CANDIDATE",
                    occurredAtEpochMs = occurredAtEpochMs,
                ),
            ),
            EscalationStage.RECHECK,
        )
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
