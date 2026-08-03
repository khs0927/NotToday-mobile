# GitHub Actions runner incident — 2026-08-03

## Scope

This record applies to the `Android safety baseline` workflow on branch
`agent/room-state-persistence-v2`, commit
`04fdc02f48d042954e0a972de2cf754413bf73c2`.

## Observed behavior

- Workflow run `30816132969` concluded as failed.
- Job `flutter-static-validation` reported failure before publishing any step.
- The dependent `android-debug-build` job was skipped.
- The failed job exposed neither step metadata nor a downloadable log; the log
  endpoint returned a missing-blob response.
- A re-run followed the same logless startup path.
- The immediately preceding verified baseline run `30804034200` succeeded with
  the same workflow and completed Flutter, Android, Room schema, native test,
  APK, and prohibited-permission gates.

## Current classification

This is classified as a GitHub-hosted runner or workflow-start incident until a
new run produces step-level evidence. It is not evidence of a Kotlin, Flutter,
Room, or safety assertion failure.

## Safe response

1. Do not weaken or bypass any safety gate.
2. Do not merge the stacked Draft PR while the current head lacks a green run.
3. Trigger a fresh run using a documentation-only commit.
4. If the fresh run starts, use its step-level result as the source of truth.
5. If it again fails before all steps, escalate as a GitHub Actions service or
   runner allocation incident and add an explicit manual-dispatch diagnostic
   workflow without changing the application safety implementation.

## Completion condition

The dialer-source hardening is considered remotely verified only after a fresh
workflow run completes the existing static analysis, native tests, Room schema,
Android test APK compilation, debug APK build, and permission inspection gates.
