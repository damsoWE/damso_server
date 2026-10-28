# Research: 답변 작성·점검·전송 (F4)

**Feature**: [spec.md](./spec.md) · **Plan**: [plan.md](./plan.md)

## R1. 스레드를 어떻게 모델링하나

- **Decision**: 새 `inquiry_thread` 테이블을 만들지 않는다. 기존 `inquiry`에 `thread_root_id`(UUID, NULL 허용) 컬럼만 추가한다. 최초 문의는 `thread_root_id = NULL`(자기 자신이 루트), 재문의는 `thread_root_id = 최초 문의.id`로 저장한다. 스레드 조회는 "`id = :rootId` OR `thread_root_id = :rootId`"로 한다.
- **Rationale**: 재문의는 FR-015·016에 따라 최초 문의와 완전히 같은 파이프라인(멱등키·1분 제한·긴급 판정·가명화·정제)을 거친다. 별도 테이블을 만들면 그 파이프라인을 통째로 복제해야 한다. 같은 테이블에 두면 F2가 이미 만든 `SubmitInquiryService`·`InquiryStage`·`UrgentRule`을 그대로 재사용할 수 있다.
- **Alternatives considered**: (a) 별도 `inquiry_thread`/`thread_message` 테이블 — 파이프라인 중복, 기각. (b) `Answer`와 재문의를 하나의 `thread_message` 폴리모픽 테이블로 통합 — 답변은 분류·정제 대상이 아니라서 필드가 대부분 NULL이 되고 F2 코드를 재사용할 수 없어 기각.

## R2. "답변 완료"(ANSWERED) 전이는 누가, 언제 하나

- **Decision**: 스레드의 **루트** `Inquiry`만 `ANSWERED`로 전이한다. 재문의(follow-up) `Inquiry` row는 자신의 `TEACHER_CONFIRMED`에서 멈추고 개별적으로 `ANSWERED`가 되지 않는다. "스레드가 열려 있는지"는 `root.stage != ANSWERED`로 판단한다(FR-020·021).
- **Rationale**: F2의 `data-model.md`는 이미 `TEACHER_CONFIRMED → ANSWERED` 전이를 "F4 몫"이라고 적어 두었다. F4의 클래리파이 결정("답변 완료"는 교사의 명시적 동작, 답변을 보낸다고 자동 전이되지 않음)과 정확히 들어맞으므로 `InquiryStage` enum을 바꿀 필요가 없다.
- **Alternatives considered**: 재문의마다 독립적으로 `ANSWERED`까지 전이 — 스레드 전체의 "열림/닫힘"과 개별 메시지 상태가 섞여 FR-021(종료 후 재문의 거부)을 판단하기 어려워져 기각.

## R3. 일반(비긴급) 문의의 "답변 완료"는 지금 끝까지 테스트할 수 있나

- **Decision**: 아니다. 알려진 범위 제한으로 문서화한다(plan.md Constitution Check 참고). 답변 **작성·전송**(FR-001~007)은 `Inquiry.stage`를 보지 않으므로 일반 문의에서도 바로 동작한다. 다만 **종료**(FR-020)는 기존 enum 규칙상 `TEACHER_CONFIRMED`에서만 `ANSWERED`로 갈 수 있고, 일반 문의가 `TEACHER_CONFIRMED`에 닿는 전이(`REFINING → TEACHER_CONFIRMED`)는 F2 설계상 F3 몫이라 아직 코드가 없다. 따라서 일반 문의 스레드의 "답변 완료"는 F3가 그 전이를 채운 뒤에만 끝까지 testable하다.
- **Rationale**: F4가 F3의 책임(정제 완료·교사 확인 판정)을 앞당겨 만들면 F3가 실제로 그 지점을 설계할 때(예: "번역본을 열면 확인", "원문 확인 필요에서 원문을 보면 확인") 충돌·재작업 위험이 크다. 긴급 문의는 F2가 이미 "첫 원문 열람 → TEACHER_CONFIRMED" 전이를 만들어 뒀으므로 F4 기능 전체(작성→점검→전송→재문의→종료)를 긴급 문의로는 처음부터 끝까지 검증할 수 있다.
- **Alternatives considered**: `답변 전송 자체`를 `TEACHER_CONFIRMED` 전이의 트리거로 삼는 방법 — "교사 확인"의 의미(원문·번역본을 봤다는 것)와 "답변을 보냈다"는 다른 사실이라 F2의 의도를 왜곡해 기각.

## R4. 답변 점검 포트는 어떻게 두나

- **Decision**: `application.port.out.AnswerReviewPort`에 `Optional<AnswerReviewResult> review(String draftContent, String pseudonymizedContext)` 하나만 둔다. `adapter.out.review.UnavailableAnswerReviewAdapter`는 항상 `Optional.empty()`를 반환하는 스텁이다. F3가 완성되면 이 어댑터만 실제 가명화 게이트웨이 호출로 교체한다.
- **Rationale**: 사용자 확인(2026-10-25): 포트+스텁만 지금 만들고 실제 LLM·가명화 연동은 F3로 이월. `pseudonymizedContext`는 F3의 3요소 분해 결과(가명화된 문자열)를 그대로 받는 자리만 만들어 둔다 — 지금은 호출하는 곳이 `null`을 넘긴다.
- **Alternatives considered**: F4에서 직접 가명화·LLM 호출까지 구현 — 사용자가 기각(새 외부 연동·범위 확대, F3→F4 순서와 충돌).

## R5. 재문의 제출은 어떤 코드로 처리하나

- **Decision**: `SubmitInquiryService`를 복제하지 않고, 같은 패턴(검증 → 중복 확인 → **대상 재확인** → 긴급 판정 → 1분 제한 → 저장)을 따르는 자매 서비스 `SubmitFollowUpService`를 만든다. 대상은 스레드 루트의 `studentId`로 `InquiryTargetQuery.resolveChild(parentId, studentId)`를 **다시 호출**해 현재 `classroomId`·담임을 조회한다(F2와 완전히 같은 규칙). 담임이 해제·공석이면 `NO_HOMEROOM_TEACHER`(409)로 거부해 F2와 동일하게 동작한다(spec.md Edge Cases와 일치). 담임이 바뀌어 있으면 새 담임에게 전달된다. 1분 제한은 `AttemptLimitUseCase.reserveInquirySubmit(parentId)`를 그대로 재사용해 최초 문의와 같은 버킷을 공유한다(클래리파이 Q2).
- **Rationale**: 로직 중복을 피하면서도 F2가 이미 정한 "담임은 학급 기준, 알림은 현재 담임에게" 규칙(F2 spec.md Assumptions)을 재문의에도 똑같이 적용해 특례를 만들지 않는다. 스레드 시작 시점의 담임으로 고정하면(이전 결정) 담임이 해제·공석인 상황에서 그 메시지가 어떤 교사의 목록에도 보이지 않는 고아 상태가 될 위험이 있었다(`TeacherInquiryService`는 `inquiry.teacherId` 값이 아니라 **학급 기준 현재 담임**으로만 필터링하기 때문, `/speckit-analyze` 2026-10-25 I2).
- **새 담임의 열람 범위**: 담임이 바뀌어도 `TeacherInquiryService`는 여전히 **학급 기준**으로 조회하므로(저장된 `teacherId` 값을 보지 않음), 새 담임은 별도 조치 없이 스레드 전체(최초 문의·이전 답변·이전 재문의)를 그대로 본다 — F2 설계의 당연한 결과이며 F4가 추가로 구현할 것은 없다.
- **Alternatives considered**: 스레드 루트의 담임으로 고정 — 대화 상대가 바뀌어 보이지 않는 장점은 있지만, 담임 공석 시 고아 메시지가 생기는 위험이 더 커서 기각(2026-10-25 수정).

## R6. 답변 도착 알림은 어떻게 보내나

- **Decision**: `notification` 도메인에 `ParentNotificationSetting`(학부모별 `answer_notify_enabled`, 기본값 `true`) 엔티티와 `NotifyAnswerUseCase`를 추가한다. 설정 row가 없으면(가입 직후) `true`로 간주한다(F2가 교사 응답 가능 시간에서 쓴 것과 같은 "설정 없으면 기본값" 패턴). 발송은 F2가 만든 아웃박스(`NotificationRequest`) + `NotificationDispatcher`(10초 스케줄러) + `FcmPushSender`를 그대로 재사용하고, `NotificationKind`에 `ANSWER`만 추가한다.
- **Rationale**: 새 발송 경로를 만들지 않아도 된다. 교사 알림 설정(`TeacherNotificationSetting`)과 학부모 알림 설정(`ParentNotificationSetting`)은 소유자·항목이 달라 같은 테이블로 합치지 않는다(교사: 응답 가능 시간+일반 알림, 학부모: 답변 알림 하나뿐).
- **Alternatives considered**: 가입 시점에 기본값 row를 미리 만들어 둠 — F1 가입 흐름을 수정해야 해서 범위가 커지고, "row 없으면 on"이라는 지연 기본값으로도 충분해 기각.

## R7. 점검 결과를 저장하나

- **Decision**: 저장하지 않는다. `AnswerReviewResult`는 응답 DTO로만 나가는 값 객체이고 엔티티가 아니다(FR-013).
- **Rationale**: 복원표와 같은 이유(원칙 II 취지) — 점검은 그 순간의 초안에만 유효하고, 답변을 고치면 바로 낡은 정보가 된다. 저장하면 교사 작성 중인 민감한 중간 산물이 DB에 남는다.

## R8. 에러 코드

- **Decision**: `ErrorCode`에 2개를 추가한다: `THREAD_CLOSED(409, "이미 종료된 문의입니다.")`(이미 종료된 스레드에 재문의·답변), `INQUIRY_NOT_READY_TO_CLOSE(409, "아직 종료할 수 없는 문의입니다.")`(아직 `TEACHER_CONFIRMED`가 아닌 문의를 종료하려는 시도). 두 코드는 원인이 달라 메시지를 공유하지 않는다 — 기존 `CONFLICT`를 재사용하면 메시지가 "다른 요청과 충돌했습니다…"로 바뀌어 사용자에게 원인을 알려주지 못한다(/speckit-analyze 2026-10-25 I1). 다른 학급·학생 접근은 기존 `NOT_FOUND`(F2와 같은 패턴)를 재사용한다. 점검 실패는 에러가 아니라 200 응답의 `available=false` 필드로 표현한다(FR-012가 전송을 막지 않아야 하므로 예외로 만들지 않는다).
- **Rationale**: 기존 `ErrorCode` 선언 순서 규칙("기존 코드 뒤에 추가, 기존 코드 의미는 바꾸지 않는다")을 따른다. `CONFLICT`처럼 범용 코드에 F4 전용 의미를 덧씌우지 않고 전용 코드를 추가해 "기존 코드 의미 불변" 원칙과 "원인을 알 수 있는 메시지"를 동시에 지킨다.

## R9. 소유권 검증

- **Decision**: 답변 작성·점검·전송·종료는 모두 `InquiryTargetQuery.isCurrentHomeroomTeacher(teacherId, classroomId)`로 매 요청 검증한다(F2의 `ViewInquiryContentService`·`TeacherInquiryService`와 같은 패턴). 재문의 제출은 `ParentInquiryQuery`처럼 `parentId` 일치만 확인한다(이미 F1·F2가 보장).
- **Rationale**: 기존 검증 지점을 재사용해 동일한 보안 수준(현재 담임 기준, 토큰 주체만 신뢰)을 유지한다(원칙 VII).

---

# 2026-10-28 추가 — 목록의 답변 여부(US6)·종료 경로 차단(US7)

브랜치 `features/f4-close-not-used`(PR #19). 근거: `docs/decisions.md` 2026-10-27 행의 미결 ①②, spec Clarifications Session 2026-10-28.

## R10. 목록의 답변 여부를 어떻게 계산하나

- **Decision**: 저장하지 않고 조회할 때 집계한다. 목록에 나온 스레드 루트 id들을 모아 `answer`에서 한 번에 `thread_root_id`별 `max(sent_at)`을 구한다(`group by thread_root_id`). `hasAnswer`는 `lastAnsweredAt != null`로 정한다. 교사 목록의 `threadThreatFlagged`(`InquiryPort.findThreadsWithThreatFlaggedFollowUp`, 루트 id 묶음 → 한 번의 쿼리)와 같은 모양이다.
- **Rationale**: `answer.thread_root_id`는 이미 루트 id를 가리키고(재문의에 단 답변도 루트 기준), 인덱스 `idx_answer_thread(thread_root_id, sent_at)`가 있어 묶음 조회가 인덱스만으로 끝난다. 스키마 변경이 없다(CLAUDE.md "DB 스키마 변경은 확인" 대상이 아님). 답변 저장 경로를 건드리지 않는다.
- **Alternatives considered**:
  - `inquiry`에 `last_answered_at` 컬럼 추가(비정규화) — 답변 저장 때 루트 row를 함께 갱신해야 하고, 그러면 루트 `@Version`이 올라가 동시 재문의·종료와 낙관적 락 충돌이 늘어난다. 스키마 변경도 생긴다. 기각.
  - 목록 행마다 `findByThreadRootId` 호출 — 페이지당 최대 50번 쿼리(N+1)에 본문까지 읽는다(복호화 비용, 원칙 VI 노출면 증가). 기각.

## R11. 응답 필드 이름과 모양

- **Decision**: `ParentInquirySummary`·`TeacherInquirySummary`에 `hasAnswer: boolean`(required)과 `lastAnsweredAt: date-time, nullable`(required, 답변이 없으면 `null`)을 추가한다.
- **Rationale**: 이름은 PR #19 리뷰 제안(`hasAnswer`·`lastAnsweredAt`) 그대로다. 두 스키마 모두 `additionalProperties: false`이고 모든 필드를 `required`로 두는 관례(`refinementStatus`는 required + nullable)를 따른다. 시각은 스레드 전체의 마지막 답변 전송 시각(US6 시나리오 6)이다.
- **Alternatives considered**: `lastAnsweredAt` 하나만(null이면 답변 없음) — 클래리파이 Q1에서 B·C를 고르지 않음. 기각.

## R12. 종료 경로를 어떻게 막나

- **Decision**: `CloseInquiryService`에 `@Value("${damso.inquiry.close-enabled:false}") boolean closeEnabled`를 주입하고, `close()`의 **첫 줄**에서 꺼져 있으면 `BusinessException(ErrorCode.NOT_FOUND)`를 던진다. `application.yml`의 `damso.inquiry` 아래에 `close-enabled: false`를 주석과 함께 명시한다.
- **Rationale**:
  - 같은 모양의 설정이 이미 있다 — `RefinementDispatchTrigger`의 `damso.refinement.dispatch-on-commit`(`@Value` boolean, 테스트는 `@TestPropertySource`로 바꿈). 새 방식(`@ConditionalOnProperty`·`@ConfigurationProperties`)을 들여오지 않는다.
  - 첫 줄에서 막으므로 문의 조회·담당 교사 확인보다 먼저 거부된다(FR-031 "담당 여부를 보기 전에"). 스레드 상태와 `inquiry_stage_history`는 바뀌지 않는다.
  - `NOT_FOUND`는 매핑이 없는 경로(`NoResourceFoundException`)와 같은 `ErrorCode.NOT_FOUND`라 응답 본문이 같다. 새 에러 코드 없음(클래리파이 Q4).
  - 경로 단위 역할 검사(`SecurityConfig` `/api/v1/teacher/**` → `hasRole("TEACHER")`)는 그대로 먼저 돌아 학부모·슈퍼 관리자는 403이다.
  - 기본값을 코드(`:false`)와 yml 양쪽에 둬, 설정이 빠져도 막힌 쪽으로 떨어진다(원칙 VIII 안전한 기본값).
- **Alternatives considered**:
  - 컨트롤러 매핑 삭제 — 다시 열려면 코드를 되살려야 함(FR-031 "나중에 경로만 다시 열 수 있게"). 기각.
  - `@ConditionalOnProperty`로 별도 컨트롤러 분리 — `TeacherAnswerController`를 쪼개야 하고 프로젝트에 선례가 없다. 기각.
  - 컨트롤러에서 검사 — 같은 효과지만 기존 토글이 서비스 쪽에 있고, 유스케이스 단위로 막는 편이 다른 진입점이 생겨도 안전하다. 기각.

## R13. 종료 성공을 전제로 한 기존 테스트

- **Decision**: 아래 5개 클래스에 `@TestPropertySource(properties = "damso.inquiry.close-enabled=true")`를 붙여 FR-021·FR-022 서버 검증을 계속 확인한다. 테스트 메서드는 지우거나 바꾸지 않는다.
  - `CloseInquiryApiTest`(종료 성공·403/404·이중 종료 409)
  - `ThreadClosedIntegrationTest`(닫힌 스레드 재문의·답변 거부, 동시성)
  - `UrgentReminderStopsAfterCloseTest`
  - `AnswerExposureTest`(다른 학급·학부모·관리자의 종료 시도 403/404 — 꺼져 있어도 통과하지만, 켜 둬야 담당 교사 검증을 실제로 지난다)
  - `AnswerSensitiveLoggingTest`(종료 경로의 로그 검사)
- 새 테스트 `CloseDisabledApiTest`(기본 설정, 꺼짐): 담당 교사의 종료 → 404 `NOT_FOUND`·stage 그대로·`ANSWERED` 이력 0건 · 그 뒤 재문의 201·답변 201 · 다른 학급 교사 404 · 학부모·관리자 403.
- **Rationale**: PR #19 리뷰 P0 — 종료 검증 테스트가 "안 쓰는 기능"으로 보여 지워지지 않게 한다(원칙 VII). 5개 클래스는 지금 `@TestPropertySource`가 없어 같은 속성 하나만 더하면 Spring 테스트 컨텍스트 하나를 함께 쓴다(컨텍스트 1개 증가).
- **Alternatives considered**: 종료 테스트를 서비스 직접 호출로 바꾸기 — HTTP 경로의 역할 검사·에러 매핑 검증이 빠진다. 기각.

## R14. 운영에 이미 닫힌 스레드가 있나

- **Decision**: 사람이 운영 DB에서 `select count(*) from inquiry where thread_root_id is null and stage = 'ANSWERED'`를 실행해 확인한다. 0건이면 추가 작업 없음. 1건 이상이면 차단만으로는 그 스레드가 계속 닫혀 있으므로 처리 방법을 따로 정한다(사용자 확인 필요).
- **Rationale**: 운영 DB는 외부 시스템이라 에이전트가 접근하지 않는다. 운영 배포가 2026-10-28이고 앱은 종료를 호출하지 않으므로 0건일 가능성이 높다(spec Assumptions).
- **Result (2026-10-28, 사용자 확인)**: 운영 DB에서 0건. 추가 작업 없음.

## R15. API 명세 반영

- **Decision**: Contract-first. 구현 전에 `contracts/list-answer-status.openapi.yaml`의 변경을 `docs/api/openapi.yaml`에 반영한다.
  - 두 Summary 스키마에 `hasAnswer`·`lastAnsweredAt` 추가
  - `POST /api/v1/teacher/inquiries/{inquiryId}/close`에 `deprecated: true`, 설명에 "운영에서 꺼져 있음 → 교사 호출은 404", 응답에 404 설명 보강
  - `InquiryStage` 설명에 "앱에서는 종료를 호출하지 않아 `ANSWERED`가 생기지 않음" 메모
  - 재문의·답변의 `THREAD_CLOSED` 설명에 "종료 경로가 꺼져 있어 지금은 생기지 않음" 메모
  - `info.version` `0.6.0` → `0.7.0`, merge 후 main에서 `/api-release api-v0.7`
- **Rationale**: 필드 추가는 응답 쪽 추가라 MINOR. 프론트는 이 파일을 기준으로 본다(PR #19 리뷰 P1). F2·F3 spec FR-021과 `InquiryStage.java` 주석은 decisions.md 영향 범위에 기록돼 있어 이번에는 바꾸지 않는다.
