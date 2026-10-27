---

description: "Task list for 005-threat-expression-flag"
---

# Tasks: 일반 문의 위협·폭언 표현 표시

**Input**: Design documents from `specs/005-threat-expression-flag/`

**Prerequisites**: [plan.md](./plan.md), [spec.md](./spec.md), [research.md](./research.md), [data-model.md](./data-model.md), [contracts/](./contracts/threat-flag.openapi.yaml), [quickstart.md](./quickstart.md)

**Tests**: 포함한다. spec의 성공 기준(SC-001~007)을 자동화 테스트로 확인할 수 있고, 대표 사례 회귀 테스트가 FR-003의 요구사항 자체다. 보안약점 체크리스트의 `조치` 근거로 테스트 이름이 필요하다(F1~F3와 같은 방식).

**Organization**: spec의 사용자 스토리(US1~US3, 모두 P1) 단위로 묶었다. 판정 규칙과 저장은 모든 스토리가 쓰므로 Foundational에 두었다. US1(교사 표시)이 MVP이고, US2(정당한 표현 비표시)와 US3(학부모 비노출)는 US1 위에서 확인·보강한다.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: 다른 파일이고 미완료 작업에 의존하지 않아 병렬 가능
- **[Story]**: 해당 사용자 스토리 (US1~US3)

## Path Conventions

- 운영 코드: `src/main/java/damso/inquiry/…`만 고친다(plan.md Project Structure). `notification`·`gateway`·`school`은 고치지 않는다
- 패키지: `domain` / `application.port.in` / `application.port.out` / `application.service` / `adapter.in.controller.dto.response` / `adapter.out.resource` (constitution 1.3.0)
- F2 긴급 판정 코드(`UrgentRule`·`UrgentExpressionSource`·`ClasspathUrgentExpressionSource`)는 **고치지 않는다**. 정규화는 `UrgentRule.normalize`를 부르기만 한다 (research R2)
- 테스트: `src/test/java/damso/…`, 테스트 전용 도구는 `src/test/java/damso/support/`에만. 통합 테스트는 `IntegrationTestSupport`(`teacherWithClassroom()`, `submitInquiry(...)`, `runRefinement()`, `send(...)`, `read(...)`)를 쓴다
- 본문·판정에 걸린 표현·예외 메시지는 로그·예외·에러 응답에 넣지 않는다(원칙 VI). 표시 값(boolean)과 UUID만 남긴다
- 시각은 `Clock` 빈을 쓴다(`Instant.now()` 금지, 기존 테스트 헬퍼 예외)
- 테스트 문장에는 실제 학생 정보가 아닌 가짜 이름만 쓴다

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: 브랜치 확인, 표현 목록·대표 사례 초안(D3), API 명세 선반영(Contract-first)

- [X] T001 작업 브랜치 확인: `git branch --show-current`가 `features/f3-threat-expression-flag`(이슈 #9 연결)인지 확인한다. 아니면 `git switch features/f3-threat-expression-flag`
- [X] T002 [P] 표현 목록 초안 `src/main/resources/threat/expressions.txt` 작성. 머리 주석은 `src/main/resources/urgent/expressions.txt`의 형식을 따른다: 한 줄에 표현 하나, 빈 줄·`#` 줄 무시, 정규화(공백·줄바꿈 제거, 소문자, 전각→반각) 뒤 포함 검사, **오탐 최소화**(분명한 위협·폭언만, 짧은 어간 금지 — 공백을 지우고 보므로 낱말 경계가 없다), **긴급 목록과 겹치는 표현 금지**(`협박`·`위협`·`죽이겠`·`죽여버리`·`칼을`·`흉기`·`학교폭력` 등은 이미 긴급, research R4), PR 리뷰로만 변경, 비면 기동 거부, 회귀 테스트는 `ThreatRuleTest`와 `src/test/resources/threat/must-flag.txt`·`must-not-flag.txt`. 맨 위에 `# [초안] 팀 리뷰 전이다. 머지 전에 리뷰한 사람·날짜를 PR 설명에 적는다.`를 둔다. 묶음 예: `# --- 신상·직위 위협 ---`(교사·학생에게 해를 끼치겠다는 비폭력 위협), `# --- 모욕·폭언 ---`(교사·학생을 향한 욕설·비하). 정당한 의사 표현(교육청 민원·면담·교장 상담·정식 이의 제기)에 걸릴 수 있는 표현은 넣지 않는다 (FR-001·002·004a, D3)
- [X] T003 [P] 대표 사례 초안 2개 작성(가짜 이름만). `src/test/resources/threat/must-flag.txt` — 반드시 표시되어야 하는 문장 15개 이상, T002 목록의 각 묶음에서 고르게, 띄어쓰기 변형 문장 포함. `src/test/resources/threat/must-not-flag.txt` — 절대 표시되면 안 되는 문장 15개 이상: 교육청 민원("교육청에 민원 넣겠습니다"), 면담 요청("면담을 요청드립니다"), 교장·교감 상담 요청("교장 선생님과 상담하고 싶습니다"), 정식 이의 제기, 강한 불만이지만 위협·폭언이 아닌 문장, 일상 문의를 **반드시** 포함한다. 두 파일 모두 머리 주석에 형식(한 줄에 하나, `#` 무시)·목적·`[초안] 팀 리뷰 전`을 적는다. **긴급 표현이 든 문장은 어느 파일에도 넣지 않는다**(긴급이 먼저라 이 판정의 대상이 아님) (FR-002·003, SC-001, D3)
- [X] T004 **Contract-first — 구현 전에** `docs/api/openapi.yaml`에 `specs/005-threat-expression-flag/contracts/threat-flag.openapi.yaml`을 통합한다.
  - `components.schemas`에 `ThreatFlagged`(boolean, 설명 그대로)를 추가한다.
  - `TeacherInquirySummary`·`TeacherInquiryDetail`에 `threatFlagged: { $ref: '#/components/schemas/ThreatFlagged' }`를 넣고 각 `required`에 추가한다. 위치는 Summary는 `refinementStatus` 뒤, Detail은 `retryAvailable` 뒤다.
  - `info.description`의 출처에 `specs/005-threat-expression-flag (위협·폭언 표현 표시)`를 덧붙인다.
  - `info.version`은 research R10대로 정한다. main에 F4 병합 전이면 `0.4.0` → `0.5.0`, F4가 먼저 병합됐으면 `0.5.0` → `0.6.0`. 정하기 전에 `git fetch origin && git show origin/main:docs/api/openapi.yaml | sed -n 4p`로 확인한다.
  - `npx -y @redocly/cli@1.34.5 lint docs/api/openapi.yaml`이 valid(오류 0건)여야 한다.
  - 태그는 PR merge 후 main에서 `/api-release api-vX.Y`로 찍는다. (constitution Contract-first, D2)
- [X] T005 프론트엔드 공지 문구를 준비한다. 사용자에게 전달만 하고 직접 보내지 않는다. 담을 내용:
  - 교사 목록·상세에 `threatFlagged`(boolean, 필수)가 추가된다.
  - 의미는 "위협·폭언 표현 포함"이고 접수 때 정해져 바뀌지 않는다.
  - 긴급 문의는 항상 false다.
  - 학부모 API는 바뀌지 않는다.
  - 목록 정렬은 그대로다.
  - 공지한 날짜·채널은 PR 설명에 적는다. (constitution Contract-first)

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: 판정 규칙·표현 목록 포트·저장 컬럼·접수 흐름 — 모든 스토리가 쓴다

**⚠️ CRITICAL**: 이 단계가 끝나야 스토리 작업을 시작한다

### Tests for Foundational ⚠️

> 먼저 쓰고 실패(컴파일 실패 포함)를 확인한다

- [X] T006 [P] `src/test/java/damso/inquiry/domain/ThreatRuleTest.java` — `UrgentRuleTest`와 같은 구조. `ClasspathThreatExpressionSource`로 `threat/expressions.txt`를 읽어 다음을 확인한다:
  - ① 목록의 모든 표현이 `"오늘 " + expression + " 일이 있었어요"`에서 표시된다.
  - ② `must-flag.txt`의 모든 문장이 표시된다(문장 15개 이상).
  - ③ `must-not-flag.txt`의 어떤 문장도 표시되지 않는다(문장 15개 이상).
  - ④ **목록의 각 표현을 `new UrgentRule(new ClasspathUrgentExpressionSource(new ClassPathResource("urgent/expressions.txt")).expressions())`에 넣어도 긴급이 아니다**(겹침 금지, research R4).
  - ⑤ 띄어쓰기·줄바꿈·탭·전각 공백 변형이 원래 문장과 같은 결과다.
  - ⑥ `null`·`""`·공백뿐인 본문은 false다.
  - ⑦ 빈 목록(`List.of()`, `List.of("  ", "\n")`)은 `IllegalArgumentException`이다.
  - (FR-003·004a·005a·006, SC-001)
- [X] T007 [P] `src/test/java/damso/inquiry/adapter/out/resource/ThreatExpressionSourceTest.java` — `UrgentExpressionSourceTest`와 같은 방식으로 확인한다: 주석·빈 줄 무시, 정규화·중복 제거, 빈 파일·주석만 있는 파일·없는 파일이면 `IllegalStateException`(기동 거부), 예외 메시지에 파일 내용 없음 (spec Edge Cases, 원칙 VIII)
- [X] T008 [P] `src/test/java/damso/inquiry/domain/InquiryStageTest.java`의 `inquiry(boolean)` 헬퍼를 새 `Inquiry.receive` 시그니처(`threatFlagged` 인자 추가, 값 `false`)에 맞춘다. 새 테스트 `src/test/java/damso/inquiry/domain/InquiryThreatFlagTest.java` — 일반 + `threatFlagged=true` → `isThreatFlagged()` true, **긴급 + `threatFlagged=true` → false**(엔티티가 FR-005a 보장), setter·변경 메서드가 없음(리플렉션으로 `threatFlagged` 필드를 바꾸는 public 메서드 0개) (FR-005a·007, data-model)

### Implementation for Foundational

- [X] T009 [P] `src/main/java/damso/inquiry/domain/ThreatRule.java` — `UrgentRule`과 같은 모양의 `final` 순수 클래스다. 다른 의존성은 없다(외부 호출·AI·Redis·알림 없음, 원칙 IV). (research R2, data-model "판정")
  - 생성자 `ThreatRule(Collection<String> expressions)`: `UrgentRule.normalize`로 정규화한 뒤 빈 값을 거르고 중복을 없앤다. 결과가 비면 `IllegalArgumentException("threat expressions must not be empty")`. 메시지에 표현을 넣지 않는다.
  - `boolean isFlagged(String content)`: `null`·정규화 후 빈 값은 false, 표현이 하나라도 포함되면 true.
  - 클래스 Javadoc: "위협·폭언 판정 규칙. 긴급이 아닌 문의에만 쓴다. 오탐 최소화 — 목록에는 분명한 표현만".
- [X] T010 [P] `src/main/java/damso/inquiry/application/port/out/ThreatExpressionSource.java` — `List<String> expressions()`. Javadoc: "비어 있지 않음을 구현이 보장한다(못 읽거나 비면 서버가 기동하지 않는다)". 그리고 `src/main/java/damso/inquiry/adapter/out/resource/ClasspathThreatExpressionSource.java` — `ClasspathUrgentExpressionSource`와 같은 구현이다. (research R3)
  - `@Value("${damso.inquiry.threat-expressions:classpath:threat/expressions.txt}") Resource`로 읽는다.
  - 빈 줄과 `#` 줄을 무시하고 `UrgentRule.normalize`로 정규화한 뒤 중복을 없앤다.
  - 못 읽으면 `IllegalStateException("위협·폭언 표현 목록을 읽을 수 없습니다")`, 비면 `IllegalStateException("위협·폭언 표현 목록이 비어 있습니다")`.
- [X] T011 `src/main/java/damso/inquiry/domain/Inquiry.java`에 판정 결과 컬럼을 추가한다. (data-model "inquiry (변경)", research R6, D1)
  - 필드: `@Column(name = "threat_flagged", nullable = false, updatable = false, columnDefinition = "boolean default false") private boolean threatFlagged;`
  - 필드 주석: "접수 때 정한 위협·폭언 표현 표시. 긴급 문의는 항상 false. 바뀌지 않는다(spec 005 FR-007)". `columnDefinition` 위에는 `deferred_notice`와 같은 이유 주석을 붙인다.
  - `receive(...)`와 private 생성자에 `boolean threatFlagged`를 `urgent` 바로 뒤에 추가하고, 생성자에서 `this.threatFlagged = !urgent && threatFlagged;`로 둔다.
  - getter `isThreatFlagged()`만 두고 setter·변경 메서드는 두지 않는다.
  - 클래스 Javadoc의 "content·urgent는 updatable = false" 문장에 `threat_flagged`를 더한다.
- [X] T012 `src/main/java/damso/inquiry/application/service/ThreatFlagScreener.java` — `@Component`, non-final 클래스(테스트에서 하위 클래스로 바꿔 끼울 수 있게). 생성자에서 `ThreatExpressionSource`를 받아 `ThreatRule`을 만들고, `public boolean screen(String content)`가 `rule.isFlagged(content)`를 돌려준다. Javadoc: "접수 때 일반 문의의 위협·폭언 표시를 정한다. 예외 처리는 호출하는 쪽(SubmitInquiryService)이 한다" (research R7)
- [X] T013 `src/main/java/damso/inquiry/application/service/SubmitInquiryService.java`에 위협·폭언 판정을 넣는다. (FR-005·005a·005b·008, research R4·R7, data-model "접수 흐름에서의 위치")
  - 생성자에 `ThreatFlagScreener threatScreener`를 받는다.
  - `submit`에서 `boolean urgent = urgentRule.isUrgent(content);` 바로 뒤에 `boolean threatFlagged = !urgent && screenThreat(content);`를 둔다.
  - `persist(...)`에 `threatFlagged`를 넘겨 `Inquiry.receive(..., urgent, threatFlagged, ...)`로 저장한다.
  - private `screenThreat(String content)`: `try { return threatScreener.screen(content); } catch (RuntimeException ex) { log.warn("위협·폭언 판정에 실패해 표시 없이 접수합니다"); return false; }`. **예외 객체·메시지·본문을 로그에 넘기지 않는다**(원칙 VI).
  - 클래스 Javadoc의 순서 설명을 "④ 긴급 판정(규칙, AI 이전) ⑤ 일반이면 위협·폭언 판정(규칙, 실패하면 표시 없음) ⑥ 일반이면 1분 제한 ⑦ 저장"으로 고친다.
  - 재전송(`replay`) 경로는 다시 판정하지 않는다.
- [X] T014 `./gradlew test --tests "damso.inquiry.domain.*" --tests "damso.inquiry.adapter.out.resource.*"`로 T006~T008이 통과하는지 확인한다(Windows: `.\gradlew.bat test --tests ...`). 이어서 `./gradlew build -x test`로 컴파일을 확인한다.

**Checkpoint**: 규칙·목록·저장·접수 흐름이 준비됐다. 아직 교사 응답에는 보이지 않는다.

---

## Phase 3: User Story 1 - 교사가 위협·폭언이 담긴 일반 문의를 목록에서 바로 알아본다 (Priority: P1) 🎯 MVP

**Goal**: 교사 목록·상세에 `threatFlagged`가 보이고, 외부 AI 상태·번역본 내용·이후 처리와 무관하게 접수 때 값 그대로다

**Independent Test**: 가짜 외부 AI를 응답 없음으로 둔 채 `must-flag.txt` 문장으로 일반 문의를 접수 → 교사 목록·상세에서 `threatFlagged: true`

### Tests for User Story 1 ⚠️

- [X] T015 [P] [US1] `src/test/java/damso/inquiry/ThreatFlagApiTest.java`(`IntegrationTestSupport` 상속). 시나리오별로 테스트 메서드를 하나씩 둔다. (US1 시나리오 1~9, SC-002·003·006, FR-007·010)
  - ① 표시할 문장으로 접수한 문의는 `GET /api/v1/teacher/inquiries`의 항목과 `GET /api/v1/teacher/inquiries/{id}`에서 `threatFlagged: true`, 일반 문장은 `false`다.
  - ② `FakeExternalAi`를 `NO_RESPONSE`로 두고 정리를 돌려 `NEEDS_ORIGINAL`로 만든 뒤에도, 정리 전(`PENDING`)에도 표시가 같다.
  - ③ 같은 문장을 서로 다른 Idempotency-Key로 두 번 접수하고, 가짜 외부 AI가 매번 다른 번역본을 돌려줘도 둘 다 `true`다.
  - ④ 정리 완료 → 번역본 열람 → 원문 열람 뒤에도, 실패 문의를 교사가 재시도한 뒤에도 값이 그대로다.
  - ⑤ 긴급 표현과 T002 목록 표현이 함께 든 문의는 `urgent: true`, `threatFlagged: false`다.
  - ⑥ `@TestConfiguration`에서 `@Primary`로 등록한 `ThreatFlagScreener` 하위 클래스(토글하면 `screen`이 `IllegalStateException`을 던짐)로 접수하면 201이고 `threatFlagged: false`다. 로그 캡처(`OutputCaptureExtension`)에 본문과 예외 메시지가 없다.
  - ⑦ 같은 키로 재전송하면 처음 결과를 돌려주고 다시 판정하지 않는다.
- [X] T016 [P] [US1] `src/test/java/damso/inquiry/TeacherInquiryApiTest.java` 확장 — 목록 항목과 상세 응답의 키 집합이 계약(`contracts/threat-flag.openapi.yaml`)의 `required`와 정확히 같다. `threatFlagged`가 있고 boolean이며, 남는 키는 없다(`additionalProperties: false`). 다른 학급 교사·학부모·슈퍼 관리자가 상세를 조회하면 404/403이다(F2·F3와 같음). (FR-011, SC-007, contracts)

### Implementation for User Story 1

- [X] T017 [US1] `src/main/java/damso/inquiry/application/port/in/TeacherInquiryQuery.java` — `InquirySummary`는 `refinementStatus` 뒤에, `InquiryDetail`은 `retryAvailable` 뒤에 `boolean threatFlagged`를 추가한다. 두 record의 Javadoc에 "threatFlagged: 접수 때 정한 위협·폭언 표현 표시, 긴급은 항상 false(spec 005)"를 덧붙이고, `toString`은 그대로 둔다(UUID만) (R8)
- [X] T018 [US1] `src/main/java/damso/inquiry/application/service/TeacherInquiryService.java` — `summaryOf`·`get`에서 `inquiry.isThreatFlagged()`를 넘긴다. 정렬(긴급 먼저, 그다음 최신순)은 바꾸지 않는다 (R8, FR-010)
- [X] T019 [P] [US1] `src/main/java/damso/inquiry/adapter/in/controller/dto/response/TeacherInquirySummaryResponseDTO.java`·`TeacherInquiryDetailResponseDTO.java`에 `boolean threatFlagged`를 같은 위치(Summary는 `refinementStatus` 뒤, Detail은 `retryAvailable` 뒤)에 추가하고 `from(...)`을 고친다. 클래스 Javadoc에 "threatFlagged는 교사에게만 보이는 위협·폭언 표현 표시(spec 005)"를 덧붙인다 (contracts)
- [X] T020 [US1] `./gradlew test --tests "damso.inquiry.ThreatFlagApiTest" --tests "damso.inquiry.TeacherInquiryApiTest"` 통과 확인

**Checkpoint**: MVP — 교사가 목록·상세에서 위협·폭언 표시를 본다

---

## Phase 4: User Story 2 - 정당한 의사 표현은 위협으로 표시하지 않는다 (Priority: P1)

**Goal**: 팀이 정한 대표 사례로 오탐이 없음을 보증하고, 목록이 바뀌면 같은 PR에서 회귀가 잡힌다

**Independent Test**: `must-not-flag.txt` 전부를 실제 접수 API로 넣어 교사 응답에서 모두 `threatFlagged: false`, `must-flag.txt` 전부는 `true`

### Tests for User Story 2 ⚠️

- [X] T021 [US2] `src/test/java/damso/inquiry/ThreatFlagApiTest.java`에 대표 사례 종단 테스트를 추가한다. (US2 시나리오 1~3, SC-001)
  - `must-flag.txt`와 `must-not-flag.txt`의 모든 문장을 실제 접수 API로 넣는다(Idempotency-Key는 문장마다 새로). 1분 제한은 기존 테스트처럼 `MutableClock`으로 넘긴다.
  - 교사 상세에서 `must-flag` 문장은 모두 `true`, `must-not-flag` 문장은 모두 `false`여야 한다.
  - 정당한 표현과 위협 표현이 함께 든 문장 하나가 `true`인지도 확인한다.
  - 실패 메시지에는 문장 본문 대신 파일 줄 번호를 쓴다.
- [X] T022 [US2] 대표 사례에서 오탐·미탐이 나오면 **목록만** 고친다(`src/main/resources/threat/expressions.txt`). 규칙에 예외 목록이나 정규식을 더하지 않는다(research R2). 고쳤으면 T006·T021을 다시 돌린다. 고칠 수 없는 사례는 사용자에게 보고하고 팀 결정을 기다린다(FR-002 — 구현자가 혼자 사례를 지우지 않는다)

### Implementation for User Story 2

- [X] T023 [US2] 팀 리뷰를 요청할 문서를 준비한다. 사용자에게 전달만 하고 직접 게시하지 않는다. (FR-002, D3)
  - 위협·폭언 표현 목록과 두 사례 파일의 요약: 묶음별 개수, 대표 예 3개씩.
  - "긴급 목록과 겹쳐 뺀 표현" 목록과 그 이유(research R4).
  - 리뷰 결과는 PR 설명의 "리뷰한 사람·날짜"에 남기고, 그 뒤 세 파일의 `[초안]` 주석을 지운다.

**Checkpoint**: 대표 사례 회귀가 묶였다. 목록 확정은 팀 리뷰 뒤다

---

## Phase 5: User Story 3 - 표시는 교사에게만 보이고 학부모에게는 나가지 않는다 (Priority: P1)

**Goal**: 학부모 응답·알림에 표시와 그 근거가 없고, 표시 여부를 추측할 수 없다

**Independent Test**: 표시된 문의와 표시되지 않은 문의를 같은 학부모로 접수해 접수 응답·목록·상세의 키 집합과 고정 문구가 같고 `threat` 키가 없음

### Tests for User Story 3 ⚠️

- [X] T024 [P] [US3] `src/test/java/damso/inquiry/InquiryExposureTest.java` 확장 — 기존 `collectKeys`를 재사용한다. (US3 시나리오 1·2, FR-012, SC-004)
  - 같은 학부모가 표시되는 문의와 표시되지 않는 문의를 접수한다.
  - ① 접수 응답(201), `GET /api/v1/parent/inquiries`, `GET /api/v1/parent/inquiries/{id}`의 키 집합이 두 문의에서 같다.
  - ② 어느 응답에도 소문자로 바꿨을 때 `threat`가 들어간 키가 없다.
  - ③ `notices`·`hotlines` 값이 두 문의에서 같다(둘 다 일반, 같은 시각 조건).
- [X] T025 [P] [US3] `src/test/java/damso/inquiry/InquirySensitiveLoggingTest.java`에 테스트를 추가한다. (FR-013·014, SC-005, US3 시나리오 3)
  - 표시되는 문장으로 접수 → 교사 목록·상세 조회 → 정리 실행 → 알림 발송 경로를 지나는 동안 로그 캡처에 다음이 0건이어야 한다: 문의 본문, T002 목록의 어떤 표현(정규화 전·후), 학생 이름.
  - `FakePushSender`가 받은 알림 제목·본문이 표시 여부와 무관하게 F2·F3 고정 문구와 같아야 한다.

### Implementation for User Story 3

- [X] T026 [US3] 학부모 DTO(`ParentInquirySummaryResponseDTO`·`ParentInquiryDetailResponseDTO`·`InquirySubmittedResponseDTO`)와 알림 코드는 **고치지 않는다**. T024·T025가 실패하면 원인을 찾아 해당 경로만 고치고, 학부모 응답에 값이 섞인 이유를 사용자에게 보고한다(R9)
- [X] T027 [US3] `./gradlew test --tests "damso.inquiry.InquiryExposureTest" --tests "damso.inquiry.InquirySensitiveLoggingTest"` 통과 확인

**Checkpoint**: 세 스토리가 모두 동작한다

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: 문서 갱신, 전체 검증

- [X] T028 [P] `docs/security-checklist.md`를 갱신한다. 기존 근거 문장은 지우지 않고 덧붙인다.
  - 구현단계 "부적절한 예외 처리" 행: `SubmitInquiryService.screenThreat`가 판정 예외를 잡아 고정 문구 경고 한 줄을 남기고 표시 없이 접수를 계속하는 이유(FR-005b, 접수권 보호)를 적는다. 근거 테스트는 `ThreatFlagApiTest`의 판정 실패 테스트 이름이다.
  - "제거되지 않고 남은 디버그 코드"(본문 로그 금지) 행: T025 테스트 이름을 덧붙인다. (quickstart 1절)
- [X] T029 [P] F2 문서에 덧붙인다. `specs/002-inquiry-intake-urgent/spec.md`의 FR-009a 아래에 "위협·폭언 표시(specs/005)는 긴급 판정 뒤, 긴급이 아닌 문의에만 한다. 긴급 판정은 바뀌지 않는다" 주석 한 줄을 붙인다. 원래 문장은 지우지 않는다(이력 보존) (research R4)
- [X] T030 [P] `src/main/resources/urgent/expressions.txt`는 고치지 않는다. 대신 T002 파일 머리 주석에 "긴급 목록을 바꿀 때는 ThreatRuleTest ④(겹침 금지)도 함께 돈다"를 적었는지 확인한다 (research R4)
- [X] T031 전체 검증을 한다. 실패는 숨기지 않고 출력과 함께 보고한다.
  - `./gradlew test`(Windows `.\gradlew.bat test`, Docker 실행 중)가 전부 통과해야 한다.
  - `npx -y @redocly/cli@1.34.5 lint docs/api/openapi.yaml`이 valid여야 한다.
  - quickstart.md 1절 표의 항목을 테스트 이름과 대조해 빠진 SC(SC-001~007)가 없는지 확인한다.
- [ ] T032 PR 준비(사용자 요청 시에만 커밋·푸시, PR은 사용자가 직접 만든다). PR 본문 초안에 다음을 넣는다.
  - `Closes #9`
  - D3 팀 리뷰 결과(리뷰한 사람·날짜)
  - API 버전(R10)과 프론트 공지 날짜·채널(T005)
  - "배포 시 주의": 기존 문의는 `threat_flagged=false`이고 다시 판정하지 않는다.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: 바로 시작한다. T004(명세 통합)는 구현 전에 끝낸다. T002·T003(초안)이 있어야 Foundational 테스트가 의미를 가진다
- **Foundational (Phase 2)**: Setup 뒤에 하고, 모든 스토리를 막는다
- **US1 (Phase 3)**: Foundational 뒤에 한다. MVP다
- **US2 (Phase 4)**: Foundational 뒤에 한다. 종단 테스트(T021)는 US1의 교사 응답 필드가 필요하므로 **US1 뒤**에 한다
- **US3 (Phase 5)**: Foundational 뒤에 한다. 학부모 쪽 확인이라 US1과 독립이지만, 표시된 문의를 만들려면 Foundational이 필요하다
- **Polish (Phase 6)**: 모든 스토리 뒤에 한다

### User Story Dependencies

```
Setup ─→ Foundational ─┬─→ US1 ─→ US2
                       └─→ US3
```

### Within Each User Story

- 테스트를 먼저 쓰고 실패를 확인한 뒤 구현한다
- 포트 record → 서비스 → DTO 순서로 한다
- 스토리의 Checkpoint 테스트가 통과하면 다음 스토리로 넘어간다

### Parallel Opportunities

- Phase 1: T002·T003 병렬
- Phase 2: 테스트 T006·T007·T008 병렬 → 구현 T009·T010 병렬 → T011 → T012 → T013
- US1: T015·T016 병렬, T019는 T017 뒤 T018과 병렬
- US3: T024·T025 병렬이며 US1과도 병렬 가능(다른 파일)
- Polish: T028·T029·T030 병렬

---

## Parallel Example: Foundational

```bash
# 테스트 먼저 (서로 다른 파일)
Task: "T006 ThreatRuleTest — 목록·대표 사례·긴급 겹침·정규화·빈 목록"
Task: "T007 ThreatExpressionSourceTest — 기동 거부"
Task: "T008 InquiryThreatFlagTest + InquiryStageTest 헬퍼"

# 그다음 구현 (서로 다른 파일)
Task: "T009 ThreatRule"
Task: "T010 ThreatExpressionSource + ClasspathThreatExpressionSource"
```

## Parallel Example: US1 · US3 (Foundational 직후)

```bash
Task: "T015 ThreatFlagApiTest (US1)"
Task: "T024 InquiryExposureTest 확장 (US3)"
Task: "T025 InquirySensitiveLoggingTest 확장 (US3)"
```

---

## Implementation Strategy

### MVP First (US1)

1. Phase 1 Setup을 한다. 초안(T002·T003)과 명세 통합(T004)이 들어간다
2. Phase 2 Foundational을 한다. 규칙·목록·컬럼·접수 흐름이다
3. Phase 3 US1을 한다. 교사 목록·상세 표시다
4. **멈추고 확인**: quickstart 1절의 US1 항목이 통과하는지 본다

### Incremental Delivery

1. MVP(US1) 다음으로 US3(학부모 비노출)을 한다. 보안 회귀 방지라 배포 전 필수다
2. 그다음 US2를 한다. 대표 사례 종단 테스트와 팀 리뷰 요청이다
3. 팀 리뷰로 목록·사례가 확정되면 `[초안]` 주석을 지우고 Polish → PR로 간다

---

## Notes

- 표현 목록·대표 사례는 **팀이 확정하기 전까지 초안**이다(D3). 초안 상태로 머지하지 않는다
- 판정에 걸린 표현은 어디에도 저장·로그하지 않는다. 디버깅용으로도 남기지 않는다(원칙 VI)
- 커밋은 사용자 요청 시에만, `Feat : …`/`Docs : …` 형식으로 하고 AI 기여자 표시는 넣지 않는다(`.claude/skills/git-conventions/SKILL.md`)
