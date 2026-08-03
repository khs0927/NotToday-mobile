# 결정론적 위험 상태 머신

## 목적

센서, Flutter UI, 다이얼러가 각각 임의로 위험 단계를 만들지 못하도록 단일 상태 전이 규칙을 정의합니다.

상태 머신 자체는 다음 동작을 절대 수행하지 않습니다.

- Android API 호출
- 다이얼러 실행
- 전화 연결
- SMS 전송
- 위치 조회·전송
- 재시도 예약

외부 코디네이터는 허용된 전이를 받은 뒤 명시적인 부수 효과를 수행하고, 성공 또는 실패 결과를 다시 이벤트로 보고해야 합니다.

## 상태

```text
NORMAL
  ↓ RecheckStarted
RECHECK
  ↓ SafetyCheckRequired
SAFETY_CHECK
  ↓ DialerRequested
DIALER_READY
  ↓ DialerOpened / DialerFailed
RESOLVED
  ↓ Reset 또는 cooldown 이후 새 후보
NORMAL / RECHECK / SAFETY_CHECK
```

| 상태 | 의미 |
|---|---|
| NORMAL | 활성 후보가 없음 |
| RECHECK | 센서 후보를 재검증 중 |
| SAFETY_CHECK | 사용자 확인이 필요함 |
| DIALER_READY | 사용자가 다이얼러 실행을 요청했거나 프롬프트 시간이 만료됨 |
| RESOLVED | 사용자가 안전을 확인했거나 다이얼러 결과가 기록됨 |

## 핵심 보호 규칙

1. 모든 이벤트 시간은 이전 이벤트보다 같거나 뒤여야 합니다.
2. 후보 관련 전이는 동일한 `candidateId`만 허용합니다.
3. 한 후보가 처리 중이면 중복 후보를 허용하지 않습니다.
4. 해결 후 기본 30초 cooldown 동안 새 후보를 거부합니다.
5. `DIALER_READY`는 실제 다이얼러가 열렸다는 뜻이 아닙니다.
6. 다이얼러 실패 시 자동 재시도하지 않고 `RESOLVED`로 기록합니다.
7. 프로세스 재시작 시 `DIALER_READY`를 자동 재개하지 않고 `SAFETY_CHECK`로 낮춥니다.
8. 재시작 시 검증 중이던 `RECHECK`는 확인되지 않은 상태로 간주해 `RESOLVED` 처리합니다.

## 이벤트

- `RecheckStarted`
- `SafetyCheckRequired`
- `CandidateRejected`
- `UserConfirmedSafe`
- `DialerRequested` — 사용자 또는 프롬프트 시간 만료
- `DialerOpened`
- `DialerFailed`
- `Reset`

## 저장 스냅샷

각 전이는 다음 정보를 보존합니다.

- 현재 상태
- 단조 증가 sequence
- 활성 candidate ID
- 상태 진입 시간
- 최종 갱신 시간
- 전이 사유
- cooldown 종료 시간

Room 저장소를 추가할 때 이 스냅샷과 이벤트 감사 로그를 분리하여 보관합니다.

## 자동 시험

현재 단위 시험은 다음을 검증합니다.

- 정상 후보 → 재검증 → 안전 확인 → 사용자 안전 확인
- 프롬프트 시간 만료가 다이얼러 준비만 만들고 자동 실행하지 않음
- 재시작 시 다이얼러 준비 상태 자동 재개 금지
- 시간 역행 이벤트 거부
- candidate ID 불일치 거부
- cooldown 중 중복 후보 거부
- 다이얼러 실패 후 자동 재시도 금지

## 통합 순서

1. 상태 머신과 단위 시험 통과
2. 낙상 검출기의 후보 시작·검증·거부 이벤트 분리
3. 네이티브 서비스가 모든 단계를 상태 머신을 통해 발행
4. Flutter는 상태 스냅샷을 표시만 하고 단계를 직접 만들지 않음
5. Room 이벤트 저장소와 재시작 복구
6. Health Services 센서 소스를 동일 인터페이스 뒤에 연결
