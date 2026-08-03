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
  ↓ cooldown 이후 새 후보
RECHECK
```

| 상태 | 의미 |
|---|---|
| NORMAL | 활성 후보가 없음 |
| RECHECK | 센서 후보를 재검증 중 |
| SAFETY_CHECK | 사용자 확인이 필요함 |
| DIALER_READY | 승인된 다이얼러 요청이 기록되었지만 실제 시스템 다이얼러 실행 전 |
| RESOLVED | 사용자 확인 또는 다이얼러 결과가 기록됨 |

## 핵심 보호 규칙

1. 모든 이벤트 시간은 단조 증가 epoch 시계로 이전 이벤트보다 같거나 뒤여야 합니다.
2. 후보 관련 전이는 동일한 `candidateId`만 허용합니다.
3. 사용자 확인과 다이얼러 요청·결과에도 후보 ID를 포함해야 합니다.
4. `NORMAL → RECHECK → SAFETY_CHECK` 순서를 건너뛴 검증 이벤트는 거부합니다.
5. 한 후보가 처리 중이면 중복 후보를 허용하지 않습니다.
6. 해결 후 기본 30초 cooldown 동안 새 후보를 거부합니다.
7. `DIALER_READY`는 실제 다이얼러가 열렸다는 뜻이 아닙니다.
8. 다이얼러 실패 시 자동 재시도하지 않고 `RESOLVED`로 기록합니다.
9. `PROMPT_TIMEOUT`과 `USER`는 이벤트 source로 구분합니다. 정책상 승인된 요청 뒤에만 시스템 다이얼러 화면을 열며, 통화 연결·SMS 전송은 하지 않습니다.
10. 프로세스 재시작 시 `DIALER_READY`를 자동 재개하지 않고 `SAFETY_CHECK`으로 낮춥니다.
11. 재시작 시 검증 중이던 `RECHECK`는 확인되지 않은 상태로 간주해 후보를 지운 `RESOLVED`로 처리합니다.
12. 공개 `Reset` 이벤트를 제공하지 않아 cooldown을 우회할 수 없습니다.

## 이벤트

- `RecheckStarted`
- `SafetyCheckRequired`
- `CandidateRejected`
- `UserConfirmedSafe`
- `DialerRequested` — `USER` 또는 `PROMPT_TIMEOUT`
- `DialerOpened`
- `DialerFailed`

## Room 영속화

Room은 현재 스냅샷과 append-only 전이 감사 로그를 별도 테이블에 저장합니다.

- `escalation_snapshot`: singleton row 1개. stage, sequence, active candidate, stage/updated timestamps, reason, cooldown, 마지막 append order를 보존합니다.
- `escalation_event`: 모든 허용·거부 전이를 append order로 기록합니다. 거부 이벤트도 `transitionAccepted=false`와 rejection reason을 남깁니다.
- 이벤트와 결과 스냅샷은 하나의 Room transaction에서 함께 기록합니다.
- `sequence`는 성공한 상태 전이에만 증가하고, `appendOrder`는 거부 이벤트를 포함한 모든 저장 이벤트마다 증가합니다.
- 데이터베이스 오류·손상·복구 실패 시 `PERSISTENCE_UNAVAILABLE`로 fail-closed 하며 새 후보 측정과 다이얼러 부수 효과를 진행하지 않습니다.
- 프로세스 재시작 시 `RECHECK`와 `DIALER_READY`는 안전한 상태로 정규화하고 `PROCESS_RESTART_NORMALIZED` synthetic event를 기록합니다. 이벤트 로그를 재생해 외부 부수 효과를 다시 실행하지 않습니다.
- 원시 IMU·심박 샘플, 지속 심박값, 위치, 전화번호, SMS 내용은 저장하지 않습니다.
- Room schema는 `android/app/schemas`로 export하며 destructive migration은 사용하지 않습니다.

## 자동 시험

현재 단위 시험은 다음을 검증합니다.

- 정상 후보 → 재검증 → 안전 확인 → 사용자 안전 확인
- 검증 이벤트가 `Started` 없이 진입하지 못함
- 프롬프트 시간 만료가 다이얼러 준비와 실제 실행을 분리함
- 재시작 시 다이얼러 준비 상태 자동 재개 금지
- 시간 역행 이벤트 거부
- candidate ID 불일치 거부
- cooldown 중 중복 후보 거부
- 다이얼러 실패 후 자동 재시도 금지
- 영속화 성공 전 상태 전이를 노출하지 않음
- 영속화 실패 시 accepted transition 롤백
- 거부 전이도 감사 로그 경계로 전달

## 통합 순서

1. 상태 머신과 단위 시험 통과
2. 낙상 검출기의 후보 시작·검증·거부 이벤트 분리
3. 네이티브 서비스가 모든 단계를 상태 머신을 통해 발행
4. Flutter는 상태 스냅샷을 표시만 하고 단계를 직접 만들지 않음
5. Room 이벤트 저장소와 재시작 복구
6. Health Services 센서 소스를 동일 인터페이스 뒤에 연결
7. 실제 Wear OS 설치·센서·화면 꺼짐·다이얼러 동작을 별도 하드웨어 게이트로 수행
