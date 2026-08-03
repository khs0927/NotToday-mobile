package com.lifesaver.cardiac.escalation

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomEscalationPersistenceInstrumentedTest {

    @Test
    fun persistedDialerReadySurvivesReopenAndRequiresFreshSafetyCheck() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val databaseName = "escalation-restart-${System.nanoTime()}.db"
        val persisted = EscalationSnapshot(
            stage = EscalationStage.DIALER_READY,
            sequence = 7L,
            activeCandidateId = "restart-candidate",
            stageEnteredAtEpochMs = 1_000L,
            updatedAtEpochMs = 1_100L,
            reason = "DIALER_REQUESTED_USER",
            cooldownUntilEpochMs = null,
        )

        try {
            val firstProcess = RoomEscalationPersistence.open(context, databaseName)
            try {
                assertTrue(firstProcess.initialize(persisted))
            } finally {
                firstProcess.close()
            }

            val secondProcess = RoomEscalationPersistence.open(context, databaseName)
            try {
                val loaded = secondProcess.loadSnapshot()
                assertEquals(persisted, loaded)

                val restored = EscalationStateMachine.restorePersisted(
                    persisted = loaded ?: error("Expected persisted snapshot"),
                    restoredAtEpochMs = 5_000L,
                )
                assertEquals(EscalationStage.SAFETY_CHECK, restored.currentSnapshot().stage)
                assertEquals(
                    "restart-candidate",
                    restored.currentSnapshot().activeCandidateId,
                )
                assertEquals(
                    "RESTORED_REQUIRES_FRESH_USER_ACTION",
                    restored.currentSnapshot().reason,
                )
                assertTrue(
                    secondProcess.persistRestartNormalization(
                        previous = loaded,
                        current = restored.currentSnapshot(),
                        occurredAtEpochMs = 5_000L,
                    ),
                )
            } finally {
                secondProcess.close()
            }

            val thirdProcess = RoomEscalationPersistence.open(context, databaseName)
            try {
                val normalized = thirdProcess.loadSnapshot()
                assertEquals(EscalationStage.SAFETY_CHECK, normalized?.stage)
                assertEquals(
                    "RESTORED_REQUIRES_FRESH_USER_ACTION",
                    normalized?.reason,
                )
            } finally {
                thirdProcess.close()
            }
        } finally {
            context.deleteDatabase(databaseName)
        }
    }

    @Test
    fun orphanedEventLogFailsInitializationInsteadOfCreatingNormalSnapshot() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val databaseName = "escalation-orphan-${System.nanoTime()}.db"
        val normal = EscalationSnapshot(
            stage = EscalationStage.NORMAL,
            sequence = 0L,
            activeCandidateId = null,
            stageEnteredAtEpochMs = 0L,
            updatedAtEpochMs = 0L,
            reason = "INITIALIZED",
            cooldownUntilEpochMs = null,
        )

        try {
            val rawDatabase = Room.databaseBuilder(
                context,
                EscalationDatabase::class.java,
                databaseName,
            ).build()
            try {
                rawDatabase.runInTransaction {
                    rawDatabase.escalationDao().insertEvent(
                        EscalationEventEntity(
                            appendOrder = 1L,
                            eventType = "ORPHANED_EVENT",
                            occurredAtEpochMs = 100L,
                            recordedAtEpochMs = 100L,
                            stageBefore = EscalationStage.NORMAL.name,
                            stageAfter = EscalationStage.RECHECK.name,
                            sequenceBefore = 0L,
                            sequenceAfter = 1L,
                            candidateId = "orphan-candidate",
                            source = null,
                            reason = "ORPHANED_EVENT",
                            transitionAccepted = true,
                            transitionRejectionReason = null,
                            synthetic = false,
                        ),
                    )
                }
            } finally {
                rawDatabase.close()
            }

            val persistence = RoomEscalationPersistence.open(context, databaseName)
            try {
                assertNull(persistence.loadSnapshot())
                assertFalse(persistence.initialize(normal))
                assertNull(persistence.loadSnapshot())
            } finally {
                persistence.close()
            }
        } finally {
            context.deleteDatabase(databaseName)
        }
    }
}
