# NotToday 개발 로드맵

## Phase 0 — 안전 기준선

- [x] 자동 119 SMS 제거
- [x] 고정 GPS 좌표 제거
- [x] WakeLock 코드와 패키지 권한 제거
- [x] Flutter ↔ Android 채널 연결
- [x] 충격 후 저활동 검사
- [x] 안전 확인 UI와 다이얼러 동작 일치

## Phase 1 — 재현 가능한 Android 빌드

- [x] Flutter Android scaffold 복구
- [x] Flutter 3.44.8, JDK 17, Gradle 9.1.0, AGP 9.0.1 고정
- [x] compileSdk·targetSdk 36, minSdk 30 고정
- [x] 유지보수 플러그인으로 교체
- [x] Android debug APK CI
- [x] APK 체크섬·패키지·금지 권한 검사
- [x] release debug-signing 제거
- [x] 로컬 설치 문서
- [ ] 표준 Gradle Wrapper 스크립트·JAR 포함 여부 확정
- [ ] 서로 다른 개발 환경 3곳에서 로컬 빌드
- [ ] Wear OS 실기기 설치

## Phase 2 — 결정론적 위험 상태 머신

센서 구현 확대 전에 다음 상태와 전이 규칙을 코드와 자동 시험으로 고정합니다.

```text
NORMAL → RECHECK → SAFETY_CHECK → DIALER_READY → RESOLVED
```

- [ ] typed event 모델
- [ ] 허용·거부 전이
- [ ] Cooldown·중복 억제
- [ ] 프로세스 재시작 안전 복구
- [ ] Room 이벤트 저장소
- [ ] 단위 시험

## Phase 3 — Health Services 전환

- [ ] 센서 소스 인터페이스
- [ ] MeasureClient — 짧은 전경 측정
- [ ] PassiveMonitoringClient — 장시간 수집
- [ ] API 36 건강 권한
- [ ] 지원 기능 런타임 검사
- [ ] 손목 미착용·신호 불량 상태
- [ ] 8시간 배터리·누락률 시험

## Phase 4 — 실기기 낙상 후보 검증

- [ ] 걷기·달리기·계단
- [ ] 책상 충격·급격한 팔 동작
- [ ] 워치 낙하
- [ ] 충격 후 이동
- [ ] 더미 기반 통제 시험
- [ ] 기기별 오탐·미탐 보고서

## Phase 5 — 모바일 Companion 앱

- [ ] Wear Data Layer
- [ ] 기기·권한·배터리 상태
- [ ] 검토된 이벤트 기록
- [ ] 명시적 동의 후 외부 공유

## Phase 6 — 개인정보·운영 준비

- [ ] 개인정보처리방침
- [ ] 데이터 최소 수집·보존·삭제
- [ ] 암호화·키 관리
- [ ] 지원 기기 목록
- [ ] 장애 대응과 기능 중단 절차

## Phase 7 — 제한 베타

실기기 검증과 비의료 제품 문구 승인을 거친 뒤 제한된 내부 테스트만 진행합니다. 자동 119 신고, 질환 진단, 의료적 위험 점수는 포함하지 않습니다.

## 우선순위

1. 안전 결함
2. 빌드 재현성
3. 결정론적 상태 관리
4. 센서 품질과 배터리
5. 오탐 방지
6. 개인정보
7. 사용자 경험
8. 보호자 기능
9. 클라우드·AI 보조 기능
