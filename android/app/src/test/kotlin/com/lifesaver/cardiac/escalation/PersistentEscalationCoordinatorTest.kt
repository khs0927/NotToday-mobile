package com.lifesaver.cardiac.escalation

import com.lifesaver.cardiac.FallCandidateEvent
import com.lifesaver.cardiac.persistence.EscalationTransitionStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PersistentEscalationCoordinatorTest {
    @Test
    fun `dialer-ready restore requires fresh user action and records recovery`() {
        val persisted = EscalationSnapshot(
            stage = EscalationStage.DIALER_READY,
            sequence = 4L,
            activeCandidateId = "candidate-1",
            stageEnteredAtEpochMs = 100L,
            updatedAtEpochMs = 100L,
            reason = "DIALER_REQUESTED_USER",
            cooldownUntilEpochMs = null,
        )
        val store = FakeStore(persistedSnapshot = persisted)

        val coordinator = EscalationCoordinator.createPersistent(
            transitionStore = store,
            restoredAtEpochMs = 200L,
        )

        val restored = coordinator.currentSnapshot()
        assertEquals(EscalationStage.SAFETY_CHECK, restored.stage)
        assertEquals("candidate-1", restored.activeCandidateId)
        assertEquals("RESTORED_REQUIRES_FRESH_USER_ACTION", restored.reason)
        assertEquals(1, store.recoveries.size)
        assertFalse(coordinator.isPersistenceFaulted())
    }

    @Test
    fun `accepted transition is persisted before it is returned`() {
        val store = FakeStore()
        val coordinator = EscalationCoordinator.createPersistent(
            transitionStore = store,
            restoredAtEpochMs = 10L,
        )

        val result = coordinator.handleFallCandidate(
            FallCandidateEvent.Started(
                candidateId = "candidate-2",
                occurredAtEpochMs = 20L,
                impactGForce = 4.5,
            ),
        )

        assertTrue(result is TransitionResult.Accepted)
        assertEquals(1, store.transitions.size)
        assertEquals(EscalationStage.RECHECK, coordinator.currentSnapshot().stage)
    }

    @Test
    fun `rejected transition is retained for audit`() {
        val store = FakeStore()
        val coordinator = EscalationCoordinator.createPersistent(
            transitionStore = store,
            restoredAtEpochMs = 10L,
        )

        val result = coordinator.handleFallCandidate(
            FallCandidateEvent.Verified(
                candidateId = "candidate-without-start",
                occurredAtEpochMs = 20L,
                impactGForce = 4.5,
                reason = "TEST",
                sampleCount = 40,
                postImpactStdDevG = 0.02,
            ),
        )

        assertTrue(result is TransitionResult.Rejected)
        assertEquals(1, store.transitions.size)
    }

    @Test
    fun `storage failure faults coordinator and blocks side effects`() {
        val store = FakeStore(throwOnPersist = true)
        val coordinator = EscalationCoordinator.createPersistent(
            transitionStore = store,
            restoredAtEpochMs = 10L,
        )

        val first = coordinator.handleFallCandidate(
            FallCandidateEvent.Started(
                candidateId = "candidate-3",
                occurredAtEpochMs = 20L,
                impactGForce = 5.0,
            ),
        )
        val second = coordinator.requestDialer(
            candidateId = "candidate-3",
            source = DialerRequestSource.USER,
            occurredAtEpochMs = 30L,
        )

        assertTrue(first is TransitionResult.Rejected)
        assertEquals("PERSISTENCE_FAILED", (first as TransitionResult.Rejected).reason)
        assertTrue(second is TransitionResult.Rejected)
        assertEquals("PERSISTENCE_UNAVAILABLE", (second as TransitionResult.Rejected).reason)
        assertTrue(coordinator.isPersistenceFaulted())
    }

    private class FakeStore(
        private val persistedSnapshot: EscalationSnapshot? = null,
        private val throwOnPersist: Boolean = false,
    ) : EscalationTransitionStore {
        val transitions = mutableListOf<TransitionResult>()
        val recoveries = mutableListOf<Pair<EscalationSnapshot, EscalationSnapshot>>()

        override fun loadLatestSnapshot(): EscalationSnapshot? = persistedSnapshot

        override fun persist(result: TransitionResult) {
            if (throwOnPersist) {
                throw IllegalStateException("simulated storage failure")
            }
            transitions += result
        }

        override fun persistRecovery(
            previous: EscalationSnapshot,
            current: EscalationSnapshot,
        ) {
            recoveries += previous to current
        }
    }
}
