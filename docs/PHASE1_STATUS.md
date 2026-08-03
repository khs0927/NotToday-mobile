# Phase 1 상태 보고서 — 재현 가능한 Android 빌드

## PM 판정

CI 빌드 기준선은 완료했습니다. 실기기 설치와 센서 동작 검증이 남아 있으므로 Phase 1 전체와 Draft PR은 아직 종료하지 않습니다.

## 완료된 항목

- Flutter 3.44.8과 Dart 3.12.2 고정
- JDK 17 고정
- Gradle 9.1.0, AGP 9.0.1, Kotlin 2.3.20 고정
- compileSdk·targetSdk 36, minSdk 30 고정
- Flutter Android 프로젝트 골격 복구
- 유지보수되지 않는 `wear`를 `wear_plus 1.2.5`로 교체
- Flutter v1 embedding에 의존하던 진동 플러그인을 `vibration 3.2.0`으로 교체
- AndroidX Core를 API 36 호환선인 1.17.0으로 고정
- debug APK 빌드
- SHA-256 생성
- APK application ID·SDK·Wear feature 검사
- APK 내부 SMS·전화·위치·WAKE_LOCK 금지 권한 검사
- release debug-signing 제거
- APK artifact 업로드

## 최종 증거

| 항목 | 값 |
|---|---|
| 코드 기준선 | `4ec6d465d1692fd812f5ac86b19bc6b058606544` |
| GitHub Actions run | `30788853452` |
| Artifact ID | `8846356169` |
| APK 크기 | `159,305,023 bytes` |
| APK SHA-256 | `476f232305fb29a81420ad9c9b496cd10c42c2ed4864bcb3164d5646e70e89e1` |
| Artifact ZIP digest | `a98f1e0df9043f08da64f36b46f7d25ef5492cbd96ad9d6eaa3213f4123fab71` |

## 패키지 검사

```text
applicationId  com.lifesaver.cardiac.debug
versionName    0.1.0-debug
compileSdk     36
minSdk         30
targetSdk      36
Wear feature   android.hardware.type.watch
```

최종 권한:

```text
BODY_SENSORS
READ_HEART_RATE
FOREGROUND_SERVICE
FOREGROUND_SERVICE_HEALTH
POST_NOTIFICATIONS
VIBRATE
```

금지 권한 부재:

```text
SEND_SMS
CALL_PHONE
ACCESS_FINE_LOCATION
ACCESS_COARSE_LOCATION
WAKE_LOCK
```

## 남은 병합 게이트

- Wear OS 실기기 설치 및 실행
- 심박 권한 허용·거부 경로
- 포그라운드 서비스 시작·중지
- 실제 심박 표시
- 화면 꺼짐 상태 정책
- 워치 낙하 오탐 시험
- 충격 후 움직임 후보 폐기 시험
- 다이얼러가 열리되 자동 통화하지 않음 확인
- 최종 UI 안전 문구 검토

## 다음 오케스트레이션 결정

Health Services의 장시간 데이터는 비동기·배치 특성이 있으므로 센서 구현을 바로 확대하기 전에 결정론적 위험 상태 머신의 경계를 먼저 고정합니다. 다음 개발 브랜치는 기존 Draft PR과 분리하여 상태 전이 모델과 센서 소스 인터페이스를 설계합니다.
