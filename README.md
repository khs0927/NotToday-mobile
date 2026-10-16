# NotToday-mobile

Wear OS 안전 보조 프로토타입입니다. 최종 빌드·권한 증거와 실기기 설치 절차는 `docs/BUILD.md`, 현재 PM 판정은 `docs/PHASE1_STATUS.md`를 기준으로 합니다.

## 검증 완료

- Flutter 3.44.8 / Dart 3.12.2 / JDK 17
- Android debug APK CI 빌드
- application ID `com.lifesaver.cardiac.debug`
- minSdk 30 / targetSdk 36
- Wear OS feature 선언
- SMS·자동 전화·위치·WAKE_LOCK 권한 부재
- APK SHA-256 `476f232305fb29a81420ad9c9b496cd10c42c2ed4864bcb3164d5646e70e89e1`

## 아직 미검증

- Wear OS 실기기 설치·실행
- 심박 권한 허용·거부
- 실제 심박 표시
- 화면 꺼짐 상태
- 워치 낙하·충격 후 움직임 오탐
- 다이얼러가 열리되 자동 통화하지 않음

이 프로젝트는 의료 진단기기가 아니며 응급 구조 또는 신고 성공을 보장하지 않습니다. 실기기 안전 시험 전에는 Draft PR을 병합하거나 공개 배포하지 않습니다.

## 문서

- [빌드 및 설치](docs/BUILD.md)
- [Phase 1 상태](docs/PHASE1_STATUS.md)
- [50개 역할 검토](docs/PM_50_ROLE_REVIEW.md)
- [아키텍처](docs/ARCHITECTURE.md)
- [안전 모델](docs/SAFETY_MODEL.md)
- [로드맵](docs/ROADMAP.md)
- [시험 계획](docs/TEST_PLAN.md)

## 구성

- `lib/` — Flutter 앱 (`main.dart` + screens/services/widgets)
- `android/` — Android 네이티브
- `database/` — 로컬 SQLite (`schema.sql`, `local_sqlite_engine.py`)
- `docker-compose.yml` — 보조 인프라 (Neo4j, Redis; 비밀값은 `.env`, 포트는 127.0.0.1에만 바인딩)

## 실행

```bash
flutter pub get
flutter run
```

보조 인프라가 필요하면:

```bash
docker compose up -d   # Neo4j(:7474/:7687), Redis(:6379)
```

## 주의

- `docker-compose.yml`의 `cloud-gateway`는 `./server/` 미구현으로 비활성화됨. 구현 후 주석 해제할 것.
- Twilio 등 외부 키는 환경변수로만 전달하고 커밋하지 않는다.
