# Quickstart: 일반 문의 위협·폭언 표현 표시 검증

**Feature**: [spec.md](./spec.md) · 설계: [data-model.md](./data-model.md) · 약속: [contracts/](./contracts/threat-flag.openapi.yaml) · 기반: [002 quickstart](../002-inquiry-intake-urgent/quickstart.md), [003 quickstart](../003-pseudonymization-pipeline/quickstart.md)

구현이 끝난 뒤 이 순서로 spec의 성공 기준(SC)을 확인한다.

## 사전 준비

- Docker 실행 중(테스트는 PostgreSQL·Redis 컨테이너를 쓴다).
- 새 환경변수는 없다. F3까지의 환경변수 그대로다.
- `src/main/resources/threat/expressions.txt`에 표현이 있어야 한다(비면 기동 거부).
- `src/test/resources/threat/must-flag.txt`·`must-not-flag.txt`는 **팀이 정한 내용**이어야 한다(FR-002, D3). 구현자가 혼자 채운 상태로 이 절차를 통과로 보지 않는다.

## 1. 자동화 테스트 — SC-001 ~ SC-007

```bash
./gradlew test          # Windows: .\gradlew.bat test
```

| 확인 대상 | 기대 결과 | SC |
|---|---|---|
| `ThreatRuleTest`: 목록의 모든 표현, `must-flag.txt`의 모든 문장 | 모두 표시 | SC-001 |
| `ThreatRuleTest`: `must-not-flag.txt`(교육청 민원·면담 요청·교장 상담 등) | 0건 표시 | SC-001 |
| `ThreatRuleTest`: 목록의 각 표현을 긴급 규칙에 넣음 | 긴급 판정 0건(겹치는 표현 없음, R4) | FR-005a |
| `ThreatRuleTest`: 띄어쓰기·줄바꿈·전각 변형 | 원래 문장과 같은 결과 | FR-006 |
| 빈 목록·못 읽는 목록 파일 | 규칙 생성 거부, 서버 기동 거부 | Edge Cases |
| 가짜 외부 AI를 응답 없음으로 둔 채 표시할 문장으로 일반 문의 접수 → 교사 목록·상세 | `threatFlagged: true`, `refinementStatus: PENDING` 또는 `NEEDS_ORIGINAL` | SC-002, US1-3 |
| 같은 문장을 두 번 접수(서로 다른 Idempotency-Key), 가짜 외부 AI가 매번 다른 번역본을 돌려줌 | 두 문의 모두 `threatFlagged: true` | SC-003 |
| 정리 완료 → 번역본 열람 → 원문 열람 → (실패 문의) 교사 재시도 | 각 단계 뒤에도 `threatFlagged` 값 그대로 | FR-007 |
| 긴급 표현과 위협 표현이 함께 든 문의 | `urgent: true`, `threatFlagged: false` | US1-8 |
| 판정이 예외를 던지도록 바꾼 규칙으로 일반 문의 접수 | 201 접수, `threatFlagged: false`, 로그에 본문·예외 메시지 없음 | US1-9, SC-006 |
| 표시가 붙은 문의와 붙지 않은 문의의 학부모 접수 응답·목록·상세 | 키 집합 같음, `threat`가 든 키 0건 | SC-004 |
| 다른 학급 교사·다른 학부모·슈퍼 관리자의 목록·상세 조회 | 404 / 403 (F2·F3와 같음) | SC-007 |
| 위 시나리오 전체의 로그 캡처 | 본문·걸린 표현 0건 | SC-005 |
| 교사 알림 요청 | 문구가 F2·F3 고정 문구 그대로, 표시 여부와 무관 | FR-014 |

## 2. 계약 확인

```bash
npx -y @redocly/cli@1.34.5 lint specs/004-threat-expression-flag/contracts/threat-flag.openapi.yaml
```

- 오류 0건. 통합 뒤에는 `docs/api/openapi.yaml`을 같은 명령으로 검사하고, `info.version`을 R10에 따라 올린다.
- 교사 목록·상세 응답 JSON이 계약의 `required`와 `additionalProperties: false`를 지키는지 기존 API 테스트(`TeacherInquiryApiTest`)에서 확인한다.

## 3. 수동 확인 (선택)

1. 서버 실행(F2 quickstart의 실행 절차, 8080이 막혀 있으면 `SERVER_PORT=18080`).
2. 학부모 계정으로 `must-flag.txt`의 문장 하나와 `must-not-flag.txt`의 문장 하나를 각각 접수한다.
3. 담임 교사 계정으로 `GET /api/v1/teacher/inquiries`를 호출해 첫 문의만 `threatFlagged: true`인지 본다.
4. 학부모 계정으로 `GET /api/v1/parent/inquiries/{id}`를 호출해 응답에 표시 관련 값이 없는지 본다.
