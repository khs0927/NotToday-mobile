# Remote orchestration execution policy

This repository is being advanced through stacked Draft pull requests and explicit safety gates.

## Rules

1. `main` is not modified directly.
2. Each major risk boundary is developed on a stacked `agent/*` branch.
3. Every branch must pass Flutter analysis, Android compilation, native tests, APK inspection, and prohibited-permission checks.
4. Automatic SMS, direct calling, hard-coded location, and automatic emergency retry remain prohibited.
5. Real-device claims are not marked complete from emulator or unit-test evidence.
6. External services are introduced only when local/offline behavior and consent boundaries are already defined.
7. Authentication is requested from the repository owner only when a connected service cannot be used through the existing GitHub integration.

## Current stack

- `main`
  - `agent/buildable-mvp-safety-baseline` — PR #7
    - `agent/escalation-state-machine` — PR #8
      - `agent/room-persistence` — PR #10

## Approved next stack

- `agent/health-services`
- `agent/wear-emulator-validation`
- `agent/phone-companion-boundary`
- `agent/privacy-beta-controls`

## Completed remote gates

- reproducible Flutter/Android debug APK
- packaged permission regression checks
- deterministic escalation state machine
- guarded sensor-candidate lifecycle
- fail-closed Room persistence
- checked-in Room schema equality check

Each next branch is created only from a CI-green predecessor and remains Draft until its own gates are satisfied.
