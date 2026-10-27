---

description: "Task list for 006-audit-log-history"
---

# Tasks: 감사 로그·이력 보존 (F5)

**Input**: Design documents from `specs/006-audit-log-history/`

**Prerequisites**: [plan.md](./plan.md), [spec.md](./spec.md), [research.md](./research.md), [data-model.md](./data-model.md), [contracts/](./contracts/), [quickstart.md](./quickstart.md)

**Tests**: 포함한다. spec의 성공 기준(SC-001~007)이 자동화 테스트로 확인할 수 있는 형태이고, 보안약점 체크리스트의 `조치` 근거로 테스트 이름이 필요하다(F1~F4와 같은 방식).

**Organization**: spec의 사용자 스토리(US1~US4) 단위로 묶었다. 각 작업 설명 앞의 **[F5 신규] / [F3 변경] / [F4 변경]** 표시는 plan.md "변경 범위" 절의 A·B·C와 같다(팀 공유용).

## Format: `[ID] [P?] [Story] Description`

- **[P]**: 다른 파일이고 미완료 작업에 의존하지 않아 병렬 가능
- **[Story]**: 해당 사용자 스토리 (US1~US4)

## Path Conventions

- 운영 코드: `src/main/java/damso/{audit,inquiry}/…` (plan.md Project Structure). 새 도메인 없음
- 도메인 패키지 안은 `domain` / `application.port.in` / `application.port.out` / `application.service` / `adapter.in.controller` / `adapter.out.persistence` (constitution 1.3.0)
- 컨트롤러는 `adapter.in.controller`, 이름은 `…Controller`. DTO는 별도 파일의 record로, 응답은 `adapter.in.controller.dto.response`(`…ResponseDTO`)
- 테스트: `src/test/java/damso/…`, 테스트 전용 도구는 `src/test/java/damso/support/`에만
- 감사 기록은 `AuditLogUseCase.record(actorId, action, AuditTargetType.INQUIRY, targetId)`(port.in)로만 남긴다. 다른 도메인 리포지토리를 직접 쓰지 않는다. 학급 목록은 school `ClassroomAdminUseCase.listClassrooms()`(port.in)로 받는다
- **새 테이블·새 컬럼을 만들지 않는다**(data-model.md). `audit_log`에 수정·삭제 메서드를 더하지 않는다(`AuditLogAppendOnlyTest`)
- 본문(`inquiry.content`·`inquiry.title`·`answer.content`·정제 결과)을 로그·예외·감사 기록·현황 응답에 넣지 않는다(원칙 VI)
- 기록 실패 테스트는 `GeneralContentViewTest`처럼 `AuditLogPort`를 실패하는 구현으로 바꿔 끼운다

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: 브랜치 확인과 API 명세 선반영(Contract-first). 새 외부 의존성·시크릿 없음

- [X] T001 작업 브랜치 확인: `git branch --show-current`가 `features/f5-audit-log`(이슈 #20 연결)인지 확인한다. 아니면 `git switch features/f5-audit-log`
- [X] T002 **Contract-first — 구현 전에** `docs/api/openapi.yaml`에 `specs/006-audit-log-history/contracts/audit.openapi.yaml`을 통합한다: ① 경로 `GET /api/v1/admin/inquiry-status`(tag `admin-inquiry`) + 스키마 `InquiryStatusSummary`·`ClassroomInquiryStatus`(`classroomId` UUID, `waiting`·`confirmed`·`answered` `integer, minimum: 0`, `additionalProperties: false`) 추가 ② `/api/v1/teacher/inquiries/{inquiryId}/refinement` 설명의 "번역본 열람은 감사 기록 대상이 아니다(원문 열람만 기록)"를 contract의 새 설명으로 교체하고 `'500'` 응답 추가 ③ `POST …/refinement/retry`·`POST …/answer-reviews`·`POST …/close`에 contract의 같은 경로 정의대로 감사 기록 설명(기록 실패 시 500 INTERNAL_ERROR이고 처리도 일어나지 않음, 거부된 요청은 기록 없음)을 더하고 `'500': { $ref: '#/components/responses/InternalError' }` 응답 추가(close에는 "앱은 이 경로를 쓰지 않는다 — docs/decisions.md 2026-10-27"도 적는다) ④ 공통 `responses`에 `InternalError`가 없으면 추가 ⑤ `info.description` 출처에 `specs/006-audit-log-history` 추가 ⑥ `info.version` `0.6.0` → `0.7.0`. 새 ErrorCode는 없다. `npx -y @redocly/cli@1.34.5 lint docs/api/openapi.yaml`이 valid여야 한다. 태그 `api-v0.7`은 PR merge 후 main에서 `/api-release api-v0.7`로 찍는다

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: 모든 스토리가 쓰는 새 `AuditAction` 값과, 그 값을 기존 DB에 넣을 수 있는지 확인(research R8)

**⚠️ CRITICAL**: T004 결과에 따라 배포 조치가 필요하면 **사용자 확인을 받기 전에는 다음 Phase로 넘어가지 않는다**(CLAUDE.md: DB 스키마 조치는 확인 후)

- [X] T003 **[F5 신규]** `src/main/java/damso/audit/domain/AuditAction.java` — 값 6개 추가: `INQUIRY_REFINEMENT_VIEW_ATTEMPTED`, `INQUIRY_REFINEMENT_VIEWED`, `INQUIRY_REFINEMENT_VIEW_FAILED`, `REFINEMENT_RETRY_REQUESTED`, `ANSWER_REVIEW_REQUESTED`, `INQUIRY_CLOSED`. 컬럼은 그대로 "`action` varchar(40)"(최장 33자). 클래스 주석을 "원문·번역본 열람(시도→성공/실패)과 사람의 처리 행위(재시도 요청·점검 요청·종료)를 남긴다. 시스템 행위는 inquiry_stage_history·refinement_attempt가 이미 기록한다(F5 research R1)"로 갱신. `AuditTargetType`은 바꾸지 않는다(모두 `INQUIRY`)
- [X] T004 **[F5 신규]** `src/test/java/damso/audit/AuditEnumConstraintTest.java` — research R8 확인: ① Testcontainers DB에서 `audit_log`의 CHECK 제약 존재 여부를 `information_schema`/`pg_constraint`로 조회해 기록 ② F2 시점 값 3개만 허용하는 제약으로 테이블을 다시 만든 뒤 앱을 띄우고(`ddl-auto=update`) 새 값 INSERT가 실패하는지 확인. 결과(제약 이름·재현 여부)를 이 파일 하단 "구현 메모"에 적고, **재현되면 멈추고 사용자에게 보고**한다 — 운영 DB에서 제약을 새 값 목록으로 다시 만드는 조치가 필요하다(quickstart "배포 시 확인")

**Checkpoint**: 새 enum 값이 테스트 DB에 저장되고, 운영 DB 조치 필요 여부가 확정됨

---

## Phase 3: User Story 2 - 교사의 원문·번역본 열람이 빠짐없이 기록된다 (Priority: P1)

> US1(재구성)의 운영자 조회문이 번역본 열람 기록을 읽으므로 US2를 먼저 한다.

**Goal**: 번역본 열람도 원문 열람과 같은 규칙(시도 → 성공/실패, 기록 실패 시 미반환)으로 기록한다 (FR-002·003·005)

**Independent Test**: 정리 완료 문의의 번역본을 열면 `INQUIRY_REFINEMENT_VIEW_ATTEMPTED`·`INQUIRY_REFINEMENT_VIEWED`가 남고, 번역본이 없으면 409에 기록 0건, 감사 저장이 실패하면 번역본이 나가지 않는다

### Tests for User Story 2

- [X] T005 [US2] **[F3 변경]** `src/test/java/damso/inquiry/TeacherRefinementApiTest.java` — `firstTranslationViewConfirmsOnceAndIsNotAudited`(120행 `audit_log` 0건 단언)를 `firstTranslationViewConfirmsOnceAndIsAudited`로 바꾸고, 두 번 열면 `INQUIRY_REFINEMENT_VIEW_ATTEMPTED`·`INQUIRY_REFINEMENT_VIEWED`가 각 2건, `actor_id`=교사·`target_id`=문의, 단계 이력 TEACHER_CONFIRMED는 1줄인지 단언. 번역본이 없는 문의(정리 중·원문 확인 필요·긴급)는 409 `REFINEMENT_NOT_READY`이고 `audit_log` 0건(spec US2-7). 거부된 요청은 기록하지 않는다(spec FR-002, research R3): 다른 학급 교사·없는 문의 UUID·담임 해제 뒤 이전 담임이 요청하면 모두 404이고, 세 응답 본문이 같으며(`assertSafeErrorResponse(…, "NOT_FOUND")`), `audit_log` 0건
- [X] T006 [P] [US2] **[F5 신규]** `src/test/java/damso/inquiry/RefinementViewAuditFailureTest.java` — **[팀 결정 2026-10-27]** `AuditLogPort.append`가 실패하도록 바꾼 상태에서 `GET …/refinement`가 **200이고 번역본이 정상으로 나오며**, 첫 열람 전이(TEACHER_CONFIRMED)도 그대로 일어나고, `audit_log`는 0건이며, 캡처한 로그에 `inquiry=<UUID>`·`action=INQUIRY_REFINEMENT_VIEW_ATTEMPTED`·`action=INQUIRY_REFINEMENT_VIEWED`·`type=<예외 클래스>`가 있고 문의 원문·번역본(사실관계·요청사항)·제목·학생 이름 문자열은 없는지(FR-005, SC-005, `OutputCaptureExtension`). 같은 상태에서 `/content`는 여전히 500(원문은 원칙 III 그대로). 확인 전이(`InquiryConfirmationStep.confirmIfRefined`)가 낙관적 락이 아닌 예외로 실패하면 `ATTEMPTED`·`VIEW_FAILED`가 남고 번역본이 나가지 않는지. `ViewInquiryContentAtomicityTest`와 같은 방식으로 작성

### Implementation for User Story 2

- [X] T007 [US2] **[F3 변경]** `src/main/java/damso/inquiry/application/service/ViewRefinementService.java` — `AuditLogUseCase` 주입. 순서: ① 담임 확인(없으면 404 `NOT_FOUND`) ② 번역본 준비 확인(없으면 409 `REFINEMENT_NOT_READY`, **기록하지 않음**) ③ `audit.record(teacherId, INQUIRY_REFINEMENT_VIEW_ATTEMPTED, INQUIRY, inquiryId)` — 실패하면 `log.warn("번역본 열람 감사 기록 실패 (inquiry={}, action={}, type={})", …)`만 남기고 계속(예외 객체·메시지는 넣지 않는다, 팀 결정 2026-10-27) ④ 단계가 REFINING이면 `confirmation.confirmIfRefined` — `OptimisticLockingFailureException`은 지금처럼 debug 로그 후 계속, 그 밖의 `RuntimeException`은 `INQUIRY_REFINEMENT_VIEW_FAILED`를 남기고(그 기록이 실패하면 `addSuppressed`) 전파 ⑤ `INQUIRY_REFINEMENT_VIEWED`(실패 시 ③과 같이 로그만) ⑥ 반환. `ViewInquiryContentService`의 `recordFailure` 구조를 그대로 따른다. 클래스 주석의 "번역본 열람은 감사 기록 대상이 아니다"를 "F5부터 번역본 열람도 기록한다. 기록이 실패해도 번역본은 내보내고 로그만 남긴다 — 원문과 다른 점(specs/006 research R3)"으로 바꾼다. 로그·예외에 본문을 넣지 않는다
- [X] T008 [US2] T005·T006·`GeneralContentViewTest`·`TeacherInquiryApiTest`를 실행해 통과 확인(`./gradlew test --tests 'damso.inquiry.*Refinement*' --tests 'damso.inquiry.GeneralContentViewTest' --tests 'damso.inquiry.TeacherInquiryApiTest'`)

**Checkpoint**: 원문·번역본 열람 모두 기록되고, 기록 없이 내용이 나가는 경로가 없음

---

## Phase 4: User Story 1 - 분쟁 시 한 문의의 전 과정을 기록으로 재구성한다 (Priority: P1) 🎯 MVP

**Goal**: 행위자가 빠져 있던 처리 행위(재시도 요청·점검 요청·종료)를 기록하고, 운영자 조회문으로 스레드·계정 단위 전 과정을 시간순으로 재구성한다 (FR-001·004·006~011)

**Independent Test**: 문의 하나를 접수 → 정리 → 번역본·원문 열람 → 점검 요청 → 답변 전송 → 재문의 → 추가 답변까지 진행한 뒤 `contracts/operator-queries.md` Q1을 실행하면 모든 단계가 시간순으로 나오고 본문·이름·이메일이 없다

### Tests for User Story 1

- [X] T009 [P] [US1] **[F3 변경]** `src/test/java/damso/inquiry/RefinementFailureTest.java`(재시도 API를 다루는 기존 테스트) — 받아들여진 재시도는 `REFINEMENT_RETRY_REQUESTED` 1건(`actor_id`=교사, `target_id`=문의). 409 `REFINEMENT_RETRY_UNAVAILABLE`(원문 확인 필요 아님·이미 재시도함·긴급)과 동시 요청에서 진 쪽은 기록 0건
- [X] T010 [P] [US1] **[F4 변경]** `src/test/java/damso/inquiry/AnswerReviewApiTest.java`·`AnswerReviewFailOpenTest.java` — 점검 요청 1번에 `ANSWER_REVIEW_REQUESTED` 1건(`target_id`=스레드 루트). 점검 포트가 실패·예외여도 기록은 1건이고 응답은 기존처럼 `available=false`(fail-open 유지). 다른 학급 교사는 404, 빈 초안 등 400 `INVALID_INPUT`은 기록 0건. 감사 저장이 실패해도 점검 요청은 200(`available=false`)이고 `POST …/answers` 전송은 201이며, 로그에 `inquiry=`·`action=ANSWER_REVIEW_REQUESTED`·`type=`만 있고 초안·답변·문의 본문이 없다(팀 결정 2026-10-28)
- [X] T011 [P] [US1] **[F4 변경]** `src/test/java/damso/inquiry/CloseInquiryApiTest.java` — 종료 성공 시 `INQUIRY_CLOSED` 1건 + 단계 ANSWERED 1줄. 409(`INQUIRY_NOT_READY_TO_CLOSE`·`THREAD_CLOSED`)·404는 기록 0건. 테스트 주석에 "앱은 이 경로를 쓰지 않는다(docs/decisions.md 2026-10-27)"를 적는다
- [X] T012 [US1] **[F5 신규]** `src/test/java/damso/audit/OperatorQueriesTest.java` — `contracts/operator-queries.md`의 Q1·Q2 SQL을 파일에서 그대로 읽어(코드 블록 추출, `\set` 줄은 바인딩 값으로 대체) 실행한다. 시각은 `support/MutableClock`으로 고정해, 한 요청 안의 기록은 같은 시각이 되게 하고 서로 다른 요청 사이에는 1초씩 진행한다(다른 요청끼리의 동시 시각은 R5상 어느 순서여도 사실과 어긋나지 않으므로 단언하지 않는다). 시나리오와 단언: ① **일반 흐름** — 접수 → 자동 정리 성공 → 번역본 첫 열람 → 원문 열람 → 점검 요청 → 답변 전송 → 재문의 → 추가 답변. 이벤트 순서가 `INQUIRY_SUBMITTED` → `STAGE_RECEIVED` → `REFINE_ATTEMPT` → `STAGE_REFINING` → `INQUIRY_REFINEMENT_VIEW_ATTEMPTED` → `STAGE_TEACHER_CONFIRMED` → `INQUIRY_REFINEMENT_VIEWED` → `INQUIRY_CONTENT_VIEW_ATTEMPTED` → `INQUIRY_CONTENT_VIEWED` → `ANSWER_REVIEW_REQUESTED` → `ANSWER_SENT` → `FOLLOW_UP_SUBMITTED` → … → `ANSWER_SENT`이고, 시계를 멈춘 채로 진행해도(모든 기록이 같은 시각) 한 요청 안의 같은 시각 기록이 seq대로 나온다(research R5, operator-queries의 seq 표) ② **긴급** — 같은 시각의 `STAGE_RECEIVED` → `STAGE_DELIVERED`, 그 뒤 원문 열람 시도 → `STAGE_TEACHER_CONFIRMED` → 열람 완료 ③ **원문 확인 필요 → 교사 재시도**(spec US1-3) — `REFINE_ATTEMPT`(`detail` `AUTO:…`·`AUTO_RETRY:…` 실패, 같은 시각이면 AUTO가 먼저) → `REFINEMENT_RETRY_REQUESTED`(`actor_id`=교사) → `REFINE_ATTEMPT`(`TEACHER:SUCCESS`) → `STAGE_REFINING`(재시도 요청과 실행은 한 HTTP 요청 안이라 같은 시각이 될 수 있다 — seq 3으로 시도보다 앞) ④ **담임 교체·연결 해제**(spec FR-008, Edge Cases) — 교사 A가 열람한 뒤 학급 담임을 B로 바꾸고 학생 연결을 해제해도 Q1 결과의 A 기록(`actor_id`=A)이 그대로이고 B는 행위자로 나오지 않음, Q2(A)에도 그대로 나옴 ⑤ 결과 컬럼은 `occurred_at, seq, actor_kind, actor_id, event, message_id, detail`(Q1)뿐이고 어떤 값에도 문의·답변 본문·학생 이름·이메일 문자열이 없음(SC-003) ⑥ Q2(교사 UUID)에 그 교사의 열람·재시도 요청·점검 요청·답변 전송만 나옴 ⑦ 탈퇴한 학부모 UUID로 Q2를 돌려도 접수 기록이 그대로(FR-008)
- [X] T013 [P] [US1] **[F5 신규]** `src/test/java/damso/inquiry/UrgentRecordUnderOutageTest.java` — FR-006·SC-006 확인. 운영 코드는 바꾸지 않는다(F2 구조를 증명하는 테스트). ① 외부 AI(위기 분류)와 푸시 발송을 **동시에** 실패하게 한 상태에서 긴급 문의·긴급 재문의를 보내면 201이고, `inquiry_stage_history`에 각각 RECEIVED·DELIVERED가 있으며, Q1(operator-queries)에도 `STAGE_RECEIVED`·`STAGE_DELIVERED`가 나온다. `notification_request`는 PENDING으로 남는다 ② 단계 이력 저장이 실패하게 바꾸면(`InquiryStageHistoryPort`를 `append`에서 예외를 던지는 `@Primary` 테스트 구현으로 교체) 긴급 접수는 500이고 `inquiry`에도 행이 남지 않는다 — 처리는 됐는데 기록이 없는 상태가 없다. 실패 주입 방식은 `GeneralContentViewTest`·`CrisisClassificationPipelineTest`의 방식을 따른다

### Implementation for User Story 1

- [X] T014 [P] [US1] **[F3 변경]** `src/main/java/damso/inquiry/application/service/RetryRefinementService.java` — `AuditLogUseCase` 주입. `transaction.execute` 블록 안에서 `inquiries.saveInquiry(inquiry)` 뒤에 `audit.record(teacherId, REFINEMENT_RETRY_REQUESTED, INQUIRY, inquiryId)`. 같은 트랜잭션이므로 커밋 시 낙관적 락으로 진 요청은 기록도 롤백된다. **→ R9[팀 결정 2026-10-28]로 바뀜: 커밋 뒤 요청 시각으로 기록하고, 실패하면 로그만 남긴다(`ActionAuditFailureTest`)** 클래스 주석에 한 줄 추가
- [X] T015 [P] [US1] **[F4 변경]** `src/main/java/damso/inquiry/application/service/RequestAnswerReviewService.java` — `@Transactional(readOnly = true)`를 없애 바깥 트랜잭션을 두지 않고(읽기 전용이면 감사 INSERT가 거부되고, 쓰기 트랜잭션이면 감사 실패가 요청 전체를 롤백 전용으로 만든다, research R4) `AuditLogUseCase` 주입. 담임 확인 뒤 **점검 포트 호출 전에** `audit.record(teacherId, ANSWER_REVIEW_REQUESTED, INQUIRY, inquiry.getId())`(스레드 루트). 점검 포트 실패를 삼키는 기존 fail-open 처리는 그대로 둔다. 기록이 실패하면 `log.warn("답변 점검 요청 감사 기록 실패 (inquiry={}, action={}, type={})", …)`만 남기고 점검을 계속한다(팀 결정 2026-10-28). 클래스 주석에 이 동작을 적는다
- [X] T016 [P] [US1] **[F4 변경]** `src/main/java/damso/inquiry/application/service/CloseInquiryService.java` — `AuditLogUseCase` 주입. `histories.append(… ANSWERED …)` 뒤에 `audit.record(teacherId, INQUIRY_CLOSED, INQUIRY, saved.getId())`. 종료 규칙(FR-020~022)은 바꾸지 않는다. **→ R9[팀 결정 2026-10-28]로 바뀜: `TransactionTemplate`으로 바꾸고 커밋 뒤 종료 시각으로 기록, 실패하면 로그만 남긴다** 클래스 주석에 "앱은 이 경로를 쓰지 않는다(docs/decisions.md 2026-10-27). 호출되면 누가 닫았는지 기록한다" 추가
- [X] T017 [US1] **[F5 신규]** `specs/006-audit-log-history/contracts/operator-queries.md` — T012가 드러낸 컬럼·순서 차이를 반영해 조회문을 확정한다(테스트와 문서가 같은 SQL을 쓰는 상태 유지). 읽기 전용 DB 계정이 필요하다는 점은 배포 설정 범위로 남긴다
- [X] T018 [US1] T009~T013 실행 후 통과 확인

**Checkpoint**: 스레드 하나의 전 과정을 운영자 조회문만으로 재구성할 수 있음 (MVP)

---

## Phase 5: User Story 3 - 슈퍼 관리자가 내용 없이 문의 처리 현황을 본다 (Priority: P2)

**Goal**: 학급 × 처리 상태(waiting·confirmed·answered) 건수만 돌려준다. 긴급 여부는 드러나지 않는다 (FR-014~016, research R6)

**Independent Test**: 학급 2개(한 곳은 문의 0건)에 단계가 다른 문의(긴급 포함, 교사 확인 전에 답변을 보낸 문의 포함)를 만든 뒤 `GET /api/v1/admin/inquiry-status`의 건수가 맞고, 응답 필드는 4개뿐이며, 교사·학부모는 403

### Tests for User Story 3

- [X] T019 [P] [US3] **[F5 신규]** `src/test/java/damso/inquiry/AdminInquiryStatusApiTest.java` — ① 묶음 규칙: 전송된 답변 1건 이상 또는 루트 단계 ANSWERED → `answered`(먼저 판정), 루트 단계 TEACHER_CONFIRMED이고 답변 없음 → `confirmed`, 나머지(RECEIVED·DELIVERED·REFINING, 답변 없음) → `waiting` ② 긴급 문의(DELIVERED)는 `waiting`에 합쳐짐 ③ 재문의는 따로 세지 않음(스레드 단위) ④ 문의 없는 학급도 `0,0,0`으로 나오고 순서는 학년·반 순 ⑤ 응답 JSON 키가 `items[].{classroomId,waiting,confirmed,answered}`뿐(제목·이름·긴급 여부 없음) ⑥ 교사·학부모 토큰은 403 `FORBIDDEN`, 토큰 없음은 401 ⑦ 조회 뒤 `audit_log` 변화 없음(현황 조회는 감사 대상 아님) ⑧ 에러 응답은 `ErrorResponseAssertions`로 내부 정보 없음 확인

### Implementation for User Story 3

- [X] T020 [P] [US3] **[F5 신규]** `src/main/java/damso/inquiry/application/port/in/InquiryStatusQuery.java` — `List<ClassroomInquiryStatus> statusByClassroom()`, `record ClassroomInquiryStatus(UUID classroomId, long waiting, long confirmed, long answered)`
- [X] T021 [US3] **[F5 신규]** `src/main/java/damso/inquiry/application/port/out/InquiryPort.java` + `adapter/out/persistence/InquiryPersistenceAdapter.java` + `SpringDataInquiryRepository.java` — 집계 메서드 1개: 루트 문의(`thread_root_id IS NULL`)만, 학급·묶음별 건수. 답변 존재 여부는 `answer.thread_root_id`로 판정. JPQL(파라미터 바인딩)로 작성하고 `content`·`title`을 select하지 않는다. 결과는 `(classroomId, bucket, count)` 형태의 내부 record. 다른 도메인 리포지토리는 쓰지 않는다(`answer`는 같은 inquiry 도메인)
- [X] T022 [US3] **[F5 신규]** `src/main/java/damso/inquiry/application/service/InquiryStatusService.java` — `@Transactional(readOnly = true)`. school `ClassroomAdminUseCase.listClassrooms()` 순서(학년·반)대로 학급마다 T021 결과를 채우고 없으면 0. 본문을 읽지 않는다
- [X] T023 [US3] **[F5 신규]** `src/main/java/damso/inquiry/adapter/in/controller/AdminInquiryController.java` + `adapter/in/controller/dto/response/InquiryStatusSummaryResponseDTO.java`(`List<ClassroomInquiryStatusResponseDTO> items`) + `ClassroomInquiryStatusResponseDTO.java`(`UUID classroomId, long waiting, long confirmed, long answered`) — `GET /api/v1/admin/inquiry-status`. 권한은 기존 `SecurityConfig`의 `/api/v1/admin/**` = `SUPER_ADMIN`에 맡긴다. 엔티티를 직접 내보내지 않는다
- [X] T024 [US3] T019 실행 후 통과 확인. `SuperAdminInquiryAccessTest`가 계속 통과하는지(슈퍼 관리자는 여전히 문의 내용 경로에 403) 확인

**Checkpoint**: 슈퍼 관리자가 내용 없이 학급별 처리 현황을 봄

---

## Phase 6: User Story 4 - 학부모·교사는 문의 상세에서 처리 단계 타임라인만 본다 (Priority: P3)

**Goal**: 감사 기록이 넓어져도 타임라인에는 처리 단계만 나온다 (FR-012). 기존 타임라인 코드는 바꾸지 않는다

**Independent Test**: 열람·재시도·점검 요청 기록이 있는 문의의 학부모·교사 상세 `timeline`에 단계만 있다

- [X] T025 [P] [US4] **[F5 신규]** `src/test/java/damso/inquiry/TimelineSeparationTest.java` — 원문 확인 필요 → 교사 재시도 → 정리 성공 → 번역본·원문 열람 → 점검 요청을 거친 문의에서 학부모·교사 상세의 `timeline[].stage`가 `InquiryStage` 값뿐이고 두 화면이 같은 목록이며, `audit_log`의 행위 이름(`*_VIEW*`·`*_REQUESTED`)이 응답 JSON 어디에도 없음. 학부모·교사·슈퍼 관리자로 감사 기록을 돌려주는 경로가 없음(`/api/**` 매핑 목록에 audit 경로 0개)

**Checkpoint**: 모든 사용자 스토리 완료

---

## Phase 7: Polish & Cross-Cutting Concerns

- [X] T026 [P] `src/test/java/damso/inquiry/InquirySensitiveLoggingTest.java` 보강 — 번역본 열람(성공·기록 실패)·재시도·점검 요청·종료·관리자 현황 동안 로그를 캡처해 문의·답변 본문·정제 결과 문자열이 0건인지(원칙 VI)
- [X] T027 [P] `src/test/java/damso/audit/AuditLogAppendOnlyTest.java` 확인 — `AuditLogRepository`·`AuditLogPort`에 수정·삭제 메서드가 여전히 없는지(새 값 추가 뒤에도 통과, SC-002)
- [X] T028 [P] `docs/security-checklist.md` 갱신 — 보안기능(감사 기록: 원문·번역본 열람과 처리 행위, append-only)·캡슐화(기록·현황에 본문 없음)·보안기능 권한(관리자 현황 메타데이터만)의 상태와 근거(클래스·테스트 이름)를 기재
- [X] T029 [P] `docs/features.md` 상태 표 F5 행의 spec 경로를 `specs/006-audit-log-history`로, 상태를 `구현 완료`로 갱신
- [X] T030 `quickstart.md`의 시나리오 1~14와 "운영자 조회 실습"을 실행하고 결과를 이 파일 하단 "구현 메모"에 적는다
- [X] T031 `./gradlew test` 전체 실행 — 실패가 있으면 숨기지 말고 테스트 이름과 함께 보고한다
- [X] T032 이 파일 하단에 "구현 메모" 절을 만들어 계획과 달라진 점, T004의 enum 제약 확인 결과를 F1~F4 tasks.md와 같은 형식으로 기록한다

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: 바로 시작. T002(openapi)는 구현 전에 끝낸다
- **Foundational (Phase 2)**: Setup 뒤. T003 → T004. **T004에서 제약이 재현되면 사용자 확인 전까지 멈춘다**
- **US2 (Phase 3)**: Foundational 뒤
- **US1 (Phase 4)**: Foundational 뒤. T014~T016은 US2와 병렬 가능. T012(조회문 테스트)는 번역본 열람·재시도 요청 기록을 단언하므로 T007·T014 뒤. T013은 US2·US1 구현과 무관해 Foundational 뒤 언제든 가능
- **US3 (Phase 5)**: Foundational 뒤라면 독립적(감사 값과 무관). US1·US2와 병렬 가능
- **US4 (Phase 6)**: T007·T014·T015 뒤(그 기록들이 타임라인에 섞이지 않는지 보는 테스트)
- **Polish (Phase 7)**: 모든 스토리 뒤

### PR 순서 (D1)

- F5 문서·코드는 `docs/decisions.md`의 2026-10-27 행(종료 기능 앱 미사용)을 근거로 쓴다(spec FR-001, research R2, T011·T016, contract의 close 설명). 이 행은 **PR #19**(`features/f4-close-not-used`, 이슈 #18)에만 있고 main에는 아직 없다
- 순서: **PR #19 merge → 이 브랜치에 main 반영 → F5 PR 생성·merge**. F5 PR을 먼저 올려야 하면 PR 본문에 "PR #19에 의존"을 적는다

### Within Each User Story

- 테스트를 먼저 쓰고 실패를 확인한 뒤 구현한다
- F3·F4 변경 작업(T007·T014·T015·T016)은 각 기능 담당에게 공유한 뒤 진행한다(plan.md 변경 범위 B·C)

### Parallel Opportunities

- T006 ∥ T005(다른 파일)
- T009 ∥ T010 ∥ T011 ∥ T013, T014 ∥ T015 ∥ T016 (서로 다른 서비스·테스트 파일)
- US3 전체(T019~T024)는 US1·US2와 병렬
- T026 ∥ T027 ∥ T028 ∥ T029

## Parallel Example: User Story 1

```text
Task: "T009 [US1] 재시도 기록 테스트"
Task: "T010 [US1] 점검 요청 기록 테스트"
Task: "T011 [US1] 종료 기록 테스트"
# 이어서
Task: "T014 RetryRefinementService"   Task: "T015 RequestAnswerReviewService"   Task: "T016 CloseInquiryService"
```

## Parallel Example: User Story 3

```text
Task: "T019 AdminInquiryStatusApiTest"
Task: "T020 InquiryStatusQuery (port.in)"
```

## Implementation Strategy

### MVP First

1. Phase 1~2 (openapi, enum 값, R8 확인)
2. Phase 3 US2 (번역본 열람 기록) → Phase 4 US1 (처리 행위 기록 + 운영자 조회문)
3. **멈추고 확인**: 운영자 조회문으로 스레드 하나를 재구성해 본다 — 분쟁 대응에 필요한 최소 기능

### Incremental Delivery

1. US2 + US1 → 분쟁 기록 완성 (MVP)
2. US3 → 슈퍼 관리자 현황
3. US4 → 타임라인 분리 회귀 테스트
4. Polish → 체크리스트·features.md·전체 테스트

---

## 구현 메모

### T002 openapi 통합 (2026-10-27)

- `docs/api/openapi.yaml` 0.6.0 → 0.7.0. 관리자 현황 경로·스키마, `/refinement` 설명 교체, retry·answer-reviews·close 설명과 `'500'` 응답, 태그 `admin-inquiry`를 더했다. `InternalError`는 이미 있었다.
- `npx -y @redocly/cli@1.34.5 lint docs/api/openapi.yaml` valid. 경고는 통합 전 4건에서 3건으로 줄었다(새 경고 없음).

### T004 enum CHECK 제약 확인 결과 (2026-10-27) — **재현됨, 배포 조치 필요**

- 제약 이름: `audit_log_action_check`. Hibernate 7.4.5가 `action` 컬럼에 `CHECK (action IN (…enum 값…))`을 만든다(`freshSchemaHasAnActionCheckListingEveryAuditAction`).
- 제약을 F2 시점 값 3개로 되돌린 뒤 `hibernate.hbm2ddl.auto=update`로 띄워도 제약은 그대로이고, 새 값(`INQUIRY_CLOSED`) INSERT는 제약 위반으로 실패한다(`schemaUpdateKeepsAnExistingNarrowActionCheck`).
- Hibernate 7에는 `SchemaUpdate` 클래스가 없어, `MetadataSources(AuditLog) + hbm2ddl.auto=update`로 SessionFactory를 띄우는 방식으로 앱 기동과 같은 경로를 재현했다.
- 따라서 운영 DB에 F5를 배포하기 전에 `audit_log_action_check`를 새 값 9개로 다시 만들어야 한다(quickstart "배포 시 확인"). **운영 DB 스키마 조치이므로 사용자·팀 확인 전에는 Phase 3 이후로 넘어가지 않는다.**

### US2·US1 구현 (2026-10-27)

- **[팀 결정 2026-10-27] 번역본 감사 실패는 막지 않는다**: `ViewRefinementService`는 시도·완료 기록이 실패해도 번역본을 200으로 내보내고 `WARN 번역본 열람 감사 기록 실패 (inquiry=…, action=…, type=…)`만 남긴다. 예외 객체·메시지는 로그에 넣지 않는다. 원문 열람은 그대로(기록 실패 시 500). spec FR-003·005·SC-005·US2-4, research R3, plan, openapi 설명을 함께 고쳤다.
- ~~재시도·종료의 감사 실패는 500~~ → **커밋 후 기록으로 바꿨다(research R9, 아래 "재시도·종료 감사 실패" 메모)**. **점검 요청은 번역본과 같이 요청 성공 + 로그**로 바꿨다[팀 결정 2026-10-28]. 감사 실패가 트랜잭션을 롤백 전용으로 만들지 않도록 `RequestAnswerReviewService`의 바깥 트랜잭션을 없앴다.
- **R8 배포 조치**: 사용자가 A안을 골랐다. `docs/db/2026-10-27-f5-audit-action-check.sql`(DROP IF EXISTS + ADD, 한 트랜잭션)과 규칙 문서 `docs/db/README.md`를 더했다. `AuditEnumConstraintTest.deploymentSqlWidensTheNarrowCheckAndCanRunTwice`가 F2 시점 제약에 이 파일을 두 번 실행해 검증한다. F5 PR 설명에 "배포 전 실행: docs/db/2026-10-27-f5-audit-action-check.sql"을 적는다.
- **`SuperAdminInquiryAccessTest`(F2) 71행**: 팀원 확인을 받아 `/api/v1/admin/inquiry-status`만 경로 이름 검사에서 예외로 두었다(FR-014·015). 72행 필드 검사는 그대로 적용된다.
- **조회문 seq 조정(T012에서 발견)**: 교사 재시도는 요청 기록이 커밋된 뒤 같은 HTTP 요청 안에서 실행돼, 시계가 같으면 `REFINEMENT_RETRY_REQUESTED`(기존 seq 9)가 재시도 시도(5)보다 뒤로 정렬됐다. seq를 3으로 옮기고 나머지를 하나씩 밀었다(operator-queries 표, research R5, data-model).
- **Q2 정렬**: 같은 시각의 열람 시도·완료가 순서 없이 나와 `*_ATTEMPTED`를 먼저 두는 정렬 키를 더했다.
- **T012 시계**: 서로 다른 요청이 같은 시각이면 R5상 순서를 정하지 않으므로, 한 요청 안만 같은 시각으로 두고 요청 사이에는 1초씩 진행했다.
- **테스트 도구**: Q1·Q2를 문서에서 그대로 읽어 실행하는 `src/test/java/damso/support/OperatorQueries.java`를 더했다(T012·T013이 함께 쓴다).
- **`gradlew` 실행 권한**: 이 체크아웃에서 `gradlew`가 644라 `sh ./gradlew`로 실행했다. 파일 권한은 바꾸지 않았다.

### US3 구현 (2026-10-28)

- 집계는 JPQL 2개(답변 없는 스레드 루트·답변 있는 스레드 루트를 각각 (학급, 단계)별로 count)로 하고, 포트는 `ThreadStageCount(classroomId, stage, answerSent, count)`를 돌려준다. 묶음 판정(answered 먼저 → confirmed → waiting)은 tasks의 "adapter가 bucket을 돌려준다" 대신 `InquiryStatusService`에 두었다 — 규칙이 한곳에 모이고 adapter는 세기만 한다. `content`·`title`은 select하지 않는다.
- `AdminInquiryStatusApiTest`의 필드 부재 검사는 `"answer"` 접두어가 정상 키 `answered`와 겹쳐, 키 형태(`"필드":`)로 검사한다.

### US4·Polish (2026-10-28)

- **T025** `TimelineSeparationTest`: 경로 이름 검사에서 `log`는 뺐다 — `/api/v1/auth/login`·`logout`이 걸린다. `audit`·`history`만 본다.
- **T026** `InquirySensitiveLoggingTest.auditPathsNeverLogContentRefinementResultsOrNames`를 더했다(기존 3개 테스트는 그대로).
- **T027** `AuditLogAppendOnlyTest` 3개는 새 enum 값 뒤에도 그대로 통과(코드 변경 없음).
- **T028** `docs/security-checklist.md`에는 감사 기록 전용 행이 없어(49개 기준 항목) 보안기능 2(부적절한 인가)와 캡슐화 2(디버그 코드·로그) 행의 근거에 F5를 덧붙였다.
- **T030 quickstart 시나리오 → 확인한 테스트** (모두 `./gradlew test`에서 통과, Testcontainers):

  | # | 확인한 테스트 |
  |---|---|
  | 1 | `TeacherRefinementApiTest.firstTranslationViewConfirmsOnceAndIsAudited` |
  | 2 | `TeacherRefinementApiTest.notReadyWhenThereIsNoTranslation` |
  | 3 | `ViewInquiryContentAtomicityTest.contentIsNotReturnedAndNothingChangesWhenTheAuditRecordFails`, `RefinementViewAuditFailureTest.originalContentIsStillWithheldWhenTheAuditRecordFails` |
  | 3a | `RefinementViewAuditFailureTest.translationIsReturnedAndOnlyIdentifiersAreLoggedWhenTheAuditRecordFails` |
  | 4 | `RefinementFailureTest`(재시도 기록 단언 5곳) |
  | 5 | `AnswerReviewApiTest.reviewAlwaysReturnsUnavailableBeforeF3`, `AnswerReviewFailOpenTest` |
  | 6 | `CloseInquiryApiTest.teacherClosesAConfirmedThread` |
  | 7·8 | `AdminInquiryStatusApiTest` |
  | 9 | `TimelineSeparationTest` |
  | 10 | `OperatorQueriesTest` — Q1·Q2를 문서에서 그대로 읽어 실행, 테스트 DB에서 수십 ms(SC-004 "5분 안에"는 운영 DB 접속 시간 포함이라 배포 뒤 운영자가 확인) |
  | 11 | `AuditLogAppendOnlyTest` |
  | 12 | `AuditEnumConstraintTest` — 재현됨, `docs/db/2026-10-27-f5-audit-action-check.sql`로 조치 |
  | 13 | `TeacherRefinementApiTest.refusedRequestsLookTheSameAndLeaveNoAuditRecord`, `TeacherInquiryApiTest.formerHomeroomTeacherLosesAccessAfterRelease` |
  | 14 | `UrgentRecordUnderOutageTest` |

  "운영자 조회 실습"(psql로 운영·스테이징 DB에 직접 실행)은 이 환경에 운영 DB 접근이 없어 하지 않았다. 같은 SQL을 `OperatorQueriesTest`가 실행한다.
- **T031** `./gradlew test` 전체 662개 통과(실패 0, 건너뜀 0).
- **남은 배포 작업**: PR #19 merge → main 반영 → F5 PR. PR 설명에 "배포 전 실행: docs/db/2026-10-27-f5-audit-action-check.sql". merge 뒤 main에서 `/api-release api-v0.7`.

## Phase 8: Convergence

- [X] T033 Add a closed-thread scenario to `src/test/java/damso/audit/OperatorQueriesTest.java` asserting Q1 ends with `ANSWER_SENT` → `STAGE_ANSWERED` → `INQUIRY_CLOSED` (actor = teacher) when the close records share one instant per US1/AC5, FR-009 (partial)
- [X] T034 Add a teacher-withdrawal scenario to `src/test/java/damso/audit/OperatorQueriesTest.java` asserting the withdrawn teacher's view/answer records stay in Q1 and Q2 under the same account UUID per US1/AC8, FR-008 (partial)

### 재시도·종료 감사 실패 (2026-10-28, research R9)

- 팀원 요청으로 재시도·종료도 감사 기록이 실패할 때 500을 내지 않는다. 사용자가 B안(커밋 후 기록)을 골랐다.
- `AuditLogUseCase`에 `record(…, Instant occurredAt)`를 더했다. 재시도는 요청 시각, 종료는 종료 시각(단계 이력과 같은 값)을 넘긴다 — 커밋 뒤에 써도 운영자 조회문의 순서가 그대로다(`OperatorQueriesTest` 7개 통과).
- `CloseInquiryService`는 `@Transactional` 대신 `TransactionTemplate`. 동시 종료의 409는 그대로(`saveAndFlush`에서 드러나 기록 전에 실패).
- 새 테스트 `ActionAuditFailureTest` 3개. 전체 667개 통과.

