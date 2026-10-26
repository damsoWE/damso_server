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
