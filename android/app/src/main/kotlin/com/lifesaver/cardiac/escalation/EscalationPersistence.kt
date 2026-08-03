package com.lifesaver.cardiac.escalation

/**
 * Durable boundary for escalation snapshots and transition audit events.
 *
 * Persistence is injected at the coordinator boundary. The state machine
 * remains deterministic and side-effect free, while a failed write can fail
 * closed before a candidate window or dialer side effect proceeds.
 */
interface EscalationPersistence {
    fun initialize(snapshot: EscalationSnapshot): Boolean = true

    fun persist(result: TransitionResult): Boolean = true

    fun persistRestartNormalization(
        previous: EscalationSnapshot,
        current: EscalationSnapshot,
        occurredAtEpochMs: Long,
    ): Boolean = true

    fun close() = Unit
}
