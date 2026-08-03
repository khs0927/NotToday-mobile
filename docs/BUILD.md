# NotToday Wear OS 빌드 및 설치 가이드

## 1. 검증된 빌드 기준선

다음 조합을 GitHub Actions의 깨끗한 Ubuntu 환경에서 검증했습니다.

| 항목 | 고정 값 |
|---|---|
| Flutter | 3.44.8 |
| Dart | 3.12.2 |
| JDK | Temurin 17 |
| Gradle 배포판 | 9.1.0 |
| Android Gradle Plugin | 9.0.1 |
| Kotlin Gradle Plugin | 2.3.20 |
| compileSdk | 36 |
| targetSdk | 36 |
| minSdk | 30 — Wear OS 3 이상 |
| AndroidX Core KTX | 1.17.0 |
| Wear UI 플러그인 | wear_plus 1.2.5 |
| 진동 플러그인 | vibration 3.2.0 |

## 2. 최종 재현 성공 증거

2026년 8월 3일 다음 기준선에서 debug APK 생성과 패키지 권한 검증에 성공했습니다.

- 브랜치 커밋: `4ec6d465d1692fd812f5ac86b19bc6b058606544`
- PR merge-test commit: `48d2cc8b0a48f7dc06447edf1e196d786cbf3c12`
- GitHub Actions run: `30788853452`
- Artifact ID: `8846356169`
- APK 파일 크기: `159,305,023 bytes` — 약 152 MiB
- APK SHA-256: `476f232305fb29a81420ad9c9b496cd10c42c2ed4864bcb3164d5646e70e89e1`
- Artifact ZIP digest: `a98f1e0df9043f08da64f36b46f7d25ef5492cbd96ad9d6eaa3213f4123fab71`

패키지 검사 결과:

```text
applicationId  com.lifesaver.cardiac.debug
versionName    0.1.0-debug
compileSdk     36
minSdk         30
targetSdk      36
Wear feature   android.hardware.type.watch
```

최종 APK 권한:

```text
BODY_SENSORS — API 35 이하
READ_HEART_RATE — API 36 이상
FOREGROUND_SERVICE
FOREGROUND_SERVICE_HEALTH
POST_NOTIFICATIONS
VIBRATE
```

다음 권한은 패키지에 존재하지 않음을 CI와 산출물 직접 검사로 확인했습니다.

```text
SEND_SMS
CALL_PHONE
ACCESS_FINE_LOCATION
ACCESS_COARSE_LOCATION
WAKE_LOCK
```

이 APK는 모든 주요 ABI와 Flutter debug 자산을 포함한 개발용 universal APK이므로 용량이 큽니다. 배포용 산출물이 아닙니다.

GitHub Actions artifact는 보존 기간이 제한됩니다. 장기 보관보다 동일한 고정 환경에서 다시 빌드하는 것을 기준으로 합니다.

## 3. 로컬 준비

필수 설치 항목:

- Flutter 3.44.8
- JDK 17
- Android SDK Platform 36
- Android SDK Build-Tools 36.0.0
- Android Debug Bridge — `adb`
- Wear OS 3 이상 실기기 또는 에뮬레이터

환경 확인:

```bash
flutter --version
java -version
flutter doctor -v
```

Flutter 버전이 다르면 3.44.8 기준 환경을 사용합니다. FVM 또는 동등한 버전 관리 도구 사용을 권장합니다.

## 4. 깨끗한 빌드

저장소 루트에서 실행합니다.

```bash
flutter clean
flutter pub get
dart format --output=none lib
flutter analyze --no-fatal-infos --no-fatal-warnings
flutter build apk --debug --no-pub
```

생성 파일:

```text
build/app/outputs/flutter-apk/app-debug.apk
```

체크섬 생성:

```bash
sha256sum build/app/outputs/flutter-apk/app-debug.apk
```

Windows PowerShell:

```powershell
Get-FileHash build\app\outputs\flutter-apk\app-debug.apk -Algorithm SHA256
```

## 5. Wear OS 실기기 연결

워치에서 개발자 옵션과 무선 디버깅을 활성화합니다. 화면에 표시되는 페어링 주소와 디버깅 주소는 서로 다를 수 있습니다.

```bash
adb pair <WATCH_IP>:<PAIRING_PORT>
adb connect <WATCH_IP>:<DEBUG_PORT>
adb devices
```

APK 설치:

```bash
adb install -r build/app/outputs/flutter-apk/app-debug.apk
```

설치 확인:

```bash
adb shell pm list packages | grep com.lifesaver.cardiac.debug
```

Windows PowerShell:

```powershell
adb shell pm list packages | Select-String "com.lifesaver.cardiac.debug"
```

## 6. 첫 실행 확인

1. 앱을 실행합니다.
2. `모니터링 시작`을 누릅니다.
3. 심박 또는 건강 데이터 권한 요청을 확인합니다.
4. Android 13 이상에서는 알림 권한 요청도 확인합니다.
5. 권한을 거부했을 때 서비스가 거짓 성공 상태를 표시하지 않는지 확인합니다.
6. 권한을 허용했을 때 포그라운드 서비스 알림과 BPM 대기 상태를 확인합니다.

API 수준별 심박 권한:

| 기기 API | 요청 권한 |
|---|---|
| API 35 이하 | `BODY_SENSORS` |
| API 36 이상 | `READ_HEART_RATE` |

## 7. 안전 시험 주의사항

- 실제 119에 시험 전화를 걸지 않습니다.
- 앱은 119가 입력된 시스템 다이얼러만 엽니다.
- 자동 전화와 자동 SMS 기능은 포함하지 않습니다.
- 사람이 위험한 낙상을 직접 재현하지 않습니다.
- 낙상 후보 시험은 워치 더미, 안전 매트, 제어된 장치를 우선 사용합니다.
- 현재 임계값은 임상 기준이 아닌 엔지니어링 시험값입니다.

세부 시험은 [`TEST_PLAN.md`](TEST_PLAN.md)를 따릅니다.

## 8. 릴리스 제한

현재 저장소에는 release 서명 키와 release signing configuration을 포함하지 않습니다.

다음 항목이 완료되기 전에는 Play Console 또는 외부 배포용 release를 만들지 않습니다.

- 개인정보처리방침
- 지원 기기 목록
- 데이터 보존·삭제 정책
- release 키 관리 절차
- 제한 베타 승인
- 실기기 권한·센서·오탐 시험
- UI 안전 문구 최종 검토

## 9. CI가 검증하는 항목

GitHub Actions는 다음을 자동 확인합니다.

- 필수 프로젝트 파일 존재
- 자동 SMS 코드와 고정 좌표 부재
- `CALL_PHONE` 및 `ACTION_CALL` 부재
- Flutter 의존성 해석
- Dart 포맷 파싱
- Flutter 정적 분석
- Android debug APK 생성
- APK SHA-256 생성
- application ID, SDK 수준, Wear 기능 선언
- 패키지 내부 SMS·전화·위치·WAKE_LOCK 금지 권한 부재
- APK artifact 업로드

최종 실기기 동작은 CI만으로 증명할 수 없으므로 별도의 Wear OS 기기 시험 결과가 필요합니다.
