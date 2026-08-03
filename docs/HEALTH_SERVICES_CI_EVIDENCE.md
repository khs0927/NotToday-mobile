# Health Services CI evidence

- Branch head tested: `2121ae73b61133edb6e1602fdf19fa70cfa62205`
- Pull-request merge SHA tested: `e84f16baa52a9e73f53042c0724c6654cdf14147`
- GitHub Actions run: `30799802583`
- Artifact ID: `8850555055`
- Artifact digest: `798b4b3a1d2468791a4b340d54f1def69d47ea0e48f8f0ef03f17e13d81316ca`
- Debug APK SHA-256: `b22f7783ea710ad7c4c43a85e3510dd9efa968cedf4528def70a93f711f548bf`
- Room schema SHA-256: `a383249e90029615b9b597eb67b74b190d637ad1453329d177d20548ba456add`
- Native tests: 27
- Failures: 0
- Errors: 0
- Skipped: 0

Verified gates:

- Flutter static analysis
- raw heart-rate SensorManager API exclusion
- Health Services `PassiveMonitoringClient` compilation
- passive listener service manifest binding
- API 33–35 background body-sensor permission packaging
- API 36+ heart-rate and background health-data permission packaging
- Room schema remains unchanged
- deterministic state, persistence, and signal-quality tests
- debug APK build
- SMS, direct-call, location, and WAKE_LOCK permissions remain absent

Not verified by this evidence:

- Health Services capabilities on a running Wear OS image
- runtime permission grant, denial, and revocation behavior
- real passive heart-rate delivery
- screen-off persistence
- physical-device battery use
- supported-device matrix
