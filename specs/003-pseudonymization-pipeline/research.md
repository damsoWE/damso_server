# Research: 가명화·정제 파이프라인 (F3)

**Feature**: [spec.md](./spec.md) · **Date**: 2026-10-25 · **기반**: [002-inquiry-intake-urgent research](../002-inquiry-intake-urgent/research.md)

각 항목은 Decision / Rationale / Alternatives considered 형식이다. CLAUDE.md "큰 영향 결정은 확인 후 진행"(외부 시스템·DB 스키마·시크릿·앱 계약) 대상은 **[확인 필요]**로 표시했다. 팀 확인을 받으면 **[팀 결정 날짜]**로 바꾼다.

---

## R1. 도메인 나누기 — `gateway`(가명화 게이트웨이) 신설, 처리 상태는 `inquiry`

- **Decision**:
  - 새 도메인 `gateway`: 가명화(1차 사전·2차 패턴), 외부 AI 호출, 출력 검증, 복원을 맡는다. **상태가 없는** 도메인이다(저장소 없음). 입력은 원문 + 사전 재료(`Roster`), 출력은 검증·복원이 끝난 결과 또는 실패 사유 코드다.
    - `port.in`: `RefineInquiryGateway`(일반 문의 3요소 정리), `ClassifyCrisisGateway`(긴급 문의 위기 종류)
    - `port.out`: `ExternalAiPort` — 외부 AI 호출의 **유일한** 출력 포트. 구현은 `adapter.out.openai.OpenAiClient` 하나뿐이다.
  - `inquiry`: 정리 작업(`refinement_job`)·결과(`inquiry_refinement`)·시도 기록(`refinement_attempt`)과 처리 단계 전이, 교사·학부모 화면을 맡는다. 게이트웨이는 `gateway`의 `port.in`으로만 부른다.
  - 의존 방향: `inquiry → gateway`(port.in), `inquiry → school`·`account`(사전 재료, port.in), `inquiry → notification`(알림 요청, F2와 같음). `gateway`는 다른 도메인을 부르지 않는다.
- **Rationale**: 원칙 II(외부 LLM으로 나가는 모든 텍스트는 가명화 게이트웨이를 거친다, 우회 경로 금지)를 **패키지 구조로** 보장한다. `ExternalAiPort`는 `gateway` 안에서만 쓰이고, 게이트웨이의 공개 입구는 가명화를 먼저 하는 메서드뿐이다. 구조 테스트로 "`ExternalAiPort`를 주입받는 클래스는 `gateway.application.service` 안에만 있다"를 확인한다. F4(답변 점검)도 같은 게이트웨이에 입구를 하나 더 붙이면 된다.
- **Alternatives considered**:
  - `inquiry` 안에 모두 넣기: 지금은 단순하지만 F4가 문의 내부의 AI 코드에 의존하게 되고, 우회 호출을 구조로 막기 어렵다.
  - 게이트웨이가 처리 상태까지 갖기: 게이트웨이가 문의 단계·알림을 알아야 해서 의존이 양방향이 된다.

## R2. 1차 가명화 — 학급 명단 사전

- **Decision**: 정리할 때마다 문의의 **접수 시점 학급**(`inquiry.classroom_id`)을 기준으로 사전을 만든다.

  | 종류 | 토큰 | 출처 |
  |---|---|---|
  | 학생 이름 | `[STUDENT_n]` | 학급의 학생 전체(`school` port.in) |
  | 보호자 이름 | `[GUARDIAN_n]` | 학급 학생의 연결 보호자 계정 이름(`account` port.in) |
  | 교사 이름 | `[TEACHER_n]` | 현재 담임의 계정 이름 + 교사 프로필 표시 이름 |
  | 학급 | `[CLASS_n]` | 학급의 학년·반("3학년 2반", "3-2", "3학년", "2반") |
  | 학교 | `[SCHOOL_n]` | 서버 리소스 `pseudonymization/school-terms.txt`(학교 이름과 줄임말, 한 줄에 하나) |

  - 이름 변형: 성+이름 전체와, 3자 이름의 이름 부분(앞 1자를 뺀 2자)을 함께 넣는다("김민준", "민준"). "민준이", "민준아"처럼 조사·호격이 붙어도 포함 검사로 함께 가려진다.
  - 긴 값부터 먼저 바꾼다(겹침 방지). 같은 값은 한 문의 안에서 같은 토큰이다(FR-004).
  - **표기마다 토큰을 따로 매기고, 복원하면 본 표기 그대로 돌아온다** [자체 코드 리뷰, 방향 A]. "김민준"과 "민준이"는 다른 토큰이다. 한 사람을 한 토큰으로 묶어 정식 이름으로 복원하면 "하나만"(일반어)이 "이하나만"으로, "2학년"이 "2학년 3반"으로 바뀌어 번역본의 뜻이 달라진다. 동일인이 두 토큰으로 보이는 쪽이 사실이 바뀌는 쪽보다 덜 위험하다.
  - 숫자로 시작·끝나는 표기("2반", "3-2")는 숫자 경계, 영문으로 시작·끝나는 표기("EN")는 영문·숫자 경계를 건다. "13반"·"OPEN" 안에서 매치되지 않는다. 이미 들어간 토큰 안은 건너뛴다.
  - 학생 이름은 F1부터 암호화 저장(`SensitiveText`)이라 사전을 만드는 순간에만 메모리에서 복호화한다.
  - 사전 재료 중 학생·보호자·교사·학급은 `inquiry`가 `school`·`account`의 `port.in`으로 모아 넘기고, **학교 용어는 `gateway` 서비스가 자기 리소스(`SchoolTermsSource`)에서 직접 읽어 합친다**. `inquiry`는 `gateway`의 `port.out`을 참조하지 않는다(헌법 아키텍처: 다른 도메인은 `port.in`으로만, analyze C1).
  - `school-terms.txt`가 비었거나 못 읽으면 **기동 거부**(긴급 표현 목록과 같은 방식). 학교 이름은 1개교 구조에서 데이터로 없으므로 파일로 받는다. 학교 이름은 가명 **"한양초등학교"**로 한다 [팀 결정 2026-10-25: D5]. 파일에는 학부모가 쓸 법한 표기 변형(띄어쓰기·줄임말)을 한 줄씩 적는다: `한양초등학교` / `한양 초등학교` / `한양초교` / `한양초` [팀 결정 2026-10-25: D5].
- **Rationale**: 원칙 II의 가명화 대상(학생·보호자·교사·학교·학급)을 모두 덮는다. 이름 부분만 쓰는 경우("민준이가")가 흔하다.
- **Alternatives considered**:
  - 형태소 분석으로 인명 추출: 의존성 부담이 크고, 놓치면 유출이다. 사전 + 패턴이 기획서 방식이다.
  - 학교 전체 학생 사전: 1개교라 가능하지만 사전이 커져 일반 단어가 과하게 가려진다. 학급 기준으로 시작하고 대표 사례로 점검한다.
- **알려진 한계**: 명단에 없는 사람(다른 반 친구, 학원 강사 등)의 이름과 패턴에 맞지 않는 식별정보는 가려지지 않는다(spec Edge Cases). 이 한계를 `docs/security-checklist.md` L3 근거에 적고, 대표 사례 모음(`src/test/resources/pseudonymization/cases.txt`)으로 무엇이 가려지고 무엇이 남는지 테스트로 고정한다.

## R3. 2차 가명화 — 패턴

- **Decision**: **1차보다 먼저** 정규식으로 찾아 바꾼다 [자체 코드 리뷰]. 1차를 먼저 하면 학급 표기("1-1", "4-5")가 주민번호·전화번호 안을 먼저 바꿔 번호 패턴이 깨지고 번호가 그대로 나갔다.
  - `[PHONE_n]`: 휴대전화(`01[016789]`)·지역번호(`0[2-6][0-9]?`) + 구분자(하이픈·점·공백·없음) 변형
  - `[ADDRESS_n]`: 도로명(`…로`·`…길` + 번호), 지번(`…동`·`…리` + 번지), 아파트 동·호(`101동 202호`)
  - 과잉 가림은 안전한 쪽이므로 **이메일 주소**와 **주민등록번호 형태**도 함께 가린다(`[EMAIL_n]`, `[ID_NUMBER_n]`). spec의 대상(전화번호·주소)보다 넓다.
  - 입력에 이미 `[STUDENT_1]` 같은 토큰 모양 문자열이 있으면 가명화 전에 대괄호를 다른 문자(`［ ］`, 전각)로 바꿔 둔다. 학부모가 쓴 글이 복원 단계에서 다른 사람 이름으로 바뀌는 일을 막는다.
- **Rationale**: 원칙 II(패턴 탐지로 찾은 전화번호·주소).

## R4. 가린 뒤 자기 점검 — 못 가렸으면 보내지 않는다

- **Decision**: 가명화 결과에 사전의 원래 값이 하나라도 남아 있거나 2차 패턴이 다시 매치되면 외부 AI를 **호출하지 않고** `MASKING_FAILED`로 실패 처리한다(FR-005). 가명화 중 예외도 같다.
- **Rationale**: 가명화 코드의 버그가 곧 유출이 되지 않게 한 번 더 막는다(fail-closed).

## R5. 외부 AI 호출 — OpenAI API, Structured Outputs(strict) [팀 결정 2026-10-25: D1 RestClient, D2 모델은 환경변수]

- **Decision**:
  - 공급자: OpenAI API. F1부터 `DAMSO_OPENAI_API_KEY`가 필수 시크릿이고 README 기술 스택에도 있다.
  - 호출 방식: **Spring `RestClient`로 HTTP API를 직접 호출**한다(새 의존성 없음, `RestTemplate`은 쓰지 않는다) [팀 결정 2026-10-25]. 요청은 JSON 스키마를 강제하는 Structured Outputs(`strict: true`)로 보내고, 결과도 서버에서 다시 검증한다(R6).
  - 모델: 설정 `damso.openai.model` ← 새 필수 환경변수 `DAMSO_OPENAI_MODEL` [팀 결정 2026-10-25]. 모델 이름을 코드에 박지 않고, 어떤 모델을 쓸지는 배포 환경에서 정한다. 비어 있으면 기동 거부(원칙 VIII와 같은 방식).
  - 시스템 지시와 사용자 입력 분리: 지시는 서버 리소스의 고정 문구(`gateway/prompts/refine.txt`, `classify-crisis.txt`)를 system 역할로, 가명화된 본문은 user 역할로만 보낸다. 본문을 지시 문자열에 이어 붙이지 않는다(백엔드 요건, security-checklist L1).
  - 대상 주소는 코드 상수(`https://api.openai.com`)로 고정한다. 설정·입력으로 바꿀 수 없다(security-checklist 1-12 SSRF).
  - 요청에 공급자 측 저장을 끄는 옵션(`store: false`)을 넣는다.
  - 시간 제한: 연결 3초, 응답 15초. 이 15초가 spec FR-013a의 "처리 시간 상한"이다. 넘으면 `NO_RESPONSE`.
  - 로그: 요청·응답 본문은 어떤 로그에도 남기지 않는다. 오류는 HTTP 상태 코드와 실패 사유 코드만 남긴다(공급자의 오류 본문도 입력을 되풀이할 수 있으므로 버린다).
- **Rationale**: 백엔드 보안 요건(Structured Outputs strict + 서버 재검증, 역할 분리). `RestClient`는 Spring Boot에 이미 있어서 시간 제한·오류 처리를 직접 통제하고 테스트에서 포트를 가짜로 바꾸기 쉽다.
- **Alternatives considered**:
  - OpenAI 공식 Java SDK: 스키마·재시도가 편하지만 새 의존성이고 내부 재시도·로깅을 따로 꺼야 한다. 재시도 횟수는 spec이 정한 1회여야 하므로 SDK의 자동 재시도와 겹치지 않게 통제하기 쉬운 쪽을 고른다.
  - Spring AI: 추상화가 크고 버전 변경이 잦다. 호출 지점이 둘뿐이라 이점이 작다.

## R6. 출력 검증과 복원 — 복원은 처리 중 메모리에서, 저장은 복원된 값을 암호화

- **Decision**:
  - **서버 재검증**(하나라도 어기면 결과 전체 폐기, `INVALID_OUTPUT`):
    - 필드: `title`(1~30자), `facts`(1~1,000자), `requests`(0~1,000자), `emotionLevel`(`LOW`·`MEDIUM`·`HIGH`), `emotionLabels`(고정 6개 중 0~2개, 중복 없음). 그 밖의 필드가 있으면 위반.
    - 토큰: 결과 안의 `[KIND_n]` 모양 문자열은 이번 가명화가 만든 토큰이어야 한다. 모르는 토큰이 있으면 위반.
    - 위기 종류 분류: `crisisType` ∈ {`SELF_HARM`, `ABUSE`, `OTHER_SAFETY`}만. 그 밖은 버림(F2 `CrisisClassificationUseCase`가 한 번 더 막는다).
  - **복원**: 검증을 통과하면 같은 처리 안에서 대응표로 토큰을 원래 값으로 바꾸고, 대응표는 버린다. **복원된 결과를 `SensitiveText`로 암호화해 저장**한다.
  - **연락처·식별번호는 복원하지 않는다** [실호출 점검 2026-10-25]: 전화·이메일·주소·주민번호 토큰은 원래 값 대신 고정 문구("(연락처 생략)", "(주민등록번호 생략)")로 남긴다. 실제 호출에서 "010-…로 연락 가능하다고 했다"가 사실관계로 복원돼 정리 결과에 연락처가 새로 저장되는 것을 확인했다(원칙 I). 시스템 지시에도 연락처 안내를 정리하지 말라고 넣었다.
    - spec Key Entities의 "토큰은 표시할 때만 되돌린다"는 이 결정으로 고친다: 대응표를 저장할 수 없으므로(원칙 II) 표시할 때는 되돌릴 방법이 없다. 저장 시점에 되돌리고 암호화한다.
  - 정리 결과는 저장·표시 용도로만 쓴다. 조회 조건·경로·권한 판단에 쓰지 않는다(FR-010).
- **Rationale**: 원칙 II(대응표 메모리 전용)와 III(정제본 append-only)을 함께 지키는 유일한 방법이다. 복원된 결과는 원문과 같은 수준(AES-256-GCM)으로 보호된다.
- **Alternatives considered**: 토큰 상태로 저장하고 표시할 때 명단으로 다시 복원 — 대응표를 다시 만들면 그사이 명단이 바뀌면(학생 전입·개명) 엉뚱한 이름이 나온다. 대응표를 저장하는 것은 원칙 II 위반이다.

## R7. 비동기 처리 — 정리 작업 아웃박스 + 커밋 직후 즉시 실행 + 10초 안전망

- **Decision**: F2 알림(R7)과 같은 모양이다.
  - 일반 문의 접수 트랜잭션에서 `refinement_job(kind = REFINE, status = PENDING)`을 함께 저장한다.
  - 커밋 직후 이벤트(`@TransactionalEventListener(AFTER_COMMIT)` + 비동기)로 그 작업을 바로 한 번 실행한다. 안전망으로 10초 주기 실행기가 `PENDING ∧ scheduled_at ≤ now`(또는 임대가 끝난 `RUNNING`)를 `FOR UPDATE SKIP LOCKED`로 집는다.
  - 작업 확보는 짧은 트랜잭션(`RUNNING`, `locked_until = now + 60초`)으로 하고, **외부 AI 호출은 트랜잭션 밖에서** 한다.
  - [자체 코드 리뷰] 안전망은 작업을 **하나씩** 확보해 처리한다(한 건 최악 약 36초라 여러 개를 한꺼번에 임대하면 뒤의 임대가 끝난다). 반영 직전에 임대가 그대로인지(내가 잡은 작업인지) 확인하고, 아니면 반영하지 않는다. 반영이 낙관적 락 충돌이면 한 번 다시 반영한다. 같은 작업이 5번을 넘게 확보되면(반영이 계속 실패) 외부 AI를 다시 부르지 않고 실패로 끝낸다(일반 정리는 "원문 확인 필요"로 확정·알림). 결과 반영(결과 저장·단계 전이·알림 요청)은 다시 짧은 트랜잭션 하나로 한다.
  - 긴급 문의는 정리 작업을 만들지 않는다. 대신 `kind = CLASSIFY_CRISIS` 작업을 만든다(R11).
- **Rationale**: 접수 응답이 외부 AI를 기다리지 않는다(FR-006). 서버가 중간에 재시작돼도 작업이 DB에 남는다. 커밋 직후 즉시 실행이라 평균 10초 목표(FR-031)를 맞출 수 있고, 안전망 주기가 10초라 최악에도 크게 늦지 않다. 같은 작업을 두 경로가 동시에 집어도 `SKIP LOCKED`라 한 번만 돈다(Edge Case: 중복 시작).
- **Alternatives considered**: 접수 요청 안에서 동기 호출 — 학부모가 외부 AI 응답을 기다리고, AI 장애가 접수 실패처럼 보인다. 메시지 브로커 — F2 R7과 같은 이유로 보류.

## R8. 재시도 — 자동 1회 + 교사 1회

- **Decision**:
  - 1차 시도가 `INVALID_OUTPUT`·`NO_RESPONSE`·`MASKING_FAILED`면 같은 실행 안에서 **곧바로 1회 자동 재시도**한다. 둘 다 실패하면 작업 `FAILED`, 문의 `refinement_status = NEEDS_ORIGINAL`, 교사 알림 요청(R10).
  - 교사 재시도(`POST …/refinement/retry`): `NEEDS_ORIGINAL`이고 `teacher_retry_used = false`일 때만. 같은 트랜잭션에서 `teacher_retry_used = true`, 작업을 `PENDING`으로 되돌린다. 동시 요청은 작업 행의 `@Version`으로 하나만 성공한다(나머지는 409). 교사 재시도는 **자동 재시도 없이 1회**만 시도한다.
  - 한 문의의 시도는 최대 3회(자동 2 + 교사 1)이며, 시도마다 `refinement_attempt`에 결과 코드를 덧붙인다(SC-006).
  - `DAMSO_OPENAI_API_KEY`나 모델 설정이 없으면 기동 자체를 거부하므로(R5), "설정 없음" 실패는 런타임에 생기지 않는다. spec FR-030의 "설정이 없으면 실패 처리"는 기동 거부로 더 강하게 지킨다.
- **Rationale**: 팀 결정(자동 1회 + 교사 1회). 시도 기록에는 내용 없이 코드만 남긴다.

## R9. 처리 단계 전이와 "교사 확인" [clarify Q3: A]

- **Decision**: `InquiryStage.canMoveTo`를 다음으로 바꾼다. `REFINING`은 "정제(완료)" 단계이며 화면 이름은 "정제"다(F2에서 정한 enum을 그대로 쓴다).

  ```
  일반:  RECEIVED ─(정리 성공)→ REFINING ─(번역본 첫 열람)→ TEACHER_CONFIRMED ─(F4)→ ANSWERED
         RECEIVED ─(정리 실패 확정 문의의 원문 첫 열람)→ TEACHER_CONFIRMED          ← 새 전이, NEEDS_ORIGINAL일 때만
  긴급:  RECEIVED → DELIVERED → TEACHER_CONFIRMED → ANSWERED (변경 없음)
  ```

  - `RECEIVED → TEACHER_CONFIRMED`는 문의의 `refinement_status = NEEDS_ORIGINAL`일 때만 허용한다(도메인 메서드가 상태를 확인).
  - 번역본 열람(`GET …/refinement`)은 감사 기록 대상이 아니다(spec FR-026). 첫 열람이 단계 이력에 `TEACHER_CONFIRMED`로 남는다.
  - 원문 열람(`GET …/content`)은 일반 문의에도 열린다. 감사 기록은 F2 방식(시도 → 완료/실패 3단계, 시도 기록 선커밋) 그대로다. 번역본이 있는 문의는 원문을 열람해도 단계가 바뀌지 않는다.
  - 교사 재시도가 `TEACHER_CONFIRMED` 이후에 성공하면 결과는 저장되지만 `REFINING` 단계는 남기지 않는다(단계는 앞으로만).
  - 동시 첫 열람은 F2의 `InquiryConfirmationStep`과 같은 방식(낙관적 락 충돌은 이미 확인된 것으로 보고 열람을 계속)으로 한 번만 기록된다.

## R10. 일반 문의 알림을 정리 확정 뒤로 [clarify Q2: B, F2 규칙 변경]

- **Decision**:
  - 접수 트랜잭션에서는 일반 문의 알림 요청을 **만들지 않는다**. 대신 `NotifyInquiryUseCase.previewGeneral(teacherId)`(새 메서드, 저장 없음)로 "지금이면 미뤄질지"만 보고 학부모 안내(`DEFERRED_TO_AVAILABLE_HOURS`) 여부를 정한다.
  - 정리가 확정되는 트랜잭션(성공 또는 `NEEDS_ORIGINAL` 확정)에서 기존 `requestGeneral(inquiryId, teacherId)`를 호출한다. 응답 가능 시간·일반 알림 끔·묶음 규칙은 F2 그대로이고 기준 시각만 "정리 확정 시각"이 된다. `DEFERRED`면 그때 `inquiry.notification_deferred = true`(묶음 건수 집계 대상)로 표시한다.
  - 학부모에게 접수 때 보인 안내와 재전송 응답이 같아야 하므로(F2 FR-002a), 접수 때 정한 안내 여부를 새 컬럼 `inquiry.deferred_notice`에 남기고 학부모 응답은 이 값으로 만든다. `notification_deferred`는 이제 "실제 알림이 미뤄졌는지"만 뜻한다.
  - 교사 재시도 성공으로는 추가 알림을 만들지 않는다(spec FR-033).
  - F2에서 바뀌는 것: `SubmitInquiryService.persist`의 일반 알림 호출, `ParentInquiryService`·`SubmitInquiryService.resultOf`의 안내 기준 컬럼, F2 spec FR-027·028·030과 SC-007의 문구(tasks에서 함께 고친다).
- **Rationale**: 알림을 받고 연 교사가 빈 화면을 보지 않는다(SC-014). F2 알림 아웃박스·묶음 로직을 그대로 재사용한다.
- **배포 시 기존 데이터(FR-035)**: 아직 운영 배포 전이라 대기 중인 F2 일반 알림은 개발 데이터뿐이다. 이관 코드를 만들지 않고, F3 배포 전에 개발 DB를 비우는 것으로 갈음한다. F3 이전에 접수된 일반 문의도 정리 대상에 넣지 않는다(spec Assumptions) [팀 결정 2026-10-25: D5]. 운영 데이터가 생긴 뒤에 같은 종류의 변경을 한다면 그때는 이관이 필요하다.

## R11. 위기 종류 분류 [clarify Q1: A]

- **Decision**: 긴급 문의 접수 트랜잭션에서 `refinement_job(kind = CLASSIFY_CRISIS)`을 함께 저장한다. 실행 경로·확보 방식은 R7과 같다. 실행은 `ClassifyCrisisGateway`(가명화 → 외부 AI → `crisisType` 허용값 검증)를 거쳐 F2의 `CrisisClassificationUseCase.apply(inquiryId, rawType)`를 부른다.
  - **한 번만 시도**한다. 실패·시간 초과·허용값 위반이면 `FAILED`로 끝내고 문의는 `UNCLASSIFIED`(기본 안내 112·109)로 남는다.
  - 긴급 알림(F2 `UrgentDispatchTrigger`)과 분류 작업은 서로 기다리지 않는다. 같은 커밋 이벤트에 각자 붙는다.
  - 결과는 학부모 안내 선택에만 쓰인다(F2 `GuidancePolicy`). 교사 화면에는 보이지 않는다.
- **Rationale**: 긴급 경로의 fail-open(원칙 IV)을 해치지 않는다. 분류가 없어도 F2 기본 안내가 이미 안전하다.

## R12. API와 에러 코드 (contracts/refinement.openapi.yaml)

- **Decision**:
  - `GET /api/v1/teacher/inquiries/{id}/refinement` — 번역본 열람. 첫 열람이면 `TEACHER_CONFIRMED`. 아직 없으면 409 `REFINEMENT_NOT_READY`.
  - `POST /api/v1/teacher/inquiries/{id}/refinement/retry` — 교사 재시도. 202. 할 수 없으면 409 `REFINEMENT_RETRY_UNAVAILABLE`.
  - `GET /api/v1/teacher/inquiries/{id}/content` — 일반 문의도 열람 가능. `INQUIRY_CONTENT_UNAVAILABLE`은 더 이상 쓰지 않는다(코드 정의는 남기고 응답 목록에서 뺀다).
  - 교사 목록·상세에 `refinementStatus`(`PENDING`·`REFINED`·`NEEDS_ORIGINAL`, 긴급은 null), 상세에 `retryAvailable`을 더한다. 제목 고정 문구에 "원문 확인 필요"를 더한다.
  - 학부모 API의 형태는 바뀌지 않는다. 타임라인에 `REFINING`·`TEACHER_CONFIRMED`가 나타날 뿐이다. 정리 결과·실패 사유는 학부모 응답 어디에도 없다.
  - API 버전 0.3.0 → **0.4.0**(앱 계약 변경) [팀 결정 2026-10-25: D4]. contracts 사람 리뷰 후 `docs/api/openapi.yaml`에 통합하고 `api-v0.4` 태그를 찍는다.
  - 권한·거부 방식은 F2와 같다(현재 담임만, 남의 문의 404, 슈퍼 관리자 403).

## R13. 로그·보안 점검

- **Decision**:
  - 원문·가린 본문·정리 결과·제목·대응표는 로그·예외 메시지·에러 응답에 넣지 않는다. 가린 본문과 대응표는 `toString`을 가리는 값 객체로만 다룬다(F2 `SensitiveText`와 같은 방식).
  - `RestClient` 요청·응답 로깅을 켜지 않는다. 외부 AI 오류는 상태 코드와 사유 코드만 WARN.
  - 로그 캡처 테스트(F2 `InquirySensitiveLoggingTest` 방식)에 정리 성공·실패·재시도·분류 경로를 더한다.
  - security-checklist 갱신 대상: 1-1(LLM 출력을 쿼리에 안 씀), 1-12(SSRF, 고정 대상), L1·L2·L3.

## R14. 성능

- **Decision**: 접수 → 번역본 표시 평균 10초 목표(FR-031)는 커밋 직후 즉시 실행(R7) + 외부 AI 응답 시간으로 결정된다. 테스트에서는 가짜 `ExternalAiPort`로 파이프라인 자체의 지연(가명화·검증·저장)이 1초 미만인지 확인하고, 실제 외부 AI 지연은 quickstart의 수동 점검으로 잰다.
