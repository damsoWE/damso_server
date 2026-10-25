# Quickstart: F2 문의 접수·긴급 분류 검증

**Feature**: [spec.md](./spec.md) · 설계: [data-model.md](./data-model.md) · 약속: [contracts/](./contracts/inquiry.openapi.yaml) · 기반: [001-auth-signup quickstart](../001-auth-signup/quickstart.md)

구현이 끝난 뒤 이 순서로 spec의 성공 기준(SC)을 확인한다.

## 사전 준비

- Docker 실행 중 (테스트는 PostgreSQL·Redis 컨테이너를 쓴다)
- F1 환경변수에 더해 `DAMSO_FCM_CREDENTIALS`(Firebase 서비스 계정 JSON을 Base64로)가 필요하다. 값은 `.env`에만 두고 커밋하지 않는다.
- 테스트는 FCM 대신 가짜 푸시 포트를 쓴다(보낸 알림을 기록). 실제 기기 확인은 2절에서만 한다.

## 1. 자동화 테스트 — SC-001 ~ SC-014

```bash
./gradlew test          # Windows: .\gradlew.bat test
```

시간 의존 테스트(1분 제한, 1시간 재알림, 응답 가능 시간)는 F1의 `MutableClock`으로 시간을 옮기고, 알림 발송기는 테스트에서 직접 한 번씩 돌린다.

| 확인 대상 | 기대 결과 | SC |
|---|---|---|
| 자녀를 골라 제출 → 접수 안내, 원문 암호화 저장, 수정·삭제 경로 없음 | 201 + `notices=[RECEIVED]`, DB 원본 ≠ 평문 | SC-001·004 |
| 긴급 표현이 든 문의 제출 | 응답 3초 이내, `urgent=true`, `stage=DELIVERED`, 교사 목록 맨 위 | SC-002 |
| 가짜 푸시 포트가 예외를 던지는 상태 + AI 없음 + Redis 중지에서 긴급 문의 | 접수·긴급 표시·학부모 안내 정상, 알림 요청은 대기로 남음 | SC-003 |
| 긴급 문의 제출 직후(발송기를 돌리지 않음) | 커밋 후 즉시 발송으로 가짜 푸시 포트에 긴급 알림 1건. 즉시 발송을 실패시키면 다음 발송기 실행에서 1건만 나감(중복 0건) | R7 |
| 다른 학부모·다른 학급 교사·슈퍼 관리자로 문의 조회 | 404 또는 403 100% | SC-005 |
| 전체 흐름 동안 로그 캡처 | 본문·제목 문자열 0건 | SC-006 |
| 응답 가능 시간 밖 일반 문의 3건 → 시작 시각에 발송기 실행 | 묶음 알림 1건(건수 3), 긴급 알림은 지연 0건 | SC-007 |
| 2,001자·공백 본문·이모지 2,000자 | 앞의 둘은 400, 이모지 2,000자는 201 | SC-008 |
| 학부모 안내 | 응답의 안내가 계약의 `NoticeCode`·`HotlineCode` 값뿐 | SC-009 |
| 교사의 긴급 원문 열람 2회 | 감사 기록 2건, 단계 `TEACHER_CONFIRMED`는 1회만 | SC-010 |
| 응답 가능 시간 변경 직후 문의 | 새 시간 기준으로 지연 여부 결정 | SC-011 |
| 기기 등록 후 그 세션 로그아웃 → 긴급 문의 | 그 기기로 발송 0건 | SC-012 |
| 확인 안 된 긴급 문의, 시간 +1h씩 이동하며 발송기 실행 | 매번 재알림 1건, 원문 열람 뒤 0건 | SC-013 |
| 같은 `Idempotency-Key`로 두 번 제출 / 1분 안에 일반 문의 두 번 / 1분 안에 긴급 문의 | 문의 1건 / 두 번째 429 + `Retry-After` / 긴급은 201 | SC-014 |
| 같은 `Idempotency-Key`로 자녀나 본문이 다른 제출 | 409 `CONFLICT`, 처음 문의는 그대로 | FR-002a |
| Redis 중지 상태에서 일반 문의 | 제한만 건너뛰고 201(문의권 우선, fail-open) | FR-002b |
| 긴급 표현 목록 파일 | 대표 문장 전부 긴급 판정, 목록이 비면 기동 거부 | FR-009a |
| `inquiry_stage_history`·`audit_log` 리포지토리 | 수정·삭제 메서드 0개(리플렉션 테스트) | 원칙 III |

## 2. 수동 확인 — 실제 기기 알림

F1 quickstart 2절처럼 PostgreSQL·Redis·Mailpit 컨테이너와 로컬 서버를 띄운 뒤, [contracts](./contracts/inquiry.openapi.yaml) 순서대로 호출한다.

1. F1 흐름으로 학급·학생·교사·학부모를 만든다.
2. 교사 앱(또는 FCM 테스트 토큰)으로 `PUT /api/v1/me/devices` 등록.
3. 학부모가 `POST /api/v1/parent/inquiries`로 일반 문의 → 교사 기기에 고정 문구 알림이 오는지 확인(이름·본문 없음).
4. 긴급 표현이 든 문의 → 즉시 알림, 학부모 응답에 `URGENT_DELIVERED`와 기본 번호 두 개.
5. 교사가 `GET /api/v1/teacher/inquiries/{id}/content`로 원문 열람 → 단계 `TEACHER_CONFIRMED`, 이후 재알림이 오지 않음.
6. 교사가 응답 가능 시간을 지금 밖으로 설정 → 일반 문의 제출 → 알림이 오지 않고 학부모 응답에 `DEFERRED_TO_AVAILABLE_HOURS`.
7. 교사 기기에서 로그아웃 → 긴급 문의 제출 → 그 기기로 알림이 오지 않음.

## 3. 보안 체크리스트 갱신

| 항목 | 근거가 될 것 |
|---|---|
| 보안기능 2 부적절한 인가 | 문의 건별 소유권(학부모 본인·현재 담임) 테스트 — F1에서 "F2에서 재검증"으로 남긴 부분 |
| 보안기능 5 암호화되지 않은 중요정보 | 문의 본문·제목 `SensitiveText` |
| 에러처리 2 오류상황 대응 부재 | 긴급 경로 fail-open(알림·AI·Redis 장애에도 접수), 알림 아웃박스 재시도 |
| 캡슐화 2 디버그 코드 | 본문 로그 0건 테스트 |
| 시간 및 상태 1 TOCTOU | 문의 단계 `@Version`, 알림 요청 `SKIP LOCKED`, 중복 제출 유니크 제약 |
