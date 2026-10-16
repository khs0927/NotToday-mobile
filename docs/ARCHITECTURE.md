# NotToday 목표 아키텍처

## 1. 설계 원칙

- 응급 판단은 로컬에서 결정론적으로 수행합니다.
- 네트워크, LLM, Graph RAG는 응급 경로의 필수 의존성이 될 수 없습니다.
- 센서 수집, 위험 판정, 사용자 확인, 연락 실행을 분리합니다.
- 실제 기능과 UI 문구가 항상 일치해야 합니다.
- 기기와 OS 버전별 기능 지원 여부를 런타임에 확인합니다.

## 2. 단계별 구조

### 현재 안전 기준선

```text
Flutter Wear UI
  ├─ SensorServiceBridge
  ├─ DashboardScreen
  └─ EmergencyPromptDialog
          ↕ MethodChannel / EventChannel
Android Native
  ├─ MainActivity
  ├─ NativeSensorForegroundService
  ├─ WearOSHealthSensorManager
  ├─ IMUFallCollapseDetector
  └─ EmergencyDispatchController
```

### 목표 제품 구조

```text
Wear OS Kotlin 앱
  ├─ Health Services 수집 계층
  ├─ IMU 특징 추출 계층
  ├─ Signal Quality Gate
  ├─ Escalation State Machine
  ├─ Room Event Store
  ├─ Wear UI
  └─ Phone Data Layer Client
          ↕
Flutter 모바일 앱
  ├─ 사용자·보호자 등록
  ├─ 기기 상태
  ├─ 위치·연락 설정
  ├─ 이벤트 기록
  └─ 개인정보 동의
          ↕ 선택적 네트워크
FastAPI 백엔드
  ├─ 인증
  ├─ 기기 등록
  ├─ 보호자 Push
  ├─ 전달 재시도
  └─ 감사 로그
```

## 3. 컴포넌트 책임

### Sensor Acquisition

- 심박과 움직임 원시값 수집
- 센서 지원 여부 확인
- 정확도와 신호 품질 제공
- 배터리 친화적 샘플링

장기적으로 `SensorManager` 직접 수집을 `Health Services PassiveMonitoringClient`로 교체합니다.

### Signal Quality Gate

다음 조건이 충족되지 않으면 위험 판정에 입력하지 않습니다.

- 센서가 존재함
- 권한이 허용됨
- 최근 유효 심박이 있음
- 값이 생리적으로 가능한 범위임
- 손목 미착용 가능성이 낮음
- 움직임 잡음이 지나치게 크지 않음

### Fall Candidate Engine

- 강한 충격 후보 감지
- 충격 후 안정화 구간 제외
- 후속 움직임 분산 계산
- 중력 크기 안정성 검사
- 착용 신호 보조 확인
- Cooldown 적용

이 엔진은 `낙상 확정`이 아니라 `안전 확인이 필요한 후보`만 생성합니다.

### Escalation State Machine

```text
NORMAL
  → RECHECK
  → SAFETY_CHECK
  → DIALER_READY
  → RESOLVED
```

각 전이는 이벤트, 원인, 시간, 사용자 응답과 함께 기록합니다. 임의의 UI 코드가 위험 단계를 직접 변경하지 않도록 상태 머신을 단일 진실 공급원으로 둡니다.

### Emergency Handoff

프로토타입에서는 다음만 허용합니다.

- 강한 햅틱
- 안전 확인 화면
- 사용자 취소
- 119 다이얼러 열기

다음은 검증과 별도 승인 전까지 금지합니다.

- 119 자동 SMS
- 119 자동 통화
- 검증되지 않은 위치 자동 전송
- 클라우드 응답을 기다린 뒤 신고

## 4. 데이터 경계

### 워치 로컬

- 최근 센서 품질
- 위험 후보 특징값
- 상태 전이
- 사용자 취소 여부
- 연락 화면 실행 여부

### 모바일

- 사용자 설정
- 보호자 연락처
- 데이터 공유 동의
- 기기 연결 상태
- 동기화된 이벤트 요약

### 서버

- 최소한의 계정·기기 식별자
- 보호자 Push 전달 상태
- 재시도와 감사 로그

원시 생체신호를 서버에 저장하는 기능은 별도 동의, 암호화, 보존 기간과 삭제 절차가 없으면 구현하지 않습니다.

## 5. 실패 처리

| 실패 | 안전한 동작 |
|---|---|
| 심박 센서 없음 | 모니터링 불가 표시, 위험 판정 중지 |
| 권한 거부 | 권한 안내, 서비스 시작 금지 |
| Flutter 연결 해제 | 네이티브 서비스는 기록만 유지, 자동 신고 금지 |
| 네트워크 끊김 | 로컬 안전 확인 유지 |
| 위치 실패 | 위치 없이 다이얼러 제공, 거짓 좌표 금지 |
| SMS 실패 | 성공으로 표시하지 않음 |
| 프로세스 재시작 | 이전 상태를 검증한 뒤 정상 상태에서 재개 |

## 6. 향후 모듈 경계

```text
apps/wear
apps/mobile
services/api
shared/contracts
shared/test-vectors
docs
infra
```

현재 저장소를 한 번에 대규모 재배치하지 않고, 빌드 기준선과 시험을 확보한 후 단계적으로 이동합니다.
