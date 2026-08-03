# Room persistence CI evidence

- Branch head before documentation seal: `e8ec828870ae347bb5a37353c3b77e724c869799`
- Pull-request merge SHA tested: `571ac56654e182aa35bbb8e881e4fc2d8841dc9e`
- GitHub Actions run: `30798014013`
- Artifact ID: `8849830577`
- Artifact digest: `ed009a6cf91ac435a732591d0b06926d77583e59ba312ebc8f066301fd89bf8c`
- Room schema SHA-256: `a383249e90029615b9b597eb67b74b190d637ad1453329d177d20548ba456add`
- Debug APK SHA-256: `8fc146f4b20a8c74c681e3d86a1dcb259ceb531bb228e506798add91befc0783`
- Native tests: 22
- Failures: 0
- Errors: 0
- Skipped: 0

Verified gates:

- Flutter static analysis
- Android API 36 debug APK build
- Room/KSP code generation
- checked-in schema equality
- deterministic state-machine tests
- coordinator tests
- persistence and fail-closed tests
- packaged application ID, SDK, Wear OS feature, and prohibited-permission checks

Not verified by this evidence:

- Android process kill/recreate instrumentation
- real Wear OS device installation
- real sensor permission and heart-rate delivery
