package com.lifesaver.cardiac.escalation

import android.content.Context
import android.util.Log
import androidx.room.Database
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@Entity(tableName = "escalation_snapshot")
data class EscalationSnapshotEntity(
    @androidx.room.PrimaryKey val singletonId: Int = 1,
    val stage: String,
    val sequence: Long,
    val activeCandidateId: String?,
    val stageEnteredAtEpochMs: Long,
    val updatedAtEpochMs: Long,
    val reason: String,
    val cooldownUntilEpochMs: Long?,
)

@Entity(tableName = "escalation_event")
data class EscalationEventEntity(
    @androidx.room.PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val eventType: String,
    val candidateId: String?,
    val dialerSource: String?,
    val occurredAtEpochMs: Long,
    val accepted: Boolean,
    val rejectionReason: String?,
    val resultingStage: String,
    val resultingSequence: Long,
    val resultingActiveCandidateId: String?,
    val resultingReason: String,
    val resultingCooldownUntilEpochMs: Long?,
)

@Dao
interface EscalationDao {
    @Query("SELECT * FROM escalation_snapshot WHERE singletonId = 1 LIMIT 1")
    fun loadSnapshot(): EscalationSnapshotEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertSnapshot(snapshot: EscalationSnapshotEntity)

    @Insert
    fun insertEvent(event: EscalationEventEntity)

    @Query("DELETE FROM escalation_event WHERE occurredAtEpochMs < :cutoffEpochMs")
    fun deleteEventsBefore(cutoffEpochMs: Long): Int
}

@Database(
    entities = [
        EscalationSnapshotEntity::class,
        EscalationEventEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class EscalationDatabase : RoomDatabase() {
    abstract fun escalationDao(): EscalationDao
}

/**
 * Room-backed local persistence.
 *
 * Database work is serialized on a dedicated executor so service actions do
 * not access Room from the main thread. Each transition writes its audit event
 * and accepted snapshot in one Room transaction.
 */
class RoomEscalationPersistence private constructor(
    private val database: EscalationDatabase,
) : EscalationPersistence {
    private val executor: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "escalation-room").apply { isDaemon = true }
    }

    private val dao: EscalationDao
        get() = database.escalationDao()

    companion object {
        private const val TAG = "EscalationPersistence"
        private const val DATABASE_NAME = "escalation_state.db"

        fun open(context: Context): RoomEscalationPersistence {
            val database = Room.databaseBuilder(
                context.applicationContext,
                EscalationDatabase::class.java,
                DATABASE_NAME,
            ).build()
            return RoomEscalationPersistence(database)
        }
    }

    fun loadSnapshot(): EscalationSnapshot? =
        runOnDatabase { dao.loadSnapshot()?.toDomain() }

    override fun initialize(snapshot: EscalationSnapshot) {
        runOnDatabase {
            database.runInTransaction {
                if (dao.loadSnapshot() == null) {
                    dao.upsertSnapshot(snapshot.toEntity())
                }
            }
        }
    }

    override fun persist(result: TransitionResult) {
        runOnDatabase {
            database.runInTransaction {
                dao.insertEvent(result.toEntity())
                if (result is TransitionResult.Accepted) {
                    dao.upsertSnapshot(result.current.toEntity())
                }
            }
        }
    }

    override fun persistRestartNormalization(
        previous: EscalationSnapshot,
        current: EscalationSnapshot,
        occurredAtEpochMs: Long,
    ) {
        runOnDatabase {
            database.runInTransaction {
                dao.insertEvent(
                    EscalationEventEntity(
                        eventType = "PROCESS_RESTART_NORMALIZED",
                        candidateId = current.activeCandidateId,
                        dialerSource = null,
                        occurredAtEpochMs = occurredAtEpochMs,
                        accepted = true,
                        rejectionReason = null,
                        resultingStage = current.stage.name,
                        resultingSequence = current.sequence,
                        resultingActiveCandidateId = current.activeCandidateId,
                        resultingReason = current.reason,
                        resultingCooldownUntilEpochMs = current.cooldownUntilEpochMs,
                    ),
                )
                dao.upsertSnapshot(current.toEntity())
            }
        }
    }

    override fun close() {
        executor.shutdown()
        try {
            if (!executor.awaitTermination(2L, TimeUnit.SECONDS)) {
                Log.w(TAG, "Timed out while closing the escalation database executor.")
            }
        } catch (exception: InterruptedException) {
            Thread.currentThread().interrupt()
            Log.w(TAG, "Interrupted while closing the escalation database executor.", exception)
        } finally {
            database.close()
        }
    }

    private fun <T> runOnDatabase(block: () -> T): T {
        try {
            return executor.submit(Callable { block() }).get()
        } catch (exception: InterruptedException) {
            Thread.currentThread().interrupt()
            throw IllegalStateException("Escalation database operation interrupted.", exception)
        } catch (exception: ExecutionException) {
            throw IllegalStateException("Escalation database operation failed.", exception.cause)
        }
    }
}

private fun EscalationSnapshot.toEntity(): EscalationSnapshotEntity =
    EscalationSnapshotEntity(
        stage = stage.name,
        sequence = sequence,
        activeCandidateId = activeCandidateId,
        stageEnteredAtEpochMs = stageEnteredAtEpochMs,
        updatedAtEpochMs = updatedAtEpochMs,
        reason = reason,
        cooldownUntilEpochMs = cooldownUntilEpochMs,
    )

private fun EscalationSnapshotEntity.toDomain(): EscalationSnapshot =
    EscalationSnapshot(
        stage = EscalationStage.valueOf(stage),
        sequence = sequence,
        activeCandidateId = activeCandidateId,
        stageEnteredAtEpochMs = stageEnteredAtEpochMs,
        updatedAtEpochMs = updatedAtEpochMs,
        reason = reason,
        cooldownUntilEpochMs = cooldownUntilEpochMs,
    )

private fun TransitionResult.toEntity(): EscalationEventEntity {
    val event = when (this) {
        is TransitionResult.Accepted -> event
        is TransitionResult.Rejected -> event
    }
    val current = when (this) {
        is TransitionResult.Accepted -> current
        is TransitionResult.Rejected -> current
    }

    return EscalationEventEntity(
        eventType = event.typeName(),
        candidateId = event.candidateIdOrNull(),
        dialerSource = event.dialerSourceOrNull(),
        occurredAtEpochMs = event.occurredAtEpochMs,
        accepted = this is TransitionResult.Accepted,
        rejectionReason = (this as? TransitionResult.Rejected)?.reason,
        resultingStage = current.stage.name,
        resultingSequence = current.sequence,
        resultingActiveCandidateId = current.activeCandidateId,
        resultingReason = current.reason,
        resultingCooldownUntilEpochMs = current.cooldownUntilEpochMs,
    )
}

private fun EscalationEvent.typeName(): String =
    when (this) {
        is EscalationEvent.RecheckStarted -> "RECHECK_STARTED"
        is EscalationEvent.SafetyCheckRequired -> "SAFETY_CHECK_REQUIRED"
        is EscalationEvent.CandidateRejected -> "CANDIDATE_REJECTED"
        is EscalationEvent.UserConfirmedSafe -> "USER_CONFIRMED_SAFE"
        is EscalationEvent.DialerRequested -> "DIALER_REQUESTED"
        is EscalationEvent.DialerOpened -> "DIALER_OPENED"
        is EscalationEvent.DialerFailed -> "DIALER_FAILED"
        is EscalationEvent.Reset -> "RESET"
    }

private fun EscalationEvent.candidateIdOrNull(): String? =
    when (this) {
        is EscalationEvent.RecheckStarted -> candidateId
        is EscalationEvent.SafetyCheckRequired -> candidateId
        is EscalationEvent.CandidateRejected -> candidateId
        is EscalationEvent.UserConfirmedSafe -> null
        is EscalationEvent.DialerRequested -> null
        is EscalationEvent.DialerOpened -> null
        is EscalationEvent.DialerFailed -> null
        is EscalationEvent.Reset -> null
    }

private fun EscalationEvent.dialerSourceOrNull(): String? =
    (this as? EscalationEvent.DialerRequested)?.source?.name
