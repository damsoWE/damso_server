# Data Model: 답변 작성·점검·전송 (F4)

**Feature**: [spec.md](./spec.md) · **Research**: [research.md](./research.md)

- 별도 표시가 없으면 엔티티는 `BaseEntity`(UUID v4 `id`, `created_at`, `updated_at`, `version`)를 상속한다.
- 시각은 UTC `Instant`.
- 암호화가 필요한 본문은 F1·F2와 같은 `SensitiveText`(AES-256-GCM)를 쓴다.
- 다른 도메인의 엔티티는 UUID로만 가리킨다.

---

## 관계 개요

```
inquiry(루트) ─1:N─ inquiry(재문의, thread_root_id = 루트.id)
inquiry(루트 또는 재문의) ─1:N─ answer (thread_root_id = 스레드 루트.id)
inquiry(루트) ─1:N─ inquiry_stage_history (F2 재사용, 재문의도 각자 자기 이력을 가진다)
parent(user_account) ─1:0..1─ parent_notification_setting
answer 전송 ─1:1─ notification_request(kind=ANSWER, F2 아웃박스 재사용)
```

## inquiry (`inquiry` 도메인, 기존 테이블 수정)

F2가 만든 테이블에 컬럼 1개만 추가한다. 기존 컬럼·제약·인덱스는 그대로다.

| 필드 | 타입 | 규칙 | 근거 |
|---|---|---|---|
| `thread_root_id` | UUID, NULL 허용 | 최초 문의는 NULL(자기 자신이 루트). 재문의는 스레드 최초 문의의 `id`. `updatable = false` | FR-014~016, research R1 |

- 인덱스 추가: `(thread_root_id)` — 스레드 타임라인 조회용.
- 재문의로 저장되는 `Inquiry` row는 스레드 루트의 `studentId`로 `InquiryTargetQuery.resolveChild`를 다시 호출해 현재 `classroomId`·담임을 구한다(F2와 같은 규칙, research R5). 담임이 없으면(해제·공석) `NO_HOMEROOM_TEACHER`로 거부한다.
- 스레드가 열려 있는지는 **루트** row의 `stage != ANSWERED`로 판단한다. 재문의 row 자신은 `TEACHER_CONFIRMED`까지만 가고 개별적으로 `ANSWERED`가 되지 않는다(research R2).
- 재문의 제출 시 루트가 `ANSWERED`면 `THREAD_CLOSED`(409)로 거부한다(FR-021).

**스레드 안의 단계 전이** (기존 `InquiryStage` enum·전이표는 바꾸지 않는다)

```
루트 또는 재문의 각각:  RECEIVED → (DELIVERED | REFINING) → TEACHER_CONFIRMED
루트만:                 TEACHER_CONFIRMED → ANSWERED   ← F4가 "답변 완료" 종료로 실행 (F2가 이미 예정해 둔 전이)
```

## answer (신규, `inquiry` 도메인)

| 필드 | 타입 | 규칙 | 근거 |
|---|---|---|---|
| `thread_root_id` | UUID | 이 답변이 속한 스레드(루트 `inquiry.id`). `updatable = false` | FR-001·014 |
| `teacher_id` | UUID | 작성한 교사(전송 시점의 현재 담임). `updatable = false` | FR-005 |
| `content` | SensitiveText | 자유 서술, 공백 제거 후 1자 이상. `updatable = false`, 수정·삭제 API 없음 | FR-001·003 |
| `reviewed` | boolean | 전송 전 점검을 요청했는지(결과 반영 여부는 구분하지 않는다) | FR-002, Key Entities |
| `sent_at` | timestamptz | 전송 시각 | FR-001 |

- 인덱스: `(thread_root_id, sent_at)` — 스레드 타임라인 조회용.
- 수정·삭제 API를 두지 않는다(원칙 III, FR-003).
- `toString`은 `content`를 가린다(F2의 `InquiryContentView`와 같은 패턴, 원칙 VI).
- 전송 시 소유권 검증: `thread_root_id`가 가리키는 루트 `inquiry`의 `classroomId`에 대해 `isCurrentHomeroomTeacher(teacherId, classroomId)`가 참이어야 한다(FR-005). 아니면 `NOT_FOUND`.
- 전송 시 스레드(루트)가 이미 `ANSWERED`면 `THREAD_CLOSED`(409)로 거부한다. 재문의와 같은 규칙이다 — 종료는 교사 자신의 동작이므로, 종료 후 답변을 더 보내려면 먼저 그런 경로가 없다는 것을 교사도 알아야 한다.
- `reviewed`는 **교사가 점검(`POST .../answer-reviews`)을 요청했는지**만 기록한다. "점검이 실제로 오해 소지·근거 누락을 걸러냈는지"를 보증하지 않는다 — F3 전까지 점검은 항상 `available=false`(스텁)라서, `reviewed=true`인 답변도 실질적인 점검 효과는 없다(FR-012, 아래 "임시 상태" 참고).

## answer_review_result (영구 저장하지 않음 — 값 객체)

| 필드 | 타입 | 규칙 |
|---|---|---|
| `available` | boolean | 점검이 결과를 냈는지. 스텁 단계에서는 항상 `false` |
| `highlights` | List\<Highlight\> | `available=false`면 빈 목록 |

`Highlight(excerpt, reason, suggestion)` — 모두 문자열, 응답에만 존재.

- 서버에 저장하지 않고 요청-응답 동안만 존재한다(FR-013).
- `AnswerReviewPort.review(draftContent, pseudonymizedContext)`가 `Optional.empty()`를 반환하면 `available=false`로 변환한다. 예외를 던지지 않는다(fail-open, FR-012).
- **임시 상태(F3 연동 전)**: `AnswerReviewPort`의 구현은 지금 항상 `Optional.empty()`를 반환하는 `UnavailableAnswerReviewAdapter`뿐이다. 즉 F3가 실제 가명화 게이트웨이로 이 어댑터를 교체하기 전까지는 **모든 답변이 실질적인 점검 없이 전송된다** — 교사가 점검을 요청해도 `available=false`만 돌아오고, 전송 자체는 FR-012(fail-open)에 의해 항상 허용된다. 이는 버그가 아니라 사용자 확인(2026-10-25)에 따른 의도된 범위 제한이다.

## parent_notification_setting (신규, `notification` 도메인)

| 필드 | 타입 | 규칙 | 근거 |
|---|---|---|---|
| `parent_id` | UUID | 유니크 | FR-024 |
| `answer_notify_enabled` | boolean | 기본값 `true` | FR-024, 클래리파이 Q3 |

- row가 없으면(학부모가 한 번도 설정을 바꾸지 않음) 기본값 `true`로 간주한다 — 코드에서 `findByParentId(...).map(...).orElse(true)` 패턴(F2의 "응답 가능 시간 미설정 교사는 항상 가능" 패턴과 동일).
- `NotificationKind`에 `ANSWER` 값을 추가하고, `NotifyAnswerUseCase.requestAnswerNotification(answerId, parentId)`가 이 설정을 확인해 끔이면 알림 요청을 만들지 않는다(F2의 `NotifyInquiryUseCase.requestGeneral`과 같은 구조).
- 알림 내용은 고정 문구만(본문·교사 이름 없음, FR-023).

## 변경 없음(재사용)

- `inquiry_stage_history` — 재문의도 같은 테이블에 자기 `inquiry_id`로 이력을 남긴다. 스키마 변경 없음.
- `notification_request` / `device_registration` — F2 아웃박스·FCM 발송기·기기 등록을 그대로 쓴다. `NotificationKind.ANSWER` 추가만 필요.
- `audit_log` — 이 기능은 새 감사 대상을 추가하지 않는다(답변·재문의는 원칙 III의 "수정·삭제 불가"로 충분하고, 열람 감사는 F2·F5 범위).

## 에러 코드 추가

| 코드 | 상태 | 의미 |
|---|---|---|
| `THREAD_CLOSED` | 409 | 이미 "답변 완료"로 종료된 스레드에 재문의·답변을 추가하려는 시도(FR-021) |
| `INQUIRY_NOT_READY_TO_CLOSE` | 409 | 아직 `TEACHER_CONFIRMED`에 닿지 않은 문의를 "답변 완료"로 종료하려는 시도(FR-020, 일반 문의는 F3 전까지 해당) |
