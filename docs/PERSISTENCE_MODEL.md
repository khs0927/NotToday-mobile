# Escalation persistence and recovery model

## Purpose

The local database exists to make escalation transitions auditable and to prevent a process restart from silently resuming an unverified emergency side effect.

It is not a medical record, diagnostic database, or cloud synchronization queue.

## Tables

### `escalation_snapshot`

A single-row table containing the latest durable state:

- stage
- monotonic sequence
- active candidate ID, when applicable
- stage-entered timestamp
- last-updated timestamp
- transition reason
- cooldown deadline

### `escalation_transition`

A bounded audit history containing both accepted and rejected events:

- event type and timestamp
- candidate ID
- event reason
- accepted/rejected result
- rejection reason
- previous/current stage
- current sequence and reason

The current engineering retention limit is 500 transitions. This is a local implementation limit, not a final privacy retention policy.

## Transaction boundary

Each event record and its resulting latest snapshot are written in one Room transaction. Android side-effect code receives an accepted transition only after the transaction completes.

If durable storage cannot be confirmed:

1. the coordinator returns `PERSISTENCE_FAILED`;
2. the coordinator instance becomes faulted;
3. later requests return `PERSISTENCE_UNAVAILABLE`;
4. monitoring components stop;
5. the emergency dialer is not opened.

## Process restart rules

- `NORMAL` remains `NORMAL`.
- `RESOLVED` remains `RESOLVED` and retains cooldown information.
- `SAFETY_CHECK` remains visible for the same candidate.
- unverified `RECHECK` becomes `RESOLVED` with a cooldown.
- `DIALER_READY` becomes `SAFETY_CHECK`; a fresh explicit user action is required.

A normalization transition is written to the audit history.

## Data minimization

The persistence layer stores state-machine facts and derived IMU event metadata only. It does not store:

- raw ECG or PPG waveforms
- continuous raw accelerometer streams
- location
- SMS content
- phone-call recordings
- medical diagnosis labels

## Migration rule

Every Room schema version must be exported to `android/app/schemas`, committed, hashed in CI, and accompanied by a migration test before the database version is incremented.
