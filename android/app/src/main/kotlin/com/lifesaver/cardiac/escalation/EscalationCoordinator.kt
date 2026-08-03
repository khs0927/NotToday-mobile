package com.lifesaver.cardiac.escalation

import com.lifesaver.cardiac.FallCandidateEvent

/**
 * Maps sensor and user events into the pure escalation state machine.
 *
 * A transition is exposed to callers only after its optional durable write
 * succeeds. If persistence fails, accepted in-memory transitions are rolled
 * back and the caller receives PERSISTENCE_UNAVAILABLE.
 */
class EscalationCoordinator(
    private val machine: EscalationStateMachine = EscalationStateMachine(),
    private val persistence: EscalationPersistence? = null,
) {
    @Synchronized
    fun currentSnapshot(): EscalationSnapshot = machine.currentSnapshot()

    @Synchronized
    fun handleFallCandidate(event: FallCandidateEvent): TransitionResult =
        record(
            when (event) {
                is FallCandidateEvent.Started -> machine.handle(
                    EscalationEvent.RecheckStarted(
                        candidateId = event.candidateId,
                        reason = "IMPACT_CANDIDATE",
                        occurredAtEpochMs = event.occurredAtEpochMs,
                    ),
                )

                is FallCandidateEvent.Verified -> machine.handle(
                    EscalationEvent.SafetyCheckRequired(
                        candidateId = event.candidateId,
                        reason = event.reason,
                        occurredAtEpochMs = event.occurredAtEpochMs,
                    ),
                )

                is FallCandidateEvent.Rejected -> machine.handle(
                    EscalationEvent.CandidateRejected(
                        candidateId = event.candidateId,
                        reason = event.reason,
                        occurredAtEpochMs = event.occurredAtEpochMs,
                    ),
                )
            },
        )

    @Synchronized
    fun confirmSafe(
        candidateId: String,
        occurredAtEpochMs: Long,
    ): TransitionResult =
        record(
            machine.handle(
                EscalationEvent.UserConfirmedSafe(
                    candidateId = candidateId,
                    occurredAtEpochMs = occurredAtEpochMs,
                ),
            ),
        )

    @Synchronized
    fun requestDialer(
        candidateId: String,
        source: DialerRequestSource,
        occurredAtEpochMs: Long,
    ): TransitionResult = record(
        machine.handle(
            EscalationEvent.DialerRequested(
                candidateId = candidateId,
                source = source,
                occurredAtEpochMs = occurredAtEpochMs,
            ),
        ),
    )

    @Synchronized
    fun recordDialerOpened(
        candidateId: String,
        occurredAtEpochMs: Long,
    ): TransitionResult =
        record(
            machine.handle(
                EscalationEvent.DialerOpened(
                    candidateId = candidateId,
                    occurredAtEpochMs = occurredAtEpochMs,
                ),
            ),
        )

    @Synchronized
    fun recordDialerFailed(
        candidateId: String,
        reason: String,
        occurredAtEpochMs: Long,
    ): TransitionResult = record(
        machine.handle(
            EscalationEvent.DialerFailed(
                candidateId = candidateId,
                reason = reason,
                occurredAtEpochMs = occurredAtEpochMs,
            ),
        ),
    )

    private fun record(result: TransitionResult): TransitionResult {
        val store = persistence ?: return result
        if (store.persist(result)) return result

        if (result is TransitionResult.Accepted) {
            machine.restoreSnapshotForPersistenceFailure(result.previous)
        }

        return TransitionResult.Rejected(
            current = machine.currentSnapshot(),
            event = when (result) {
                is TransitionResult.Accepted -> result.event
                is TransitionResult.Rejected -> result.event
            },
            reason = "PERSISTENCE_UNAVAILABLE",
        )
    }
}
