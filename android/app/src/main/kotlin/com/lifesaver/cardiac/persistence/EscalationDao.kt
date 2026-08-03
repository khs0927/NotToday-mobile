package com.lifesaver.cardiac.persistence

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
abstract class EscalationDao {
    @Query("SELECT * FROM escalation_snapshot WHERE singletonId = 1 LIMIT 1")
    abstract suspend fun loadSnapshot(): EscalationSnapshotEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun upsertSnapshot(snapshot: EscalationSnapshotEntity)

    @Insert
    protected abstract suspend fun insertTransition(transition: EscalationTransitionEntity)

    @Query(
        """
        DELETE FROM escalation_transition
        WHERE id NOT IN (
            SELECT id FROM escalation_transition
            ORDER BY id DESC
            LIMIT :keepLatest
        )
        """,
    )
    protected abstract suspend fun trimTransitions(keepLatest: Int)

    @Query("SELECT * FROM escalation_transition ORDER BY id DESC LIMIT :limit")
    abstract suspend fun recentTransitions(limit: Int): List<EscalationTransitionEntity>

    @Query("SELECT COUNT(*) FROM escalation_transition")
    abstract suspend fun transitionCount(): Long

    @Transaction
    open suspend fun persist(
        transition: EscalationTransitionEntity,
        snapshot: EscalationSnapshotEntity,
        keepLatest: Int,
    ) {
        require(keepLatest > 0) { "keepLatest must be positive" }
        insertTransition(transition)
        upsertSnapshot(snapshot)
        trimTransitions(keepLatest)
    }
}
