# Data Model: 가명화·정제 파이프라인 (F3)

**Feature**: [spec.md](./spec.md) · **Research**: [research.md](./research.md)

- 규칙은 F2와 같다: `BaseEntity`(UUID v4 `id`, `created_at`, `updated_at`, `version`) 상속, 시각은 UTC `Instant`, 엔티티 매핑 + `ddl-auto=update`, 다른 도메인은 UUID로만 가리킨다.
- 테이블 **3개 추가**, `inquiry`에 컬럼 **2개 추가** [팀 결정 2026-10-25: D3].
- `gateway` 도메인은 저장소가 없다. 가명화 대응표는 어떤 테이블에도 없다(원칙 II).

---

## 관계 개요

```
inquiry ─1:0..1─ refinement_job(kind=REFINE)           일반 문의만
inquiry ─1:0..1─ refinement_job(kind=CLASSIFY_CRISIS)  긴급 문의만
inquiry ─1:0..1─ inquiry_refinement (append-only, 성공한 결과 하나)
refinement_job ─1:N─ refinement_attempt (append-only, 시도마다 한 줄)
inquiry ─1:N─ inquiry_stage_history (F2, REFINING·TEACHER_CONFIRMED 줄이 더해짐)
audit_log (F2, 일반 문의 원문 열람도 같은 3단계 기록)
```

## inquiry (변경)

| 필드 | 타입 | 규칙 | 근거 |
|---|---|---|---|
| `refinement_status` | varchar(20), NULL 허용 | **추가**. 일반: `PENDING`(정리 전) · `REFINED`(번역본 있음) · `NEEDS_ORIGINAL`(원문 확인 필요). 긴급은 NULL | FR-015·016, R9 |
| `deferred_notice` | boolean | **추가**. 접수 때 학부모에게 "응답 가능 시간에 전달됩니다"를 보였는지. 학부모 응답(첫 응답·재전송·상세)은 이 값으로 만든다. 변경 불가 | R10, F2 FR-002a |
| `notification_deferred` | boolean | **의미 변경**: 정리 확정 때 실제로 알림이 미뤄졌는지(묶음 건수 집계 대상). 접수 때는 항상 false | R10 |
| `title` | SensitiveText, NULL 허용 | F2가 비워 둔 칸. 정리 성공 때 복원된 제목을 채운다. 한 번 채우면 바꾸지 않는다 | FR-012 |
| `stage` | varchar(20) | 전이표 변경(아래) | R9 |

**화면 제목(저장 안 함)**: 긴급 → "긴급 문의" / `NEEDS_ORIGINAL`이고 `title` 없음 → "원문 확인 필요" / `title` 있음 → 제목 / 그 밖 → "정리 중".

**단계 전이** (허용 목록 밖은 거부, `@Version`으로 동시 전이 차단)

```
일반:  RECEIVED ──정리 성공──→ REFINING ──번역본 첫 열람──→ TEACHER_CONFIRMED ──(F4)──→ ANSWERED
       RECEIVED ──원문 첫 열람(refinement_status = NEEDS_ORIGINAL일 때만)──→ TEACHER_CONFIRMED
긴급:  RECEIVED ──→ DELIVERED ──원문 첫 열람──→ TEACHER_CONFIRMED ──(F4)──→ ANSWERED   (F2, 변경 없음)
```

- 교사 재시도 성공이 `TEACHER_CONFIRMED` 이후면 `REFINING`은 남기지 않는다(단계는 앞으로만).

**정리 상태 전이** (`refinement_status`)

```
PENDING ──정리 성공──→ REFINED
PENDING ──자동 2회 실패──→ NEEDS_ORIGINAL ──교사 재시도 성공──→ REFINED
                          NEEDS_ORIGINAL ──교사 재시도 실패──→ NEEDS_ORIGINAL (재시도 불가)
```

## refinement_job (`inquiry` 도메인)

정리·분류 작업의 처리 상태(아웃박스). 내용은 담지 않는다.

| 필드 | 타입 | 규칙 |
|---|---|---|
| `inquiry_id` | UUID | |
| `kind` | varchar(20) | `REFINE`(일반) · `CLASSIFY_CRISIS`(긴급) |
| `status` | varchar(20) | `PENDING` · `RUNNING` · `DONE` · `FAILED` |
| `scheduled_at` | timestamptz | 실행할 시각(접수·교사 재시도 시각) |
| `locked_until` | timestamptz, NULL 허용 | `RUNNING` 임대 만료. 지나면 다른 실행기가 다시 집는다 |
| `attempt_count` | int | 지금까지의 시도 수(자동·교사 합계) |
| `teacher_retry_used` | boolean | 교사 재시도를 썼는지. `REFINE`만 |
| `claim_count` | int, 기본 0 | 확보된 횟수. 5를 넘으면 외부 AI 없이 실패로 끝낸다(자체 코드 리뷰) |

- 유니크: `(inquiry_id, kind)`.
- 인덱스: `(status, scheduled_at)`(실행 대상 확보).
- 확보(`claimDue`): 짧은 트랜잭션에서 `(status = PENDING ∧ scheduled_at ≤ now) ∨ (status = RUNNING ∧ locked_until ≤ now)`를 `FOR UPDATE SKIP LOCKED`로 잡아 `RUNNING`·`locked_until = now + 60초`로 바꾸고 커밋한다. 외부 AI 호출은 트랜잭션 밖이다.

**상태 전이**

```
PENDING ──확보──→ RUNNING ──성공──→ DONE
RUNNING ──실패(REFINE: 자동 재시도까지 / CLASSIFY_CRISIS: 1회)──→ FAILED
RUNNING ──임대 만료(서버 중단)──→ RUNNING(다시 확보)
FAILED ──교사 재시도(REFINE, teacher_retry_used = false일 때만)──→ PENDING   teacher_retry_used = true
```

- 동시 교사 재시도는 `@Version`으로 하나만 성공한다.
- 한 작업의 시도는 `REFINE` 최대 3회(자동 2 + 교사 1), `CLASSIFY_CRISIS` 1회.

## inquiry_refinement (`inquiry` 도메인, append-only, BaseEntity 미상속)

검증을 통과한 정리 결과. 성공했을 때 한 번만 생긴다.

| 필드 | 타입 | 규칙 |
|---|---|---|
| `id` | UUID | v4 |
| `inquiry_id` | UUID | **유니크** — 문의당 결과 하나 |
| `facts` | SensitiveText | 사실관계. 복원된 값, 1~1,000자 |
| `requests` | SensitiveText | 요청사항. 복원된 값, 0~1,000자 |
| `emotion_level` | varchar(10) | `LOW` · `MEDIUM` · `HIGH` |
| `emotion_labels` | varchar(80) | 고정 목록 코드 0~2개, 쉼표 구분. `ANXIETY`(불안) · `DISSATISFACTION`(불만) · `ANGER`(분노) · `WORRY`(걱정) · `FRUSTRATION`(답답함) · `SADNESS`(슬픔) |
| `refined_at` | timestamptz | |

- `@Immutable`, 저장·조회만 있는 리포지토리(F2 `inquiry_stage_history`와 같은 방식, 리플렉션 테스트).
- 제목은 `inquiry.title`에 둔다(목록 표시용, F2 칸).
- 토큰은 저장 전에 처리 중 메모리에서 복원한다. 대응표는 저장하지 않는다(research R6).

## refinement_attempt (`inquiry` 도메인, append-only, BaseEntity 미상속)

| 필드 | 타입 | 규칙 |
|---|---|---|
| `id` | UUID | v4 |
| `job_id` | UUID | |
| `trigger` | varchar(20) | `AUTO` · `AUTO_RETRY` · `TEACHER` |
| `outcome` | varchar(20) | `SUCCESS` · `INVALID_OUTPUT` · `NO_RESPONSE` · `MASKING_FAILED` |
| `occurred_at` | timestamptz | |

- 내용·프롬프트·응답은 없다. 코드만.
- `@Immutable`, 저장·조회만.

## inquiry_stage_history / audit_log (F2, 변경 없음)

- 일반 문의에도 `REFINING`·`TEACHER_CONFIRMED` 줄이 쌓인다.
- 일반 문의의 원문 열람은 F2와 같은 `INQUIRY_CONTENT_VIEW_ATTEMPTED` → `INQUIRY_CONTENT_VIEWED` / `INQUIRY_CONTENT_VIEW_FAILED`로 기록한다. 번역본 열람은 감사 기록 대상이 아니다. **F5에서 변경(2026-10-28)**: 번역본 열람도 시도·완료·실패를 감사 기록에 남긴다(`INQUIRY_REFINEMENT_VIEW_*`). 기록이 실패해도 번역본은 반환하고 서버 로그(문의 ID·행위 종류·오류 종류)만 남긴다. 원문 열람 규칙은 그대로다 (`specs/006-audit-log-history` FR-003·005, research R3).

---

## 메모리 전용 값 객체 (`gateway` 도메인, 저장 안 함)

| 이름 | 내용 | 규칙 |
|---|---|---|
| `Roster` | 학생·보호자·교사 이름, 학년·반, 학교 용어 | 정리 한 번 동안만 존재. `toString`은 건수만 |
| `PseudonymMap` | 토큰 ↔ 원래 값 | 복원 직후 버린다. `toString`은 토큰 종류별 건수만. 직렬화 금지 |
| `MaskedText` | 가린 본문 | `toString`은 길이만 |
| `RefineOutcome` | 성공(복원된 제목·사실·요청·정서) 또는 실패 코드 | 실패 코드에는 내용이 없다 |

## 에러 코드 추가

| 코드 | HTTP | 메시지(고정) | 쓰임 |
|---|---|---|---|
| `REFINEMENT_NOT_READY` | 409 | 아직 정리된 내용이 없습니다. | 번역본이 없는 문의의 번역본 열람 |
| `REFINEMENT_RETRY_UNAVAILABLE` | 409 | 다시 정리할 수 없는 문의입니다. | 원문 확인 필요가 아니거나 재시도를 이미 쓴 문의, 긴급 문의 |

- `INQUIRY_CONTENT_UNAVAILABLE`(F2)은 더 이상 응답하지 않는다(일반 문의 원문 열람이 열림). enum 값은 남긴다.

## 시크릿·설정·리소스 추가

| 이름 | 필수 | 요건 | 쓰임 |
|---|---|---|---|
| `DAMSO_OPENAI_API_KEY` | 필수(기존) | 비어 있지 않음 | 외부 AI 호출 |
| `DAMSO_OPENAI_MODEL` | 필수(**추가**) [팀 결정 2026-10-25: D2] | 비어 있지 않음 | 모델 이름. 코드에 박지 않는다 |
| `src/main/resources/pseudonymization/school-terms.txt` | 필수(파일) | 비어 있지 않음, 못 읽으면 기동 거부 | 학교 이름·줄임말 (R2) |
| `src/main/resources/gateway/prompts/refine.txt`, `classify-crisis.txt` | 필수(파일) | 비어 있지 않음 | 고정 시스템 지시 (R5) |
| `damso.refinement.dispatch-interval` | 설정 | 기본 10s | 안전망 실행 주기 |
| `damso.openai.timeout.connect` / `.read` | 설정 | 기본 3s / 15s | 처리 시간 상한(FR-013a) |
