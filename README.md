# NotToday-mobile

Wear OS에서 심박수와 충격 후 저활동 상태를 관찰하고, 사용자에게 안전 확인과 119 전화 화면 연결을 제공하는 **안전 보조 프로토타입**입니다.

> 이 프로젝트는 질환을 진단하거나 심정지·심근경색을 판정하는 의료기기가 아닙니다. 응급 구조나 신고 성공을 보장하지 않습니다.

## 현재 단계

`0.1.0 — Buildable MVP Safety Baseline`

현재 구현된 범위:

- Flutter Wear OS 대시보드
- Flutter ↔ Android MethodChannel/EventChannel 연결
- Android 포그라운드 심박·가속도 모니터링 서비스
- 강한 충격 이후 저활동 후보 검사
- 최근 유효 심박 신호를 이용한 착용 가능성 보조 확인
- 사용자 안전 확인 대화상자
- 119 번호가 입력된 시스템 다이얼러 열기
- 자동 SMS, 자동 전화, 고정 GPS 좌표 제거
- Flutter 3.44.8 기반 Android Gradle 빌드 기준선
- GitHub Actions debug APK·체크섬·패키지 권한 검사

아직 구현되지 않은 범위:

- 검증된 의료 판정
- ECG·PPG 원시 파형과 HRV 계산
- 실제 위치 공유
- 보호자 앱과 보호자 알림
- Health Services 기반 저전력 수집
- 실기기 회귀 테스트와 제품 배포

## 빌드 상태

2026년 8월 3일 깨끗한 GitHub Actions 환경에서 다음 항목을 통과했습니다.

- `flutter pub get`
- `flutter analyze`
- `flutter build apk --debug`
- SHA-256 생성
- APK 패키지 정보와 권한 검사
- APK artifact 업로드

최종 검증 APK:

```text
SHA-256
476f232305fb29a81420ad9c9b496cd10c42c2ed4864bcb3164d5646e70e89e1

크기
159,305,023 bytes — 약 152 MiB
```

검증된 패키지:

```text
applicationId  com.lifesaver.cardiac.debug
minSdk         30
targetSdk      36
Wear feature   android.hardware.type.watch
```

SMS, 자동 전화, 위치, WAKE_LOCK 권한은 최종 APK에 포함되지 않습니다.

이 APK는 개발용 universal debug APK이며 배포용이 아닙니다. 자세한 환경과 설치 절차는 [`docs/BUILD.md`](docs/BUILD.md)를 따릅니다.

## 안전 원칙

1. 응급 판단 경로에 LLM이나 Graph RAG를 사용하지 않습니다.
2. 단일 센서 임계값만으로 응급 신고를 실행하지 않습니다.
3. 프로토타입 단계에서는 자동 SMS와 자동 전화 연결을 금지합니다.
4. 앱이 실제로 수행하지 않는 ECG·HRV·신고 진행 문구를 표시하지 않습니다.
5. 임계값은 임상 기준이 아니라 시험용 엔지니어링 기본값임을 명시합니다.
6. 실기기 안전 시험 전에는 Draft PR을 병합하거나 공개 배포하지 않습니다.

## 현재 흐름

```text
심박 센서 + 가속도 센서
        ↓
4G 이상 충격 후보
        ↓
10초 동안 후속 움직임 분산 검사
        ↓
최근 유효 심박 + 낮은 움직임
        ↓
Safety Check 대화상자
        ↓
사용자 취소 또는 119 다이얼러 열기
```

## 저장소 구조

```text
lib/                                  Flutter Wear OS UI와 상태 브리지
android/                              Android·Gradle 기준선
android/app/src/main/kotlin/...       센서·안전 처리
android/app/src/main/AndroidManifest.xml
                                       Android 권한과 서비스 선언
database/                             초기 데이터 모델 실험 코드
docs/                                 빌드·아키텍처·안전·로드맵·시험 문서
.github/workflows/                    정적 검사와 debug APK CI
```

`database/local_sqlite_engine.py`는 현재 앱 런타임에 연결되지 않은 초기 실험 코드입니다. Wear OS 로컬 저장소는 이후 Room으로 다시 구현합니다.

## 고정 개발 환경

- Flutter 3.44.8
- Dart 3.12.2
- JDK 17
- Gradle 9.1.0
- Android Gradle Plugin 9.0.1
- Kotlin 2.3.20
- compileSdk 36
- targetSdk 36
- minSdk 30 — Wear OS 3 이상

기본 빌드:

```bash
flutter clean
flutter pub get
flutter analyze --no-fatal-infos --no-fatal-warnings
flutter build apk --debug --no-pub
```

## 1차 완료 기준

- [x] `flutter pub get` 성공
- [x] `flutter analyze` 성공
- [x] Android debug APK CI 빌드 성공
- [x] APK 체크섬과 artifact 생성
- [x] APK 패키지와 금지 권한 검사
- [ ] Wear OS 실기기에서 앱 실행
- [ ] Wear OS 실기기에서 심박 표시
- [ ] 권한 허용·거부 경로 확인
- [ ] 화면이 꺼진 상태에서 모니터링 정책 확인
- [ ] 정상 손목 동작에서 오탐 없이 30분 유지
- [ ] 충격 후 움직임이 있으면 후보 폐기
- [ ] 충격 후 저활동 시 안전 확인 화면 표시
- [ ] 119 다이얼러가 열리지만 자동 통화되지 않음 확인

## 문서

- [빌드 및 실기기 설치](docs/BUILD.md)
- [50개 역할 가상 검토 결과](docs/PM_50_ROLE_REVIEW.md)
- [목표 아키텍처](docs/ARCHITECTURE.md)
- [안전 모델](docs/SAFETY_MODEL.md)
- [개발 로드맵](docs/ROADMAP.md)
- [시험 계획](docs/TEST_PLAN.md)

## 라이선스와 배포

release signing configuration과 release 키는 저장소에 포함하지 않습니다.

라이선스, 개인정보처리방침, 지원 기기 목록, 데이터 보존 정책, 실기기 시험이 확정되기 전에는 공개 배포하거나 의료·응급 기능을 홍보하지 않습니다.
