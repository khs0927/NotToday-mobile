package com.lifesaver.cardiac.escalation

/**
 * Durable boundary for escalation snapshots and transition audit events.
 *
 * The state machine remains deterministic and side-effect free. Persistence is
 * deliberately injected at the coordinator boundary so pure transition tests
 * do not require Android or a database.
 */
interface EscalationPersistence {
    fun initialize(snapshot: EscalationSnapshot) = Unit

    fun persist(result: TransitionResult) = Unit

    fun persistRestartNormalization(
        previous: EscalationSnapshot,
        current: EscalationSnapshot,
        occurredAtEpochMs: Long,
    ) = Unit

    fun close() = Unit
}
