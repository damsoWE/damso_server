# Quickstart: F4 답변 작성·점검·전송 검증

**Feature**: [spec.md](./spec.md) · 설계: [data-model.md](./data-model.md) · 약속: [contracts/](./contracts/answer.openapi.yaml) · 기반: [002-inquiry-intake-urgent quickstart](../002-inquiry-intake-urgent/quickstart.md)

구현이 끝난 뒤 이 순서로 spec의 성공 기준(SC)을 확인한다.

## 사전 준비

- Docker 실행 중(PostgreSQL·Redis), F1·F2 환경변수 그대로.
- `AnswerReviewPort`는 테스트에서 두 가지 가짜 구현을 바꿔 끼운다: ① 운영과 같은 스텁(`Optional.empty()`만 반환) ② 하이라이트를 돌려주는 가짜 어댑터(FR-008a 입력 범위 검증용). 실제 LLM 호출은 없다.
- F4는 **담당 교사가 아직 TEACHER_CONFIRMED에 닿지 않은 일반(비긴급) 문의**에서는 "답변 완료" 종료까지 끝까지 검증할 수 없다(research.md R3). 종료까지 포함한 전체 흐름은 **긴급 문의**로 검증하고, 답변 작성·전송·재문의·알림만 일반 문의로 추가 검증한다.

## 1. 자동화 테스트 — SC-001 ~ SC-011

```bash
./gradlew test          # Windows: .\gradlew.bat test
```

| 확인 대상 | 기대 결과 | SC |
|---|---|---|
| 점검 서비스가 중단된 상태(스텁)에서 답변을 점검 없이 작성·전송 | 201, 전송 성공 | SC-001 |
| `POST .../answer-reviews` 호출(스텁) | 10초 이내(사실상 즉시) `available=false, highlights=[]` 응답 | SC-002 |
| 점검 결과 없이(또는 실패 후) 전송 | 전송이 막히지 않고 201 | SC-003 |
| 전송된 답변·재문의에 대한 수정·삭제 경로 리플렉션 검사 | 메서드 0개 | SC-004 |
| 다른 학급 문의에 답변 작성·전송·점검·종료 시도 | 100% 404 NOT_FOUND | SC-005 |
| 스레드 안에서 재문의가 긴급 표현을 포함 | 3초 이내 교사 목록에 긴급으로 반영(F2 로직 재사용 확인) | SC-006 |
| 답변 도착 알림을 끈 학부모에게 답변 전송 | 가짜 푸시 포트에 발송 0건, 답변은 저장됨 | SC-007 |
| "답변 완료"로 닫힌 문의에 재문의 시도 | 100% 409 THREAD_CLOSED | SC-008 |
| 답변 본문·점검 결과(하이라이트 포함)를 로그에 남기도록 유도하는 경로 전체에서 로그 캡처 | 문자열 0건 | SC-009 |
| 점검 결과를 그대로 저장하는 코드 경로 탐색(가짜 어댑터가 하이라이트를 반환해도) | DB에 점검 결과 테이블/컬럼 없음 — 응답 DTO 생성 후 버려짐 | SC-010 |
| 1분 안에 재문의 두 번(같은 학부모, 같은 스레드) / 그중 긴급 재문의 | 두 번째 일반 재문의 429 + `Retry-After` / 긴급 재문의는 201(제한 없음) | SC-011 |
| `AnswerReviewPort`에 가짜 어댑터(하이라이트 반환)를 꽂고 호출 | 요청에 `pseudonymizedContext`가 전달되는지, 가명화 전 원문 문자열이 포트 입력에 섞이지 않는지 확인 | FR-008a |

## 2. 수동 확인 — 실제 흐름(긴급 문의로 종료까지)

1. F2 흐름으로 긴급 문의를 하나 접수한다.
2. 교사가 `GET /api/v1/teacher/inquiries/{id}/content`로 원문을 열람한다 → 단계 `TEACHER_CONFIRMED`.
3. 교사가 `POST /api/v1/teacher/inquiries/{id}/answer-reviews`로 점검을 요청한다 → `available=false` 확인(스텁 단계).
4. 교사가 `POST /api/v1/teacher/inquiries/{id}/answers`로 답변을 전송한다 → 학부모 기기에 고정 문구 알림이 오는지 확인(이름·본문 없음).
5. 학부모가 `POST /api/v1/parent/inquiries/{id}/follow-ups`로 재문의한다 → 같은 스레드에 쌓이는지 확인.
6. 교사가 추가 답변을 보낸다(여러 차례 반복해도 스레드가 계속 열려 있는지 확인).
7. 교사가 `POST /api/v1/teacher/inquiries/{id}/close`로 종료한다 → 이후 학부모의 재문의 시도가 409로 거부되는지 확인.
8. 학부모가 `PUT /api/v1/parent/notification-settings`로 답변 알림을 끈 뒤, 같은 문의에 새 답변이 오면 알림이 가지 않는지 확인(앱에서는 여전히 보임).

## 3. 보안 체크리스트 갱신

| 항목 | 근거가 될 것 |
|---|---|
| 보안기능 2 부적절한 인가 | 답변 작성·점검·전송·종료의 현재 담임 검증, 재문의의 본인 학부모 검증 |
| 보안기능 5 암호화되지 않은 중요정보 | `answer.content` `SensitiveText` |
| 에러처리 2 오류상황 대응 부재 | 점검 서비스 장애에도 전송 가능(fail-open), 저장소 장애 시 1분 제한만 건너뛰고 접수 |
| 캡슐화 2 디버그 코드 | 답변 본문·점검 결과 로그 0건 테스트 |
| 시간 및 상태 1 TOCTOU | "답변 완료" 전이 `@Version`, 재문의 멱등키 유니크 제약 |
