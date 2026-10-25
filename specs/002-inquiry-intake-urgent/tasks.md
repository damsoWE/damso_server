---

description: "Task list for 002-inquiry-intake-urgent"
---

# Tasks: 문의 접수·긴급 분류 (F2)

**Input**: Design documents from `specs/002-inquiry-intake-urgent/`

**Prerequisites**: [plan.md](./plan.md), [spec.md](./spec.md), [research.md](./research.md), [data-model.md](./data-model.md), [contracts/](./contracts/inquiry.openapi.yaml), [quickstart.md](./quickstart.md)

**Tests**: 포함한다. spec의 성공 기준(SC-001~014)이 자동화 테스트로 확인할 수 있는 형태이고, 보안약점 체크리스트의 `조치` 근거로 테스트 이름이 필요하다(F1과 같은 방식).

**Organization**: spec의 사용자 스토리(US1~US7) 단위로 묶었다. Phase 2가 끝나면 각 스토리를 구현·검증할 수 있다. 다만 알림 공통 부분은 처음 필요한 스토리에 두었고, US2·US3·US5는 앞선 스토리가 만든 접수·알림 흐름 위에서 동작한다(아래 Dependencies).

## Format: `[ID] [P?] [Story] Description`

- **[P]**: 다른 파일이고 미완료 작업에 의존하지 않아 병렬 가능
- **[Story]**: 해당 사용자 스토리 (US1~US7)

## Path Conventions

- 운영 코드: `src/main/java/damso/{inquiry,notification,audit,school,attempt,common}/…` (plan.md Project Structure)
- 도메인 패키지 안은 `domain` / `application.port.in` / `application.port.out` / `application.service` / `adapter.in.controller` / `adapter.out.{persistence,fcm,resource}` (constitution 1.3.0)
- 컨트롤러는 `adapter.in.controller`, 이름은 `…Controller`. DTO는 컨트롤러 안의 중첩 타입이 아니라 별도 파일의 record로 두고, 요청은 `adapter.in.controller.dto.request`(`…RequestDTO`), 응답은 `adapter.in.controller.dto.response`(`…ResponseDTO`)에 둔다
- 테스트: `src/test/java/damso/…`, 테스트 전용 도구는 `src/test/java/damso/support/`에만
- 다른 도메인 기능은 그 도메인의 `port.in`으로만 호출한다. 다른 도메인의 리포지토리를 직접 쓰지 않는다
- 문의 본문·제목은 `SensitiveText`. 응답 DTO로 옮길 때만 `reveal()`하고 로그·예외 메시지에 넣지 않는다
- 시각 비교는 F1의 `Clock` 빈을 주입받아 쓴다(`Instant.now()` 직접 호출 금지). 테스트는 `MutableClock`으로 시간을 옮긴다
- 시스템 안내는 고정 문구 코드(`NoticeCode`, `HotlineCode`)로만 내려 준다. AI가 만든 문장을 학부모에게 보내지 않는다(원칙 V)
- 테스트용 시크릿·토큰 값을 파일에 커밋하지 않는다. `TestSecrets`가 실행 시 만든다

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: 의존성·설정 준비, API 명세 선반영(Contract-first)

- [x] T001 작업 브랜치 확인: `git branch --show-current`가 `features/f2-inquiry-urgent`(이슈 #4 연결)인지 확인한다. 아니면 `git switch features/f2-inquiry-urgent`
- [x] T002 `build.gradle`에 `implementation 'com.google.firebase:firebase-admin:<Boot 4.1.1과 호환되는 최신 안정 버전>'` 추가(research R8). 스케줄링은 F1의 `SchedulingConfig`를 재사용한다. 버전은 구현 시 Maven Central에서 확인하고 `PR 설명`에 적는다
- [x] T003 `src/main/resources/application.yml`에 추가: `damso.fcm.credentials: ${DAMSO_FCM_CREDENTIALS}` · `damso.notification.dispatch-interval: 10s` · `damso.notification.urgent-reminder-interval: 1h` · `damso.notification.retry-delays: 1m,5m,15m` · `damso.inquiry.submit-interval: 60s`(학부모당 1분 1건)
- [x] T004 [P] `.env.example`에 변수 **이름만** 추가: `DAMSO_FCM_CREDENTIALS`(옆에 주석 "Firebase 서비스 계정 JSON을 Base64로 인코딩한 값")
- [x] T005 `./gradlew build -x test`로 컴파일 확인 (Windows: `.\gradlew.bat build -x test`)
- [x] T006 **Contract-first — 구현 전에** `docs/api/openapi.yaml`에 `specs/002-inquiry-intake-urgent/contracts/inquiry.openapi.yaml`을 통합: 경로 8개(오퍼레이션 10개) 추가, 스키마 추가, 공통 `ErrorCode` enum에 `NO_HOMEROOM_TEACHER`·`INQUIRY_CONTENT_UNAVAILABLE` 추가, contract의 외부 `$ref`(`../../../docs/api/openapi.yaml#…`)는 내부 참조로 바꾼다, 태그 3개 추가, `info.version` `0.2.0` → `0.3.0`. `npx -y @redocly/cli@1.34.5 lint docs/api/openapi.yaml`이 valid여야 한다. 태그 `api-v0.3`은 PR merge 후 main에서 `/api-release api-v0.3`으로 찍는다 (constitution Contract-first, docs/speckit-guide.md 5단계)
- [ ] T007 프론트엔드 공지: 프론트 레포(`damsoWE/damso_app`) 담당에게 F2 API 명세(`docs/api/openapi.yaml` 0.3.0)를 알린다. 알릴 내용: 경로 8개(오퍼레이션 10개), 에러 코드 2개, **`Idempotency-Key` 필수 헤더**(문의 제출마다 새 UUID, 재시도 때 같은 값, 같은 키에 다른 내용이면 409 `CONFLICT`), 본문 길이는 **코드 포인트** 2,000자(이모지 1개 = 1자), 학부모 안내 고정 문구(`NoticeCode`·`HotlineCode`)를 앱이 그대로 표시, 기기 등록(`PUT /api/v1/me/devices`)과 FCM 토큰 전달 방식. 공지한 날짜·채널을 PR 설명에 적는다. 이후 명세가 바뀌면 T006를 다시 하고 다시 공지한다 (constitution Contract-first: 명세 수정 → 프론트 공지 → 코드 수정)

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: 모든 스토리가 쓰는 에러 코드·시크릿·긴급 판정 규칙·제출 제한·학교 조회 포트·푸시 포트·테스트 지원

**⚠️ CRITICAL**: 이 단계가 끝나야 스토리 작업을 시작한다

- [x] T008 [P] `src/main/java/damso/common/error/ErrorCode.java`에 F2 코드 2개를 기존 코드 **뒤에** 추가(기존 의미 불변): `NO_HOMEROOM_TEACHER`(409, "담임 선생님이 아직 연결되지 않았습니다."), `INQUIRY_CONTENT_UNAVAILABLE`(409, "아직 열람할 수 없는 문의입니다.") (data-model.md "에러 코드 추가")
- [x] T009 [P] `src/main/java/damso/common/config/SecretsValidator.java`에 `DAMSO_FCM_CREDENTIALS` 검증 추가: 값이 있어야 하고(`(누락)`), Base64로 디코딩되며(`(형식 오류)`), 디코딩한 JSON의 `type`이 `service_account`여야 한다(`(형식 오류)`). 값은 메시지에 넣지 않는다. 테스트 `src/test/java/damso/common/config/SecretsValidatorTest.java`에 누락·Base64 아님·JSON 아님·`type` 불일치 케이스 추가, `StartupRefusalTest`에 이름이 나오는지 추가 (data-model.md 시크릿 표, 원칙 VIII)
- [x] T010 [P] `src/test/java/damso/support/TestSecrets.java`에 `DAMSO_FCM_CREDENTIALS`(실행 시 만든 `{"type":"service_account",…}` JSON의 Base64, 실제 Firebase 값 아님) 시스템 프로퍼티 추가. 테스트는 `PushSenderPort`를 가짜로 바꾸므로 실제 FCM에 접속하지 않는다
- [x] T011 [P] `src/main/java/damso/inquiry/domain/UrgentRule.java` — 순수 함수. 본문을 정규화(공백·줄바꿈 제거, 소문자, 전각→반각)한 뒤 표현 목록의 항목이 **하나라도 포함되면 긴급**이다. 외부 의존 없음(research R2). `src/main/java/damso/inquiry/application/port/out/UrgentExpressionSource.java`(표현 목록을 돌려주는 포트) + `src/main/java/damso/inquiry/adapter/out/resource/ClasspathUrgentExpressionSource.java`(`src/main/resources/urgent/expressions.txt`를 한 줄에 표현 하나로 읽음, 빈 줄·`#` 주석 무시, 정규화해 보관, **목록이 비었거나 읽지 못하면 기동 거부** `IllegalStateException`). `src/main/resources/urgent/expressions.txt`(자해·학대·폭력·실종·위협 관련 표현)는 **이 작업을 시작하기 전에 팀이 정한다**(research R2). 리뷰한 사람·날짜를 PR 설명에 적는다. 테스트 `src/test/java/damso/inquiry/domain/UrgentRuleTest.java`(대표 긴급 문장 전부 긴급, 띄어쓰기·전각 변형, 일반 문장은 긴급 아님, 빈 본문) · `src/test/java/damso/inquiry/adapter/out/resource/UrgentExpressionSourceTest.java`(빈 목록·없는 파일 → 기동 거부) · **목록 회귀 테스트**(목록의 모든 항목이 긴급으로 판정되고, 대표 사례 목록을 `src/test/resources/urgent/must-be-urgent.txt`에 두어 하나라도 놓치면 실패) (FR-009·009a·010, SC-003)
- [x] T012 `src/main/resources/redis/mail_send.lua`를 `rate_limit.lua`로 일반화(최소 간격·윈도·최대 건수·고유 멤버를 인자로 받음, 반환은 기다려야 하는 밀리초 또는 0)하고 `src/main/java/damso/attempt/domain/AttemptKind.java`에 `INQUIRY_SUBMIT` 추가. `AttemptLimitPort`에 `long reserveRate(AttemptKind, String subjectHash, Instant now, Duration minGap, Duration window, int max)`, `AttemptLimitUseCase`에 `long reserveInquirySubmit(UUID parentId)`(60초·60초·1건, 거부 시 기다려야 하는 초, 예외 없음)를 추가하고 `RedisAttemptLimitAdapter`·`AttemptLimitService`를 맞춘다. 기존 메일 재발송(60초 1건·시간당 5회)은 같은 스크립트로 동작이 그대로여야 한다. 테스트 `src/test/java/damso/attempt/AttemptLimitTest.java`에 제출 제한 60초 1건·학부모별 분리·`MutableClock`으로 61초 뒤 허용 추가, 기존 메일 제한 테스트는 그대로 통과 (research R6, data-model.md "Redis 키 추가")
- [x] T013 `src/main/java/damso/school/application/port/in/InquiryTargetQuery.java` + `src/main/java/damso/school/application/service/InquiryTargetService.java` — inquiry가 school 정보를 읽는 유일한 통로. 메서드: `ChildTarget resolveChild(UUID parentId, UUID studentId)`(본인이 보호자인 학생이면 학생 이름·학급·**현재 담임(없으면 null)**을 돌려주고, 아니면 `NOT_FOUND`), `boolean isCurrentHomeroomTeacher(UUID teacherId, UUID classroomId)`, `Optional<UUID> currentHomeroomTeacher(UUID classroomId)`, `Map<UUID,String> studentNames(Collection<UUID> studentIds)`, `Optional<UUID> homeroomClassroomOf(UUID teacherId)`. 이름은 응답용 String이며 로그에 넣지 않는다. 테스트 `src/test/java/damso/school/InquiryTargetQueryTest.java`(본인 자녀·남의 자녀·담임 없음·담임 해제) (depends on F1 `ClassroomPort`·`StudentPort`)
- [x] T014 [P] `src/main/java/damso/notification/application/port/out/PushSenderPort.java` — `SendResult send(String deviceToken, PushMessage message)`(`SENT` · `INVALID_TOKEN` · `FAILED`). `src/main/java/damso/notification/domain/PushMessage.java`는 종류(`URGENT`·`URGENT_REMINDER`·`GENERAL`·`GENERAL_DIGEST`)에서 **고정 제목·본문**을 만들고(고정 문구는 상수, 이름·본문 없음), 문의 UUID(있을 때)와 묶음 건수만 담는다. 가짜 구현 `src/test/java/damso/support/FakePushSender.java`(`@Primary`, 보낸 메시지·토큰 기록, 실패·무효 토큰 응답을 켜고 끌 수 있음)를 `TestSupportConfig`에 등록. 테스트 `src/test/java/damso/notification/domain/PushMessageTest.java`(고정 문구뿐, 묶음은 건수만) (FR-014, 원칙 V)
- [x] T015 `src/test/java/damso/support/IntegrationTestSupport.java`에 헬퍼 추가: `submitInquiry(token, studentId, content)`(요청 + 새 `Idempotency-Key`), `URGENT_SAMPLE`(긴급 표현 목록에 있는 대표 문장 상수)·`NORMAL_SAMPLE`, `teacherWithClassroom()`(교사·학급·학생·학부모를 F1 가입 헬퍼로 한 번에 만들어 `Scenario` record로 반환), `registerDevice(userId, sessionId, token)`(DB에 직접 기기 등록), `pushes` 접근자(FakePushSender). `FakePushSender`는 테스트마다 비운다 (depends on T014)

**Checkpoint**: 컨텍스트가 뜨고, 긴급 판정 순수 함수·제출 제한·학교 조회 포트·푸시 포트가 준비되었으며 F1 테스트가 모두 통과한다

---

## Phase 3: User Story 1 - 학부모가 자녀를 고르고 문의를 작성해 제출한다 (Priority: P1) 🎯 MVP

**Goal**: 자녀 선택·자유 서술 제출, 접수 안내, 원문 암호화 보존, 중복 제출·1분 제한 (FR-001~008, FR-002a·002b, FR-034)

**Independent Test**: 학부모 토큰으로 자녀를 골라 문의를 제출하고 접수 안내와 암호화된 원문(수정·삭제 경로 없음)을 확인한다

### Tests for User Story 1

- [x] T016 [P] [US1] `src/test/java/damso/inquiry/SubmitInquiryApiTest.java` — 정상 제출 201: `urgent=false`, `stage=RECEIVED`, `notices=[RECEIVED]`, `hotlines=[]`, DB `content`가 평문과 다르고 `inquiry_stage_history`에 `RECEIVED` 1줄 · 본문 공백만·빈 문자열·2,001자 400, **이모지 2,000자(코드 포인트)는 201** · 알 수 없는 필드(`title`·`phone`) 400 · 자녀가 아닌 학생(다른 학부모의 자녀·없는 UUID) 404 · 담임 없는 학급 자녀 409 `NO_HOMEROOM_TEACHER`(저장 없음) · 자녀 둘인 학부모의 둘째 자녀 문의는 둘째 자녀의 담임에게(`teacher_id`) · 접수 시점 `classroom_id`·`teacher_id` 저장, 이후 담임 해제돼도 불변 · 교사·관리자 토큰 403, 무인증 401 · 문의를 수정·삭제하는 메서드(`PUT`·`PATCH`·`DELETE /api/v1/parent/inquiries/{id}`)가 없음(405/404) (US1 시나리오 1~6, FR-001~006·034, SC-001·004·008)
- [x] T017 [P] [US1] `src/test/java/damso/inquiry/SubmitInquiryRateLimitTest.java` — 같은 `Idempotency-Key`로 두 번 제출 → 문의 1건, 두 응답의 `inquiryId`가 같음(두 번째는 1분 제한도 보지 않음) · **동시에** 같은 키 2건 → 문의 1건 · `Idempotency-Key` 누락·UUID 아님 400 · 서로 다른 키로 1분 안에 일반 문의 두 번째 → 429 `TOO_MANY_ATTEMPTS` + `Retry-After`(1~60), 저장 안 됨 · `MutableClock` 61초 뒤 다시 제출 가능 · 다른 학부모는 영향 없음 · 같은 학부모의 자녀가 달라도 같은 제한 · **같은 키에 다른 자녀·본문**으로 다시 제출 → 409 `CONFLICT`, 새 문의 없음 · Redis를 쓸 수 없는 상태(실패하는 `AttemptLimitPort` 대체 빈)에서도 일반 문의는 201로 접수(fail-open, 제한 확인만 건너뜀), WARN 로그에 본문 없음 (US1 시나리오 7~8·10~11, FR-002a·002b, SC-014)
- [x] T018 [P] [US1] `src/test/java/damso/inquiry/InquiryAppendOnlyTest.java` — 리플렉션으로 `InquiryStageHistoryRepository`에 `delete`·`update`로 시작하는 메서드와 `@Modifying` 메서드가 없고 `Repository`만 상속, `InquiryStageHistory`는 `@Immutable`·public setter 없음·개인정보 필드 없음 · `Inquiry`에 `content`·`urgent`·`studentId`·`teacherId`·`classroomId`의 public setter가 없고 해당 컬럼이 `updatable = false` (원칙 III, research R4·R5, SC-004)

### Implementation for User Story 1

- [x] T019 [P] [US1] `src/main/java/damso/inquiry/domain/` — `InquiryStage.java`(`RECEIVED`·`DELIVERED`·`REFINING`·`TEACHER_CONFIRMED`·`ANSWERED`와 **허용 전이표**: 일반 `RECEIVED → REFINING → TEACHER_CONFIRMED → ANSWERED`, 긴급 `RECEIVED → DELIVERED → TEACHER_CONFIRMED → ANSWERED`, 그 밖은 거부하는 `canMoveTo(urgent, next)`), `CrisisType.java`(`UNCLASSIFIED`·`SELF_HARM`·`ABUSE`·`OTHER_SAFETY`), `NoticeCode.java`(`RECEIVED`·`URGENT_DELIVERED`·`DEFERRED_TO_AVAILABLE_HOURS`), `HotlineCode.java`(`SUICIDE_PREVENTION_109`·`CHILD_ABUSE_112`), `InquiryContentPolicy.java`(공백을 뺀 뒤 **코드 포인트 1~2,000자**, 아니면 `InvalidInputException("content", …)`), `Inquiry.java`(`BaseEntity` 상속, 테이블 `inquiry`: `parent_id` UUID 변경 불가 · `student_id` UUID 변경 불가 · `classroom_id` UUID 변경 불가 · `teacher_id` UUID 변경 불가 · `content` `SensitiveText` "코드 포인트 1~2,000자(공백 제거 후 1자 이상)" `updatable = false` · `title` `SensitiveText` NULL 허용 · `urgent` boolean `updatable = false` · `crisis_type` varchar(20) 기본 `UNCLASSIFIED` · `stage` varchar(20) · `notification_deferred` boolean · `deferred_notified_at` timestamptz NULL 허용 · `idempotency_key` UUID · `request_hash` char(64) "자녀 UUID와 본문을 `CodeHasher`(HMAC-SHA256)로 해시, 원문 저장 안 함" · `submitted_at` timestamptz, `@Table`에 **유니크 `(parent_id, idempotency_key)`**, 인덱스 `(classroom_id, urgent, submitted_at)`·`(parent_id, submitted_at)`·`(teacher_id, notification_deferred, deferred_notified_at)`, 단계 전이 메서드 `moveTo(next)`는 전이표로 검사), `InquiryStageHistory.java`(`BaseEntity` 미상속, `@Immutable`, `Persistable`, id는 앱에서 UUID v4, 테이블 `inquiry_stage_history`: `inquiry_id` · `stage` varchar(20) · `occurred_at`, setter 없음) (data-model.md)
- [x] T020 [US1] `src/main/java/damso/inquiry/application/port/out/InquiryPort.java`(저장, `findInquiryById`, `findByParentAndKey(parentId, key)`, 학부모별 목록, 학급별 목록 페이지, 교사별 지연 문의 수·표시 갱신은 US5), `InquiryStageHistoryPort.java`(`append`·`findByInquiry`) + `src/main/java/damso/inquiry/adapter/out/persistence/`의 Spring Data 리포지토리·포트 구현. `InquiryStageHistoryRepository`는 `Repository`를 상속하고 `save`·`findByInquiryIdOrderByOccurredAt`만 선언한다(F1 `LinkHistoryRepository`와 같은 방식) (depends on T019)
- [x] T021 [US1] `src/main/java/damso/inquiry/application/port/in/SubmitInquiryUseCase.java` + `src/main/java/damso/inquiry/application/service/SubmitInquiryService.java` — 순서(research R6): ① 입력 검증(`InquiryContentPolicy`) ② `InquiryTargetQuery.resolveChild`(없으면 404, 담임 없으면 `NO_HOMEROOM_TEACHER`) ③ **중복 확인**: `(parent_id, idempotency_key)`가 있으면 `request_hash`를 비교해 같으면 그 문의를 그대로 돌려주고(제한 확인 안 함), 다르면 409 `CONFLICT` ④ **긴급 판정** `UrgentRule` ⑤ 일반이면 `AttemptLimitUseCase.reserveInquirySubmit`(거부 시 `TooManyAttemptsException`, Redis 장애면 제한 확인만 건너뛰고 WARN 로그에 종류만 남긴 뒤 접수 계속) ⑥ 저장: `Inquiry`(+`RECEIVED` 이력 1줄), 동시 같은 키는 유니크 위반을 잡아 ③과 같은 규칙(해시 같으면 기존 문의, 다르면 409)으로 처리. 결과 `InquirySubmittedResult`(inquiryId·urgent·stage·submittedAt·notices·hotlines). 이 스토리에서 긴급이면 `urgent=true`만 저장하고 전달·알림은 US2가 이어서 구현한다. 본문·제목은 어떤 로그에도 남기지 않는다 (depends on T011, T012, T013, T020)
- [x] T022 [US1] `src/main/java/damso/inquiry/adapter/in/controller/ParentInquiryController.java` + DTO — `POST /api/v1/parent/inquiries`(201, `Idempotency-Key` 헤더 필수 UUID). 요청 DTO `dto/request/SubmitInquiryRequestDTO`(`studentId` `@NotNull`, `content` `@NotBlank`, 알 수 없는 필드 거부), 응답 DTO `dto/response/InquirySubmittedResponseDTO`. 사용자는 `CurrentUser`에서만. 경로 역할 검사는 F1 `SecurityConfig`의 `/api/v1/parent/**` (depends on T021)

**Checkpoint**: T016·T017·T018 통과. 학부모가 문의를 제출하고 접수 안내를 받는다

---

## Phase 4: User Story 2 - 안전 사안은 정제를 건너뛰고 교사에게 즉시 닿는다 (Priority: P1)

**Goal**: 긴급 문의의 즉시 전달·알림·재알림·학부모 고정 안내, 장애에도 동작 (FR-009~017, FR-019, FR-020, FR-032)

**Independent Test**: 긴급 표현이 든 문의를 제출하고, FCM·Redis·AI가 모두 없는 상태에서도 `stage=DELIVERED`, 알림 요청 저장, 학부모 고정 안내가 나오는지 확인한다

### Tests for User Story 2

- [x] T023 [P] [US2] `src/test/java/damso/inquiry/UrgentInquiryApiTest.java` — 긴급 표현 문의 제출 201: `urgent=true`, `stage=DELIVERED`, `notices=[URGENT_DELIVERED]`, `hotlines=[CHILD_ABUSE_112, SUICIDE_PREVENTION_109]`(F3 전 기본 안내), `inquiry_stage_history`에 `RECEIVED`→`DELIVERED` 2줄 · **응답 시간 3초 이내**(SC-002) · 애매한 표현도 긴급 · **1분 안에 일반 문의를 낸 뒤 긴급 문의는 201**(제한 확인·기록 안 함), 긴급을 낸 직후의 일반 문의는 제한에 걸리지 않음 · **Redis를 쓸 수 없는 상태에서 긴급 문의는 201**(같은 상태의 일반 문의도 201, T017) · `PushSenderPort`가 예외를 던지는 상태·기기가 하나도 없는 상태에서도 201, 알림 요청은 `PENDING`으로 남음 · 긴급 표시를 바꾸는 경로 없음 · 응답의 안내가 `NoticeCode`·`HotlineCode` 값뿐 (US2 시나리오 1~6, FR-009~011·015·016·019·020, SC-002·003·009)
- [x] T024 [P] [US2] `src/test/java/damso/notification/UrgentNotificationTest.java` — 기기를 등록한 담임에게 긴급 문의 제출 직후(발송기를 돌리지 않아도) `FakePushSender`에 긴급 알림 1건, 고정 문구와 문의 UUID뿐(본문·이름·학생 이름 없음) · 즉시 발송을 실패시키면 다음 발송기 실행에서 **1건만** 나감(중복 0) · 두 경로가 동시에 같은 요청을 집어도 1건(`SKIP LOCKED` + `SENDING` 임대) · `SENDING`에서 멈춘 요청은 `locked_until`(60초)이 지나면 다시 발송 · 전송 실패 시 재시도가 1분·5분·15분 뒤, 그 뒤로는 1시간 간격으로 계속(`MutableClock`) · 발송 전에 담임이 바뀌면 **새 담임**에게 나감 · 활성 기기 여러 대에 모두 발송 · 세션이 끝난 기기 제외 · 응답 가능 시간 밖·일반 알림 끔 설정이어도 긴급은 즉시 · **재알림**: 확인되지 않은 긴급 문의는 마지막 알림 1시간 뒤마다 재알림 1건씩, 담임이 해제·변경되면 취소 · 무효 토큰 응답이면 그 기기 `active=false` (US2 시나리오 4~5·7, FR-011~014, SC-007·012·013)
- [x] T025 [P] [US2] `src/test/java/damso/notification/adapter/FcmMessageMappingTest.java` — `PushMessage` → FCM 메시지 변환이 고정 제목·본문과 데이터(문의 UUID)만 담는지, 토큰·본문이 로그에 나가지 않는지(`toString` 가림) 확인(실제 FCM 호출 없음) (FR-014, 원칙 VI)

### Implementation for User Story 2

- [x] T026 [P] [US2] `src/main/java/damso/notification/domain/` — `NotificationKind.java`(`URGENT`·`URGENT_REMINDER`·`GENERAL`·`GENERAL_DIGEST`), `NotificationStatus.java`(`PENDING`·`SENDING`·`SENT`·`CANCELLED`·`SKIPPED`), `NotificationRequest.java`(`BaseEntity` 상속, 테이블 `notification_request`: `recipient_id` UUID · `kind` varchar(20) · `inquiry_id` UUID NULL 허용 · `status` varchar(20) · `scheduled_at` timestamptz · `attempts` int · `sent_at` timestamptz NULL 허용 · `locked_until` timestamptz NULL 허용 "전송 중 임대 만료 시각" · `digest_count` int NULL 허용 · `active_digest_key` varchar(60) NULL 허용 **유니크**("대기 중 묶음이면 `DIGEST:{교사 UUID}`, 끝나면 NULL"), 메서드 `claim(Instant lockedUntil)`·`markSent`·`retryAfter(Duration)`·`cancel`·`skip`와 상태 전이 `PENDING → SENDING/CANCELLED/SKIPPED`·`SENDING → SENT/PENDING/SKIPPED`·`SENDING → SENDING`(임대 만료 후 재확보)만 허용), `DeviceRegistration.java`(`BaseEntity` 상속, 테이블 `device_registration`: `user_id` UUID · `session_id` UUID · `token` varchar(512) **유니크** · `platform` varchar(10) `ANDROID`·`IOS` · `active` boolean, `toString`에 토큰 미출력) (data-model.md, research R7·R9)
- [x] T027 [US2] `src/main/java/damso/notification/application/port/out/NotificationRequestPort.java`(저장·조회, **`claimDue(Instant now, Duration lease, int limit)`** 는 짧은 트랜잭션에서 `(status = PENDING ∧ scheduled_at ≤ now) ∨ (status = SENDING ∧ locked_until ≤ now)`를 `FOR UPDATE SKIP LOCKED`로 잡아 `SENDING`·`locked_until = now + lease`로 표시하고 커밋, `claimOne(requestId, now, lease)`(즉시 발송용), `findById`, 교사별 대기 중 묶음 조회), `DevicePort.java`(`findActiveByUsers`, 토큰으로 조회·저장, 세션별 해제) + `src/main/java/damso/notification/adapter/out/persistence/` 리포지토리·어댑터 (depends on T026)
- [x] T028 [P] [US2] `src/main/java/damso/notification/adapter/out/fcm/FcmPushSender.java` — `PushSenderPort`를 Firebase Admin SDK로 구현. `damso.fcm.credentials`(Base64 JSON)로 `FirebaseApp`을 초기화하고, 고정 제목·본문과 데이터(문의 UUID·건수)만 보낸다. 등록되지 않은 토큰 응답은 `INVALID_TOKEN`, 그 밖의 오류는 `FAILED`. 오류는 예외 종류만 WARN으로 남기고 토큰·본문은 남기지 않는다. `FcmConfig`는 자격 증명이 있을 때만 빈을 만든다(테스트는 `FakePushSender`가 `@Primary`) (depends on T014, T002) (research R8)
- [x] T029 [P] [US2] `src/main/java/damso/inquiry/domain/GuidancePolicy.java` — 순수 규칙. `hotlinesFor(CrisisType)`: `SELF_HARM` → [109], `ABUSE` → [112], `OTHER_SAFETY` → [], `UNCLASSIFIED` → [112, 109]. `noticesFor(urgent, deferred)`: 일반 → [RECEIVED] (+ 지연이면 `DEFERRED_TO_AVAILABLE_HOURS`), 긴급 → [URGENT_DELIVERED]. 테스트 `src/test/java/damso/inquiry/domain/GuidancePolicyTest.java` (FR-016·017·019·028, 원칙 V)
- [x] T030 [US2] `src/main/java/damso/notification/application/port/in/NotifyInquiryUseCase.java` + `src/main/java/damso/notification/application/service/NotifyInquiryService.java` — `requestUrgent(inquiryId, teacherId)`: 같은 트랜잭션에서 `URGENT` 요청을 `scheduled_at = now`로 저장하고, 커밋 후 즉시 발송 이벤트를 발행한다. 일반 문의 알림(`requestGeneral`)은 US5에서 추가한다 (depends on T027)
- [x] T031 [US2] `src/main/java/damso/notification/application/service/NotificationDispatcher.java` — `dispatchDue()`: `claimDue(now, 60초, limit)`로 확보한 요청마다(FCM 호출은 **트랜잭션 밖**, 결과는 다시 짧은 트랜잭션으로 반영) 받는 사람을 정한다 — 긴급·재알림·일반은 `UrgentReminderPolicy.currentRecipient(inquiryId)`(보낼 때의 현재 담임, 없으면 재알림은 `CANCELLED`·나머지는 `PENDING` 유지), 묶음은 요청의 교사 — 그 사람의 **활성 기기 중 세션이 살아 있는 것**(F1 `SessionStatusChecker`)에 `PushSenderPort.send`. 전부 `SENT`면 `markSent`, `FAILED`가 있으면 `retryAfter`(1분·5분·15분, 그 뒤로는 1시간 간격으로 계속), 활성 기기가 없으면 `PENDING` 유지(FR-032). `INVALID_TOKEN`은 그 기기 `active=false`. 긴급을 `SENT`로 만든 뒤 `URGENT_REMINDER`(+1시간)를 만들고, 재알림을 보낼 때마다 다음 +1시간을 만든다. 재알림은 `inquiry`의 `UrgentReminderPolicy.shouldRemind(inquiryId, recipientId)`가 거짓이면 `CANCELLED`. `@Scheduled(fixedDelayString = "${damso.notification.dispatch-interval}")`는 안전망으로 `dispatchDue()`를 호출한다. `UrgentDispatchTrigger`(`@TransactionalEventListener(AFTER_COMMIT)` + `@Async`)는 긴급 요청 하나에 대해 `dispatchOne(requestId)`(`claimOne`으로 확보)를 즉시 호출하고, 실패해도 예외를 삼키며(요청은 DB에 남음) 로그에는 요청 UUID만 남긴다 (depends on T027, T028, T029) (research R7, FR-011~014)
- [x] T032 [P] [US2] `src/main/java/damso/inquiry/application/port/in/UrgentReminderPolicy.java` + `src/main/java/damso/inquiry/application/service/UrgentReminderService.java` — `currentRecipient(inquiryId)`: 문의 학급의 `InquiryTargetQuery.currentHomeroomTeacher`(활성 교사만, 없으면 `Optional.empty()`). `shouldRemind(inquiryId, recipientId)`: 문의 단계가 아직 `DELIVERED`이고, `recipientId`가 `currentRecipient`와 같을 때만 참. notification이 inquiry의 port.in으로만 묻는다 (depends on T013)
- [x] T033 [US2] `src/main/java/damso/inquiry/application/service/SubmitInquiryService.java` 확장: 긴급이면 **같은 트랜잭션**에서 `Inquiry.moveTo(DELIVERED)` + 이력 `DELIVERED` 1줄 + `NotifyInquiryUseCase.requestUrgent`, 응답의 `notices=[URGENT_DELIVERED]`, `hotlines=GuidancePolicy.hotlinesFor(UNCLASSIFIED)`. 긴급은 `reserveInquirySubmit`을 호출하지 않고(Redis를 거치지 않음), 알림 요청 저장 실패는 접수 전체를 막지 않도록 요청 저장은 문의와 같은 트랜잭션에 두되 **발송**은 커밋 후다 (depends on T021, T030, T029)

**Checkpoint**: T023·T024·T025 통과. 긴급 문의가 장애에도 교사 목록용 데이터와 알림 요청으로 남는다

---

## Phase 5: User Story 3 - 교사가 문의 목록에서 일반·긴급을 구분해 본다 (Priority: P1)

**Goal**: 교사 목록(긴급 우선)·상세(본문 없음)·긴급 원문 열람(감사 기록, 첫 열람에 `TEACHER_CONFIRMED`) (FR-007, FR-011, FR-024~026, FR-034)

**Independent Test**: 일반·긴급 문의를 제출한 뒤 교사 목록에서 구분·정렬을 확인하고, 긴급 원문을 열람해 감사 기록과 단계 변경을 확인한다

### Tests for User Story 3

- [x] T034 [P] [US3] `src/test/java/damso/inquiry/TeacherInquiryApiTest.java` — 목록: 긴급이 먼저, 그다음 최신순, 기본 `size=20`·최대 50(51이면 400), `page` 이동, 응답에 `title`(긴급 "긴급 문의", 일반 "정리 중")·`studentName`·`stage`·`submittedAt`, **본문·학부모 이메일·이름 없음** · 다른 학급 교사는 보이지 않음 · 담임이 해제·변경된 학급은 이전 교사에게 404/빈 목록 · 상세: 본문 없이 `timeline` · `GET …/content`: 긴급이면 200 + `content`, **일반이면 409 `INQUIRY_CONTENT_UNAVAILABLE`**, 남의 문의 404, 학부모·관리자 토큰 403 · 첫 열람에 `stage=TEACHER_CONFIRMED`·`inquiry_stage_history` 1줄 추가·감사 기록 1건, **두 번째 열람은 감사 기록만 늘고** 단계 이력은 그대로 · 열람 후 재알림이 가지 않음(발송기 실행 시 `URGENT_REMINDER` 요청이 `CANCELLED`) (US3 시나리오 1~4, FR-024~026, SC-005·010·013)
- [x] T035 [P] [US3] `src/test/java/damso/inquiry/ViewInquiryContentAtomicityTest.java` — `AuditLogPort.append`가 실패하는 상태에서 원문 열람 요청은 실패(500)하고 응답에 본문이 없으며 단계가 바뀌지 않고 이력이 늘지 않는다(감사 기록 없이는 원문이 나가지 않음) · **동시에** 두 번 첫 열람 → `TEACHER_CONFIRMED` 이력은 정확히 1줄(낙관적 락), 감사 기록은 2건 (원칙 III, FR-022·025)
- [x] T036 [P] [US3] `src/test/java/damso/audit/AuditLogAppendOnlyTest.java` — 리플렉션으로 `AuditLogRepository`에 `delete`·`update`로 시작하는 메서드·`@Modifying`이 없고 `Repository`만 상속, `AuditLogPort`에 `append`·`findByActor`·`findByTarget`만, `AuditLog`는 `@Immutable`·setter 없음·개인정보(이메일·이름·본문) 필드 없음 (원칙 III·VI, research R11)

### Implementation for User Story 3

- [x] T037 [P] [US3] `src/main/java/damso/audit/` — `domain/AuditLog.java`(`BaseEntity` 미상속, `@Immutable`, `Persistable`, 테이블 `audit_log`: `actor_id` UUID · `action` varchar(40) `INQUIRY_CONTENT_VIEWED` · `target_type` varchar(20) `INQUIRY` · `target_id` UUID · `occurred_at` timestamptz, setter 없음, 개인정보 컬럼 없음), `domain/AuditAction.java`, `application/port/in/AuditLogUseCase.java`(`record(actorId, action, targetType, targetId)`), `application/port/out/AuditLogPort.java`(`append`·조회만), `application/service/AuditLogService.java`(`Clock`으로 시각, 호출한 트랜잭션에 참여), `adapter/out/persistence/AuditLogRepository.java`(`Repository` 상속, `save`·조회만) + 어댑터. 조회 API는 두지 않는다(F5, 서버에서만 조회)
- [x] T038 [US3] `src/main/java/damso/inquiry/application/port/in/TeacherInquiryQuery.java` + `src/main/java/damso/inquiry/application/service/TeacherInquiryService.java` — `list(teacherId, page, size)`: `InquiryTargetQuery.homeroomClassroomOf(teacherId)`의 학급 문의를 **`urgent` 내림차순 → `submitted_at` 내림차순**으로(담임이 아니면 빈 목록), `size`는 1~50. `get(teacherId, inquiryId)`: 문의의 학급 `isCurrentHomeroomTeacher`가 아니면 `NOT_FOUND`, 본문 없이 `timeline`(`InquiryStageHistoryPort`)과 화면 제목(긴급 "긴급 문의", 제목 NULL이면 "정리 중")을 돌려준다. 학생 이름은 `InquiryTargetQuery.studentNames`로 (FR-033) (depends on T020, T013)
- [x] T039 [US3] `src/main/java/damso/inquiry/application/port/in/ViewInquiryContentUseCase.java` + `src/main/java/damso/inquiry/application/service/ViewInquiryContentService.java` — `view(teacherId, inquiryId)`: ① 현재 담임이 아니면 `NOT_FOUND` ② 긴급이 아니면 `INQUIRY_CONTENT_UNAVAILABLE`(409) ③ **한 트랜잭션에서** `AuditLogUseCase.record(teacherId, INQUIRY_CONTENT_VIEWED, INQUIRY, inquiryId)` → 단계가 `DELIVERED`이면 `moveTo(TEACHER_CONFIRMED)` + 이력 1줄(낙관적 락 충돌은 이미 확인된 것으로 보고 열람은 계속) → 본문 `reveal()` 반환. 감사 기록이 실패하면 전체가 롤백되어 본문이 나가지 않는다. 본문을 로그에 남기지 않는다 (depends on T037, T020, T013) (FR-025, 원칙 III)
- [x] T040 [US3] `src/main/java/damso/inquiry/adapter/in/controller/TeacherInquiryController.java` + DTO — `GET /api/v1/teacher/inquiries`(`page` 기본 0·`size` 기본 20 최대 50, 범위 밖 400), `GET /api/v1/teacher/inquiries/{inquiryId}`, `GET /api/v1/teacher/inquiries/{inquiryId}/content`. 응답 DTO `dto/response/TeacherInquiryPageResponseDTO`·`TeacherInquirySummaryResponseDTO`·`TeacherInquiryDetailResponseDTO`·`InquiryContentResponseDTO`(`InquiryContentResponseDTO.toString`은 본문을 가림), 단계 이벤트는 공용 `dto/response/StageEventResponseDTO`. 학부모 이메일·이름 필드는 어떤 DTO에도 없다. 경로 역할 검사는 F1 `SecurityConfig`의 `/api/v1/teacher/**` (depends on T038, T039)

**Checkpoint**: T034·T035·T036 통과. 여기까지가 P1(시연 가능한 MVP)

---

## Phase 6: User Story 4 - 학부모가 처리 단계를 확인한다 (Priority: P2)

**Goal**: 학부모 문의 목록·상세, 시간순 타임라인, 단계 전이 규칙 (FR-021~023)

**Independent Test**: 문의를 제출한 학부모가 상세에서 접수 단계를 보고, 긴급 문의는 전달·확인 단계까지 순서대로 보이는지 확인한다

### Tests for User Story 4

- [x] T041 [P] [US4] `src/test/java/damso/inquiry/domain/InquiryStageTest.java` — 허용 전이표: 일반 `RECEIVED→REFINING→TEACHER_CONFIRMED→ANSWERED`, 긴급 `RECEIVED→DELIVERED→TEACHER_CONFIRMED→ANSWERED`만 허용, 일반이 `DELIVERED`로 가거나 건너뛰는 전이·거꾸로 가는 전이·같은 단계로의 전이는 거부 · 같은 문의 두 엔티티가 같은 버전에서 동시에 전이하면 한 번만 성공(`@Version`, 통합 테스트) (FR-021·022, 원칙 백엔드 요건)
- [x] T042 [P] [US4] `src/test/java/damso/inquiry/ParentInquiryQueryApiTest.java` — 목록: 본인이 작성한 문의만 최신순(자녀 이름 포함), 상세: 본인의 원문·`timeline`(일반은 `RECEIVED`, 긴급은 `RECEIVED`→`DELIVERED`, 교사가 원문을 열람한 뒤에는 `TEACHER_CONFIRMED`까지 시각과 함께 순서대로)·`notices`·`hotlines` · 다른 학부모의 문의 404 · 교사·관리자 토큰 403 · 일반 문의의 `hotlines`는 빈 배열 · 응답에 교사 이메일 없음 · 문의 작성 후 학부모가 탈퇴해도(다른 학부모는 볼 수 없음) 원문은 지워지지 않음을 DB로 확인 (US4 시나리오 1~4, FR-023, SC-005)

### Implementation for User Story 4

- [x] T043 [US4] `src/main/java/damso/inquiry/application/port/in/ParentInquiryQuery.java` + `src/main/java/damso/inquiry/application/service/ParentInquiryService.java` — `list(parentId)`·`get(parentId, inquiryId)`: 본인이 작성한 문의가 아니면 `NOT_FOUND`(존재 여부를 드러내지 않음). 상세는 `content.reveal()`, `InquiryStageHistoryPort`의 시간순 이력, `GuidancePolicy`로 안내·번호(긴급이면 문의의 `crisis_type` 기준, 지연 문의는 `DEFERRED_TO_AVAILABLE_HOURS` 포함). 학생 이름은 `InquiryTargetQuery.studentNames` (depends on T020, T029, T013)
- [x] T044 [US4] `src/main/java/damso/inquiry/adapter/in/controller/ParentInquiryController.java`에 `GET /api/v1/parent/inquiries`(목록), `GET /api/v1/parent/inquiries/{inquiryId}`(상세) 추가 + 응답 DTO `dto/response/ParentInquirySummaryResponseDTO`·`ParentInquiryDetailResponseDTO`(`toString`에서 본문 가림) (depends on T043, T040)

**Checkpoint**: T041·T042 통과

---

## Phase 7: User Story 5 - 응답 가능 시간 밖에 보낸 일반 문의는 알림만 늦춘다 (Priority: P2)

**Goal**: 응답 가능 시간 판정, 일반 알림 즉시/지연/끔, 묶음 알림 (FR-027~030, FR-014)

**Independent Test**: 응답 가능 시간 안팎에서 일반 문의를 제출하고 알림 시각·묶음 건수·학부모 안내 문구를 확인한다

### Tests for User Story 5

- [x] T045 [P] [US5] `src/test/java/damso/notification/domain/AvailabilityCalendarTest.java` — 설정이 없으면 항상 응답 가능 · 요일별 구간 안(시작 포함)·밖(종료 미포함), 시작 정각은 안, 종료 정각은 밖 · 오늘 구간이 지났거나 없는 요일이면 7일 안에서 가장 가까운 다음 시작 시각(Asia/Seoul) · 다음 시작이 7일 안에 없으면(구간이 하나도 없는데 설정만 있는 경우 제외) 항상 응답 가능으로 처리 (FR-027·029, Edge Case)
- [x] T046 [P] [US5] `src/test/java/damso/notification/GeneralNotificationTest.java` — 응답 가능 시간 안에서 일반 문의 → `GENERAL` 알림이 즉시, `notification_deferred=false`, 응답 `notices=[RECEIVED]` · **밖**에서 제출 → 접수되고 `notification_deferred=true`, 응답 `notices=[RECEIVED, DEFERRED_TO_AVAILABLE_HOURS]`, 즉시 알림 없음, 교사별로 대기 중 `GENERAL_DIGEST` **하나**가 다음 시작 시각에 잡힘 · 밖에서 3건 → 시작 시각이 되면 발송기가 **묶음 알림 1건**(건수 3, 고정 문구 + 건수), 문의들의 `deferred_notified_at` 기록, 이후 같은 문의로 재발송 없음 · 쌓인 문의가 0건이면(예: 그사이 사라짐) 알림 없이 `SKIPPED` · 묶음이 잡힌 뒤 교사가 일반 알림을 끄면 시작 시각에 `SKIPPED` · 교사가 일반 알림을 끈 상태 → 일반·묶음 요청을 만들지 않고 문의는 목록에 남음 · 설정이 없는 교사는 항상 응답 가능·일반 알림 켜짐 · 설정을 바꾸면 **이후** 문의부터 적용 · **긴급 문의는 어떤 설정에도 지연·차단되지 않음** · 학부모가 발송 시각을 고르는 파라미터가 없음 (US5 시나리오 1~6, FR-027~030, SC-007·011)

### Implementation for User Story 5

- [x] T047 [P] [US5] `src/main/java/damso/notification/domain/` — `TeacherNotificationSetting.java`(`BaseEntity` 상속, 테이블 `teacher_notification_setting`: `teacher_id` UUID **유니크** · `general_enabled` boolean 기본 true), `TeacherAvailability.java`(`BaseEntity` 상속, 테이블 `teacher_availability`: `teacher_id` UUID · `day_of_week` varchar(10) `MONDAY`~`SUNDAY` · `start_time` time · `end_time` time "`start_time < end_time`, 한국 시간, 시작 포함·종료 미포함", **유니크 `(teacher_id, day_of_week)`**), `AvailabilityCalendar.java`(순수 규칙: `isAvailable(windows, Instant)`·`nextStart(windows, Instant)`, Asia/Seoul) + `src/main/java/damso/notification/application/port/out/NotificationSettingPort.java`와 persistence 어댑터. 설정 행이 없으면 일반 알림 켜짐 + 항상 응답 가능
- [x] T048 [US5] `src/main/java/damso/notification/application/service/NotifyInquiryService.java`에 `requestGeneral(inquiryId, teacherId)` 추가: 일반 알림이 꺼져 있으면 아무것도 만들지 않고 `NotificationDecision.OFF`, 응답 가능 시간 안이면 `GENERAL` 요청(`scheduled_at = now`)과 `IMMEDIATE`, 밖이면 `DEFERRED`를 돌려주고 그 교사의 대기 중 `GENERAL_DIGEST`가 없으면 다음 시작 시각으로 만든다(`active_digest_key` 유니크 충돌은 이미 있는 것으로 보고 그대로 둠). `NotifyInquiryUseCase`에 반영 (depends on T047, T030)
- [x] T049 [US5] `src/main/java/damso/inquiry/application/port/in/DeferredInquiryCounter.java` + 서비스 — `claimDeferred(teacherId, Instant now)`: 그 교사 앞으로 `notification_deferred = true ∧ deferred_notified_at IS NULL`인 문의의 수를 세고 `deferred_notified_at`을 `now`로 표시해 돌려준다(한 트랜잭션, 같은 문의를 두 번 세지 않음). `NotificationDispatcher`는 `GENERAL_DIGEST`를 보낼 때 먼저 교사의 일반 알림 설정을 다시 확인해 꺼져 있으면 세지 않고 `SKIP`, 켜져 있으면 이 수를 `digest_count`로 쓰고 0이면 `SKIP`. 일반 문의 제출(`SubmitInquiryService`)은 일반 알림 결정에 따라 `notification_deferred`를 저장하고 응답 안내에 `DEFERRED_TO_AVAILABLE_HOURS`를 더한다 (depends on T048, T033, T031)

**Checkpoint**: T045·T046 통과

---

## Phase 8: User Story 6 - 긴급 문의의 학부모 안내가 위기 종류에 맞는 번호만 보여 준다 (Priority: P2)

**Goal**: 위기 종류 분류 결과를 받는 연결 지점과 번호 선택, 허용값 검증 (FR-017~020)

**Independent Test**: 분류 결과를 직접 넣어 종류별 번호가 맞게 보이는지, 결과가 없거나 잘못되면 기본 안내가 보이는지 확인한다

### Tests for User Story 6

- [x] T050 [P] [US6] `src/test/java/damso/inquiry/CrisisClassificationTest.java` — `CrisisClassificationUseCase.apply(inquiryId, "SELF_HARM")` 후 학부모 상세 `hotlines=[SUICIDE_PREVENTION_109]` · `ABUSE` → `[CHILD_ABUSE_112]` · `OTHER_SAFETY` → `[]`(번호 없이 전달 안내만) · 분류하지 않은 긴급(기본 `UNCLASSIFIED`)과 `"BOGUS"`·소문자·빈 문자열·null은 값을 버리고 `[CHILD_ABUSE_112, SUICIDE_PREVENTION_109]` · 일반 문의에는 적용하지 않고 무시(문의 변경 없음) · **이미 분류된 문의(`UNCLASSIFIED`가 아님)에 다른 값을 다시 적용해도 첫 값이 유지된다** · 학부모 응답의 `notices`·`hotlines`가 `NoticeCode`·`HotlineCode` 값뿐, 어떤 응답에도 자유 문장 필드가 없음 · `CrisisClassificationService`가 외부 호출 포트(`PushSenderPort` 등)나 AI 클라이언트를 주입받지 않음(리플렉션으로 생성자 파라미터 확인) (US6 시나리오 1~6, FR-017~020, SC-009)

### Implementation for User Story 6

- [x] T051 [US6] `src/main/java/damso/inquiry/application/port/in/CrisisClassificationUseCase.java` + `src/main/java/damso/inquiry/application/service/CrisisClassificationService.java` — `apply(UUID inquiryId, String rawType)`: 허용값(`SELF_HARM`·`ABUSE`·`OTHER_SAFETY`) 정확 일치만 받고 그 밖은 `UNCLASSIFIED`로 둔다. 긴급 문의에만 적용하며 일반 문의는 무시한다. 이미 분류된(`UNCLASSIFIED`가 아닌) 문의는 첫 값을 유지하고 무시한다. `Inquiry`에 `applyCrisisType(CrisisType)`을 추가하고 `crisis_type`만 바뀐다(원문·긴급 표시 불변). F2에서는 호출하는 곳이 없고 F3가 가명화 게이트웨이를 거친 결과로 호출한다. 결과는 어디에도 문장으로 저장·전달하지 않는다 (research R3, 백엔드 요건 "LLM 출력 검증·격리") (depends on T019, T029)

**Checkpoint**: T050 통과

---

## Phase 9: User Story 7 - 교사가 응답 가능 시간과 알림을 설정하고 기기를 등록한다 (Priority: P2)

**Goal**: 교사 알림 설정 API, 기기 등록·해제, 세션과 연동 (FR-031·032, FR-013)

**Independent Test**: 응답 가능 시간을 저장하고 그 안팎에서 알림 시각이 달라지는지, 기기 등록 후 로그아웃하면 그 기기로 알림이 가지 않는지 확인한다

### Tests for User Story 7

- [x] T052 [P] [US7] `src/test/java/damso/notification/NotificationSettingsApiTest.java` — 설정 전 `GET` → `generalEnabled=true`·`availability=[]` · `PUT`으로 요일별 구간 저장(요일당 한 구간) 후 `GET`이 같은 값 · `start`가 `end`보다 늦거나 같음 400 · 같은 요일 두 구간 400 · `"25:00"`·형식 오류 400 · 구간 8개 이상 400 · 빈 `availability` = 항상 응답 가능 · `generalEnabled=false` 저장 · 응답·요청에 긴급 알림 항목이 없음(스키마 키 확인) · 학부모·관리자 토큰 403, 무인증 401 · 알 수 없는 필드 400 · **저장 직후** 제출한 문의부터 새 시간 적용(SC-011), 이미 잡힌 묶음 알림 시각은 바뀌지 않음 (US7 시나리오 1~3·6~7, FR-031, SC-011)
- [x] T053 [P] [US7] `src/test/java/damso/notification/DeviceRegistrationApiTest.java` — `PUT /api/v1/me/devices` 204, 같은 토큰 다시 오면 갱신(행 1개) · 같은 사용자가 여러 기기 등록 가능 · 다른 사용자가 같은 토큰을 등록하면 기존 등록이 그 사용자·세션으로 옮겨짐 · **그 세션에서 로그아웃 → 긴급 문의 알림 0건**(SC-012) · 비밀번호 재설정·탈퇴로 세션이 끝난 기기 0건 · 다른 기기(다른 세션)는 계속 받음 · `DELETE /api/v1/me/devices/current` 204(등록 없어도 204), 이후 그 기기로 알림 없음 · 기기가 없을 때 만들어진 `PENDING` 긴급 요청이 기기 등록 후 첫 발송기 실행에서 나감 · `platform`·`token` 검증(빈 토큰·512자 초과·알 수 없는 플랫폼 400) · 응답·로그에 토큰이 남지 않음 · 교사·학부모 모두 등록 가능(학부모의 알림 사용은 F4) (US7 시나리오 4~5, FR-032, SC-012)

### Implementation for User Story 7

- [x] T054 [US7] `src/main/java/damso/notification/application/port/in/NotificationSettingsUseCase.java` + `src/main/java/damso/notification/application/service/NotificationSettingsService.java` — `get(teacherId)`·`save(teacherId, settings)`: `start < end`·요일당 한 구간·최대 7개를 검증(위반은 `InvalidInputException`, 사유는 고정 문구), 한 트랜잭션에서 `teacher_notification_setting`을 만들거나 바꾸고 `teacher_availability`를 통째로 교체한다. 이미 잡힌 `GENERAL_DIGEST`의 `scheduled_at`은 건드리지 않는다 (depends on T047)
- [x] T055 [P] [US7] `src/main/java/damso/notification/application/port/in/DeviceRegistrationUseCase.java` + `src/main/java/damso/notification/application/service/DeviceRegistrationService.java` — `register(userId, sessionId, token, platform)`(같은 토큰이면 사용자·세션을 갱신하고 `active=true`), `unregisterCurrent(userId, sessionId)`. 로그아웃 시 별도 처리는 두지 않는다: 발송기가 세션 활성 여부(`SessionStatusChecker`)로 거른다 (research R9) (depends on T027)
- [x] T056 [US7] `src/main/java/damso/notification/adapter/in/controller/NotificationSettingsController.java` + DTO — `GET`·`PUT /api/v1/teacher/notification-settings`. 요청·응답 DTO `dto/request/NotificationSettingsRequestDTO`(`generalEnabled` `@NotNull`, `availability` `@Size(max = 7)`, 각 구간 `dayOfWeek`·`start`(`HH:mm`)·`end`(`HH:mm` 또는 `24:00`)), 응답 DTO `dto/response/NotificationSettingsResponseDTO`. 경로 역할 검사는 F1 `SecurityConfig`의 `/api/v1/teacher/**` (depends on T054)
- [x] T057 [US7] `src/main/java/damso/notification/adapter/in/controller/DeviceController.java` + DTO — `PUT /api/v1/me/devices`(204), `DELETE /api/v1/me/devices/current`(204). 요청 DTO `dto/request/RegisterDeviceRequestDTO`(`token` `@NotBlank @Size(max = 512)`, `platform` `ANDROID`·`IOS`, `toString`에서 토큰 가림). 세션은 `CurrentUser.sessionId()`에서만 (depends on T055)

**Checkpoint**: T052·T053 통과. 모든 스토리 완료

---

## Phase 10: Polish & Cross-Cutting Concerns

- [x] T058 [P] `src/test/java/damso/inquiry/InquirySensitiveLoggingTest.java` — 문의 제출(일반·긴급)·열람·알림 발송·기기 등록·실패 경로(1분 제한 거부, 푸시 실패, 감사 실패) 동안 로그를 캡처해 문의 본문·제목 문자열, FCM 토큰, 학생 이름이 0건인지 확인 (SC-006, 원칙 VI). F1 `AuthSensitiveLoggingTest` 방식을 따른다 (FR-008)
- [x] T059 [P] `src/test/java/damso/inquiry/InquiryExposureTest.java` — 학부모·교사 응답 전체에서 다른 사용자의 이메일 문자열·연락처 필드가 0건, 학부모 안내 필드가 `NoticeCode`·`HotlineCode` 값뿐 · 다른 학급 교사·다른 학부모가 상대의 문의 존재를 알 수 있는 경로 없음(404만) (SC-005·009, FR-026)
- [x] T060 [P] `src/test/java/damso/inquiry/SuperAdminInquiryAccessTest.java` — 슈퍼 관리자 토큰으로 `/api/v1/parent/inquiries/**`·`/api/v1/teacher/inquiries/**` 모두 403이고, 관리자 경로(`/api/v1/admin/**`)에는 문의 내용·제목·처리 내용을 돌려주는 API가 없다(응답 스키마 점검). F1 tasks 메모에서 "F2에서 테스트로 넣는다"고 남긴 FR-004 검증이다 (FR-007, SC-005)
- [x] T061 [P] `docs/security-checklist.md` 갱신 — 보안기능 2(문의 건별 소유권: F1에서 "F2에서 재검증"으로 남긴 부분)·보안기능 5(문의 본문·제목 암호화)·에러처리 2(긴급 경로 fail-open, 알림 아웃박스 재시도)·캡슐화 2(본문 로그 0건)·시간 및 상태 1(문의 단계 `@Version`, 알림 `SKIP LOCKED`, 중복 제출 유니크)의 상태를 `조치`로 바꾸고 근거(클래스·테스트 이름) 기재 (quickstart.md 3)
- [x] T062 [P] `docs/features.md` 상태 표 F2 행을 `구현 완료`로 갱신
- [x] T063 `quickstart.md` 2절 수동 확인을 실행하고 결과를 이 파일 하단 "구현 메모"에 적는다: PostgreSQL·Redis·Mailpit 컨테이너와 로컬 서버로 학급·교사·학부모 가입 → 일반·긴급 문의 → 교사 목록·원문 열람 → 응답 가능 시간 설정·지연 → 기기 등록·로그아웃. **실제 FCM 알림은 팀의 Firebase 테스트 프로젝트 자격 증명이 있을 때만 확인**하고, 없으면 "FCM 실기기 확인 못 함"이라고 적는다
- [x] T064 `./gradlew test` 전체 실행 — 실패가 있으면 숨기지 말고 테스트 이름과 함께 보고한다
- [x] T065 이 파일 하단에 "구현 메모" 절을 만들어 계획과 달라진 점(예: firebase-admin 버전, 추가 클래스, 긴급 표현 목록 리뷰 결과)을 F1 tasks.md와 같은 형식으로 기록한다

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: 바로 시작. T006·T007는 구현 전에 끝낸다
- **Foundational (Phase 2)**: Setup 이후. **모든 스토리를 막는다**
- **US1 (Phase 3)**: Phase 2 이후 독립
- **US2 (Phase 4)**: US1의 제출 서비스(T021)에 긴급 경로를 더하므로 US1 다음
- **US3 (Phase 5)**: US2의 긴급 문의·단계 이력 위에서 열람한다. 테스트 데이터로 긴급 문의가 필요
- **US4 (Phase 6)**: US1 이후 독립. 타임라인 검증은 US2·US3의 단계 전이를 쓴다
- **US5 (Phase 7)**: US2의 알림 요청·발송기 위에 일반 알림을 더한다
- **US6 (Phase 8)**: US2의 `GuidancePolicy`·긴급 문의 위에서 독립
- **US7 (Phase 9)**: US2의 기기·알림 구조와 US5의 설정 엔티티 위에 API를 더한다
- **Polish (Phase 10)**: 원하는 스토리 완료 후

### User Story Dependencies

```
Phase 2 ── US1 ── US2 ─┬─ US3 ── US4
                       ├─ US5 ── US7
                       └─ US6
```

### Within Each User Story

- 테스트를 먼저 쓰고 실패를 확인한 뒤 구현한다
- 엔티티 → 포트·어댑터 → 서비스 → 컨트롤러 순
- 스토리가 끝날 때마다 Checkpoint 테스트를 돌리고 커밋한다(`Feat : …`, git-conventions)

### Parallel Opportunities

- Phase 2: T008·T009·T010·T011·T014는 서로 다른 파일이라 병렬 가능
- 각 스토리의 테스트 작업([P])은 서로 병렬 가능
- US2: T026·T028·T029·T032는 병렬 가능
- US3의 T037은 US3 안의 다른 구현과 병렬 가능

---

## Parallel Example: Phase 2

```text
개발자 A: T008, T009, T010
개발자 B: T011, T012
개발자 C: T013, T014, T015
```

> constitution 개발 흐름상 **한 기능은 한 사람이 끝까지** 맡는 것이 원칙이므로 F2는 한 명이 순서대로 진행하는 것을 기본으로 한다. 위 예시는 일정이 급할 때의 선택지다.

---

## Implementation Strategy

### MVP First (P1: US1~US3)

1. Phase 1 → Phase 2 (F1 테스트가 모두 통과하는지 확인)
2. US1 → US2 → US3 (P1 세 개)
3. **멈추고 확인**: 긴급 문의가 장애 중에도 접수·전달되고 교사가 원문을 열람해 단계가 바뀌는지, quickstart 2절 1~5단계 수동 확인
4. 이 시점에 F3(가명화·정제) plan을 시작해도 된다 — 접수·긴급 경로·알림·감사 뼈대·위기 종류 연결 지점이 준비됨

### Incremental Delivery

1. P1 완료 → 시연 가능(문의 제출 → 긴급 즉시 전달 → 교사 확인)
2. US4(학부모 타임라인)·US5(응답 가능 시간 지연)·US6(위기 종류 안내)·US7(교사 설정·기기 등록) (P2)
3. Phase 10 → 로그·노출 점검, 체크리스트 갱신 (API 명세 통합·공지는 Phase 1에서 이미 끝남)

---

## Notes

- [P] = 다른 파일, 미완료 작업 의존 없음
- **테스트 실행 전 Docker를 켠다.** PostgreSQL·Redis 컨테이너를 쓴다
- 시간 의존 테스트는 `MutableClock`으로 시간을 앞당기고, 발송기는 테스트에서 직접 호출한다(Redis TTL·`@Scheduled` 실제 시간에 기대지 않는다)
- 긴급 경로(`SubmitInquiryService`의 긴급 분기)는 Redis·FCM·외부 AI에 의존하는 코드를 호출하지 않는다. 리뷰할 때 이 분기에 새 호출이 들어오지 않는지 본다
- 본문·제목·FCM 토큰·학생 이름을 로그·예외 메시지에 넣지 않는다
- **긴급 표현 목록은 T011 시작 전에 팀이 정한다.** 놓치는 표현이 없는지가 안전과 직결된다. 리뷰한 사람·날짜는 PR 설명에 남긴다
- AI가 만든 코드도 설명할 수 있을 때까지 읽는다 (대회 진단·발표 대비)
- F3에서 할 일(이 spec의 범위 밖): 일반 문의 정제·번역본·원문 열람, 제목 자동 생성, 위기 종류 분류 호출(`CrisisClassificationUseCase`)

---

## 구현 메모 (2026-10-18)

계획과 달라졌거나 구현 중 추가된 것:

- **T007 미완료**: 프론트엔드 공지는 사람이 해야 하는 작업이라 하지 않았다. 담당자에게 `docs/api/openapi.yaml` 0.3.0(경로 8개·오퍼레이션 10개, 에러 코드 2개, `Idempotency-Key` 필수 헤더, 같은 키 다른 내용이면 409, 본문 길이는 코드 포인트)을 알리고 날짜·채널을 PR 설명에 적는다. `api-v0.3` 태그는 PR이 main에 merge된 뒤 `/api-release api-v0.3`으로 찍는다.
- **긴급 표현 목록은 초안이다(T011)**: 팀이 정한 목록이 없어서 사용자 선택에 따라 초안을 직접 작성했다(`src/main/resources/urgent/expressions.txt`, 대표 문장 `src/test/resources/urgent/must-be-urgent.txt`). **팀이 리뷰해 확정해야 하고, 리뷰한 사람·날짜를 PR 설명에 적는다.** 목록이 비거나 파일을 못 읽으면 기동을 거부하는 것은 구현·테스트되어 있다.
- **커밋·푸시 안 함**: CLAUDE.md 규칙(요청이 있을 때만 커밋)에 따라 모든 변경은 작업 트리에 있다. 커밋은 `git-conventions` 규칙으로 따로 요청한다.
- **firebase-admin 9.11.0**을 썼다(2026-10-18 Maven Central 최신 안정 버전). `FcmPushSender`는 `FirebaseApp`을 처음 보낼 때 만든다 — 기동이 Google 연결·자격 증명 파싱에 묶이지 않는다. 자격 증명 형식(Base64 → JSON, `type=service_account`)은 `SecretsValidator`가 기동 때 본다.
- **접수 순서를 한 가지 바꿨다(T021)**: 문서의 ①검증 ②대상 확인 ③중복 확인을 ①검증 ②**중복 확인** ③대상 확인으로 했다. 접수된 뒤 담임이 해제되어도 같은 키의 재시도가 `NO_HOMEROOM_TEACHER`가 아니라 처음 결과를 돌려받게 하려는 것이다. 재전송 응답은 **접수 시점의 단계**(긴급 `DELIVERED`, 일반 `RECEIVED`)를 돌려준다. 같은 키가 동시에 와서 제한에 먼저 걸린 요청도 429 대신 이미 접수된 문의의 결과를 돌려준다. `submittedAt`은 마이크로초로 맞춰 저장해 첫 응답과 재전송 응답이 같다.
- **`AttemptLimitPort`**: `reserveRate(kind, subjectHash, now, minGap, window, max)`를 추가하고 `reserveMailSend`는 이를 부르는 `default` 메서드로 남겼다(기존 호출·테스트 불변). 스크립트는 `mail_send.lua` → `rate_limit.lua`로 이름을 바꿨다. 문의 제출 간격은 `damso.inquiry.submit-interval`(기본 60초)을 따른다. 저장소 장애는 `SubmitInquiryService`가 `INTERNAL_ERROR`를 잡아 제한만 건너뛴다(WARN, 종류만).
- **알림 발송기 구조(T031)**: `claimDue`·`claimOne`은 `REQUIRES_NEW` 짧은 트랜잭션으로 `SENDING`·`locked_until`(60초)을 표시하고 커밋한다. 발송기의 결과 반영 트랜잭션도 `REQUIRES_NEW`라 커밋 직후 콜백에서 불려도 끝난 트랜잭션에 얹히지 않는다. `@Scheduled`는 `initialDelay`도 같은 주기로 둬서 기동 직후에 돌지 않는다.
- **묶음 알림 보강(T049)**: 묶음 건수는 처음 세는 순간 요청의 `digest_count`에 기록하고, 전송이 실패해 다시 시도할 때는 이미 표시된 문의를 다시 세지 않고 기록한 건수를 쓴다. 묶음을 보낸 뒤 그사이 새로 쌓인 문의가 있으면 다음 시작 시각에 새 묶음을 잡는다(`scheduleFollowUpDigest`). 대기 중 묶음 생성은 `INSERT … ON CONFLICT DO NOTHING`이라 동시에 두 문의가 들어와도 접수 트랜잭션이 깨지지 않는다.
- **원문 열람의 첫 확인 전이(T039)**: `InquiryConfirmationStep`(`REQUIRES_NEW`)을 따로 두었다. 동시에 처음 열람할 때 낙관적 락 충돌이 바깥 트랜잭션(감사 기록)을 롤백으로 표시하지 않게 하려는 것이다. 감사 기록이 실패하면 단계 전이도 본문도 나가지 않는다.
- **하루 끝(24:00) 표현**: `AvailabilityWindow.END_OF_DAY`는 `23:59:59` 표식 값이다. `time` 컬럼에 마이크로초 값이 안정적으로 왕복하지 않아 처음 쓴 `23:59:59.999999`를 바꿨다. API는 `24:00`으로 주고받는다.
- **추가한 클래스**: `StageEventView`(학부모·교사 타임라인 공용), `AuditTargetType`, `FcmPayload`(SDK 없이 나가는 내용을 검사), `UrgentNotificationRequested`(이벤트), `InquiryConfirmationStep`. `GuidancePolicy`(T029)는 US1의 응답 조립에도 필요해 US1에서 먼저 만들었다.
- **테스트 구성**: `FcmMessageMappingTest`는 package-private인 `FcmPayload`를 보려고 `damso.notification.adapter.out.fcm` 패키지에 두었고, T041의 동시 전이 통합 테스트는 `InquiryStageConcurrencyTest`로 분리했다. 통합 테스트는 `@Async`를 동기 실행기로 바꿔(커밋 직후 즉시 발송이 응답 전에 끝남) `damso.notification.dispatch-interval=1h`로 `@Scheduled`가 저절로 돌지 않게 한다. 운영 구조(커밋 후·요청 스레드 밖·10초 안전망)는 `UrgentDispatchTriggerStructureTest`가 어노테이션으로 확인한다. `MutableClock`은 실제 시간에 오프셋을 더한 것이라 시각 비교는 몇 초 오차를 허용하고, 로그인 세션이 14일이라 시계를 크게 옮기면 401이 나므로 응답 가능 시간 테스트는 "지금 기준 오늘·내일·모레 요일"로 구간을 만든다.
- **첫 기동의 Hibernate WARN**: F1과 같이 `ddl-auto=update`가 처음 테이블을 만들 때 "constraint … does not exist, skipping" 경고가 나온다(무해).
- **T063 수동 확인(2026-10-18)**: PostgreSQL·Redis·Mailpit 컨테이너와 로컬 서버(포트 18080, 슈퍼 관리자는 환경변수로 생성, JDK 21)로 quickstart 2절 1~7단계 흐름을 스크립트로 실행해 **28/28 통과**했다. 학급·교사·학부모 가입 → 기기 등록 → 일반·긴급 문의(긴급 응답 3초 이내, `URGENT_DELIVERED`와 기본 번호 둘) → 교사 목록(긴급 맨 위, 본문 없음)·긴급 원문 열람(감사 기록 1건, `TEACHER_CONFIRMED`)·일반 원문 409 → 학부모 타임라인 → 1분 제한 429 + `Retry-After` → 응답 가능 시간 밖 문의의 지연 안내와 묶음 알림 대기 1건 → 교사 로그아웃 뒤 긴급 문의에서 알림 요청이 보낼 곳 없이 `PENDING(attempts=0)`로 남음. 서버 로그에는 본문·FCM 토큰·이메일·학생 이름·비밀번호가 없었고, 실제 스레드(`task-N` 커밋 직후 발송, `scheduling-1` 10초 안전망)에서 FCM 호출이 일어나 가짜 자격 증명이라 `FCM 발송 오류 (type=IOException)`만 남기고 재시도 대기로 넘어가는 것을 확인했다. **FCM 실기기 확인 못 함**: 팀의 Firebase 테스트 프로젝트 자격 증명이 없어 실제 알림 수신은 확인하지 못했다. 확인 후 서버와 컨테이너는 정리했다.
- **T064 전체 테스트**: `./gradlew test` — **409개 테스트 모두 통과**(실패·건너뜀 0). F1 246개 + F2 163개.
- **docs 갱신**: `docs/security-checklist.md`(보안기능 2·5, 시간 및 상태 1, 에러처리 2, 캡슐화 2), `docs/features.md`(F2 `구현 완료`). `docs/api/openapi.yaml`은 0.3.0으로 통합했고 `npx @redocly/cli@1.34.5 lint`가 valid다(경고는 기존 `no-unused-components`).
