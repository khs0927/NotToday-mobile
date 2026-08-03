package com.lifesaver.cardiac.persistence

import android.content.Context
import com.lifesaver.cardiac.escalation.EscalationSnapshot
import com.lifesaver.cardiac.escalation.TransitionResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

interface EscalationTransitionStore {
    fun loadLatestSnapshot(): EscalationSnapshot?

    /**
     * Persists a transition record and the latest snapshot in one Room transaction.
     * Implementations must throw when durable storage cannot be confirmed.
     */
    fun persist(result: TransitionResult)

    fun persistRecovery(
        previous: EscalationSnapshot,
        current: EscalationSnapshot,
    )
}

class RoomEscalationTransitionStore(
    context: Context,
    private val keepLatestTransitions: Int = DEFAULT_HISTORY_LIMIT,
) : EscalationTransitionStore {
    private val dao = NotTodayDatabase.getInstance(context).escalationDao()

    init {
        require(keepLatestTransitions > 0) {
            "keepLatestTransitions must be positive"
        }
    }

    override fun loadLatestSnapshot(): EscalationSnapshot? =
        runBlocking(Dispatchers.IO) {
            val entity = dao.loadSnapshot() ?: return@runBlocking null
            entity.toDomainOrNull()
                ?: throw IllegalStateException("Persisted escalation snapshot is invalid")
        }

    override fun persist(result: TransitionResult) {
        val current = when (result) {
            is TransitionResult.Accepted -> result.current
            is TransitionResult.Rejected -> result.current
        }
        val transitionEntity = EscalationTransitionEntity.fromTransition(result)
        val snapshotEntity = EscalationSnapshotEntity.fromDomain(current)

        runBlocking(Dispatchers.IO) {
            dao.persist(
                transition = transitionEntity,
                snapshot = snapshotEntity,
                keepLatest = keepLatestTransitions,
            )
        }
    }

    override fun persistRecovery(
        previous: EscalationSnapshot,
        current: EscalationSnapshot,
    ) {
        runBlocking(Dispatchers.IO) {
            dao.persist(
                transition = EscalationTransitionEntity.recovery(previous, current),
                snapshot = EscalationSnapshotEntity.fromDomain(current),
                keepLatest = keepLatestTransitions,
            )
        }
    }

    companion object {
        const val DEFAULT_HISTORY_LIMIT = 500
    }
}
