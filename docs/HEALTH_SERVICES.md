# Wear OS Health Services integration

## Decision

NotToday uses `PassiveMonitoringClient` with a `PassiveListenerService` for all-day heart-rate collection. The previous direct `SensorManager.TYPE_HEART_RATE` registration is removed.

This is a safety-assistance signal source, not an ECG, PPG, HRV, arrhythmia, cardiac-arrest, or myocardial-infarction diagnostic engine.

## Dependency

- `androidx.health:health-services-client:1.1.0-rc02`

## Permission matrix

| Device API | Foreground heart rate | Background passive monitoring |
|---|---|---|
| 30–32 | `BODY_SENSORS` | no separate background permission |
| 33–35 | `BODY_SENSORS` | `BODY_SENSORS_BACKGROUND` |
| 36+ | `READ_HEART_RATE` | `READ_HEALTH_DATA_IN_BACKGROUND` |

Primary and background permissions are requested sequentially. Monitoring does not start when the required background permission is denied; Flutter receives an explicit platform error.

## Runtime flow

1. `MainActivity` obtains the version-correct primary permission.
2. It requests background health access in a separate step where required.
3. `NativeSensorForegroundService` creates `WearOSHealthSensorManager`.
4. The manager checks passive capabilities for `HEART_RATE_BPM`.
5. It registers `HealthServicesHeartRateListenerService`.
6. Health Services binds to the listener service and delivers heart-rate data.
7. `HeartRateSignalTracker` rejects invalid ranges, invalid timestamps, and out-of-order readings.
8. The process-local telemetry hub forwards only accepted latest-sample telemetry to Flutter.
9. Permission loss clears the wearer signal immediately.

## Safety and privacy boundaries

- No raw heart-rate stream is written to Room.
- No cloud upload is introduced.
- A heart-rate value is accepted only between 25 and 240 BPM.
- A wearer signal older than 30 seconds is not used by the fall-candidate gate.
- An unsupported device reports `UNSUPPORTED` instead of silently falling back to raw sensors.
- Registration errors report `ERROR`; they do not manufacture a heart-rate value.
- Stopping monitoring clears the passive listener registration.

## Known limits before hardware validation

Passive delivery timing is device-controlled and may be batched. A fresh heart-rate sample is therefore not guaranteed at the moment of an impact. The current safety behavior is conservative: stale or absent heart-rate data rejects the candidate rather than treating the watch as worn.

The 30-second freshness threshold and supported-device list remain engineering defaults until Wear OS emulator and physical-device evidence is collected.
