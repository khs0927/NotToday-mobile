package com.lifesaver.cardiac.escalation

import com.lifesaver.cardiac.FallCandidateEvent

/**
 * Maps sensor and user events into the pure escalation state machine.
 *
 * The coordinator deliberately performs no Android side effects. A service may
 * execute an explicitly approved side effect only after an accepted transition
 * and must report the outcome through [recordDialerOpened] or
 * [recordDialerFailed].
 */
class EscalationCoordinator(
    private val machine: EscalationStateMachine = EscalationStateMachine(),
) {
    fun currentSnapshot(): EscalationSnapshot = machine.currentSnapshot()

    fun handleFallCandidate(event: FallCandidateEvent): TransitionResult =
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
        }

    fun confirmSafe(
        candidateId: String,
        occurredAtEpochMs: Long,
    ): TransitionResult =
        machine.handle(
            EscalationEvent.UserConfirmedSafe(
                candidateId = candidateId,
                occurredAtEpochMs = occurredAtEpochMs,
            ),
        )

    fun requestDialer(
        candidateId: String,
        source: DialerRequestSource,
        occurredAtEpochMs: Long,
    ): TransitionResult = machine.handle(
        EscalationEvent.DialerRequested(
            candidateId = candidateId,
            source = source,
            occurredAtEpochMs = occurredAtEpochMs,
        ),
    )

    fun recordDialerOpened(
        candidateId: String,
        occurredAtEpochMs: Long,
    ): TransitionResult =
        machine.handle(
            EscalationEvent.DialerOpened(
                candidateId = candidateId,
                occurredAtEpochMs = occurredAtEpochMs,
            ),
        )

    fun recordDialerFailed(
        candidateId: String,
        reason: String,
        occurredAtEpochMs: Long,
    ): TransitionResult = machine.handle(
        EscalationEvent.DialerFailed(
            candidateId = candidateId,
            reason = reason,
            occurredAtEpochMs = occurredAtEpochMs,
        ),
    )
}
