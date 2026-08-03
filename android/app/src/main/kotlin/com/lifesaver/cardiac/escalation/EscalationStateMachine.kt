package com.lifesaver.cardiac.escalation

enum class EscalationStage {
    NORMAL,
    RECHECK,
    SAFETY_CHECK,
    DIALER_READY,
    RESOLVED,
}

enum class DialerRequestSource {
    USER,
    PROMPT_TIMEOUT,
}

sealed interface EscalationEvent {
    val occurredAtEpochMs: Long

    data class RecheckStarted(
        val candidateId: String,
        val reason: String,
        override val occurredAtEpochMs: Long,
    ) : EscalationEvent

    data class SafetyCheckRequired(
        val candidateId: String,
        val reason: String,
        override val occurredAtEpochMs: Long,
    ) : EscalationEvent

    data class CandidateRejected(
        val candidateId: String,
        val reason: String,
        override val occurredAtEpochMs: Long,
    ) : EscalationEvent

    data class UserConfirmedSafe(
        val candidateId: String,
        override val occurredAtEpochMs: Long,
    ) : EscalationEvent

    data class DialerRequested(
        val candidateId: String,
        val source: DialerRequestSource,
        override val occurredAtEpochMs: Long,
    ) : EscalationEvent

    data class DialerOpened(
        val candidateId: String,
        override val occurredAtEpochMs: Long,
    ) : EscalationEvent

    data class DialerFailed(
        val candidateId: String,
        val reason: String,
        override val occurredAtEpochMs: Long,
    ) : EscalationEvent
}

data class EscalationSnapshot(
    val stage: EscalationStage,
    val sequence: Long,
    val activeCandidateId: String?,
    val stageEnteredAtEpochMs: Long,
    val updatedAtEpochMs: Long,
    val reason: String,
    val cooldownUntilEpochMs: Long?,
)

sealed interface TransitionResult {
    data class Accepted(
        val previous: EscalationSnapshot,
        val current: EscalationSnapshot,
        val event: EscalationEvent,
    ) : TransitionResult

    data class Rejected(
        val current: EscalationSnapshot,
        val event: EscalationEvent,
        val reason: String,
    ) : TransitionResult
}

/**
 * Deterministic escalation state machine.
 *
 * This class never invokes Android APIs, opens a dialer, sends a message, or
 * schedules a retry. It only accepts or rejects explicit state-transition
 * events. Side effects must be performed by a separate coordinator after an
 * accepted transition and must report their result back as another event.
 */
class EscalationStateMachine private constructor(
    initialSnapshot: EscalationSnapshot,
    private val cooldownMs: Long,
) {
    private var snapshot: EscalationSnapshot = validateSnapshot(initialSnapshot)

    init {
        require(cooldownMs >= 0L) { "cooldownMs must be non-negative" }
    }

    constructor(
        cooldownMs: Long = DEFAULT_COOLDOWN_MS,
        initialTimestampEpochMs: Long = 0L,
    ) : this(
        initialSnapshot = normalSnapshot(initialTimestampEpochMs),
        cooldownMs = cooldownMs,
    )

    @Synchronized
    fun currentSnapshot(): EscalationSnapshot = snapshot

    @Synchronized
    fun handle(event: EscalationEvent): TransitionResult {
        if (event.occurredAtEpochMs < snapshot.updatedAtEpochMs) {
            return reject(event, "OUT_OF_ORDER_EVENT")
        }

        return when (snapshot.stage) {
            EscalationStage.NORMAL -> handleNormal(event)
            EscalationStage.RECHECK -> handleRecheck(event)
            EscalationStage.SAFETY_CHECK -> handleSafetyCheck(event)
            EscalationStage.DIALER_READY -> handleDialerReady(event)
            EscalationStage.RESOLVED -> handleResolved(event)
        }
    }

    private fun handleNormal(event: EscalationEvent): TransitionResult =
        when (event) {
            is EscalationEvent.RecheckStarted -> {
                if (!isValidCandidateId(event.candidateId)) {
                    reject(event, "INVALID_CANDIDATE_ID")
                } else {
                    transition(
                        event = event,
                        nextStage = EscalationStage.RECHECK,
                        candidateId = event.candidateId,
                        reason = event.reason,
                    )
                }
            }

            else -> reject(event, "EVENT_NOT_ALLOWED_FROM_NORMAL")
        }

    private fun handleRecheck(event: EscalationEvent): TransitionResult =
        when (event) {
            is EscalationEvent.SafetyCheckRequired -> {
                if (!matchesActiveCandidate(event.candidateId)) {
                    reject(event, "CANDIDATE_MISMATCH")
                } else {
                    transition(
                        event = event,
                        nextStage = EscalationStage.SAFETY_CHECK,
                        candidateId = event.candidateId,
                        reason = event.reason,
                    )
                }
            }

            is EscalationEvent.CandidateRejected -> {
                if (!matchesActiveCandidate(event.candidateId)) {
                    reject(event, "CANDIDATE_MISMATCH")
                } else {
                    resolve(event, event.reason)
                }
            }

            else -> reject(event, "EVENT_NOT_ALLOWED_FROM_RECHECK")
        }

    private fun handleSafetyCheck(event: EscalationEvent): TransitionResult =
        when (event) {
            is EscalationEvent.UserConfirmedSafe -> {
                if (!matchesActiveCandidate(event.candidateId)) {
                    reject(event, "CANDIDATE_MISMATCH")
                } else {
                    resolve(event, "USER_CONFIRMED_SAFE")
                }
            }

            is EscalationEvent.DialerRequested -> {
                if (!matchesActiveCandidate(event.candidateId)) {
                    reject(event, "CANDIDATE_MISMATCH")
                } else {
                    transition(
                        event = event,
                        nextStage = EscalationStage.DIALER_READY,
                        candidateId = event.candidateId,
                        reason = "DIALER_REQUESTED_${event.source.name}",
                    )
                }
            }

            is EscalationEvent.CandidateRejected -> {
                if (!matchesActiveCandidate(event.candidateId)) {
                    reject(event, "CANDIDATE_MISMATCH")
                } else {
                    resolve(event, event.reason)
                }
            }

            else -> reject(event, "EVENT_NOT_ALLOWED_FROM_SAFETY_CHECK")
        }

    private fun handleDialerReady(event: EscalationEvent): TransitionResult =
        when (event) {
            is EscalationEvent.DialerOpened -> {
                if (!matchesActiveCandidate(event.candidateId)) {
                    reject(event, "CANDIDATE_MISMATCH")
                } else {
                    resolve(event, "DIALER_OPENED")
                }
            }

            is EscalationEvent.DialerFailed -> {
                if (!matchesActiveCandidate(event.candidateId)) {
                    reject(event, "CANDIDATE_MISMATCH")
                } else {
                    resolve(event, "DIALER_FAILED:${event.reason}")
                }
            }

            is EscalationEvent.UserConfirmedSafe -> {
                if (!matchesActiveCandidate(event.candidateId)) {
                    reject(event, "CANDIDATE_MISMATCH")
                } else {
                    resolve(event, "USER_CONFIRMED_SAFE")
                }
            }

            else -> reject(event, "EVENT_NOT_ALLOWED_FROM_DIALER_READY")
        }

    /**
     * Rolls back an accepted in-memory transition when its durable write fails.
     * The coordinator calls this while holding its serialized event boundary.
     */
    internal fun restoreSnapshotForPersistenceFailure(previous: EscalationSnapshot) {
        snapshot = validateSnapshot(previous)
    }

    private fun handleResolved(event: EscalationEvent): TransitionResult =
        when (event) {
            is EscalationEvent.RecheckStarted -> {
                if (isInCooldown(event.occurredAtEpochMs)) {
                    reject(event, "COOLDOWN_ACTIVE")
                } else if (!isValidCandidateId(event.candidateId)) {
                    reject(event, "INVALID_CANDIDATE_ID")
                } else {
                    transition(
                        event = event,
                        nextStage = EscalationStage.RECHECK,
                        candidateId = event.candidateId,
                        reason = event.reason,
                        cooldownUntilEpochMs = null,
                    )
                }
            }

            else -> reject(event, "EVENT_NOT_ALLOWED_FROM_RESOLVED")
        }

    private fun resolve(
        event: EscalationEvent,
        reason: String,
    ): TransitionResult = transition(
        event = event,
        nextStage = EscalationStage.RESOLVED,
        candidateId = null,
        reason = reason,
        cooldownUntilEpochMs = safeAdd(event.occurredAtEpochMs, cooldownMs),
    )

    private fun transition(
        event: EscalationEvent,
        nextStage: EscalationStage,
        candidateId: String?,
        reason: String,
        cooldownUntilEpochMs: Long? = snapshot.cooldownUntilEpochMs,
    ): TransitionResult.Accepted {
        val previous = snapshot
        snapshot = EscalationSnapshot(
            stage = nextStage,
            sequence = previous.sequence + 1L,
            activeCandidateId = candidateId,
            stageEnteredAtEpochMs = event.occurredAtEpochMs,
            updatedAtEpochMs = event.occurredAtEpochMs,
            reason = sanitizeReason(reason),
            cooldownUntilEpochMs = cooldownUntilEpochMs,
        )
        return TransitionResult.Accepted(previous, snapshot, event)
    }

    private fun reject(
        event: EscalationEvent,
        reason: String,
    ): TransitionResult.Rejected = TransitionResult.Rejected(
        current = snapshot,
        event = event,
        reason = reason,
    )

    private fun matchesActiveCandidate(candidateId: String): Boolean =
        snapshot.activeCandidateId == candidateId && isValidCandidateId(candidateId)

    private fun isInCooldown(timestampEpochMs: Long): Boolean =
        snapshot.cooldownUntilEpochMs?.let { timestampEpochMs < it } ?: false

    companion object {
        const val DEFAULT_COOLDOWN_MS: Long = 30_000L
        private const val MAX_REASON_LENGTH = 240
        private const val MAX_CANDIDATE_ID_LENGTH = 96

        fun restorePersisted(
            persisted: EscalationSnapshot,
            restoredAtEpochMs: Long,
            cooldownMs: Long = DEFAULT_COOLDOWN_MS,
        ): EscalationStateMachine {
            val safeRestoreTime = maxOf(restoredAtEpochMs, persisted.updatedAtEpochMs)

            val normalized = when (persisted.stage) {
                EscalationStage.RECHECK -> EscalationSnapshot(
                    stage = EscalationStage.RESOLVED,
                    sequence = persisted.sequence + 1L,
                    activeCandidateId = null,
                    stageEnteredAtEpochMs = safeRestoreTime,
                    updatedAtEpochMs = safeRestoreTime,
                    reason = "RESTORED_UNVERIFIED_RECHECK",
                    cooldownUntilEpochMs = safeAdd(safeRestoreTime, cooldownMs),
                )

                EscalationStage.DIALER_READY -> EscalationSnapshot(
                    stage = EscalationStage.SAFETY_CHECK,
                    sequence = persisted.sequence + 1L,
                    activeCandidateId = persisted.activeCandidateId,
                    stageEnteredAtEpochMs = safeRestoreTime,
                    updatedAtEpochMs = safeRestoreTime,
                    reason = "RESTORED_REQUIRES_FRESH_USER_ACTION",
                    cooldownUntilEpochMs = null,
                )

                else -> persisted.copy(updatedAtEpochMs = safeRestoreTime)
            }

            return EscalationStateMachine(normalized, cooldownMs)
        }

        private fun normalSnapshot(timestampEpochMs: Long): EscalationSnapshot =
            EscalationSnapshot(
                stage = EscalationStage.NORMAL,
                sequence = 0L,
                activeCandidateId = null,
                stageEnteredAtEpochMs = timestampEpochMs,
                updatedAtEpochMs = timestampEpochMs,
                reason = "INITIALIZED",
                cooldownUntilEpochMs = null,
            )

        private fun validateSnapshot(value: EscalationSnapshot): EscalationSnapshot {
            require(value.sequence >= 0L) { "sequence must be non-negative" }
            require(value.stageEnteredAtEpochMs <= value.updatedAtEpochMs) {
                "stageEnteredAtEpochMs must not exceed updatedAtEpochMs"
            }
            require(value.reason.isNotBlank()) { "reason must not be blank" }

            when (value.stage) {
                EscalationStage.NORMAL -> {
                    require(value.activeCandidateId == null) {
                        "NORMAL must not retain an active candidate"
                    }
                    require(value.cooldownUntilEpochMs == null) {
                        "NORMAL must not retain a cooldown"
                    }
                }

                EscalationStage.RECHECK,
                EscalationStage.SAFETY_CHECK,
                EscalationStage.DIALER_READY,
                -> {
                    require(value.activeCandidateId != null) {
                        "${value.stage} must retain an active candidate"
                    }
                    require(value.cooldownUntilEpochMs == null) {
                        "${value.stage} must not retain a cooldown"
                    }
                }

                EscalationStage.RESOLVED -> {
                    require(value.activeCandidateId == null) {
                        "RESOLVED must not retain an active candidate"
                    }
                    val cooldownUntil = requireNotNull(value.cooldownUntilEpochMs) {
                        "RESOLVED must retain a cooldown"
                    }
                    require(cooldownUntil >= value.updatedAtEpochMs) {
                        "RESOLVED cooldown must not precede updatedAtEpochMs"
                    }
                }
            }

            if (value.activeCandidateId != null) {
                require(isValidCandidateId(value.activeCandidateId)) {
                    "activeCandidateId is invalid"
                }
            }
            return value.copy(reason = sanitizeReason(value.reason))
        }

        private fun isValidCandidateId(candidateId: String): Boolean =
            candidateId.isNotBlank() && candidateId.length <= MAX_CANDIDATE_ID_LENGTH

        private fun sanitizeReason(reason: String): String =
            reason.trim().ifBlank { "UNSPECIFIED" }.take(MAX_REASON_LENGTH)

        private fun safeAdd(left: Long, right: Long): Long =
            if (right > 0L && left > Long.MAX_VALUE - right) Long.MAX_VALUE else left + right
    }
}
