# NotToday Wear OS 빌드 및 설치 가이드

최종 빌드·권한 검증 결과는 `docs/PHASE1_STATUS.md`와 GitHub Draft PR #7을 기준으로 합니다.

## 고정 빌드 환경

| 항목 | 값 |
|---|---|
| Flutter | 3.44.8 |
| Dart | 3.12.2 |
| JDK | 17 |
| Gradle | 9.1.0 |
| AGP | 9.0.1 |
| Kotlin | 2.3.20 |
| compileSdk / targetSdk | 36 / 36 |
| minSdk | 30 |

## 로컬 빌드

```bash
flutter clean
flutter pub get
dart format --output=none lib
flutter analyze --no-fatal-infos --no-fatal-warnings
flutter build apk --debug --no-pub
```

APK 위치:

```text
build/app/outputs/flutter-apk/app-debug.apk
```

## Wear OS 설치

```bash
adb pair <WATCH_IP>:<PAIRING_PORT>
adb connect <WATCH_IP>:<DEBUG_PORT>
adb install -r build/app/outputs/flutter-apk/app-debug.apk
```

## 최종 검증 APK

```text
applicationId  com.lifesaver.cardiac.debug
versionName    0.1.0-debug
compileSdk     36
minSdk         30
targetSdk      36
APK size       159,305,023 bytes
SHA-256        476f232305fb29a81420ad9c9b496cd10c42c2ed4864bcb3164d5646e70e89e1
```

최종 APK에는 SMS, 자동 전화, 위치, WAKE_LOCK 권한이 포함되지 않습니다.

실기기 시험에서는 실제 119 통화를 실행하지 말고, 다이얼러 화면이 열리되 자동 통화가 수행되지 않는지만 확인합니다.
