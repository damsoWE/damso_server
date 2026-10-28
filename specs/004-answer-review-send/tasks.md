---

description: "Task list for 004-answer-review-send"
---

# Tasks: 답변 작성·점검·전송 (F4)

**Input**: Design documents from `specs/004-answer-review-send/`

**Prerequisites**: [plan.md](./plan.md), [spec.md](./spec.md), [research.md](./research.md), [data-model.md](./data-model.md), [contracts/](./contracts/answer.openapi.yaml), [quickstart.md](./quickstart.md)

**Tests**: 포함한다. spec의 성공 기준(SC-001~011)이 자동화 테스트로 확인할 수 있는 형태이고, 보안약점 체크리스트의 `조치` 근거로 테스트 이름이 필요하다(F1·F2와 같은 방식).

**Organization**: spec의 사용자 스토리(US1~US5) 단위로 묶었다(2026-10-28 추가분 US6·US7은 파일 하단 Phase 9~12). US2(점검)는 US1(전송) 위에서 동작하지만 독립적으로 테스트할 수 있다. US3(알림)·US5(종료)는 US1의 전송 서비스를 확장한다. US4(재문의)는 F2의 접수 파이프라인을 재사용한다(아래 Dependencies).

## Format: `[ID] [P?] [Story] Description`

- **[P]**: 다른 파일이고 미완료 작업에 의존하지 않아 병렬 가능
- **[Story]**: 해당 사용자 스토리 (US1~US5)

## Path Conventions

- 운영 코드: `src/main/java/damso/{inquiry,notification,common}/…` (plan.md Project Structure)
- 도메인 패키지 안은 `domain` / `application.port.in` / `application.port.out` / `application.service` / `adapter.in.controller` / `adapter.out.{persistence,review}` (constitution 1.3.0)
- 컨트롤러는 `adapter.in.controller`, 이름은 `…Controller`. DTO는 컨트롤러 안의 중첩 타입이 아니라 별도 파일의 record로 두고, 요청은 `adapter.in.controller.dto.request`(`…RequestDTO`), 응답은 `adapter.in.controller.dto.response`(`…ResponseDTO`)에 둔다
- 테스트: `src/test/java/damso/…`, 테스트 전용 도구는 `src/test/java/damso/support/`에만
- 다른 도메인 기능은 그 도메인의 `port.in`으로만 호출한다. 다른 도메인의 리포지토리를 직접 쓰지 않는다
- `answer.content`·점검 결과는 로그·예외 메시지에 넣지 않는다(원칙 VI). `Answer`의 `toString`은 `content`를 가린다
- 시각 비교는 F1의 `Clock` 빈을 주입받아 쓴다. 테스트는 `MutableClock`으로 시간을 옮긴다
- `{inquiryId}` 경로 파라미터는 항상 **스레드 루트**의 id다(재문의 자신의 id로 답변·점검·종료를 호출하는 경로는 두지 않는다, research R1)
- 테스트용 시크릿·토큰 값을 파일에 커밋하지 않는다

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: API 명세 선반영(Contract-first). 새 외부 의존성·시크릿은 없다(research R4, 사용자 확인 2026-10-25)

- [x] T001 작업 브랜치 확인: `git branch --show-current`가 `features/f4-answer-review-send`(이슈 #7 연결)인지 확인한다. 아니면 `git switch features/f4-answer-review-send`
- [x] T002 **Contract-first — 구현 전에** `docs/api/openapi.yaml`에 `specs/004-answer-review-send/contracts/answer.openapi.yaml`을 통합: 경로 6개(오퍼레이션 7개) 추가, 스키마 추가, 공통 `ErrorCode` enum에 `THREAD_CLOSED`·`INQUIRY_NOT_READY_TO_CLOSE` 2개 추가, contract의 외부 `$ref`(`../../../docs/api/openapi.yaml#…`)는 내부 참조로 바꾼다, 태그 3개 추가, `info.version` `0.3.0` → `0.4.0`. `npx -y @redocly/cli@1.34.5 lint docs/api/openapi.yaml`이 valid여야 한다. 태그 `api-v0.4`는 PR merge 후 main에서 `/api-release api-v0.4`로 찍는다
- [ ] T003 프론트엔드 공지: 프론트 레포(`damsoWE/damso_app`) 담당에게 F4 API 명세(`docs/api/openapi.yaml` 0.4.0)를 알린다. 알릴 내용: 경로 6개(답변 점검·전송·종료, 재문의, 학부모 알림 설정), 에러 코드 2개(`THREAD_CLOSED`, `INQUIRY_NOT_READY_TO_CLOSE`), 학부모 답변 알림 기본값은 켜짐. **점검(`POST .../answer-reviews`)은 F3 연동 전까지 임시 상태로, 지금은 항상 `available=false`만 돌아온다 — 교사가 점검을 요청하든 안 하든 실질적인 점검 효과가 없으니 프론트는 "점검 완료"처럼 보이는 UI 문구를 쓰지 말고 `reviewed`를 "점검을 요청한 적이 있는지"로만 표시해야 한다.** 공지한 날짜·채널을 PR 설명에 적는다
  - 메모: 이후 앱에서는 종료(`/close`)를 호출하지 않기로 함. 공지할 때 종료 경로와 `THREAD_CLOSED`·`INQUIRY_NOT_READY_TO_CLOSE`는 앱에서 쓰지 않는다고 함께 알린다(decisions.md 2026-10-27)
- [x] T004 `./gradlew build -x test`로 컴파일 확인 (Windows: `.\gradlew.bat build -x test`)

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: 모든 스토리가 쓰는 에러 코드와 테스트 지원 헬퍼

**⚠️ CRITICAL**: 이 단계가 끝나야 스토리 작업을 시작한다

- [x] T005 [P] `src/main/java/damso/common/error/ErrorCode.java`에 F4 코드 2개를 기존 코드 **뒤에** 추가(기존 의미 불변): `THREAD_CLOSED(HttpStatus.CONFLICT, "이미 종료된 문의입니다.")`, `INQUIRY_NOT_READY_TO_CLOSE(HttpStatus.CONFLICT, "아직 종료할 수 없는 문의입니다.")` (data-model.md "에러 코드 추가", research R8)
- [x] T006 [P] `src/test/java/damso/support/IntegrationTestSupport.java`에 F4 전용 헬퍼 추가: `urgentInquiryConfirmedByTeacher()`(F2 `teacherWithClassroom()`으로 교사·학급·학생·학부모를 만들고 긴급 문의를 제출한 뒤 교사가 `/content`를 열람해 `TEACHER_CONFIRMED`까지 만들어 둔 `Scenario`를 반환 — 답변 테스트의 공통 전제조건, research R3), `submitFollowUp(token, inquiryId, content)`(요청 + 새 `Idempotency-Key`)

**Checkpoint**: 컨텍스트가 뜨고 F1·F2 테스트가 모두 통과한다

---

## Phase 3: User Story 1 - 교사가 답변을 작성해 바로 전송한다 (Priority: P1) 🎯 MVP

**Goal**: 답변 작성·전송, 암호화·불변 저장, 소유권 검증, 점검 서비스 장애와 무관하게 동작 (FR-001~007)

**Independent Test**: 교사 토큰으로 담당 학급 문의에 점검 없이 답변을 전송하고, 저장된 답변이 암호화되어 있고 수정·삭제 경로가 없는지 확인한다

### Tests for User Story 1

- [x] T007 [P] [US1] `src/test/java/damso/inquiry/SendAnswerApiTest.java` — `POST /api/v1/teacher/inquiries/{inquiryId}/answers` 정상 전송 201: `answerId`·`inquiryId`·`sentAt` 반환, DB `answer.content`가 평문과 다름 · 본문 공백만·빈 문자열·2,001자 400("코드 포인트 1~2,000자", data-model.md) · 알 수 없는 필드 400 · 다른 학급 교사 404 · 학부모·관리자 토큰 403, 무인증 401 · `REFINING`(아직 `TEACHER_CONFIRMED` 아님) 상태의 일반 문의에도 답변 전송 가능(단계와 무관, research R3) · 외부 점검 포트를 예외를 던지는 가짜로 바꾼 상태에서도 전송 201(SC-001) · 답변을 수정·삭제하는 경로(`PUT`·`PATCH`·`DELETE`)가 없음(405/404) (FR-001~007, SC-001)
- [x] T008 [P] [US1] `src/test/java/damso/inquiry/AnswerAppendOnlyTest.java` — 리플렉션으로 `SpringDataAnswerRepository`에 `delete`·`update`로 시작하는 메서드·`@Modifying`이 없고 `Repository`만 상속, `Answer`는 public setter 없음·`content`·`teacherId`·`threadRootId` 컬럼이 `updatable = false`, `Answer.toString()`에 본문 문자열이 없음 (원칙 III·VI, SC-004·009)

### Implementation for User Story 1

- [x] T009 [P] [US1] `src/main/java/damso/inquiry/domain/Answer.java` — `BaseEntity` 상속, 테이블 `answer`: `thread_root_id` UUID `updatable = false`("이 답변이 속한 스레드(루트 inquiry.id)") · `teacher_id` UUID `updatable = false` · `content` `SensitiveText` "자유 서술, 공백 제거 후 1자 이상" `updatable = false` · `reviewed` boolean "전송 전 점검을 요청했는지" · `sent_at` timestamptz. 인덱스 `(thread_root_id, sent_at)`. 정적 팩토리 `Answer.send(threadRootId, teacherId, content, reviewed, now)`. `toString()`은 `content`를 가린다(data-model.md "answer")
- [x] T010 [US1] `src/main/java/damso/inquiry/application/port/out/AnswerPort.java`(`save`, `findByThreadRootId(rootId)` sent_at 오름차순) + `src/main/java/damso/inquiry/adapter/out/persistence/SpringDataAnswerRepository.java`·`AnswerPersistenceAdapter.java` (depends on T009)
- [x] T011 [US1] `src/main/java/damso/inquiry/application/port/in/SendAnswerUseCase.java` + `src/main/java/damso/inquiry/application/service/SendAnswerService.java` — `send(teacherId, inquiryId, content, reviewed)`: ① `InquiryPort.findInquiryById(inquiryId)` 없으면 `NOT_FOUND` ② `InquiryTargetQuery.isCurrentHomeroomTeacher(teacherId, inquiry.classroomId)` 아니면 `NOT_FOUND`(FR-005) ③ 본문 1~2,000자(F2 `InquiryContentPolicy` 재사용) ④ `Answer.send(inquiryId, teacherId, content, reviewed, now)` 저장. 이 스토리에서는 `inquiry.stage`·종료 여부를 보지 않는다(US5가 종료 검사를 더한다, research R3) (depends on T010, F2 `InquiryPort`·`InquiryTargetQuery`·`InquiryContentPolicy`)
- [x] T012 [US1] `src/main/java/damso/inquiry/adapter/in/controller/TeacherAnswerController.java` + DTO — `POST /api/v1/teacher/inquiries/{inquiryId}/answers`(201). 요청 DTO `dto/request/SendAnswerRequestDTO`(`content` `@NotBlank @Size(max = 2000)`, `reviewed` `@NotNull`), 응답 DTO `dto/response/AnswerSentResponseDTO`. 사용자는 `CurrentUser`에서만. 경로 역할 검사는 F1 `SecurityConfig`의 `/api/v1/teacher/**` (depends on T011)

**Checkpoint**: T007·T008 통과. 교사가 점검 없이 답변을 전송하고 학부모가 받을 준비가 된다(MVP)

---

## Phase 4: User Story 2 - 교사가 전송 전에 답변을 점검받는다 (Priority: P1)

**Goal**: 답변 점검 포트+스텁, 항상 fail-open, 점검이 전송을 막지 않음 (FR-008·008a·009~013, 사용자 확인 2026-10-25)

**Independent Test**: 점검을 요청해 `available=false`(스텁)를 받고, 그 상태에서도 답변을 그대로 전송할 수 있는지 확인한다

### Tests for User Story 2

- [x] T013 [P] [US2] `src/test/java/damso/inquiry/AnswerReviewApiTest.java` — `POST /api/v1/teacher/inquiries/{inquiryId}/answer-reviews` 200: `available=false`·`highlights=[]`(스텁, FR-012) · 본문 공백만·빈 문자열·2,001자 400 · 다른 학급 교사 404, 학부모·관리자 403, 무인증 401 · 가짜 `AnswerReviewPort`(하이라이트를 돌려줌)로 바꾸면 `available=true`와 하이라이트가 그대로 응답에 담김 · 그 가짜 포트에 전달되는 `pseudonymizedContext` 인자가 항상 `null`임을 캡처해 확인(F3 전, FR-008a) · 응답이 DB 어디에도 저장되지 않음(테이블 존재 안 함) (FR-008·008a·011·013, SC-002·010)
- [x] T014 [P] [US2] `src/test/java/damso/inquiry/AnswerReviewFailOpenTest.java` — `AnswerReviewPort.review`가 예외를 던지는 가짜로 바꾼 상태에서 점검 요청은 500이 아니라 200 + `available=false`(예외를 삼킴) · 점검 요청과 무관하게 `POST .../answers`(T007)는 항상 그대로 동작 · 점검 요청 20회를 반복해도 매번 10초 이내 응답(SC-002, 스텁은 즉시 응답하므로 반복 횟수 자체보다 "항상 10초 이내"가 핵심) (FR-012, SC-002·003)

### Implementation for User Story 2

- [x] T015 [P] [US2] `src/main/java/damso/inquiry/domain/AnswerReviewResult.java` — 값 객체(엔티티 아님). `record AnswerReviewResult(boolean available, List<Highlight> highlights)`, `record Highlight(String excerpt, String reason, String suggestion)`. 저장하지 않는다(data-model.md "answer_review_result", FR-013)
- [x] T016 [P] [US2] `src/main/java/damso/inquiry/application/port/out/AnswerReviewPort.java` — `Optional<AnswerReviewResult> review(String draftContent, String pseudonymizedContext)` (research R4) (depends on T015)
- [x] T017 [US2] `src/main/java/damso/inquiry/adapter/out/review/UnavailableAnswerReviewAdapter.java` — `AnswerReviewPort` 구현. 항상 `Optional.empty()`를 반환하고 외부 호출을 하지 않는다. "F3가 가명화 게이트웨이를 완성하면 이 클래스를 실제 어댑터로 교체한다"는 주석을 남긴다(research R4) (depends on T016)
- [x] T018 [US2] `src/main/java/damso/inquiry/application/port/in/RequestAnswerReviewUseCase.java` + `src/main/java/damso/inquiry/application/service/RequestAnswerReviewService.java` — `request(teacherId, inquiryId, draftContent)`: 소유권 검증은 T011과 같은 방식(현재 담임 아니면 `NOT_FOUND`) → `pseudonymizedContext`는 항상 `null`(F3 전, FR-008a) → `AnswerReviewPort.review(draftContent, null)` 호출, 예외 또는 `Optional.empty()`는 모두 `AnswerReviewResult(false, List.of())`로 변환(예외를 던지지 않음, FR-012) (depends on T017, F2 `InquiryTargetQuery`)
- [x] T019 [US2] `TeacherAnswerController`(T012)에 `POST /api/v1/teacher/inquiries/{inquiryId}/answer-reviews`(200) 추가 + DTO `dto/request/AnswerReviewRequestDTO`(`content` `@NotBlank @Size(max = 2000)`), `dto/response/AnswerReviewResultResponseDTO`(`available`, `highlights`) (depends on T018, T012)

**Checkpoint**: T013·T014 통과

---

## Phase 5: User Story 3 - 학부모가 답변 도착 알림을 받고 켜고 끌 수 있다 (Priority: P2)

**Goal**: 답변 전송 시 알림 발송(F2 아웃박스 재사용), 학부모별 on/off 설정(기본 켜짐) (FR-023~025, 클래리파이 Q3)

**Independent Test**: 답변 도착 알림을 켠 상태와 끈 상태에서 각각 답변을 받아 알림 발송 여부를 확인한다

### Tests for User Story 3

- [x] T020 [P] [US3] `src/test/java/damso/notification/ParentNotificationSettingsApiTest.java` — 설정을 한 번도 바꾸지 않은 학부모의 `GET /api/v1/parent/notification-settings` → `answerNotifyEnabled=true`(기본값, data-model.md "row가 없으면 true로 간주") · `PUT`으로 `false` 저장 후 `GET`이 `false` · 다시 `true`로 되돌릴 수 있음 · 알 수 없는 필드 400, `answerNotifyEnabled` 누락 400 · 교사·관리자 토큰 403, 무인증 401 (FR-024, SC-007)
- [x] T021 [P] [US3] `src/test/java/damso/notification/AnswerNotificationTest.java` — 기기를 등록하고 답변 알림을 켠 학부모에게 `POST .../answers`(T007) 호출 → `FakePushSender`에 `ANSWER` 알림 1건, 고정 문구뿐(답변 본문·교사 이름 없음) · 알림을 끈 학부모는 발송 0건이지만 답변은 저장되어 `GET /api/v1/parent/inquiries/{id}` 상세에서 그대로 보임 · 기기를 등록하지 않은 학부모도 저장은 정상, 발송만 0건 (FR-023·024, SC-007·009)

### Implementation for User Story 3

- [x] T022 [P] [US3] `src/main/java/damso/notification/domain/ParentNotificationSetting.java` — `BaseEntity` 상속, 테이블 `parent_notification_setting`: `parent_id` UUID **유니크** · `answer_notify_enabled` boolean "기본값 true"(data-model.md "parent_notification_setting")
- [x] T023 [US3] `src/main/java/damso/notification/domain/NotificationKind.java`에 `ANSWER` 값 추가(기존 `URGENT`·`URGENT_REMINDER`·`GENERAL`·`GENERAL_DIGEST` 뒤에, 기존 의미 불변)
- [x] T024 [US3] `src/main/java/damso/notification/application/port/out/ParentNotificationSettingPort.java`(`findByParentId`, `save`) + `src/main/java/damso/notification/adapter/out/persistence/`의 Spring Data 리포지토리·어댑터 (depends on T022)
- [x] T025 [US3] `src/main/java/damso/notification/application/port/in/ParentNotificationSettingsUseCase.java` + `src/main/java/damso/notification/application/service/ParentNotificationSettingsService.java` — `get(parentId)`: row가 없으면 `true`로 간주(data-model.md). `save(parentId, enabled)` (depends on T024)
- [x] T026 [US3] `src/main/java/damso/notification/domain/PushMessage.java`에 `ANSWER` 종류의 고정 제목·본문 추가(이름·답변 본문 없음, 문구는 팀 리뷰 대상이며 PR 설명에 리뷰한 사람·날짜를 적는다) (depends on T023)
- [x] T027 [US3] `src/main/java/damso/notification/application/port/in/NotifyAnswerUseCase.java` + `src/main/java/damso/notification/application/service/NotifyAnswerService.java` — `requestAnswerNotification(answerId, parentId)`: `ParentNotificationSettingsUseCase.get(parentId)`가 꺼져 있으면 요청을 만들지 않는다(F2 `NotifyInquiryUseCase.requestGeneral`과 같은 구조). 켜져 있으면 `NotificationRequestPort`에 `kind=ANSWER`, `scheduled_at=now`로 저장(같은 트랜잭션, 아웃박스). 발송은 F2의 `NotificationDispatcher`(10초 안전망)가 그대로 처리한다 — 새 발송 경로를 만들지 않는다(research R6) (depends on T025, T026, F2 `NotificationRequestPort`)
- [x] T028 [US3] `SendAnswerService`(T011) 수정 — 답변 저장과 **같은 트랜잭션**에서 `NotifyAnswerUseCase.requestAnswerNotification(answer.getId(), inquiry.getParentId())` 호출 (depends on T011, T027)
- [x] T029 [US3] `src/main/java/damso/notification/adapter/in/controller/ParentNotificationSettingsController.java` + DTO — `GET`·`PUT /api/v1/parent/notification-settings`. 요청 DTO `dto/request/ParentNotificationSettingsRequestDTO`(`answerNotifyEnabled` `@NotNull`), 응답 DTO `dto/response/ParentNotificationSettingsResponseDTO`. 경로 역할 검사는 F1 `SecurityConfig`의 `/api/v1/parent/**` (depends on T025)

**Checkpoint**: T020·T021 통과

---

## Phase 6: User Story 4 - 학부모가 같은 문의에서 이어 묻고 교사가 추가 답변한다 (Priority: P2)

**Goal**: 스레드 모델링(`thread_root_id`), 재문의가 F2 접수 파이프라인을 그대로 탐, 1분 제한 공유, 타임라인 조회 (FR-014~019, FR-015a, 클래리파이 Q2)

**Independent Test**: 답변을 받은 학부모가 같은 문의에서 재문의를 보내고, 그 재문의가 F2의 긴급 분류·1분 제한을 거쳐 교사 화면에 나타나는지 확인한다

### Tests for User Story 4

- [x] T030 [P] [US4] `src/test/java/damso/inquiry/SubmitFollowUpApiTest.java` — `POST /api/v1/parent/inquiries/{inquiryId}/follow-ups` 정상 재문의 201: `inquiryId`(새 row)·`threadRootId`(=루트 id)·`urgent`·`submittedAt` · 본문 규칙은 최초 문의와 같음(1~2,000자) · 같은 `Idempotency-Key` 재시도는 같은 결과, 같은 키에 다른 내용은 409 `CONFLICT` · **1분 안에 두 번째 일반 재문의는 429**(최초 문의와 같은 제한 버킷 공유, 클래리파이 Q2) · 1분 안이라도 **긴급 재문의는 201**(제한 없음) · 긴급 재문의는 F2처럼 정제 없이 `DELIVERED`로 즉시 접수하고 **응답은 3초 이내**(SC-006) · 본인 학부모가 아니면 404, 다른 학급 404 · **담임이 해제·공석인 학급에 재문의 시도 시 409 `NO_HOMEROOM_TEACHER`**(research R5, spec Edge Cases와 일치) · **담임이 교체된 뒤 재문의를 보내면 새 담임에게 전달됨**(research R5) (F2 FR-004, FR-014~016·015a, SC-006·011)
- [x] T031 [P] [US4] `src/test/java/damso/inquiry/ThreadTimelineApiTest.java` — 최초 문의 + 재문의 2건 + 답변 1건을 만든 뒤 `GET /api/v1/parent/inquiries/{rootId}`·`GET /api/v1/teacher/inquiries/{rootId}` 응답에 `followUps`·`answers`가 시간순으로 모두 담김 · 재문의 자신의 id로는 `/teacher/inquiries/{followUpId}`를 호출해도 같은 스레드 상세로 취급하지 않는다(별도 엔드포인트 없음, 404 또는 루트와 다른 응답임을 확인) (FR-016·017, research R1)

### Implementation for User Story 4

- [x] T032 [US4] `src/main/java/damso/inquiry/domain/Inquiry.java` 수정 — `thread_root_id` UUID 컬럼("최초 문의는 NULL. 재문의는 스레드 최초 문의의 id", `updatable = false`) + 인덱스 `(thread_root_id)` 추가. 정적 팩토리 `Inquiry.followUp(threadRootId, parentId, studentId, classroomId, teacherId, content, urgent, idempotencyKey, requestHash, now)`(`receive`와 같지만 `threadRootId`를 받음) + getter `getThreadRootId()` (data-model.md "inquiry")
- [x] T033 [US4] `src/main/java/damso/inquiry/application/port/out/InquiryPort.java`에 `List<Inquiry> findThreadMessages(UUID rootId)`(`id = :rootId OR thread_root_id = :rootId`, `submitted_at` 오름차순) 추가 + `SpringDataInquiryRepository`·`InquiryPersistenceAdapter` 구현 (depends on T032)
- [x] T034 [US4] `src/main/java/damso/inquiry/application/port/in/SubmitFollowUpUseCase.java` + `src/main/java/damso/inquiry/application/service/SubmitFollowUpService.java` — F2 `SubmitInquiryService`와 같은 순서(입력 검증 → 중복 확인 → **대상 재확인** → 긴급 판정 → 일반이면 1분 제한 → 저장): 루트 `Inquiry`의 `studentId`로 `InquiryTargetQuery.resolveChild(parentId, studentId)`를 **다시 호출**해 현재 `classroomId`·담임을 조회한다(F2와 같은 규칙, research R5 수정 — 스레드 루트의 담임을 그대로 복사하지 않는다). 담임이 없으면 `NO_HOMEROOM_TEACHER`(409)로 거부한다(spec.md Edge Cases, F2 FR-004와 같은 선). **저장 전에 루트의 `stage == ANSWERED`이면 `THREAD_CLOSED`(409)로 거부**(FR-021). 1분 제한은 `AttemptLimitUseCase.reserveInquirySubmit(parentId)`를 그대로 재사용해 최초 문의와 같은 버킷을 공유한다(FR-015a). 멱등키 중복 확인·동시성 처리는 F2와 동일 (depends on T033, F2 `SubmitInquiryUseCase`·`InquiryTargetQuery`·`AttemptLimitUseCase`·`UrgentRule`·`NotifyInquiryUseCase`)
- [x] T035 [US4] `src/main/java/damso/inquiry/adapter/in/controller/ParentFollowUpController.java` + DTO — `POST /api/v1/parent/inquiries/{inquiryId}/follow-ups`(201, `Idempotency-Key` 헤더 필수 UUID, F2 `ParentInquiryController`와 같은 헤더 파싱 규칙). 요청 DTO `dto/request/FollowUpRequestDTO`(`content` `@NotBlank @Size(max = 2000)`), 응답 DTO `dto/response/FollowUpSubmittedResponseDTO` (depends on T034)
- [x] T036 [US4] `ParentInquiryService.get`·`TeacherInquiryService.get`(F2) 확장 — `InquiryPort.findThreadMessages(rootId)`와 `AnswerPort.findByThreadRootId(rootId)`를 합쳐 시간순 타임라인을 만든다. 학부모 상세의 재문의 본문은 본인 글이라 평문(`reveal()`)으로, 교사 상세의 재문의는 F2의 목록·상세와 같은 규칙(본문 없음, 긴급만 `/content`로 열람)을 따른다. 응답 DTO(`ParentInquiryDetailResponseDTO`·`TeacherInquiryDetailResponseDTO`)에 `followUps`·`answers` 배열 추가 (depends on T033, T010, T011)

**Checkpoint**: T030·T031 통과

---

## Phase 7: User Story 5 - 교사가 "답변 완료"로 문의를 종료한다 (Priority: P3)

**Goal**: 교사의 명시적 종료, 자동 전이 없음, 종료 후 재문의·답변 거부 (FR-020~022)

**Independent Test**: 여러 차례 주고받은 문의를 교사가 종료하고, 종료 후 학부모의 재문의와 교사의 추가 답변 시도가 모두 거부되는지 확인한다

### Tests for User Story 5

- [x] T037 [P] [US5] `src/test/java/damso/inquiry/CloseInquiryApiTest.java` — `urgentInquiryConfirmedByTeacher()`(T006, `TEACHER_CONFIRMED`)로 만든 문의에 `POST /api/v1/teacher/inquiries/{inquiryId}/close` → 200 `inquiryId`·`closedAt`, DB `stage=ANSWERED`, `inquiry_stage_history`에 `ANSWERED` 1줄 추가 · 답변을 전송해도(T007) 자동으로 `ANSWERED`가 되지 않음(종료 전 `stage` 그대로)(FR-020) · 아직 `TEACHER_CONFIRMED`에 닿지 않은 일반 문의(`REFINING`) 종료 시도 409 `INQUIRY_NOT_READY_TO_CLOSE` · 다른 학급 교사 404, 학부모·관리자 403 · 이미 종료된 문의를 다시 종료 시도 409 `THREAD_CLOSED` (FR-020·022)
- [x] T038 [P] [US5] `src/test/java/damso/inquiry/ThreadClosedIntegrationTest.java` — 종료된 스레드에 `POST .../follow-ups`(T034) → 409 `THREAD_CLOSED`, 재문의 저장 안 됨 · 종료된 스레드에 `POST .../answers`(T011) → 409 `THREAD_CLOSED` · **동시에** 종료 요청 2건 → 하나만 200, 다른 하나는 409(`@Version`) (FR-021, 원칙 백엔드 요건 동시성)

### Implementation for User Story 5

- [x] T039 [US5] `src/main/java/damso/inquiry/application/port/in/CloseInquiryUseCase.java` + `src/main/java/damso/inquiry/application/service/CloseInquiryService.java` — `close(teacherId, inquiryId)`: ① 소유권 검증(T011과 같은 방식) ② 대상이 스레드 루트가 아니면(`getThreadRootId() != null`) `NOT_FOUND` ③ 이미 `ANSWERED`면 `THREAD_CLOSED`(409) ④ `TEACHER_CONFIRMED`가 아니면 `moveTo`가 던지는 `IllegalStateException`을 잡아 `INQUIRY_NOT_READY_TO_CLOSE`(409, "아직 종료할 수 없는 문의입니다." 고정 문구, T005에서 추가한 전용 코드)로 변환 ⑤ `inquiry.moveTo(InquiryStage.ANSWERED)` + 이력 `ANSWERED` 1줄(`@Version` 동시성, 기존 메커니즘 재사용) (depends on T005, T011, T032, F2 `InquiryStage`·`InquiryStageHistoryPort`)
- [x] T040 [US5] `SendAnswerService`(T011) 수정 — 전송 전 스레드 루트(`threadRootId`가 가리키는 Inquiry)의 `stage == ANSWERED`면 `THREAD_CLOSED`(409)로 거부 (depends on T011, T039)
- [x] T041 [US5] `TeacherAnswerController`(T012)에 `POST /api/v1/teacher/inquiries/{inquiryId}/close`(200) 추가 + 응답 DTO `dto/response/InquiryClosedResponseDTO`(`inquiryId`, `closedAt`) (depends on T039, T040)

**Checkpoint**: T037·T038 통과. 모든 스토리 완료

---

## Phase 8: Polish & Cross-Cutting Concerns

- [x] T042 [P] `src/test/java/damso/inquiry/AnswerSensitiveLoggingTest.java` — 답변 전송·점검(성공·실패 둘 다)·재문의·종료·알림 발송 동안 로그를 캡처해 답변 본문·점검 하이라이트·재문의 본문 문자열이 0건인지 확인 (SC-009, 원칙 VI)
- [x] T043 [P] `src/test/java/damso/inquiry/AnswerExposureTest.java` — 다른 학급·다른 학부모·슈퍼 관리자가 답변·점검·종료·재문의 API에 접근할 수 있는 경로가 0개(SC-005) · 점검 결과를 저장하는 테이블/컬럼이 없음(SC-010, 스키마 점검) · 학부모 알림 설정 꺼짐 상태에서 발송 0건이 F2의 기존 알림 경로와 섞이지 않는지 확인
- [x] T044 [P] `docs/security-checklist.md` 갱신 — 보안기능 2(답변·점검·종료의 현재 담임 검증, 재문의의 본인 학부모 검증)·보안기능 5(`answer.content` 암호화)·에러처리 2(점검 서비스 장애에도 전송 가능, fail-open)·캡슐화 2(답변·점검 로그 0건)·시간 및 상태 1("답변 완료" 전이 `@Version`)의 상태를 `조치`로 바꾸고 근거(클래스·테스트 이름) 기재
- [x] T045 [P] `docs/features.md` 상태 표 F4 행을 `구현 완료`로 갱신
- [x] T046 `quickstart.md` 2절 수동 확인을 실행하고 결과를 이 파일 하단 "구현 메모"에 적는다: 긴급 문의로 열람→점검→전송→재문의→추가 답변→종료까지 전 흐름, 답변 알림 on/off
  - 메모: 종료 단계는 서버 검증 확인용이며 앱에서는 호출하지 않음(decisions.md 2026-10-27)
- [x] T047 `./gradlew test` 전체 실행 — 실패가 있으면 숨기지 말고 테스트 이름과 함께 보고한다
- [x] T048 이 파일 하단에 "구현 메모" 절을 만들어 계획과 달라진 점을 F1·F2 tasks.md와 같은 형식으로 기록한다

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: 바로 시작. T002·T003은 구현 전에 끝낸다
- **Foundational (Phase 2)**: Setup 이후. **모든 스토리를 막는다**
- **US1 (Phase 3)**: Phase 2 이후 독립 — 이 기능의 MVP
- **US2 (Phase 4)**: US1의 소유권 검증 패턴을 재사용하지만 전송(T011)과 무관하게 동작 — US1과 병행 가능
- **US3 (Phase 5)**: US1의 `SendAnswerService`(T011)를 확장(T028)하므로 US1 다음
- **US4 (Phase 6)**: F2 `SubmitInquiryService`를 재사용하고 US1의 `AnswerPort`(T010)를 타임라인에 포함(T036)하므로 US1 다음. US2·US3와는 독립
- **US5 (Phase 7)**: US1(T011)과 US4(T032·T034)를 모두 확장해 종료를 검사하므로 US1·US4 다음
- **Polish (Phase 8)**: 원하는 스토리 완료 후

### User Story Dependencies

```
Phase 2 ── US1 ─┬─ US2
                ├─ US3
                └─ US4 ── US5
```

### Within Each User Story

- 테스트를 먼저 쓰고 실패를 확인한 뒤 구현한다
- 엔티티 → 포트·어댑터 → 서비스 → 컨트롤러 순
- 스토리가 끝날 때마다 Checkpoint 테스트를 돌리고 커밋한다(`Feat : …`, git-conventions)

### Parallel Opportunities

- Phase 2: T005·T006은 서로 다른 파일이라 병렬 가능
- 각 스토리의 테스트 작업([P])은 서로 병렬 가능
- US2 전체(T013~T019)는 US1의 T012 완료 후 US3·US4와 병렬 가능(서로 다른 파일)
- US3의 T022·T023·T026은 병렬 가능

---

## Parallel Example: Phase 2

```text
개발자 A: T005
개발자 B: T006
```

> constitution 개발 흐름상 **한 기능은 한 사람이 끝까지** 맡는 것이 원칙이므로 F4는 한 명이 순서대로 진행하는 것을 기본으로 한다.

---

## Implementation Strategy

### MVP First (US1)

1. Phase 1 → Phase 2
2. US1만 구현 → **멈추고 확인**: 교사가 답변을 작성해 전송하고 DB에 암호화되어 저장되는지, 점검 서비스 장애와 무관한지
3. 시연 가능 — 이 시점에 "전송"은 끝나지만 점검·알림·재문의·종료는 아직 없다

### Incremental Delivery

1. US1(MVP) → US2(점검, 독립) → US3(알림) → US4(재문의) → US5(종료)
2. Phase 8 → 로그·노출 점검, 체크리스트 갱신(API 명세 통합·공지는 Phase 1에서 이미 끝남)

---

## Notes

- [P] = 다른 파일, 미완료 작업 의존 없음
- **테스트 실행 전 Docker를 켠다.** PostgreSQL·Redis 컨테이너를 쓴다
- 답변 본문·점검 결과(하이라이트 포함)·재문의 본문을 로그·예외 메시지에 넣지 않는다
- `AnswerReviewPort`의 실제 구현(가명화 게이트웨이 호출)은 이 spec의 범위 밖이다 — F3가 `UnavailableAnswerReviewAdapter`를 교체한다
- 일반(비긴급) 문의의 "답변 완료" 종료는 F3가 `REFINING → TEACHER_CONFIRMED` 전이를 만들기 전까지 끝까지 테스트할 수 없다(research R3). US5 테스트는 `urgentInquiryConfirmedByTeacher()`(T006)로 만든 긴급 문의를 쓴다
- AI가 만든 코드도 설명할 수 있을 때까지 읽는다 (대회 진단·발표 대비)
- F3에서 할 일(이 spec의 범위 밖): 실제 가명화 게이트웨이·LLM 호출(`AnswerReviewPort` 교체), 일반 문의의 `REFINING → TEACHER_CONFIRMED` 전이

---

## 구현 메모 (2026-10-25)

계획과 달라졌거나 구현 중 추가된 것:

- **T003 미완료**: 프론트엔드 공지는 사람이 해야 하는 작업이라 하지 않았다. 담당자에게 `docs/api/openapi.yaml` 0.4.0(경로 6개·오퍼레이션 7개, 에러 코드 2개 `THREAD_CLOSED`·`INQUIRY_NOT_READY_TO_CLOSE`, 점검은 F3 전까지 항상 `available=false`)을 알리고 날짜·채널을 PR 설명에 적는다. `api-v0.4` 태그는 PR이 main에 merge된 뒤 `/api-release api-v0.4`로 찍는다.
- **알림 요청의 `inquiryId` 자리는 answerId가 아니라 inquiryId로 구현했다(T027·T028)**: data-model.md·tasks.md 원문은 `NotifyAnswerUseCase.requestAnswerNotification(answerId, parentId)`로 적혀 있었지만, `NotificationRequest`의 그 컬럼 값은 FCM 발송 시 `FcmPayload.of()`가 그대로 `data.inquiryId`로 내보내 학부모 앱이 그 값으로 문의 상세를 딥링크한다(URGENT·GENERAL과 같은 규칙). 앱에는 "답변 상세" 화면이 없고 문의(스레드) 상세만 있어, answerId를 넣으면 알림을 눌렀을 때 존재하지 않는 리소스로 연결된다. `requestAnswerNotification(inquiryId, parentId)`로 구현하고 `SendAnswerService`가 `inquiry.getId()`(스레드 루트)를 넘긴다.
- **"재문의 자신의 id로는 답변·점검·종료를 호출할 수 없다"는 Path Conventions 규칙을 US1·US2 서비스에도 적용했다(T040 범위 확장)**: `SendAnswerService`·`RequestAnswerReviewService`는 스레드 모델(`thread_root_id`)이 생기기 전(US1·US2)에 작성돼 이 가드가 없었다. 그대로 두면 교사가 재문의 자신의 id로 답변을 보냈을 때 `Answer.threadRootId`가 진짜 스레드 루트가 아니라 그 재문의의 id가 되어, 스레드 타임라인(`AnswerPort.findByThreadRootId`)에서 그 답변이 누락되는 문제가 생긴다. 두 서비스에 `found.getThreadRootId() == null` 필터를 추가해 `CloseInquiryService`(T039)와 같은 가드를 쓰게 했다.
- **문의 목록에서 재문의를 제외했다(T036 범위 확장)**: tasks.md T036은 `.get()`(상세)만 언급했지만, `.list()`를 그대로 두면 재문의가 `GET /api/v1/parent/inquiries`·`GET /api/v1/teacher/inquiries`에 스레드 루트와 별개의 "문의"로 중복 표시된다(FR-014 "문의는 스레드로 운영" 위반). `InquiryPort.findByParent`·`findByClassroom`·`countByClassroom`을 `thread_root_id IS NULL` 조건으로 바꿨다.
- **US3는 테스트보다 구현이 먼저 끝난 채로 세션이 끊겼다가 이어졌다**: `ParentNotificationSetting`·`NotificationKind.ANSWER`·`PushMessage`의 ANSWER 문구(T022·T023·T026)는 끊기기 전에 이미 구현돼 있었고, `NotifyAnswerService`(T027 구현체)·`SendAnswerService` 연결(T028)·컨트롤러(T029)·테스트(T020·T021)는 이어서 마무리했다.
- **T014의 반복 횟수(1,000회 → 20회)**: `AnswerReviewFailOpenTest`의 "항상 10초 이내" 테스트는 즉시 응답하는 가짜 포트(예외를 던지는 fake)를 쓰므로 반복 횟수와 무관하게 결정적으로 통과한다. 1,000회든 20회든 SC-002의 실제 측정 대상(외부 점검 서비스의 지연 분포)을 검증하지 못하는 건 같고, 20회로 줄여도 검증 강도가 떨어지지 않아 테스트 실행 시간만 줄였다.
- **추가한 클래스**: `AnswerView`·`ParentInquiryQuery.FollowUpView`·`TeacherInquiryQuery.FollowUpView`(스레드 타임라인 공용 뷰), `SubmitFollowUpService`(F2 `SubmitInquiryService`의 자매 서비스, 코드 복제 대신 같은 순서를 따르는 별도 클래스), `CloseInquiryService`.
- **테스트 구성**: F4가 추가한 테스트는 51개(`SendAnswerApiTest`·`AnswerAppendOnlyTest`·`AnswerReviewApiTest`·`AnswerReviewFailOpenTest`·`ParentNotificationSettingsApiTest`·`AnswerNotificationTest`·`SubmitFollowUpApiTest`·`ThreadTimelineApiTest`·`CloseInquiryApiTest`·`ThreadClosedIntegrationTest`·`AnswerSensitiveLoggingTest`·`AnswerExposureTest`). 기존 F2 `CrisisClassificationTest.parentResponsesContainOnlyKnownFieldsWithCodeValues`는 `ParentInquiryDetail`에 `followUps`·`answers`가 추가돼(계약에 이미 반영됨) 허용 필드 목록을 갱신했다. `ThreadClosedIntegrationTest.concurrentCloseRequestsOnlyOneSucceeds`는 `ExecutorService`+`CountDownLatch`로 같은 종료 요청을 동시에 두 번 보내 하나만 200, 다른 하나는 `@Version` 낙관적 락 충돌로 409가 되는지 확인한다(별도 catch 없이 `GlobalExceptionHandler`가 처리).
- **T046 수동 확인(2026-10-25)**: PostgreSQL·Redis·Mailpit 컨테이너(`docker compose up -d postgres redis mailpit`)와 로컬 서버(포트 18080, 무작위 생성한 임시 시크릿, 슈퍼 관리자는 환경변수로 생성, JDK 21)로 quickstart 2절 흐름을 curl로 실행했다. 학급·학생·교사·학부모 초대 코드 발행 → 가입(Mailpit에서 인증 코드 읽음) → 기기 등록 → **긴급 문의 접수**(`DELIVERED`, `URGENT_DELIVERED` + 상담 번호 둘) → 교사 원문 열람(`TEACHER_CONFIRMED`, 열람 즉시) → 점검 요청(스텁 `available=false`) → 답변 전송(201) → 학부모 재문의(같은 스레드, `threadRootId`=루트 id) → 교사 추가 답변(201) → 교사·학부모 양쪽 상세에 `followUps`·`answers`가 시간순으로 반영(교사 쪽 재문의는 본문 없이 "정리 중" 제목, 학부모 쪽은 평문) 확인. `notification_request`에 `URGENT`·`GENERAL`·`ANSWER` 1건씩(ANSWER는 답변 2건이라 2건)이 쌓였고, 10초 안전망이 돈 뒤 가짜 FCM 자격 증명 탓에 `FCM 발송 오류 (type=IOException)`만 남기고 재시도 대기(`attempts=1`)로 넘어갔다(F2와 같은 양상, 실기기 미보유로 실제 수신 미확인). 교사가 종료(`POST .../close`) → 그 뒤 학부모 재문의 시도 409 `THREAD_CLOSED` 확인. 학부모가 답변 알림을 끈 뒤(`PUT /api/v1/parent/notification-settings`) 교사가 다시 답변을 보내려 했으나, 스레드가 이미 종료돼 있어 그 시도 자체가 409 `THREAD_CLOSED`로 막혔다(알림 꺼짐 상태에서 ANSWER 요청이 안 생기는 것은 `AnswerExposureTest.parentNotificationOffOnlySuppressesAnswerKindAndDoesNotAffectExistingF2Alerts`로 별도 확인). 서버 로그에 문의·답변·재문의 본문, 기기 토큰, 이름이 없었다. 확인 후 서버·컨테이너·임시 `.env`는 정리했다.
- **T047 전체 테스트**: `./gradlew test` — **458개 테스트 모두 통과**(실패·오류 0, `build/test-results/test/*.xml` 기준).
- **docs 갱신**: `docs/security-checklist.md`(보안기능 2·5, 시간 및 상태 1, 에러처리 2, 캡슐화 2), `docs/features.md`(F4 `구현 완료`).

---

# 2026-10-28 추가 — 목록의 답변 여부(US6)·종료 경로 차단(US7)

**Input**: spec.md(US6·US7, FR-026~032, SC-012~015, Clarifications Session 2026-10-28), plan.md "2026-10-28 추가", research.md R10~R15, data-model.md "2026-10-28 추가", [contracts/list-answer-status.openapi.yaml](./contracts/list-answer-status.openapi.yaml), quickstart.md 2-1절

**Tests**: 포함한다(위 T001~T048과 같은 이유). 운영 DB 닫힌 스레드 0건은 2026-10-28 사용자 확인(research R14) — 처리 작업 없음.

**Branch**: `features/f4-close-not-used` (PR #19, 이슈 #18). T001~T048은 그대로 두고 아래를 이어서 진행한다.

---

## Phase 9: Setup (2026-10-28 추가분)

**Purpose**: Contract-first — 코드보다 명세를 먼저 바꾼다(constitution 개발 흐름)

- [x] T049 작업 브랜치 확인: `git branch --show-current`가 `features/f4-close-not-used`인지 확인한다. 아니면 `git switch features/f4-close-not-used`
- [x] T050 **Contract-first — 구현 전에** `docs/api/openapi.yaml`에 `specs/004-answer-review-send/contracts/list-answer-status.openapi.yaml`을 반영한다: ① `ParentInquirySummary`·`TeacherInquirySummary`의 `properties`와 `required`에 `hasAnswer`(`type: boolean`)·`lastAnsweredAt`(`type: string, format: date-time, nullable: true`)를 추가하고 설명은 계약 그대로(스레드 전체 기준, stage와 무관, 답변 본문 없음, 답변이 없으면 null) ② `POST /api/v1/teacher/inquiries/{inquiryId}/close`에 `deprecated: true`, summary·description을 계약대로 바꾸고 `'404'`를 공통 `NotFound` 대신 계약의 설명(꺼져 있음 → `NOT_FOUND`)으로 교체, `'200'`·`'409'` 설명에 "(설정을 켰을 때만)" ③ `InquiryStage.description`에 "앱에서는 종료를 호출하지 않아 ANSWERED가 생기지 않는다(decisions.md 2026-10-27). 답변 여부는 목록의 hasAnswer로 판단한다." ④ `POST .../follow-ups`·`POST .../answers`의 409 `THREAD_CLOSED` 설명에 "종료 경로가 운영에서 꺼져 있어 지금은 생기지 않는다." ⑤ `info.version` `0.6.0` → `0.7.0`. `npx -y @redocly/cli@1.34.5 lint docs/api/openapi.yaml`이 valid여야 한다. 새 경로·새 `ErrorCode` 없음
- [x] T051 프론트엔드 공지(사람 작업): `damsoWE/damso_app` 담당에게 `docs/api/openapi.yaml` 0.7.0을 알린다. 알릴 내용: 목록 두 곳의 `hasAnswer`·`lastAnsweredAt`(답변한 문의 판단은 `stage`가 아니라 `hasAnswer`로), `/close`는 운영에서 꺼져 있어 404, `ANSWERED`·`THREAD_CLOSED`는 앱에서 생기지 않음. 공지한 날짜·채널을 PR #19 설명에 적는다

**Checkpoint**: lint valid. 이후 US6·US7은 서로 독립

---

## Phase 10: User Story 6 - 학부모·교사가 문의 목록에서 답변한 문의를 구별한다 (Priority: P1) 🎯

**Goal**: 학부모·교사 목록 항목에 `hasAnswer`·`lastAnsweredAt` (FR-026~030)

**Independent Test**: 답변 없는 문의와 답변 있는 문의를 하나씩 만들고 학부모·교사 목록 양쪽에서 구별되는지 확인한다

### Tests for User Story 6

- [x] T052 [P] [US6] `src/test/java/damso/inquiry/InquiryListAnswerStatusTest.java`(신규, `IntegrationTestSupport` 상속, `@TestPropertySource(properties = "damso.refinement.dispatch-on-commit=false")` — `ParentInquiryQueryApiTest`와 같은 컨텍스트) — 학부모 목록 `GET /api/v1/parent/inquiries`와 교사 목록 `GET /api/v1/teacher/inquiries` 양쪽에서: ① 답변 없는 문의 → `hasAnswer=false`, `lastAnsweredAt`이 JSON에 키로 있고 값은 `null` (US6 시나리오 1) ② `POST /api/v1/teacher/inquiries/{id}/answers` 1회 → `hasAnswer=true`, `lastAnsweredAt`이 그때의 `clock.instant()`와 같음 (시나리오 2, SC-012) ③ 그 뒤 학부모 재문의(`submitFollowUp`) → 여전히 `true`, 시각 그대로 (시나리오 3) ④ `clock`을 옮기고 추가 답변 → `lastAnsweredAt`이 새 시각 (시나리오 6) ⑤ 재문의가 먼저 오고 그 뒤 첫 답변 → `true` (Edge Case: 스레드 전체 기준) ⑥ 목록 응답 본문 문자열에 보낸 답변 본문이 없음 (시나리오 4, SC-013) ⑦ 답변 있는 문의와 없는 문의가 섞인 목록에서 각 항목이 자기 스레드 값만 가짐(다른 스레드 답변이 섞이지 않음) ⑧ 담임 교체: 답변 뒤 `classroomPort.findClassroomById(...)` → `releaseTeacher()` → `createAccount(Role.TEACHER)`로 만든 새 교사로 `assignTeacher(newTeacherId)` → `saveClassroom(...)`(`TeacherInquiryApiTest.formerHomeroomTeacherLosesAccessAfterRelease`와 같은 `classroomPort` 사용 방식) → 새 담임 목록에서도 이전 담임의 답변으로 `hasAnswer=true` (Edge Case) ⑨ 학부모 목록 항목의 키 집합이 `inquiryId, studentId, studentName, urgent, stage, submittedAt, hasAnswer, lastAnsweredAt`과 정확히 같음 ⑩ 답변한 교사가 `POST /api/v1/me/withdrawal`(현재 비밀번호 재확인)로 탈퇴한 뒤 학부모 목록에서 `hasAnswer=true`·`lastAnsweredAt` 그대로 (US6 시나리오 5)
- [x] T053 [P] [US6] `src/test/java/damso/inquiry/TeacherInquiryApiTest.java` — `listItemAndDetailCarryExactlyTheContractFieldsIncludingThreatFlagged`(268행)의 목록 키 집합에 `"hasAnswer", "lastAnsweredAt"`을 더하고 `hasAnswer`가 `Boolean`인지 확인. 상세(`$`) 키 집합은 바꾸지 않는다(상세는 이번 범위 밖)

### Implementation for User Story 6

- [x] T054 [P] [US6] `src/main/java/damso/inquiry/adapter/out/persistence/SpringDataAnswerRepository.java` — 읽기 메서드 1개 추가: `@Query("select a.threadRootId as threadRootId, max(a.sentAt) as lastSentAt from Answer a where a.threadRootId in :rootIds group by a.threadRootId")`로 루트 id 묶음별 마지막 전송 시각을 돌려준다(반환 타입은 같은 패키지의 package-private 프로젝션 인터페이스 또는 `Object[]` — 구현 시 단순한 쪽). 메서드 이름은 `find…`로 시작하고 `@Modifying`을 붙이지 않는다(`AnswerAppendOnlyTest` 유지). 본문(`content`)은 select하지 않는다(원칙 VI). 기존 인덱스 `idx_answer_thread(thread_root_id, sent_at)` 사용, 엔티티·인덱스 변경 없음
- [x] T055 [US6] `src/main/java/damso/inquiry/application/port/out/AnswerPort.java` + `src/main/java/damso/inquiry/adapter/out/persistence/AnswerPersistenceAdapter.java` — `Map<UUID, Instant> findLastAnsweredAt(Collection<UUID> rootIds)`: 입력이 비어 있으면 쿼리 없이 `Map.of()`(`InquiryPersistenceAdapter.findThreadsWithThreatFlaggedFollowUp`과 같은 처리), 답변 없는 루트는 Map에 없음 (depends on T054)
- [x] T056 [P] [US6] `src/main/java/damso/inquiry/application/port/in/ParentInquiryQuery.java` + `src/main/java/damso/inquiry/application/service/ParentInquiryService.java` — `InquirySummary`에 `boolean hasAnswer`, `Instant lastAnsweredAt`(답변이 없으면 `null`) 추가(기존 `toString`의 이름 가리기 유지). `list()`에서 조회한 루트 id들로 `answers.findLastAnsweredAt(...)`를 **한 번** 호출해 `lastAnsweredAt = map.get(id)`, `hasAnswer = lastAnsweredAt != null` (FR-026·028, depends on T055)
- [x] T057 [P] [US6] `src/main/java/damso/inquiry/application/port/in/TeacherInquiryQuery.java` + `src/main/java/damso/inquiry/application/service/TeacherInquiryService.java` — `InquirySummary`에 같은 두 필드 추가. `list()`에서 `findThreadsWithThreatFlaggedFollowUp` 옆에 `answers.findLastAnsweredAt(...)`를 페이지당 **한 번** 호출하고 `summaryOf`로 넘긴다. 담임이 없어 빈 페이지를 돌려주는 경로는 그대로 (FR-027·028·030, depends on T055)
- [x] T058 [US6] `src/main/java/damso/inquiry/adapter/in/controller/dto/response/ParentInquirySummaryResponseDTO.java` + `TeacherInquirySummaryResponseDTO.java` — record 컴포넌트 `boolean hasAnswer`, `Instant lastAnsweredAt`을 `submittedAt` 뒤에 추가하고 `from(...)`에서 옮긴다. JSON 키는 정확히 `hasAnswer`·`lastAnsweredAt`, `lastAnsweredAt`은 null이어도 키가 나와야 한다(기존 `refinementStatus`와 같은 직렬화) (FR-029, depends on T056, T057)

**Checkpoint**: T052·T053 통과, 기존 `ParentInquiryQueryApiTest`·`TeacherInquiryApiTest`·`ThreatFlagApiTest`·`AnswerAppendOnlyTest` 통과. 특히 FR-030(노출 범위·정렬 유지)은 기존 `TeacherInquiryApiTest.listPutsUrgentFirstThenNewestAndHasNoContentOrParentInfo`·`teacherOfAnotherClassroomSeesNothingOfIt`·`ParentInquiryQueryApiTest.listShowsOnlyMyInquiriesNewestFirstWithoutContent`가 그대로 통과하는 것으로 확인한다

---

## Phase 11: User Story 7 - 앱에서 쓰지 않는 종료 경로를 정리한다 (Priority: P2)

**Goal**: `damso.inquiry.close-enabled`(기본 false)로 종료 경로를 막고, 종료 검증 코드·테스트는 유지 (FR-031·032)

**Independent Test**: 기본 설정에서 담당 교사가 종료를 호출하면 404이고 스레드가 열린 채로 남는지, 설정을 켠 기존 종료 테스트가 그대로 통과하는지 확인한다

### Tests for User Story 7

- [x] T059 [P] [US7] `src/test/java/damso/inquiry/CloseDisabledApiTest.java`(신규, `IntegrationTestSupport` 상속, `@TestPropertySource` 없음 — 운영 기본값 그대로) — `urgentInquiryConfirmedByTeacher()`로 만든 문의에: ① 담당 교사 `POST /api/v1/teacher/inquiries/{id}/close` → 404, `ErrorResponseAssertions.assertSafeErrorResponse(body, "NOT_FOUND")`, DB `stage`는 `TEACHER_CONFIRMED` 그대로, `inquiry_stage_history`에 `ANSWERED` 0줄 (US7 시나리오 1, SC-014) ② 그 뒤 학부모 재문의 201, 교사 답변 201 (시나리오 1) ③ 다른 학급 교사 404, 학부모 403, 슈퍼 관리자 403, 모두 stage 그대로 (시나리오 2) ④ 없는 id(`UUID.randomUUID()`)로 호출해도 같은 404 본문(꺼진 경로와 없는 문의를 구별할 수 없음)
- [x] T060 [P] [US7] 종료 성공을 전제로 한 기존 테스트 5개 클래스에 `@TestPropertySource(properties = "damso.inquiry.close-enabled=true")`를 붙이고, 클래스 Javadoc에 "운영에서는 꺼져 있다(FR-031). 종료 경로를 다시 열 때를 대비해 FR-021·022 서버 검증을 계속 확인한다(PR #19 리뷰 P0)"를 한 줄 더한다. **테스트 메서드는 지우거나 바꾸지 않는다**: `src/test/java/damso/inquiry/CloseInquiryApiTest.java`, `src/test/java/damso/inquiry/ThreadClosedIntegrationTest.java`, `src/test/java/damso/notification/UrgentReminderStopsAfterCloseTest.java`, `src/test/java/damso/inquiry/AnswerExposureTest.java`, `src/test/java/damso/inquiry/AnswerSensitiveLoggingTest.java` (research R13)

### Implementation for User Story 7

- [x] T061 [US7] `src/main/java/damso/inquiry/application/service/CloseInquiryService.java` — 생성자에 `@Value("${damso.inquiry.close-enabled:false}") boolean closeEnabled`를 받아 필드로 두고(`RefinementDispatchTrigger`의 `dispatch-on-commit`과 같은 방식), `close()`의 **첫 줄**에서 `!closeEnabled`면 `throw new BusinessException(ErrorCode.NOT_FOUND)` — 문의 조회·담당 교사 확인보다 먼저. 이후 로직(소유권 → 루트 확인 → `THREAD_CLOSED` → `INQUIRY_NOT_READY_TO_CLOSE` → 전이·이력)은 바꾸지 않는다. 클래스 Javadoc에 "앱에서 쓰지 않아 운영에서는 꺼져 있다(decisions.md 2026-10-27, FR-031). 전이·검증은 다시 열 때를 위해 남긴다" 추가 (depends on T059, T060)
- [x] T062 [US7] `src/main/resources/application.yml` — `damso.inquiry` 아래 `submit-interval` 다음에 `close-enabled: false`와 주석 "앱에서 쓰지 않는 '답변 완료' 종료 경로. false면 POST .../close가 404 (FR-031, decisions.md 2026-10-27)" 추가. 코드 기본값(`:false`)과 같게 둔다(원칙 VIII)

**Checkpoint**: T059 통과, T060의 5개 클래스가 이전과 같이 통과

---

## Phase 12: Polish (2026-10-28 추가분)

- [x] T063 [P] `docs/security-checklist.md` 보안기능 2(부적절한 인가) 근거의 F4 범위 끝에 "종료 경로는 운영에서 꺼져 있고(`damso.inquiry.close-enabled=false`, 교사 호출 404), 담당 교사 검증·닫힌 스레드 거부는 유지한다 — `CloseDisabledApiTest`, `CloseInquiryApiTest`(설정 켬)"를 더한다
- [x] T064 [P] `docs/decisions.md` 2026-10-27 행의 **미결** ①②를 해결 내용으로 갱신한다(constitution "기획서와 달라지는 결정은 decisions.md에 MUST"): ① → "2026-10-28 해결: 목록 응답에 `hasAnswer`·`lastAnsweredAt` 추가(api 0.7.0), 앱은 `stage`가 아니라 `hasAnswer`로 판단" ② → "2026-10-28 해결: 서버에서 종료 경로를 설정(`damso.inquiry.close-enabled`, 기본 false)으로 막음, 교사 호출 404, 종료 전이·검증 코드와 테스트는 유지. 운영 DB 닫힌 스레드 0건 확인". **영향 범위**의 "다음 api 태그 때 openapi.yaml 반영"은 "api 0.7.0에서 반영"으로 바꾼다
- [x] T065 `./gradlew test` 전체 실행(Windows: `.\gradlew.bat test`) — 실패가 있으면 숨기지 말고 테스트 이름과 함께 보고한다
- [x] T066 이 파일 하단에 "구현 메모 (2026-10-28)" 절을 만들어 계획과 달라진 점, T065 결과(테스트 수·실패 0 여부), T051 공지 여부를 기록한다
- [ ] T067 (사람 작업, merge 후) main에서 `/api-release api-v0.7`로 태그를 찍는다

---

## Dependencies & Execution Order (2026-10-28 추가분)

- **Phase 9(Setup)**: 바로 시작. constitution Contract-first 순서(명세 → 프론트 공지 → 코드)를 따른다: T050(명세) → **T051(공지)** → 코드 작업
- **US6(Phase 10)**: **T051 이후**. T054 → T055 → T056·T057(병렬) → T058
- **US7(Phase 11)**: **T051 이후**, US6과 독립. T059·T060 → T061 → T062
- **Polish(Phase 12)**: US6·US7 완료 후. T063·T064는 병렬
- 테스트(T052·T053·T059·T060)는 계약만 보고 쓸 수 있으므로 T050 이후 미리 써도 된다. **운영 코드(T054~T058, T061·T062) 커밋은 T051 공지가 끝난 뒤**에 한다

```
T049 ─ T050 ─ T051 (사람, 공지) ─┬─ US6: T052·T053 ─ T054 ─ T055 ─ T056 ∥ T057 ─ T058 ─┐
                                 └─ US7: T059 ∥ T060 ─ T061 ─ T062 ─────────────────────┤
                                                       Polish: T063 ∥ T064 ─ T065 ─ T066 ─ (merge) ─ T067
```

### Parallel Opportunities (2026-10-28 추가분)

- US6 테스트 T052·T053, US7 테스트 T059·T060은 서로 다른 파일이라 병렬 가능
- T056(학부모)·T057(교사)은 서로 다른 파일이라 병렬 가능
- US6 전체와 US7 전체는 겹치는 파일이 없다(`docs/api/openapi.yaml`은 T050에서 한 번에 끝낸다)

### Implementation Strategy (2026-10-28 추가분)

1. T049·T050 → **멈추고 확인**: lint valid, 계약과 `docs/api/openapi.yaml`이 일치 → T051 프론트 공지
2. US6(프론트가 막혀 있는 작업, P1) → Checkpoint → 커밋(`Feat : …`, git-conventions)
3. US7 → Checkpoint → 커밋
4. Polish → 전체 테스트 → 구현 메모

---

## 구현 메모 (2026-10-28)

- **T051 프론트 공지**: 2026-10-28 Slack으로 `damsoWE/damso_app` 담당에게 api 0.7.0(목록 `hasAnswer`·`lastAnsweredAt`, `/close` 운영 404, `ANSWERED`·`THREAD_CLOSED`는 앱에서 생기지 않음)을 알렸다(사용자). 운영 코드는 공지 뒤에 작성했다(Contract-first 순서).
- **T050 명세**: `docs/api/openapi.yaml` `0.6.0` → `0.7.0`. lint valid, 경고 4개는 변경 전과 같다(`no-server-example.com`·`operation-4xx-response`·`no-unused-components` 2개).
- **TDD 실패 확인**: 운영 코드 전에 관련 38개를 돌려 10개 실패(`InquiryListAnswerStatusTest` 7, `TeacherInquiryApiTest` 필드 집합 1, `CloseDisabledApiTest` 2)·28개 통과를 확인했다. `CloseDisabledApiTest.otherTeacherGetsNotFoundAndParentOrAdminForbidden`은 구현 전에도 통과한다(다른 학급 교사 404·학부모/관리자 403은 기존 동작과 같음). 설정을 켠 기존 종료 테스트 5개 클래스도 그대로 통과했다.
- **계획과 달라진 점**:
  - T054 집계 쿼리의 반환 타입은 `SpringDataAnswerRepository` 안의 public 중첩 인터페이스 `LastSentAt`(인터페이스 프로젝션)로 했다. package-private 인터페이스는 Spring Data 프로젝션 프록시가 만들 수 없을 수 있어 피했다. 메서드 이름 `findLastSentAtByThreadRootIds`, `@Modifying` 없음 — `AnswerAppendOnlyTest` 통과.
  - T052는 테스트 메서드 7개로 시나리오 ①~⑩을 나눠 담았다(①⑨, ②⑥, ③④, ⑤, ⑦, ⑧, ⑩). 시각 비교는 응답의 `sentAt` 대신 DB `max(sent_at)`과 비교한다(DB의 마이크로초 정밀도와 맞추기 위해).
  - T059는 ④를 "꺼진 경로와 없는 문의의 응답 본문이 같다"로 확인했다.
- **T065 전체 테스트**: `./gradlew test` — **650개 모두 통과**(실패·오류 0, 건너뜀 0, `build/test-results/test/*.xml` 기준).
- **docs 갱신**: `docs/security-checklist.md` 보안기능 2(종료 경로 차단·목록 답변 여부 근거), `docs/decisions.md` 2026-10-27 행 미결 ①② → 2026-10-28 해결.
- **남은 일**: T067 `api-v0.7` 태그는 PR #19 merge 후 main에서 사람이 찍는다.
