package com.lifesaver.cardiac.persistence

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.lifesaver.cardiac.escalation.DialerRequestSource
import com.lifesaver.cardiac.escalation.EscalationEvent
import com.lifesaver.cardiac.escalation.EscalationSnapshot
import com.lifesaver.cardiac.escalation.EscalationStage
import com.lifesaver.cardiac.escalation.TransitionResult

@Entity(tableName = "escalation_snapshot")
data class EscalationSnapshotEntity(
    @PrimaryKey val singletonId: Int = SINGLETON_ID,
    val stage: String,
    val sequence: Long,
    val activeCandidateId: String?,
    val stageEnteredAtEpochMs: Long,
    val updatedAtEpochMs: Long,
    val reason: String,
    val cooldownUntilEpochMs: Long?,
) {
    fun toDomainOrNull(): EscalationSnapshot? {
        val parsedStage = runCatching { EscalationStage.valueOf(stage) }.getOrNull()
            ?: return null
        return runCatching {
            EscalationSnapshot(
                stage = parsedStage,
                sequence = sequence,
                activeCandidateId = activeCandidateId,
                stageEnteredAtEpochMs = stageEnteredAtEpochMs,
                updatedAtEpochMs = updatedAtEpochMs,
                reason = reason,
                cooldownUntilEpochMs = cooldownUntilEpochMs,
            )
        }.getOrNull()
    }

    companion object {
        const val SINGLETON_ID = 1

        fun fromDomain(snapshot: EscalationSnapshot): EscalationSnapshotEntity =
            EscalationSnapshotEntity(
                stage = snapshot.stage.name,
                sequence = snapshot.sequence,
                activeCandidateId = snapshot.activeCandidateId,
                stageEnteredAtEpochMs = snapshot.stageEnteredAtEpochMs,
                updatedAtEpochMs = snapshot.updatedAtEpochMs,
                reason = snapshot.reason,
                cooldownUntilEpochMs = snapshot.cooldownUntilEpochMs,
            )
    }
}

@Entity(
    tableName = "escalation_transition",
    indices = [
        Index(value = ["occurredAtEpochMs"]),
        Index(value = ["candidateId"]),
        Index(value = ["accepted"]),
    ],
)
data class EscalationTransitionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val eventType: String,
    val occurredAtEpochMs: Long,
    val candidateId: String?,
    val eventReason: String?,
    val accepted: Boolean,
    val rejectionReason: String?,
    val previousStage: String,
    val currentStage: String,
    val currentSequence: Long,
    val currentReason: String,
) {
    companion object {
        fun fromTransition(result: TransitionResult): EscalationTransitionEntity =
            when (result) {
                is TransitionResult.Accepted -> EscalationTransitionEntity(
                    eventType = eventType(result.event),
                    occurredAtEpochMs = result.event.occurredAtEpochMs,
                    candidateId = candidateId(result.event),
                    eventReason = eventReason(result.event),
                    accepted = true,
                    rejectionReason = null,
                    previousStage = result.previous.stage.name,
                    currentStage = result.current.stage.name,
                    currentSequence = result.current.sequence,
                    currentReason = result.current.reason,
                )

                is TransitionResult.Rejected -> EscalationTransitionEntity(
                    eventType = eventType(result.event),
                    occurredAtEpochMs = result.event.occurredAtEpochMs,
                    candidateId = candidateId(result.event),
                    eventReason = eventReason(result.event),
                    accepted = false,
                    rejectionReason = result.reason,
                    previousStage = result.current.stage.name,
                    currentStage = result.current.stage.name,
                    currentSequence = result.current.sequence,
                    currentReason = result.current.reason,
                )
            }

        fun recovery(
            previous: EscalationSnapshot,
            current: EscalationSnapshot,
        ): EscalationTransitionEntity = EscalationTransitionEntity(
            eventType = "PROCESS_RESTORE_NORMALIZED",
            occurredAtEpochMs = current.updatedAtEpochMs,
            candidateId = current.activeCandidateId ?: previous.activeCandidateId,
            eventReason = current.reason,
            accepted = true,
            rejectionReason = null,
            previousStage = previous.stage.name,
            currentStage = current.stage.name,
            currentSequence = current.sequence,
            currentReason = current.reason,
        )

        private fun eventType(event: EscalationEvent): String = when (event) {
            is EscalationEvent.RecheckStarted -> "RECHECK_STARTED"
            is EscalationEvent.SafetyCheckRequired -> "SAFETY_CHECK_REQUIRED"
            is EscalationEvent.CandidateRejected -> "CANDIDATE_REJECTED"
            is EscalationEvent.UserConfirmedSafe -> "USER_CONFIRMED_SAFE"
            is EscalationEvent.DialerRequested -> "DIALER_REQUESTED"
            is EscalationEvent.DialerOpened -> "DIALER_OPENED"
            is EscalationEvent.DialerFailed -> "DIALER_FAILED"
        }

        private fun candidateId(event: EscalationEvent): String = when (event) {
            is EscalationEvent.RecheckStarted -> event.candidateId
            is EscalationEvent.SafetyCheckRequired -> event.candidateId
            is EscalationEvent.CandidateRejected -> event.candidateId
            is EscalationEvent.UserConfirmedSafe -> event.candidateId
            is EscalationEvent.DialerRequested -> event.candidateId
            is EscalationEvent.DialerOpened -> event.candidateId
            is EscalationEvent.DialerFailed -> event.candidateId
        }

        private fun eventReason(event: EscalationEvent): String? = when (event) {
            is EscalationEvent.RecheckStarted -> event.reason
            is EscalationEvent.SafetyCheckRequired -> event.reason
            is EscalationEvent.CandidateRejected -> event.reason
            is EscalationEvent.UserConfirmedSafe -> null
            is EscalationEvent.DialerRequested -> when (event.source) {
                DialerRequestSource.USER -> "USER"
                DialerRequestSource.PROMPT_TIMEOUT -> "PROMPT_TIMEOUT"
            }
            is EscalationEvent.DialerOpened -> null
            is EscalationEvent.DialerFailed -> event.reason
        }
    }
}
