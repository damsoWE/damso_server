# Data Model: 감사 로그·이력 보존 (F5)

**Feature**: `specs/006-audit-log-history` · **Date**: 2026-10-27

**새 테이블·새 컬럼 없음.** 바뀌는 것은 `audit_log.action`에 들어갈 수 있는 값 목록뿐이다(research R1). 기존 DB의 CHECK 제약 여부는 research R8에서 확인한다.

## audit_log (`audit` 도메인, F2 뼈대) — 값 추가만

| 컬럼 | 형식 | 변경 |
|---|---|---|
| id | UUID | 없음 |
| actor_id | UUID, NOT NULL, FK 없음 | 없음 (사람 행위만 남으므로 "시스템" 행위자 불필요) |
| action | varchar(40) | **값 추가** (아래) |
| target_type | varchar(20) | 없음 — 모두 `INQUIRY` |
| target_id | UUID | 없음 — 행위 대상 메시지(루트 또는 재문의) id |
| occurred_at | timestamptz | 없음 |

### AuditAction 값

| 값 | 상태 | 남기는 곳 | target_id |
|---|---|---|---|
| `INQUIRY_CONTENT_VIEW_ATTEMPTED` | 기존(F2) | `ViewInquiryContentService` | 열람한 메시지 |
| `INQUIRY_CONTENT_VIEWED` | 기존(F2) | 〃 | 〃 |
| `INQUIRY_CONTENT_VIEW_FAILED` | 기존(F2) | 〃 | 〃 |
| `INQUIRY_REFINEMENT_VIEW_ATTEMPTED` | **신규** | `ViewRefinementService` (F3 변경) | 열람한 메시지 |
| `INQUIRY_REFINEMENT_VIEWED` | **신규** | 〃 | 〃 |
| `INQUIRY_REFINEMENT_VIEW_FAILED` | **신규** | 〃 | 〃 |
| `REFINEMENT_RETRY_REQUESTED` | **신규** | `RetryRefinementService` (F3 변경), 재시도가 받아들여진 경우만 | 재시도한 메시지 |
| `ANSWER_REVIEW_REQUESTED` | **신규** | `RequestAnswerReviewService` (F4 변경), 점검 포트 호출 전 | 스레드 루트 |
| `INQUIRY_CLOSED` | **신규** | `CloseInquiryService` (F4 변경), 종료 성공 시 | 스레드 루트 |

규칙(변경 없음): append-only, `@Immutable`, 리포지토리에 save·조회만(`AuditLogAppendOnlyTest`), 본문·이름·이메일 컬럼 없음.

## 이미 있는 기록 (변경 없음, 조회문에서 읽기만)

| 기록 | 기능 | 운영자 조회에서의 의미 | 행위자 |
|---|---|---|---|
| `inquiry` (id, thread_root_id, parent_id, classroom_id, submitted_at) | F2·F4 | 접수 | 학부모(`parent_id`) |
| `inquiry_stage_history` (inquiry_id, stage, occurred_at) | F2 | 단계 전이 | 시스템 (TEACHER_CONFIRMED·ANSWERED의 계기가 된 사람은 같은 시각의 audit_log로 확인) |
| `refinement_attempt` (job_id → `refinement_job.inquiry_id`, trigger, outcome, occurred_at) | F3 | 정리 시도·결과 | 시스템 |
| `answer` (id, thread_root_id, teacher_id, reviewed, sent_at) | F4 | 답변 전송 | 교사(`teacher_id`) |

조회문은 위 테이블의 본문 컬럼(`inquiry.content`·`inquiry.title`·`answer.content`·`inquiry_refinement.*`)을 읽지 않는다.

## 정렬 규칙 (FR-009, research R5)

`occurred_at` → 논리 순서(`seq`) → 대상 식별자.

- 논리 순서는 research R5와 `contracts/operator-queries.md`의 seq 표를 따른다(접수 → RECEIVED → DELIVERED → 정리 시도 AUTO·AUTO_RETRY·TEACHER → REFINING → 열람 시도 → TEACHER_CONFIRMED → 열람 완료·그 밖의 행위 → 답변 전송 → ANSWERED → 종료 기록). 이 문서에 따로 적지 않는다.
- 학부모·교사 타임라인은 이미 같은 규칙(같은 시각이면 단계 ordinal)으로 정렬한다(`InquiryPersistenceAdapter.findByInquiry`). 변경 없음.

## 슈퍼 관리자 현황 (저장하지 않는 조회 결과, research R6)

| 필드 | 뜻 |
|---|---|
| classroomId | 학급 UUID (school 도메인 학급 목록 기준, 문의 0건도 포함) |
| waiting | 전송된 답변이 없고 루트 단계가 RECEIVED·DELIVERED·REFINING인 스레드 수 |
| confirmed | 루트 단계가 TEACHER_CONFIRMED이고 전송된 답변이 없는 스레드 수 |
| answered | 전송된 답변이 1건 이상이거나 루트 단계가 ANSWERED인 스레드 수 |

- 세 묶음은 겹치지 않고 합이 그 학급의 스레드 수다. 답변이 교사 확인 전에 전송될 수 있으므로(F4 research R3) `answered`를 먼저 판정한다.
- 긴급 여부·원문 확인 필요 여부·학생·학부모·재문의 수는 들어가지 않는다.
