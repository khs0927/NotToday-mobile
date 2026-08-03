# Escalation persistence and recovery model

## Purpose

The local database exists to make escalation transitions auditable and to prevent a process restart from silently resuming an unverified emergency side effect.

It is not a medical record, diagnostic database, cloud synchronization queue, or storage location for raw health signals.

## Database baseline

- Room: 2.8.4
- Schema version: 1
- Checked-in schema: `android/app/schemas/com.lifesaver.cardiac.persistence.NotTodayDatabase/1.json`
- Verified schema SHA-256: `a383249e90029615b9b597eb67b74b190d637ad1453329d177d20548ba456add`

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

Each accepted event record and resulting latest snapshot are written in one Room transaction. Rejected transition attempts write an audit record without changing the snapshot. Android side-effect code receives an accepted transition only after the transaction completes.

If durable storage cannot be confirmed:

1. the coordinator returns `PERSISTENCE_FAILED`;
2. the in-memory state is rolled back to the last durable snapshot;
3. the coordinator instance becomes faulted;
4. later requests return `PERSISTENCE_UNAVAILABLE`;
5. monitoring components stop;
6. the emergency dialer is not opened.

## Process restart rules

- `NORMAL` remains `NORMAL`.
- `RESOLVED` remains `RESOLVED` and retains cooldown information.
- `SAFETY_CHECK` remains visible for the same candidate.
- unverified `RECHECK` becomes `RESOLVED` with a cooldown.
- `DIALER_READY` becomes `SAFETY_CHECK`; a fresh explicit user action is required.

A normalization transition is written to the audit history.

## Data minimization

The persistence layer stores state-machine facts and derived candidate metadata only. It does not store:

- raw ECG or PPG waveforms
- continuous heart-rate samples
- continuous raw accelerometer streams
- location
- SMS content
- phone-call recordings
- medical diagnosis labels

## Migration rule

Every Room schema version must be exported to `android/app/schemas`, committed, hashed in CI, and accompanied by a migration test before the database version is incremented. CI runs `git diff --exit-code -- android/app/schemas` after Room code generation so an uncommitted schema change fails the build.

## Verified remote evidence

GitHub Actions run `30798014013` verified:

- Flutter static analysis
- Room/KSP code generation
- checked-in schema exactly matches generated schema
- 22 native tests with zero failures, errors, or skipped tests
- debug APK build
- packaged manifest and prohibited-permission checks

APK SHA-256: `8fc146f4b20a8c74c681e3d86a1dcb259ceb531bb228e506798add91befc0783`

Artifact ID: `8849830577`

## Remaining gate

An Android instrumentation test must kill and recreate the process around persisted `RECHECK`, `SAFETY_CHECK`, and `DIALER_READY` states. JVM restoration tests are complete, but they do not replace process-level device validation.
