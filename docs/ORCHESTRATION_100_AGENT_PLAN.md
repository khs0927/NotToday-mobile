# NotToday-mobile — 100-Agent Critical Orchestration Plan

Status: EXECUTION STARTED
Base: `agent/wear-emulator-validation` (`785200ce65a614197ee6ffbb794ee7faa4b34058`)
Working branch: `orchestration/100-agent-critical-validation`

## 1. Mission

Produce a mergeable, evidence-backed Wear OS safety-assistance release candidate without overstating medical or emergency capabilities. Every implementation claim must be challenged by an independent reviewer and proven by reproducible tests.

## 2. Non-negotiable rules

1. No automatic call, SMS, location transmission, diagnosis, or emergency-success claim.
2. No agent may approve its own implementation.
3. Every change requires: author evidence, adversarial critique, independent verification, and integrator acceptance.
4. A test that did not execute is not a pass. Compiled instrumentation APK is not emulator/device evidence.
5. Safety, privacy, security, and product claims can veto a merge.
6. Agents work in isolated branches or worktrees; integration occurs only through reviewed PRs.
7. Failed or ambiguous evidence defaults to fail-closed.

## 3. Governance

- A001 Chief Orchestrator: owns dependency graph and stop/go decisions.
- A002 Deputy Orchestrator: detects deadlocks, duplicated work, and missing owners.
- A003 Evidence Controller: verifies SHA, run ID, artifact digest, logs, and test counts.
- A004 Safety Veto Lead: can block any unsafe state, side effect, or claim.
- A005 Integration Lead: controls merge order and conflict resolution.

No implementation cell reports completion directly. It submits an Evidence Packet to the critic cell, then to verification, then integration.

## 4. The 100 agents

### Cell 1 — Program architecture and repository control (A001–A010)

- A001 Chief Orchestrator
- A002 Deputy / dependency scheduler
- A003 Evidence controller
- A004 Safety veto lead
- A005 Integration lead
- A006 Branch and worktree manager
- A007 PR topology reviewer
- A008 Issue and milestone planner
- A009 Release train manager
- A010 Architecture decision recorder

Deliverables: canonical branch graph, merge order, ADRs, execution ledger, final release-candidate decision.

### Cell 2 — Wear OS build and CI platform (A011–A020)

- A011 Flutter toolchain owner
- A012 Gradle/AGP/Kotlin owner
- A013 Android SDK/API 36 owner
- A014 GitHub Actions workflow engineer
- A015 Dependency/cache reliability engineer
- A016 Artifact/checksum engineer
- A017 Reproducible-build verifier
- A018 CI failure-injection critic
- A019 Public-runner capacity/timeout reviewer
- A020 CI evidence auditor

Deliverables: clean-checkout build, pinned toolchain, deterministic CI, failure artifacts, debug APK and checksums.

### Cell 3 — Health Services and permissions (A021–A030)

- A021 PassiveMonitoringClient implementer
- A022 MeasureClient diagnostic implementer
- A023 Capability and unsupported-device owner
- A024 API 30–35 permission owner
- A025 API 36+ permission owner
- A026 Permission denial/revocation owner
- A027 Listener service lifecycle owner
- A028 Signal freshness/order validator
- A029 Health API adversarial critic
- A030 Health integration verifier

Deliverables: official API boundary, capability checks, permission matrix, revocation behavior, runtime evidence.

### Cell 4 — IMU and fall-candidate pipeline (A031–A040)

- A031 Accelerometer pipeline owner
- A032 Gyroscope pipeline owner
- A033 Gravity/orientation owner
- A034 Impact feature engineer
- A035 Post-impact motion engineer
- A036 Watch-drop rejection engineer
- A037 Replay-vector engineer
- A038 Threshold/versioning owner
- A039 False-positive adversarial critic
- A040 Detector verification lead

Deliverables: replayable vectors, deterministic detector tests, watch-drop rejection, threshold report, known limits.

### Cell 5 — Escalation state machine and side-effect safety (A041–A050)

- A041 State-model owner
- A042 Event-contract owner
- A043 Candidate-ID/cooldown owner
- A044 Clock/order owner
- A045 Restart-normalization owner
- A046 Dialer request-source owner
- A047 Flutter/native authority-boundary owner
- A048 Side-effect threat-model critic
- A049 Model-based test engineer
- A050 State-machine verifier

Deliverables: legal/illegal transition matrix, fail-closed restoration, no automatic side effects, model-based tests.

### Cell 6 — Room persistence, recovery, and data governance (A051–A060)

- A051 Room schema owner
- A052 DAO/transaction owner
- A053 Snapshot/event audit owner
- A054 Retention owner
- A055 Corruption/orphan recovery owner
- A056 Process-kill instrumentation owner
- A057 Migration/versioning owner
- A058 Storage-failure adversarial critic
- A059 Privacy-minimization reviewer
- A060 Persistence verifier

Deliverables: transactional guarantees, process-restart tests, schema hash, migration plan, retention/deletion policy.

### Cell 7 — Flutter UX, accessibility, and claims safety (A061–A070)

- A061 Dashboard owner
- A062 Permission UX owner
- A063 Safety prompt owner
- A064 Dialer handoff UX owner
- A065 Error/unsupported-state owner
- A066 Wear OS layout/accessibility owner
- A067 Localization owner
- A068 Claims-language critic
- A069 UX state-fuzz tester
- A070 Flutter verification lead

Deliverables: truthful UI, accessible watch layouts, explicit failure states, no implied diagnosis or guaranteed dispatch.

### Cell 8 — Security, privacy, and abuse resistance (A071–A080)

- A071 Android component exposure reviewer
- A072 Manifest/permission reviewer
- A073 Intent and deep-link threat analyst
- A074 Log and telemetry privacy reviewer
- A075 Local data encryption analyst
- A076 Dependency/supply-chain scanner
- A077 Secret and credential scanner
- A078 Abuse-case/red-team critic
- A079 Privacy-policy/data-inventory owner
- A080 Security verification lead

Deliverables: threat model, permission diff, dependency report, secret scan, privacy inventory, abuse tests.

### Cell 9 — Emulator, device, performance, and reliability (A081–A090)

- A081 Wear OS emulator owner
- A082 API 33 image test owner
- A083 API 35 image test owner
- A084 API 36 image test owner
- A085 Physical-watch installation owner
- A086 Screen-off/service-survival owner
- A087 Battery/thermal owner
- A088 Process-kill/reboot owner
- A089 Runtime chaos critic
- A090 Device evidence verifier

Deliverables: emulator matrix, device logs, permission flows, service survival, 8-hour battery/data-loss report.

### Cell 10 — Quality, product, release, and independent audit (A091–A100)

- A091 Test strategy owner
- A092 Unit-test coverage owner
- A093 Integration-test owner
- A094 End-to-end test owner
- A095 Regression/bisect owner
- A096 Product acceptance owner
- A097 Medical-claims boundary reviewer
- A098 Independent final red team
- A099 Release evidence compiler
- A100 Final audit and go/no-go authority

Deliverables: traceability matrix, regression suite, final red-team report, release dossier, signed go/no-go decision.

## 5. Parallel execution waves

### Wave 0 — Freeze and baseline

A001–A020 establish the canonical code base, branch graph, CI baseline, artifact retention, and SHA ledger. No feature work starts without a reproducible green baseline.

Exit gate:
- clean checkout builds;
- all existing unit tests pass;
- artifact and checksum downloadable;
- branch duplication/divergence documented;
- PR #9 and #10 persistence variants compared and a canonical path selected.

### Wave 1 — Independent domain audits

Cells 3–8 inspect current implementation without modifying it. Each produces:
- observed behavior;
- missing evidence;
- failure hypotheses;
- safety/privacy risks;
- proposed test cases;
- recommended patch scope.

Exit gate: every domain has an audit and at least one independent critic response.

### Wave 2 — Contract hardening

Parallel changes are allowed only after interfaces are frozen:
- health signal contract;
- detector candidate contract;
- state-machine event contract;
- persistence contract;
- Flutter/native channel contract;
- evidence packet schema.

Exit gate: contract tests pass and critics accept that ambiguous inputs fail closed.

### Wave 3 — Implementation streams

Parallel streams:
1. Health/permissions.
2. IMU/replay harness.
3. State machine/dialer boundary.
4. Room/restart recovery.
5. Flutter UX/accessibility.
6. Security/privacy.
7. CI/emulator infrastructure.

Each stream uses a four-role loop:
- Builder creates patch and tests.
- Critic attempts to falsify assumptions.
- Verifier runs independent tests from clean checkout.
- Integrator accepts, requests changes, or rejects.

### Wave 4 — Adversarial cross-review

Cross-cell attacks:
- Health cell attacks detector assumptions about stale/missing heart rate.
- Detector cell attacks state-machine duplicate/cooldown behavior.
- Persistence cell attacks restart and corruption paths.
- Security cell attacks exported components and intent inputs.
- UX cell attacks misleading states and timeout behavior.
- Red team attacks all automatic-side-effect boundaries.

Exit gate: all high/critical findings closed or explicitly accepted with owner and deadline; safety-critical accepted risk is prohibited.

### Wave 5 — Emulator matrix

Run API 33, 35, and 36 Wear OS images where available. Execute:
- install/launch;
- allow/deny/revoke health permissions;
- notification permission paths;
- service start/stop;
- process kill/recreate;
- DB reopen/corruption fixtures;
- unsupported capability state;
- dialer intent without automatic call;
- screen rotation/recomposition and repeated prompts.

Exit gate: logs, screenshots, test reports, SHA, image/API version, and outcome are attached.

### Wave 6 — Physical-device and reliability validation

Execute only controlled and safe tests:
- real heart-rate delivery;
- screen-off passive monitoring;
- battery and thermal sampling for 8 hours;
- reboot/process recovery;
- watch removal and signal staleness;
- desk impact/watch drop/post-impact motion;
- controlled dummy or rig tests only, never dangerous human falls.

Exit gate: device matrix and data-loss/false-positive report complete.

### Wave 7 — Integration and regression

A005 integrates the selected canonical branches. A091–A095 run full regression, compare artifacts, and bisect any failure. No stacked PR is merged blindly.

Exit gate:
- one canonical release-candidate branch;
- full green CI;
- no unresolved review threads;
- reproducible artifact;
- issue traceability complete.

### Wave 8 — Independent final audit

A098 attacks the candidate without relying on prior conclusions. A097 checks product/medical claims. A100 issues go/no-go.

Release is NO-GO unless all mandatory gates are satisfied.

## 6. Mandatory evidence packet

Every task must provide:

```yaml
agent_id: A000
scope: short description
base_sha: full sha
head_sha: full sha
changed_files: []
claims: []
tests:
  - command: exact command
    environment: exact environment
    result: pass|fail|blocked
    log_or_artifact: URL or artifact id
critic_findings: []
known_limits: []
safety_impact: none|low|medium|high|critical
privacy_impact: none|low|medium|high|critical
rollback: exact rollback method
verifier_agent: A000
verifier_decision: accept|reject|needs_changes
```

## 7. Verification matrix

| Layer | Required verification |
|---|---|
| Dart | format, analyze, widget/state tests |
| Kotlin | unit tests, model-based transition tests |
| Room | schema equality, transaction failure, reopen, corruption, migration |
| APK | build, checksum, package ID, SDK levels, Wear feature |
| Permissions | required permissions present; SMS/call/location/WAKE_LOCK absent |
| Health Services | compile, capability check, allow/deny/revoke, stale signal |
| Detector | replay vectors, watch drop, post-impact movement, duplicates |
| Runtime | service lifecycle, screen-off, process kill, reboot |
| Security | manifest exposure, intent fuzzing, dependency and secret scans |
| UX | small-screen layout, accessibility, truthful claims, failure states |
| Device | real BPM, battery, thermal, data loss, supported-device matrix |

## 8. Merge policy

- Draft PR first.
- At least one critic and one verifier distinct from the author.
- All required checks green on current HEAD, not an older SHA.
- High/critical security, privacy, or safety findings block merge.
- Documentation evidence must match the exact tested commit.
- Integration uses a selected canonical persistence implementation; PR #9 and #10 are not both merged as competing databases.
- `agent/wear-emulator-validation` cannot be called validated until it contains new emulator evidence commits.

## 9. Immediate first tasks

1. A006/A007 map every branch and stacked PR, including PR #9 vs #10 divergence.
2. A014/A020 confirm all current PR HEADs have green Actions and preserved artifacts.
3. A051–A060 compare both Room implementations and recommend one canonical design.
4. A081–A084 add a hosted emulator workflow or a documented self-hosted fallback without weakening current CI.
5. A021–A030 implement and test runtime permission denial/revocation and unsupported capability evidence.
6. A091 builds the requirement-to-test traceability matrix.
7. A098 begins an independent red-team audit of automatic side-effect and intent boundaries.

## 10. Completion definition

The project is complete for limited beta only when:
- canonical code is merged into a protected release branch;
- current HEAD passes full CI;
- emulator and at least one supported physical watch pass mandatory tests;
- 8-hour battery/data-loss evidence exists;
- fall-candidate limitations and false-positive results are documented;
- privacy, retention, deletion, and consent controls exist;
- no automatic call/SMS/location behavior exists;
- independent red team and final audit return GO.
