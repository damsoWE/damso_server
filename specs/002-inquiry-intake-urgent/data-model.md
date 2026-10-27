# Data Model: 문의 접수·긴급 분류 (F2)

**Feature**: [spec.md](./spec.md) · **Research**: [research.md](./research.md)

- 별도 표시가 없으면 엔티티는 `BaseEntity`(UUID v4 `id`, `created_at`, `updated_at`, `version`)를 상속한다.
- 시각은 UTC `Instant`. 응답 가능 시간 계산만 Asia/Seoul로 한다.
- 스키마는 F1과 같이 엔티티 매핑 + `ddl-auto=update`로 만든다. 제약은 `@Column`·`@Table(uniqueConstraints)`로 선언 가능한 것만 쓴다.
- 다른 도메인의 엔티티는 UUID로만 가리킨다(학부모·교사 = `user_account.id`, 학생·학급 = school).

---

## 관계 개요

```
inquiry ─N:1─ parent(user_account)   inquiry ─N:1─ student / classroom / teacher(접수 시점)
inquiry ─1:N─ inquiry_stage_history (append-only)
inquiry ─1:N─ notification_request   (긴급·재알림은 inquiry_id 연결, 묶음은 연결 없음)
audit_log (append-only) ── target: inquiry
user_account ─1:N─ device_registration ─N:1─ auth_session(sid)
teacher ─1:0..1─ teacher_notification_setting ─1:0..7─ teacher_availability
```

## inquiry (`inquiry` 도메인)

| 필드 | 타입 | 규칙 | 근거 |
|---|---|---|---|
| `parent_id` | UUID | 작성 학부모. 변경 불가 | FR-001 |
| `student_id` | UUID | 고른 자녀(본인이 보호자). 변경 불가 | FR-001·003 |
| `classroom_id` | UUID | 접수 시점 학생의 학급. 변경 불가 | Assumptions |
| `teacher_id` | UUID | 접수 시점 담임. 변경 불가(알림 대상은 보낼 때 현재 담임으로 다시 확인) | FR-004 |
| `content` | SensitiveText | 코드 포인트 1~2,000자(공백 제거 후 1자 이상). `updatable = false` | FR-002·006 |
| `title` | SensitiveText, NULL 허용 | F3가 채운다. 로그 금지 | FR-033·034 |
| `urgent` | boolean | 접수 시 규칙 판정. `updatable = false` | FR-009·015 |
| `crisis_type` | varchar(20) | `UNCLASSIFIED`(기본) · `SELF_HARM` · `ABUSE` · `OTHER_SAFETY`. 연결 지점만 바꾼다 | FR-018·019 |
| `stage` | varchar(20) | `RECEIVED` · `DELIVERED` · `REFINING` · `TEACHER_CONFIRMED` · `ANSWERED` | FR-021 |
| `notification_deferred` | boolean | 응답 가능 시간 밖이라 알림이 미뤄진 일반 문의. *F3 이후: 정리 확정 때 실제로 알림이 미뤄졌는지(묶음 건수 집계 대상)만 뜻하고, 학부모 안내는 새 컬럼 `deferred_notice`로 만든다 — specs/003 data-model* | FR-027·028 |
| `deferred_notified_at` | timestamptz, NULL 허용 | 묶음 알림에 포함되어 나간 시각(묶음 건수 계산용) | clarify Q3 |
| `idempotency_key` | UUID | 클라이언트가 보낸 키 | FR-002a |
| `request_hash` | char(64) | 자녀 UUID와 본문을 서버 키 HMAC-SHA256(F1 `CodeHasher`)으로 해시한 값. 같은 키가 다른 내용으로 재사용됐는지 비교용이며 원문은 저장하지 않는다 | FR-002a |
| `submitted_at` | timestamptz | 접수 시각 | |

- 유니크: `(parent_id, idempotency_key)`.
- 인덱스: `(classroom_id, urgent, submitted_at)`(교사 목록), `(parent_id, submitted_at)`(학부모 목록), `(teacher_id, notification_deferred, deferred_notified_at)`(묶음 건수).
- **화면 제목(저장 안 함)**: `urgent` → "긴급 문의" / `title` 있음 → 복원된 제목(F3) / 그 밖 → "정리 중". 정제 실패의 "원문 확인 필요"는 F3가 더한다.

**단계 전이** (허용 목록 밖은 거부, `@Version`으로 동시 전이 차단)

```
일반:  RECEIVED ──(F3)──→ REFINING ──(F3)──→ TEACHER_CONFIRMED ──(F4)──→ ANSWERED
긴급:  RECEIVED ──(접수와 같은 트랜잭션)──→ DELIVERED ──(교사 첫 원문 열람)──→ TEACHER_CONFIRMED ──(F4)──→ ANSWERED
```

F2가 실행하는 전이는 긴급의 `RECEIVED → DELIVERED`, `DELIVERED → TEACHER_CONFIRMED` 두 개뿐이다.

## inquiry_stage_history (append-only, BaseEntity 미상속)

| 필드 | 타입 | 규칙 |
|---|---|---|
| `id` | UUID | v4, 앱에서 생성 |
| `inquiry_id` | UUID | |
| `stage` | varchar(20) | 바뀐 단계 |
| `occurred_at` | timestamptz | |

- `@Immutable`, 저장·조회만 있는 리포지토리(F1 `link_history`와 같은 방식, 리플렉션 테스트). 접수 시 `RECEIVED` 한 줄은 항상 남는다.
- 학부모 타임라인(FR-023)은 이 표를 시간순으로 그대로 보여 준다.

## audit_log (`audit` 도메인, append-only, BaseEntity 미상속) — F5 뼈대

| 필드 | 타입 | 규칙 |
|---|---|---|
| `id` | UUID | v4 |
| `actor_id` | UUID | 행위자(교사) |
| `action` | varchar(40) | F2: `INQUIRY_CONTENT_VIEWED` **F5에서 변경(2026-10-28)**: `action` 값이 9개로 늘었다 — 원문 열람 3개(`INQUIRY_CONTENT_VIEW_ATTEMPTED`·`INQUIRY_CONTENT_VIEWED`·`INQUIRY_CONTENT_VIEW_FAILED`), 번역본 열람 3개(`INQUIRY_REFINEMENT_VIEW_*`), `REFINEMENT_RETRY_REQUESTED`·`ANSWER_REVIEW_REQUESTED`·`INQUIRY_CLOSED`. 이미 배포된 DB는 배포 전에 `docs/db/2026-10-27-f5-audit-action-check.sql`을 실행한다 (`specs/006-audit-log-history` research R8). |
| `target_type` | varchar(20) | F2: `INQUIRY` |
| `target_id` | UUID | |
| `occurred_at` | timestamptz | |

- 본문·이름 등 개인정보 컬럼 없음(원칙 VI). 수정·삭제 경로 없음(원칙 III). 조회 API는 두지 않는다(F5: 서버에서만 조회).

## notification_request (`notification` 도메인)

| 필드 | 타입 | 규칙 |
|---|---|---|
| `recipient_id` | UUID | 받는 교사 |
| `kind` | varchar(20) | `URGENT` · `URGENT_REMINDER` · `GENERAL` · `GENERAL_DIGEST` |
| `inquiry_id` | UUID, NULL 허용 | 긴급·재알림·일반은 문의, 묶음은 NULL |
| `status` | varchar(20) | `PENDING` · `SENDING` · `SENT` · `CANCELLED` · `SKIPPED` |
| `scheduled_at` | timestamptz | 보낼 시각(지연·재알림·재시도 반영) |
| `attempts` | int | 실패 재시도 횟수 |
| `sent_at` | timestamptz, NULL 허용 | |
| `locked_until` | timestamptz, NULL 허용 | 전송 중(`SENDING`) 임대 만료 시각. 지나면 다른 발송기가 다시 집는다 |
| `digest_count` | int, NULL 허용 | 묶음 발송 시 계산된 건수 |

- 대기 중 묶음은 교사당 하나: `active_digest_key` 유니크 컬럼(대기 중이면 `"DIGEST:{교사 UUID}"`, 끝나면 NULL — F1 `active_target_key`와 같은 방식).
- 발송 대상 확보(`claimDue`): 짧은 트랜잭션에서 `(status = PENDING ∧ scheduled_at ≤ now) ∨ (status = SENDING ∧ locked_until ≤ now)`를 `FOR UPDATE SKIP LOCKED`로 잡아 `SENDING`·`locked_until = now + 60초`로 표시하고 커밋한다. FCM 호출은 트랜잭션 밖에서 하고 결과를 다시 짧은 트랜잭션으로 반영한다.

**상태 전이**

```
PENDING ──확보──→ SENDING ──성공──→ SENT
SENDING ──실패──→ PENDING(attempts+1, scheduled_at 뒤로: 1분·5분·15분, 그 뒤로는 1시간 간격으로 계속)
SENDING ──임대 만료(서버 중단)──→ SENDING(다른 발송기가 다시 확보)
PENDING ──교사 확인·담임 변경──→ CANCELLED     (재알림)
PENDING ──묶음 0건·보낼 때 일반 알림 꺼짐──→ SKIPPED
```

- 활성 기기가 없으면 `PENDING`으로 두고 다음 주기에 다시 본다(FR-032).
- 받는 사람은 요청의 교사가 아니라 **보낼 때의 현재 담임**(긴급·재알림·일반)으로 다시 정한다. 묶음은 요청의 교사에게 간다.
- 긴급을 보낸 뒤 `URGENT_REMINDER`(+1시간)를 만들고, 재알림을 보낼 때마다 다음 +1시간을 만든다(문의가 `DELIVERED`이고 받는 사람이 현재 담임·활성일 때만).

## device_registration (`notification` 도메인)

| 필드 | 타입 | 규칙 |
|---|---|---|
| `user_id` | UUID | |
| `session_id` | UUID | 등록한 접근 토큰의 `sid`. 이 세션이 살아 있을 때만 발송 대상 |
| `token` | varchar(512) | FCM 등록 토큰. **유니크** |
| `platform` | varchar(10) | `ANDROID` · `IOS` |
| `active` | boolean | FCM이 무효 토큰이라 응답하거나 명시 해제하면 false |

- 같은 토큰을 다른 사용자가 등록하면 기존 행을 그 사용자·세션으로 옮긴다(기기를 넘겨받은 경우).
- 토큰은 로그에 남기지 않는다.

## teacher_notification_setting / teacher_availability (`notification` 도메인)

| 필드 | 타입 | 규칙 |
|---|---|---|
| `teacher_id` | UUID | **유니크** |
| `general_enabled` | boolean | 기본 true |

| 필드 | 타입 | 규칙 |
|---|---|---|
| `teacher_id` | UUID | |
| `day_of_week` | varchar(10) | `MONDAY`~`SUNDAY` |
| `start_time` / `end_time` | time | `start_time < end_time`, 한국 시간, 시작 포함·종료 미포함 |

- `(teacher_id, day_of_week)` 유니크 — 요일당 한 구간.
- 설정 행이 없으면 일반 알림 켜짐 + 항상 응답 가능(FR-029). 긴급 알림 항목은 없다(끌 수 없음).

---

## Redis 키 추가 (F1 `attempt` 도메인)

| 키 | 값 | TTL | 규칙 |
|---|---|---|---|
| `damso:attempt:INQUIRY_SUBMIT:{h}` | 최근 제출 시각 | 60초 | 학부모 UUID 기준 60초에 1건. 긴급 문의는 확인·기록하지 않는다 |

- 메일 재발송 제한과 같은 Lua 스크립트를 파라미터(최소 간격·윈도·최대 건수)로 일반화해 쓴다.
- Redis를 쓸 수 없으면 제한 확인만 건너뛰고 일반 문의도 접수한다(fail-open, WARN 로그에는 종류만).

## 에러 코드 추가

| 코드 | HTTP | 메시지(고정) | 쓰임 |
|---|---|---|---|
| `NO_HOMEROOM_TEACHER` | 409 | 담임 선생님이 아직 연결되지 않았습니다. | 담임 없는 학급 자녀로 제출 (FR-004) |
| `INQUIRY_CONTENT_UNAVAILABLE` | 409 | 아직 열람할 수 없는 문의입니다. | F2에서 일반 문의 원문 열람 시도 (R11) |

- 1분 제한: `TOO_MANY_ATTEMPTS`(429) + `Retry-After`.
- 자녀가 아닌 학생·남의 문의: `NOT_FOUND`(404).

## 시크릿·설정 추가

| 이름 | 필수 | 요건 | 쓰임 |
|---|---|---|---|
| `DAMSO_FCM_CREDENTIALS` | 필수 | Base64 → JSON, `type`이 `service_account` | FCM 발송 (R8) |
| `src/main/resources/urgent/expressions.txt` | 필수(파일) | 비어 있지 않음, 못 읽으면 기동 거부 | 긴급 판정 표현 목록 (R2) |
