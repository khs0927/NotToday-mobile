package com.lifesaver.cardiac.escalation

import com.lifesaver.cardiac.FallCandidateEvent
import com.lifesaver.cardiac.persistence.EscalationTransitionStore

/**
 * Maps sensor and user events into the pure escalation state machine.
 *
 * When a durable store is configured, every accepted or rejected event is
 * recorded before the result is returned to Android side-effect code. A write
 * failure permanently faults this coordinator instance and converts the result
 * to a rejection, preventing dialer execution or further escalation.
 */
class EscalationCoordinator(
    private val machine: EscalationStateMachine = EscalationStateMachine(),
    private val transitionStore: EscalationTransitionStore? = null,
) {
    @Volatile
    private var persistenceFaulted = false

    fun currentSnapshot(): EscalationSnapshot = machine.currentSnapshot()

    fun isPersistenceFaulted(): Boolean = persistenceFaulted

    fun handleFallCandidate(event: FallCandidateEvent): TransitionResult =
        when (event) {
            is FallCandidateEvent.Started -> apply(
                EscalationEvent.RecheckStarted(
                    candidateId = event.candidateId,
                    reason = "IMPACT_CANDIDATE",
                    occurredAtEpochMs = event.occurredAtEpochMs,
                ),
            )

            is FallCandidateEvent.Verified -> apply(
                EscalationEvent.SafetyCheckRequired(
                    candidateId = event.candidateId,
                    reason = event.reason,
                    occurredAtEpochMs = event.occurredAtEpochMs,
                ),
            )

            is FallCandidateEvent.Rejected -> apply(
                EscalationEvent.CandidateRejected(
                    candidateId = event.candidateId,
                    reason = event.reason,
                    occurredAtEpochMs = event.occurredAtEpochMs,
                ),
            )
        }

    fun confirmSafe(
        candidateId: String,
        occurredAtEpochMs: Long,
    ): TransitionResult = apply(
        EscalationEvent.UserConfirmedSafe(
            candidateId = candidateId,
            occurredAtEpochMs = occurredAtEpochMs,
        ),
    )

    fun requestDialer(
        candidateId: String,
        source: DialerRequestSource,
        occurredAtEpochMs: Long,
    ): TransitionResult = apply(
        EscalationEvent.DialerRequested(
            candidateId = candidateId,
            source = source,
            occurredAtEpochMs = occurredAtEpochMs,
        ),
    )

    fun recordDialerOpened(
        candidateId: String,
        occurredAtEpochMs: Long,
    ): TransitionResult = apply(
        EscalationEvent.DialerOpened(
            candidateId = candidateId,
            occurredAtEpochMs = occurredAtEpochMs,
        ),
    )

    fun recordDialerFailed(
        candidateId: String,
        reason: String,
        occurredAtEpochMs: Long,
    ): TransitionResult = apply(
        EscalationEvent.DialerFailed(
            candidateId = candidateId,
            reason = reason,
            occurredAtEpochMs = occurredAtEpochMs,
        ),
    )

    @Synchronized
    private fun apply(event: EscalationEvent): TransitionResult {
        if (persistenceFaulted) {
            return TransitionResult.Rejected(
                current = machine.currentSnapshot(),
                event = event,
                reason = "PERSISTENCE_UNAVAILABLE",
            )
        }

        val result = machine.handle(event)
        val store = transitionStore ?: return result

        return try {
            store.persist(result)
            result
        } catch (_: RuntimeException) {
            persistenceFaulted = true
            TransitionResult.Rejected(
                current = machine.currentSnapshot(),
                event = event,
                reason = "PERSISTENCE_FAILED",
            )
        }
    }

    companion object {
        fun createPersistent(
            transitionStore: EscalationTransitionStore,
            restoredAtEpochMs: Long,
            cooldownMs: Long = EscalationStateMachine.DEFAULT_COOLDOWN_MS,
        ): EscalationCoordinator {
            val persisted = transitionStore.loadLatestSnapshot()
            val machine = if (persisted == null) {
                EscalationStateMachine(
                    cooldownMs = cooldownMs,
                    initialTimestampEpochMs = restoredAtEpochMs,
                )
            } else {
                EscalationStateMachine.restorePersisted(
                    persisted = persisted,
                    restoredAtEpochMs = restoredAtEpochMs,
                    cooldownMs = cooldownMs,
                )
            }

            if (persisted != null && persisted != machine.currentSnapshot()) {
                transitionStore.persistRecovery(
                    previous = persisted,
                    current = machine.currentSnapshot(),
                )
            }

            return EscalationCoordinator(
                machine = machine,
                transitionStore = transitionStore,
            )
        }
    }
}
