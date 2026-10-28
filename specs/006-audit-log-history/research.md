# Research: 감사 로그·이력 보존 (F5)

**Feature**: `specs/006-audit-log-history` · **Date**: 2026-10-27

F2가 만든 뼈대(`audit_log`, `inquiry_stage_history`)와 F3·F4가 남기는 불변 기록(`refinement_attempt`, `answer`)을 먼저 조사했다. 결론은 **새 테이블 없이, 빠진 "사람의 행위"만 `audit_log`에 더하고, 나머지는 기존 기록을 조회문으로 엮는다**이다.

## R1. 기록을 어디에 남기나 — 기존 기록 재사용 + `audit_log` 확장

- **Decision**: FR-001의 행위를 아래처럼 나눈다. "이미 불변 기록이 있음"인 행위는 다시 저장하지 않는다.

  | 행위 (FR-001·002·003) | 행위자 | 이미 있는 기록 | F5에서 |
  |---|---|---|---|
  | 최초 문의·재문의 접수 | 학부모 | `inquiry`(parent_id·submitted_at, 불변 컬럼) + `inquiry_stage_history` RECEIVED | 그대로 |
  | 긴급 판정·교사 전달 | 시스템 | `inquiry_stage_history` DELIVERED | 그대로 |
  | 자동 정리 완료 | 시스템 | `refinement_attempt` SUCCESS + `inquiry_stage_history` REFINING | 그대로 |
  | 자동 정리 실패("원문 확인 필요") | 시스템 | `refinement_attempt` 실패 코드(AUTO·AUTO_RETRY) | 그대로 |
  | 교사 재시도 **요청** | 교사 | 없음 (`refinement_job.teacher_retry_used`는 덮어쓰는 플래그, 누가 했는지 없음) | **추가** `REFINEMENT_RETRY_REQUESTED` |
  | 교사 재시도 **결과** | 시스템 | `refinement_attempt` trigger=TEACHER | 그대로 |
  | 교사 확인 | 교사(열람이 계기) | `inquiry_stage_history` TEACHER_CONFIRMED + 열람 감사 기록 | 그대로 |
  | 원문 열람 | 교사 | `audit_log` INQUIRY_CONTENT_VIEW_* | 그대로 |
  | 번역본 열람 | 교사 | 없음 | **추가** `INQUIRY_REFINEMENT_VIEW_*` (F3 코드 변경) |
  | 답변 점검 요청 | 교사 | 없음 (`answer.reviewed`는 전송된 답변에만 있고, 요청만 하고 보내지 않은 경우가 빠짐) | **추가** `ANSWER_REVIEW_REQUESTED` (F4 코드 변경) |
  | 답변 전송 | 교사 | `answer`(teacher_id·sent_at·reviewed, 불변 컬럼) | 그대로 |
  | 답변 완료(종료) — 앱 미사용 | 교사 | `inquiry_stage_history` ANSWERED (행위자 없음) | **추가** `INQUIRY_CLOSED` (F4 코드 변경) |

- **Rationale**: 같은 사실을 두 곳에 쓰면 한쪽만 남는 경우(부분 실패)에 "어느 쪽이 맞나"라는 새 분쟁 거리가 생긴다. 시스템 행위는 이미 행위자가 정해져 있으므로(`inquiry_stage_history`·`refinement_attempt`에는 사람이 없다) `audit_log`에 "시스템" 행위자를 넣을 필요도 없다. 그래서 `audit_log.actor_id NOT NULL`을 그대로 둔다.
- **Alternatives considered**:
  - 모든 행위를 `audit_log` 하나에 다시 쓰기 → 중복 저장, F2~F4 전 서비스에 감사 호출을 끼워 넣어야 해 변경 범위가 커짐. 기각.
  - "시스템" 행위자용 고정 UUID(nil UUID) → 시스템 행위를 `audit_log`에 넣지 않으므로 필요 없음.

## R2. 종료("답변 완료") 기록 — 앱 미사용 결정 반영

- **Decision**: `CloseInquiryService`가 성공하면 커밋 뒤 `INQUIRY_CLOSED`(행위자=교사, 대상=스레드 루트, 시각=종료 시각)를 남긴다(R9). 기록이 실패해도 종료는 그대로다. 종료 API·FR-021·FR-022 동작은 바꾸지 않는다. 앱은 이 API를 호출하지 않으므로(`docs/decisions.md` 2026-10-27, PR #19) 실제로는 거의 남지 않는 기록이다.
- **Rationale**: 경로가 살아 있는 한 호출될 수 있고(앱 밖 호출·향후 재사용), 호출되면 누가 닫았는지가 분쟁 기록에 필요하다. `inquiry_stage_history` ANSWERED에는 행위자가 없다.
- **영향**: 앱의 "답변 완료" 태그는 "답변을 보냈다"는 뜻이다. 그래서 학부모·교사 타임라인에 ANSWERED가 나오는 일은 거의 없고, 앱 흐름의 마지막 단계는 TEACHER_CONFIRMED다. 슈퍼 관리자 현황의 "답변함" 건수는 단계(ANSWERED)가 아니라 **답변 전송 여부**로 센다(R6).
- **참고**: PR #19는 2026-10-27 기준 OPEN이다. 이 결정은 그 PR의 `docs/decisions.md` 행을 따른다.

## R3. 번역본 열람 감사 — 원문 열람과 같은 순서 (F3 변경)

- **Decision**: `ViewRefinementService.view`를 `ViewInquiryContentService`와 같은 순서로 바꾼다. ① 담임 확인(404) ② 번역본 준비 확인(409 `REFINEMENT_NOT_READY`, **기록 없음**) ③ `INQUIRY_REFINEMENT_VIEW_ATTEMPTED` ④ 첫 열람 확인 전이 ⑤ `INQUIRY_REFINEMENT_VIEWED` ⑥ 반환. ④에서 낙관적 락이 아닌 오류가 나면 `INQUIRY_REFINEMENT_VIEW_FAILED`를 남기고 전파한다(500, 번역본 미반환 — 감사 실패가 아니라 단계 전이 실패라서 F3 동작 그대로).
- **감사 기록 실패 시 [팀 결정 2026-10-27]**: ③·⑤의 기록 저장이 실패해도 번역본은 정상 반환한다. 실패는 `WARN` 로그로 남기며 로그에는 `inquiryId`(UUID), 행위 종류(`AuditAction` 이름), 오류 종류(예외 클래스 단순 이름)만 넣는다. 예외 객체·메시지·스택은 넣지 않는다(드라이버 메시지에 바인딩 값이 섞일 수 있음, 원칙 VI). ③이 실패해도 ④·⑤는 그대로 진행한다.
  - 근거: 원칙 III의 MUST는 **원문** 열람 기록이다. 번역본은 가명화·정리를 거친 결과라 기록 실패로 교사의 확인 업무를 막지 않는다. 원문 열람(`ViewInquiryContentService`)은 지금처럼 기록이 실패하면 원문을 내보내지 않는다.
  - 한계: 기록 저장소 장애 동안의 번역본 열람은 `audit_log`에 빠질 수 있다. 서버 로그로만 남으므로 운영자 조회문 Q1에는 나오지 않는다. 운영자는 장애 시간대의 서버 로그를 함께 본다.
- **Rationale**: spec US2-7 — 번역본이 없으면 내보낸 게 없으므로 "열람" 기록을 남기지 않는다. 시도·성공·실패 3단은 F2 R11과 같은 방식이라, 운영자 조회문이 원문·번역본을 같은 규칙으로 읽을 수 있다.
- **거부된 요청(404)은 기록하지 않는다**: ①에서 거부되면 내용에 닿지 않았으므로 "시도"가 아니다(spec FR-002의 "열람 시도"는 담임 확인을 통과한 시점). 원문 열람도 F2부터 같은 규칙이다(`TeacherInquiryApiTest.formerHomeroomTeacherLosesAccessAfterRelease`가 404 뒤 `audit_log` 0건을 단언한다).
  - 없는 문의까지 기록하면 대상 식별자에 요청자가 보낸 임의 UUID가 쌓이고, 교사 토큰 하나로 기록을 무한히 늘릴 수 있다.
  - 있는 문의(다른 학급)만 기록하면 기록 경로가 문의 존재 여부에 따라 갈린다. 기록 저장이 실패할 때 응답이 달라지면(500 대 404) 존재 여부가 응답으로 샌다.
  - 그래서 두 경우 모두 기록 없이 같은 404를 낸다. 다른 학급 열람 시도를 감시하는 일은 감사 기록이 아니라 보안 로그·탐지의 범위로 남긴다(F5 범위 밖).
- **범위 밖**: 교사 목록·상세의 `title`(번역본에서 만든 제목)은 목록 표시용 요약이라 감사 대상에 넣지 않는다. 사실관계·요청사항·정서강도는 `/refinement`로만 나가므로 열람 기록이 빠지지 않는다.
- **Alternatives considered**: 성공(VIEWED)만 남기기 → 확인 전이 중 실패했을 때 "열람 시도는 있었는데 나가지 않았다"를 구분할 수 없음. 기각.

## R4. 답변 점검 요청 감사 — 읽기 전용 트랜잭션 문제 (F4 변경)

- **Decision**: `RequestAnswerReviewService`의 `@Transactional(readOnly = true)`를 없애 바깥 트랜잭션을 두지 않고, 점검 포트를 부르기 **전에** `ANSWER_REVIEW_REQUESTED`를 남긴다(감사 기록은 자기 트랜잭션). **기록이 실패해도 점검 요청은 정상 처리하고** `WARN` 로그(inquiryId·행위 종류·오류 종류만)를 남긴다 [팀 결정 2026-10-28]. 답변 전송(`SendAnswerService`)은 원래부터 무관하다.
- **바깥 트랜잭션을 없앤 이유**: 쓰기 트랜잭션 안에서 감사 INSERT가 실패하면 예외를 잡아도 트랜잭션이 롤백 전용이 되어 커밋에서 500이 난다. 재시도·종료도 같은 이유로 처리 트랜잭션 밖(커밋 뒤)에서 기록한다(R9).
- **Rationale**: 지금처럼 읽기 전용 트랜잭션 안에서 `AuditLogUseCase.record`를 부르면 그 트랜잭션에 참여해 INSERT가 거부된다(PostgreSQL read-only 트랜잭션). 점검은 선택 기능이고 전송 경로와 분리돼 있으므로, 기록 실패 시 점검만 실패하는 것은 원칙 V·F4 FR-012(fail-open)와 충돌하지 않는다. F4 FR-012의 fail-open은 "점검 서비스 장애"에 관한 것이고 그대로 유지된다.
- **Alternatives considered**: `REQUIRES_NEW`로 감사만 별도 트랜잭션에 쓰기 → `AuditLogUseCase`의 "호출한 트랜잭션에 참여" 계약과 달라지고, 한 곳만 예외가 됨. 기각.

## R5. 같은 시각 기록의 순서 (FR-009) — 스키마 변경 없이

- **Decision**: 정렬 키를 `(occurred_at, seq, 대상 식별자)`로 정한다. `seq`는 같은 시각에 남은 기록끼리의 **논리 순서**이고, 운영자 조회문 Q1에 표로 적는다(`contracts/operator-queries.md`).
  - 접수 → RECEIVED → DELIVERED → 교사 재시도 요청 → 정리 시도(AUTO → AUTO_RETRY → TEACHER) → REFINING → 열람 시도(`*_ATTEMPTED`) → TEACHER_CONFIRMED → 열람 완료·실패와 그 밖의 사람 행위 → 답변 전송 → ANSWERED → 종료 기록(`INQUIRY_CLOSED`)
  - 학부모·교사 타임라인은 단계 이력만 쓰므로 지금처럼 `(occurred_at, InquiryStage 선언 순서)`를 그대로 쓴다(`InquiryPersistenceAdapter.findByInquiry`).
- **Rationale**: 같은 시각은 한 트랜잭션이 같은 `now`를 쓸 때 생긴다.
  - 접수: 문의 저장과 RECEIVED(긴급이면 DELIVERED까지)가 같은 `now`다(`SubmitInquiryService`·`SubmitFollowUpService`).
  - 자동 정리: 그 회차의 시도 기록 전부와 REFINING이 같은 `now`다(`RefinementRunner.confirm`). 그래서 시도는 단계보다 **앞**이어야 하고, 시도끼리는 trigger 순서로 정한다.
  - 교사 재시도: 재시도는 커밋 직후 같은 HTTP 요청 안에서 실행되고, 요청 기록(`REFINEMENT_RETRY_REQUESTED`)은 그 뒤에 남지만 시각은 요청 시각이다(R9). 시계가 같으면 요청이 시도보다 앞이어야 한다(T012에서 발견해 반영).
  - 첫 번역본·원문 열람: 열람 시도 → 확인 전이(TEACHER_CONFIRMED) → 열람 완료 순서로 일어난다. 시계가 같은 값을 돌려줘도 이 순서가 유지돼야 "확인한 뒤에 봤다"로 읽히지 않는다.
  - 서로 다른 요청이 같은 마이크로초에 일어난 경우는 동시 행위라 어느 쪽을 먼저 둬도 사실과 어긋나지 않는다.
- **검증**: `OperatorQueriesTest`가 고정 시계로 위 경우를 만들어 순서를 단언한다(tasks).
- **Alternatives considered**: `audit_log`에 순번 컬럼(bigserial) 추가 → **DB 스키마 변경**이고 F2 테이블까지 바꿔야 함. 순번은 외부에 노출하지 않더라도 원칙 VII의 취지("순번 ID 대신 UUID")와도 어긋남. 기각.

## R6. 슈퍼 관리자 현황 — 긴급 여부가 드러나지 않는 상태 묶음

- **Decision**: 스레드(루트 문의) 단위로, 학급마다 다음 3개 묶음의 건수를 준다. 학급은 문의가 없어도 0으로 나온다.

  | 묶음 | 포함 |
  |---|---|
  | `waiting` (교사 확인 전) | 전송된 답변 0건이고 루트 단계 RECEIVED · DELIVERED · REFINING |
  | `confirmed` (교사 확인, 답변 전) | 루트 단계 TEACHER_CONFIRMED 이고 전송된 답변 0건 |
  | `answered` (답변 보냄) | 전송된 답변 1건 이상, 또는 루트 단계 ANSWERED — 먼저 판정(답변은 교사 확인 전에도 보낼 수 있다, F4 research R3) |

- **Rationale**: 단계를 그대로 세면 **DELIVERED가 곧 긴급 건수**가 된다(DELIVERED는 긴급 문의만 거친다). Q1 결정(긴급 건수 미제공)을 지키려면 DELIVERED와 REFINING을 같은 묶음에 넣어야 한다. "원문 확인 필요"(refinement_status)도 내보내지 않는다. "답변 보냄"은 앱의 "답변 완료" 태그(= 답변을 보냈다)와 뜻을 맞춘다(R2).
- **스레드 단위**: 재문의는 따로 세지 않는다. 재문의 수까지 세면 특정 학부모의 문의 빈도를 짐작할 수 있다.
- **Alternatives considered**: 단계 5개 그대로 → 긴급 건수 노출. 기각. 학급 합계만 → features.md "처리 상태"를 충족하지 못함. 기각.

## R7. 운영자 조회 — 문서로 정한 조회문 (Q3 = A)

- **Decision**: `contracts/operator-queries.md`에 매개변수 바인딩 형태의 조회문 2개(스레드 단위·계정 단위)를 둔다. 결과 컬럼은 식별자·종류·시각뿐이고, 본문 컬럼(`content`·`title`·정제 결과)은 어떤 조회문에도 나오지 않는다. 앱 코드에는 조회 API·명령을 추가하지 않는다.
- **Rationale**: Q3 결정. 구현 범위가 가장 작고, 앱 공격 표면이 늘지 않는다.
- **검증**: 조회문을 Testcontainers에서 그대로 실행하는 테스트를 둬서, 스키마가 바뀌면 조회문이 깨지는 것을 CI에서 잡는다.
- **한계**: 운영자 DB 접근 권한(읽기 전용 계정)은 배포 설정 범위다(F1 research R10과 같은 선).

## R8. enum 값 추가와 기존 DB (`ddl-auto=update`) — 구현 첫 단계에서 확인

- **위험**: Hibernate는 `@Enumerated(STRING)` 컬럼에 허용값 CHECK 제약을 만들 수 있다. `ddl-auto=update`는 이미 있는 제약을 고치지 않으므로, **배포된 DB에서는 새 `AuditAction` 값 INSERT가 제약 위반으로 실패할 수 있다.** 새로 만든 DB(테스트 컨테이너)에서는 재현되지 않는다.
- **Decision**: tasks 첫 단계에서 "이전 enum으로 만든 `audit_log`에 새 값을 넣는" 테스트로 실제 동작을 확인한다. 제약이 생긴다면 배포 시 `audit_log_action_check` 제약을 새 값 목록으로 다시 만드는 운영 조치를 quickstart에 적고 **사용자 확인을 받는다**(DB 스키마 조치).
- **참고**: `audit_log.action`은 varchar(40)이라 새 값 길이(최대 `INQUIRY_REFINEMENT_VIEW_ATTEMPTED` 33자)는 문제없다. `AuditTargetType`은 새 값이 없다(모든 대상이 `INQUIRY`).

## R9. 재시도·종료의 감사 기록 실패 — 커밋 후 기록 [팀 결정 2026-10-28]

- **Decision**: 재시도·종료는 처리 트랜잭션이 커밋된 뒤 감사 기록을 남긴다(B안). 기록 시각은 처리 트랜잭션 안에서 정한 값(재시도 요청 시각, 종료 시각)을 `AuditLogUseCase.record(…, occurredAt)`로 넘긴다. 기록이 실패해도 재시도 202·종료 200이고, 실패는 `WARN` 로그(inquiryId·행위 종류·오류 종류만)로 남긴다. `CloseInquiryService`는 `@Transactional` 대신 `TransactionTemplate`을 쓴다.
- **Rationale**: 네 경로(번역본 열람·점검 요청·재시도·종료)가 "처리는 정상, 기록 실패는 로그" 한 규칙이 된다. 종료가 잡은 문의 행 잠금(답변 전송·재문의의 `SELECT … FOR UPDATE`가 기다리는 잠금)은 커밋 때 풀리므로 기록 때문에 잠금 시간이 늘지 않는다. 낙관적 락 충돌은 `saveAndFlush` 때 드러나 기록 전에 실패하므로, 동시 요청에서 진 쪽은 기록되지 않는다.
- **Alternatives considered**: A. 처리 트랜잭션 안에서 `REQUIRES_NEW`로 기록 → 기록이 먼저 커밋된 뒤 처리 트랜잭션이 롤백되면 일어나지 않은 재시도·종료가 기록된다(거짓 기록). 처리 중 커넥션 2개를 함께 쓰고 종료의 행 잠금도 그만큼 오래 쥔다. 제외.
- **한계**: 커밋 직후 기록 전에 서버가 죽으면 그 처리의 감사 기록이 빠질 수 있다(로그도 남지 않는다).

