package com.lifesaver.cardiac.escalation

import android.content.Context
import android.util.Log
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
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
    @PrimaryKey val singletonId: Int = 1,
    val stage: String,
    val sequence: Long,
    val activeCandidateId: String?,
    val stageEnteredAtEpochMs: Long,
    val updatedAtEpochMs: Long,
    val reason: String,
    val cooldownUntilEpochMs: Long?,
    val lastAppendOrder: Long,
)

@Entity(tableName = "escalation_event")
data class EscalationEventEntity(
    @PrimaryKey val appendOrder: Long,
    val eventType: String,
    val occurredAtEpochMs: Long,
    val recordedAtEpochMs: Long,
    val stageBefore: String,
    val stageAfter: String,
    val sequenceBefore: Long,
    val sequenceAfter: Long,
    val candidateId: String?,
    val source: String?,
    val reason: String,
    val transitionAccepted: Boolean,
    val transitionRejectionReason: String?,
    val synthetic: Boolean,
)

@Dao
interface EscalationDao {
    @Query("SELECT * FROM escalation_snapshot WHERE singletonId = 1 LIMIT 1")
    fun loadSnapshot(): EscalationSnapshotEntity?

    @Query("SELECT COALESCE(MAX(appendOrder), 0) + 1 FROM escalation_event")
    fun nextAppendOrder(): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertSnapshot(snapshot: EscalationSnapshotEntity)

    @Insert
    fun insertEvent(event: EscalationEventEntity)
}

@Database(
    entities = [
        EscalationSnapshotEntity::class,
        EscalationEventEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class EscalationDatabase : RoomDatabase() {
    abstract fun escalationDao(): EscalationDao
}

/**
 * Room-backed local persistence.
 *
 * Database operations run on one executor and the event plus resulting
 * snapshot are written in a single Room transaction. Raw IMU/heart-rate
 * samples, location, phone numbers, and message contents are not persisted.
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

    override fun initialize(snapshot: EscalationSnapshot): Boolean =
        runCatching {
            runOnDatabase {
                database.runInTransaction {
                    if (dao.loadSnapshot() == null) {
                        dao.upsertSnapshot(snapshot.toEntity(lastAppendOrder = 0L))
                    }
                }
            }
            true
        }.getOrElse { exception ->
            Log.e(TAG, "Unable to initialize escalation persistence.", exception)
            false
        }

    override fun persist(result: TransitionResult): Boolean =
        runCatching {
            runOnDatabase {
                database.runInTransaction {
                    val appendOrder = dao.nextAppendOrder()
                    dao.insertEvent(
                        result.toEntity(
                            appendOrder = appendOrder,
                            recordedAtEpochMs = System.currentTimeMillis(),
                        ),
                    )
                    dao.upsertSnapshot(
                        result.currentSnapshot().toEntity(appendOrder),
                    )
                }
            }
            true
        }.getOrElse { exception ->
            Log.e(TAG, "Unable to persist escalation transition.", exception)
            false
        }

    override fun persistRestartNormalization(
        previous: EscalationSnapshot,
        current: EscalationSnapshot,
        occurredAtEpochMs: Long,
    ): Boolean =
        runCatching {
            runOnDatabase {
                database.runInTransaction {
                    val appendOrder = dao.nextAppendOrder()
                    dao.insertEvent(
                        EscalationEventEntity(
                            appendOrder = appendOrder,
                            eventType = "PROCESS_RESTART_NORMALIZED",
                            occurredAtEpochMs = occurredAtEpochMs,
                            recordedAtEpochMs = System.currentTimeMillis(),
                            stageBefore = previous.stage.name,
                            stageAfter = current.stage.name,
                            sequenceBefore = previous.sequence,
                            sequenceAfter = current.sequence,
                            candidateId = current.activeCandidateId,
                            source = null,
                            reason = current.reason,
                            transitionAccepted = true,
                            transitionRejectionReason = null,
                            synthetic = true,
                        ),
                    )
                    dao.upsertSnapshot(current.toEntity(appendOrder))
                }
            }
            true
        }.getOrElse { exception ->
            Log.e(TAG, "Unable to persist restart normalization.", exception)
            false
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

private fun EscalationSnapshot.toEntity(lastAppendOrder: Long): EscalationSnapshotEntity =
    EscalationSnapshotEntity(
        stage = stage.name,
        sequence = sequence,
        activeCandidateId = activeCandidateId,
        stageEnteredAtEpochMs = stageEnteredAtEpochMs,
        updatedAtEpochMs = updatedAtEpochMs,
        reason = reason,
        cooldownUntilEpochMs = cooldownUntilEpochMs,
        lastAppendOrder = lastAppendOrder,
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

private fun TransitionResult.currentSnapshot(): EscalationSnapshot =
    when (this) {
        is TransitionResult.Accepted -> current
        is TransitionResult.Rejected -> current
    }

private fun TransitionResult.previousSnapshot(): EscalationSnapshot =
    when (this) {
        is TransitionResult.Accepted -> previous
        is TransitionResult.Rejected -> current
    }

private fun TransitionResult.toEntity(
    appendOrder: Long,
    recordedAtEpochMs: Long,
): EscalationEventEntity {
    val event = when (this) {
        is TransitionResult.Accepted -> event
        is TransitionResult.Rejected -> event
    }
    val before = previousSnapshot()
    val after = currentSnapshot()

    return EscalationEventEntity(
        appendOrder = appendOrder,
        eventType = event.typeName(),
        occurredAtEpochMs = event.occurredAtEpochMs,
        recordedAtEpochMs = recordedAtEpochMs,
        stageBefore = before.stage.name,
        stageAfter = after.stage.name,
        sequenceBefore = before.sequence,
        sequenceAfter = after.sequence,
        candidateId = event.candidateIdOrNull(),
        source = event.sourceOrNull(),
        reason = event.reasonOrDefault(),
        transitionAccepted = this is TransitionResult.Accepted,
        transitionRejectionReason = (this as? TransitionResult.Rejected)?.reason,
        synthetic = false,
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
    }

private fun EscalationEvent.candidateIdOrNull(): String? =
    when (this) {
        is EscalationEvent.RecheckStarted -> candidateId
        is EscalationEvent.SafetyCheckRequired -> candidateId
        is EscalationEvent.CandidateRejected -> candidateId
        is EscalationEvent.UserConfirmedSafe -> candidateId
        is EscalationEvent.DialerRequested -> candidateId
        is EscalationEvent.DialerOpened -> candidateId
        is EscalationEvent.DialerFailed -> candidateId
    }

private fun EscalationEvent.sourceOrNull(): String? =
    (this as? EscalationEvent.DialerRequested)?.source?.name

private fun EscalationEvent.reasonOrDefault(): String =
    when (this) {
        is EscalationEvent.RecheckStarted -> reason
        is EscalationEvent.SafetyCheckRequired -> reason
        is EscalationEvent.CandidateRejected -> reason
        is EscalationEvent.UserConfirmedSafe -> "USER_CONFIRMED_SAFE"
        is EscalationEvent.DialerRequested -> "DIALER_REQUESTED_${source.name}"
        is EscalationEvent.DialerOpened -> "DIALER_OPENED"
        is EscalationEvent.DialerFailed -> "DIALER_FAILED:${reason}"
    }
