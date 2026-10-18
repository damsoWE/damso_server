# Research: 문의 접수·긴급 분류 (F2)

**Feature**: [spec.md](./spec.md) · **Date**: 2026-10-18 · **기반**: [001-auth-signup research](../001-auth-signup/research.md)

각 항목은 Decision / Rationale / Alternatives considered 형식이다. CLAUDE.md "큰 영향 결정은 확인 후 진행"(외부 시스템·DB 스키마·시크릿·앱 계약) 대상인 R6·R7·R8과 테이블 추가(data-model)는 **[팀 결정 2026-10-18]**로 확인을 마쳤다.

---

## R1. 도메인 나누기

- **Decision**: 새 도메인 셋을 둔다.
  - `inquiry`: 문의 접수, 긴급 판정, 처리 단계, 교사·학부모 조회, 원문 열람
  - `notification`: 기기 등록, 교사 알림 설정(응답 가능 시간·일반 알림), 알림 요청 저장과 발송
  - `audit`: 원문 열람 감사 기록(F5 감사 로그의 뼈대)
  - 문의 → 알림은 `notification`의 `port.in`(알림 요청 만들기)으로만 호출한다. 문의 → 감사 기록도 `audit`의 `port.in`으로만 한다.
- **Rationale**: 알림 설정·기기 등록은 F4(답변 도착 알림)도 쓰므로 문의 도메인 안에 두면 F4가 문의 내부에 의존한다. 감사 기록은 F3(원문 열람)·F5가 이어서 쓴다.
- **Alternatives considered**: 문의 도메인 하나에 모두 넣기 — 지금은 단순하지만 F4에서 알림 코드를 꺼내야 한다.

## R2. 긴급 판정 — 고정 표현 목록 [clarify 결정]

- **Decision**: `src/main/resources/urgent/expressions.txt`에 표현을 한 줄씩 둔다(분류 태그 없이). 판정은 본문을 정규화(공백·줄바꿈 제거, 소문자, 전각→반각)한 뒤 목록의 표현이 **하나라도 포함되면 긴급**이다. 순수 함수(`UrgentRule`)라 외부 의존이 없고 AI·네트워크 장애와 무관하다. 목록 파일이 비었거나 못 읽으면 기동을 거부한다(긴급 판정이 꺼진 채 서버가 뜨지 않게).
  - 목록 초안과 대표 사례(`must-be-urgent.txt`)는 **구현 전에 팀이 정한다**(아동 안전을 아는 사람이 리뷰, 자해·학대·폭력·실종 등을 포괄). 구현자가 혼자 만든 목록을 구현자의 테스트로만 검증하지 않는다. 변경은 PR 리뷰와 배포로만 하고, 목록 파일에서 대표 문장들을 읽어 "모두 긴급"인지 확인하는 테스트가 함께 돈다(FR-009a).
- **Rationale**: 원칙 IV(LLM 이전·규칙 기반·보수적). 포함 검사는 오탐이 있어도 놓침을 줄이는 쪽이다. 정규화로 띄어쓰기 변형("죽고 싶어"/"죽고싶어")을 같이 잡는다.
- **Alternatives considered**:
  - 정규식 목록: 표현력이 크지만 리뷰하는 사람이 실수하기 쉽다. 필요하면 나중에 추가한다.
  - 형태소 분석기: 의존성·성능 부담 대비 MVP 이점이 작다.

## R3. 위기 종류 분류 연결 지점 [clarify Q1: C]

- **Decision**: `inquiry.application.port.in.CrisisClassificationUseCase.apply(inquiryId, String rawType)`을 둔다. 허용값(`SELF_HARM`, `ABUSE`, `OTHER_SAFETY`) 외의 값은 버리고 `UNCLASSIFIED`로 남긴다. F2에서는 아무도 호출하지 않으며, F3가 가명화 게이트웨이를 거친 분류 결과로 호출한다. 학부모 안내의 번호는 위기 종류에서 고정 규칙으로 고른다: `SELF_HARM` → [109], `ABUSE` → [112], `OTHER_SAFETY` → [], `UNCLASSIFIED` → [112, 109].
- **Rationale**: 백엔드 보안 요건(LLM 출력은 허용값으로 서버 재검증, 실행 경로에 쓰지 않음)을 연결 지점에서 미리 강제한다. F3가 붙기 전에도 안내 선택 규칙과 테스트가 끝나 있다.

## R4. 처리 단계와 동시성 [clarify Q2: C]

- **Decision**: `InquiryStage` = `RECEIVED`(접수) · `DELIVERED`(교사에게 전달됨, 긴급만) · `REFINING`(정제, F3) · `TEACHER_CONFIRMED`(교사 확인) · `ANSWERED`(답변 완료, F4). F2가 하는 전이는 `RECEIVED → DELIVERED`(긴급 접수 시 같은 트랜잭션) · `DELIVERED → TEACHER_CONFIRMED`(교사가 원문을 처음 열람)뿐이다. 허용 전이표를 도메인에 두고 그 밖의 전이는 거부한다. 문의는 `BaseEntity`의 `@Version`으로 동시 전이를 막는다. 단계가 바뀔 때마다 `inquiry_stage_history`에 한 줄씩 덧붙인다(append-only, F1 `link_history`와 같은 방식).
- **Rationale**: 원칙 III(이력 append-only)·백엔드 요건(상태 전이 낙관적 락). 학부모 타임라인(FR-023)은 이 이력을 그대로 보여 준다.

## R5. 원문·제목 저장

- **Decision**: 본문은 `SensitiveText`(AES-256-GCM 자동 암호화, 로그 가림), 컬럼은 `updatable = false`. 제목도 `SensitiveText`이고 F3가 채운다(F2에서는 NULL). 화면의 제목은 서버가 상태로 정한 고정 문구를 쓴다: 긴급 → "긴급 문의", 정제 실패 → "원문 확인 필요"(F3), 그 밖의 제목 NULL → "정리 중". 본문 길이는 **코드 포인트 수**로 1~2,000자(이모지도 1자).
- **Rationale**: 원칙 III·VI, FR-006·032·033. 수정 경로를 엔티티 수준에서 없앤다.

## R6. 중복 제출과 1분 제한 [clarify Q4: B]

- **Decision**:
  - **중복 제출**: 요청 헤더 `Idempotency-Key`(UUID, **필수**)를 받는다. `inquiry`에 `(parent_id, idempotency_key)` 유니크 제약을 걸고, 같은 키가 다시 오면 처음 접수된 문의를 그대로 돌려준다(새로 만들지 않음, 1분 제한도 보지 않음). 단 같은 키로 **자녀나 본문이 다른** 제출이 오면 409 `CONFLICT`로 거부한다(`request_hash`로 비교, 처음 문의는 그대로). 동시 도착은 유니크 제약 위반을 잡아 기존 문의를 돌려준다.
  - **1분 제한**: 순서는 입력 검증 → 중복 확인 → **긴급 판정** → (일반이면) 1분 제한 → 저장. 1분 제한은 F1의 Redis 실패 제한(`attempt` 도메인)에 `INQUIRY_SUBMIT` 종류를 더해 쓴다. 메일 재발송 제한과 같은 Lua 스크립트를 파라미터만 바꿔 일반화한다(최소 간격 60초, 윈도 60초, 최대 1건). 학부모 계정 UUID 기준이며 거부 시 429 `TOO_MANY_ATTEMPTS` + `Retry-After`. 긴급 문의는 제한을 확인하지도, 기록하지도 않는다.
  - Redis 장애 시: 제한을 확인할 수 없으면 **제한만 건너뛰고 일반 문의도 접수**한다(fail-open, WARN 로그에는 종류만). 1분 제한은 도배 방지일 뿐이라, 로그인 무차별 대입 방어(F1, fail-closed)와 달리 서버 장애가 학부모의 문의권을 막으면 안 된다. 긴급 문의는 Redis를 거치지 않는다(원칙 IV).
- **Rationale**: 연타·재시도는 클라이언트가 같은 키를 다시 보내는 것으로 정확히 구분된다. 긴급 판정을 제한보다 먼저 해야 FR-002b·원칙 IV를 지킨다.
- **Alternatives considered**: 같은 본문 해시로 중복 판단 — 학부모가 같은 내용을 일부러 다시 보낸 것과 구분하지 못한다.
- **[팀 결정 2026-10-18]**: `Idempotency-Key` 필수 헤더 확정. 앱 계약에 영향이 있다(프론트가 제출마다 UUID를 만들어 재시도 때 재사용).

## R7. 알림 — 요청 저장 후 발송(트랜잭셔널 아웃박스) + 긴급 즉시 발송 [팀 결정 2026-10-18]

- **Decision**: 문의 접수와 **같은 트랜잭션**에서 `notification_request`를 저장하고, 별도 발송기가 저장된 요청을 꺼내 보낸다.
  - 발송기: `@Scheduled(fixedDelay = 10초)`가 `claimDue`로 보낼 요청을 확보한다: 짧은 트랜잭션에서 `PENDING ∧ scheduled_at <= now`(또는 임대가 끝난 `SENDING`) 요청을 `SELECT … FOR UPDATE SKIP LOCKED`로 잡아 `SENDING`·`locked_until = now + 60초`로 표시하고 **커밋한 뒤 트랜잭션 밖에서** FCM을 호출하며, 결과는 다시 짧은 트랜잭션으로 반영한다. FCM이 느려도 DB 연결·행 잠금을 잡고 있지 않고, 서버가 중간에 죽어도 임대가 끝나면 다시 집는다(서버가 늘어나도 중복 발송 없음).
  - **긴급 즉시 발송**: 긴급 문의가 커밋되면 같은 서버 안에서 커밋 후 이벤트(`@TransactionalEventListener(AFTER_COMMIT)` + 비동기)로 발송기를 그 문의의 긴급 요청 하나에 대해 바로 한 번 돌린다. 이 깨우기가 실패하거나 서버가 그 사이 재시작돼도 요청은 DB에 남아 있어 10초 폴링이 안전망으로 보낸다. 같은 요청을 두 경로가 동시에 집어도 `SKIP LOCKED`로 한 번만 나간다.
  - 긴급(`URGENT`): 접수 즉시 발송 대상. 실패하면 1분·5분·15분 뒤 재시도하고, 세 번 모두 실패한 뒤에도 1시간 간격으로 계속 시도한다(재알림 주기와 같음, 조용히 멈추거나 10초마다 반복하지 않음). 받는 사람은 요청의 교사가 아니라 **보낼 때의 현재 담임**으로 다시 정한다(접수와 첫 발송 사이에 담임이 바뀌어도 새 담임에게 간다).
  - 재알림(`URGENT_REMINDER`) [clarify Q1]: 긴급 알림을 보낸 뒤 **1시간 뒤** 재알림 요청을 만든다. 보낼 시점에 문의가 아직 `DELIVERED`이고 받을 교사가 그 학급의 현재 담임·활성 계정이면 보내고 다음 1시간 요청을 만든다. 교사가 확인했거나 담임이 바뀌었으면 취소한다.
  - 일반(`GENERAL`): 응답 가능 시간 안이면 즉시. 밖이면 교사별로 하나뿐인 대기 중 `GENERAL_DIGEST`를 다음 응답 가능 시간 시작 시각에 잡아 두고(이미 있으면 그대로), 보낼 때 그동안 알림이 미뤄진 문의 수를 세어 "새 문의 N건" 한 번만 보낸다. 0건이면 보내지 않는다 [clarify Q3].
  - 일반 알림을 끈 교사에게는 일반·묶음 요청을 만들지 않고, 이미 잡힌 묶음 알림은 보낼 때 설정을 다시 확인해 꺼져 있으면 `SKIPPED`로 한다. 긴급·재알림은 설정과 무관하다.
  - 등록된 활성 기기가 없으면 요청은 대기로 남아 다음 주기에 다시 본다(FR-032). 긴급 요청은 기기가 등록되는 즉시 나간다.
- **Rationale**: 알림 서비스가 죽어도 문의 접수·긴급 표시는 이미 커밋돼 있고(FR-010·012), 요청이 DB에 남아 있어 서버 재시작에도 잃지 않는다. 메시지 브로커 같은 새 인프라 없이 PostgreSQL만으로 된다.
- **Alternatives considered**:
  - 커밋 후 비동기 바로 발송만(F1 메일 방식): 서버가 그 사이 재시작되면 긴급 알림을 잃는다 → 즉시 발송은 쓰되 DB 아웃박스를 안전망으로 둔다.
  - **Redis Stream**(검토 후 보류): ① 문의 커밋(PostgreSQL)과 스트림 발행(Redis)을 한 트랜잭션으로 묶을 수 없어 그 사이 장애 시 긴급 알림이 사라진다(결국 DB 아웃박스가 필요). ② 긴급 알림 발송이 Redis 가용성에 기대게 된다(원칙 IV). ③ 1시간 재알림·묶음 알림 같은 예약 발송을 스트림만으로 못 해 ZSET 등 별도 구조와 Redis 영속성 관리가 필요하다. 서버가 여러 대가 되거나 알림량이 크게 늘면 "깨우기" 신호만 Redis Stream으로 바꾸는 것을 다시 검토한다(DB 아웃박스는 유지).
  - Redis 지연 큐: TTL·재시작 시 유실 위험, 재알림 상태를 DB와 맞추기 어렵다.

## R8. 푸시 발송 수단 — FCM [팀 결정 2026-10-18]

- **Decision**: 팀 결정(공통 표 2026-10-15)대로 FCM. 앱 계층은 `notification.application.port.out.PushSenderPort`만 알고, 어댑터 `adapter.out.fcm.FcmPushSender`가 Firebase Admin SDK(`com.google.firebase:firebase-admin`)로 보낸다.
  - 새 시크릿 `DAMSO_FCM_CREDENTIALS`: 서비스 계정 JSON을 Base64로 넣는다. **필수**이며 형식(Base64 → JSON의 `type: service_account`)이 맞지 않으면 기동을 거부한다(원칙 VIII). 값은 어떤 로그에도 출력하지 않는다.
  - 메시지는 고정 문구(제목·본문)와 화면 이동용 문의 UUID만 담는다. 이름·본문·학생 정보는 없다(FR-014).
  - FCM이 "등록되지 않은 토큰"을 돌려주면 그 기기 등록을 비활성화한다.
  - 테스트는 `PushSenderPort`를 가짜 구현으로 바꿔 보낸 알림을 기록한다.
- **Rationale**: Android·iOS 공통, 팀 결정. SDK가 OAuth 토큰 갱신·HTTP v1 API를 처리한다.
- **Alternatives considered**: FCM HTTP v1 API 직접 호출 — 의존성은 줄지만 OAuth 서명·토큰 갱신을 직접 구현해야 한다.

## R9. 기기 등록 — 세션에 묶기

- **Decision**: `PUT /api/v1/me/devices`가 현재 접근 토큰의 세션(`sid`)과 FCM 토큰을 묶어 저장한다(같은 토큰이 다시 오면 갱신, 다른 사용자가 같은 토큰을 등록하면 이전 등록을 비활성화). 발송 대상은 **세션이 살아 있는 기기만**이다 — F1의 `SessionStatusChecker`로 확인한다. 그래서 로그아웃(그 세션 폐기)·비밀번호 재설정·탈퇴(모든 세션 폐기)가 되면 F1 코드를 고치지 않아도 그 기기로 알림이 가지 않는다(FR-032, SC-012). 명시적 해제 `DELETE /api/v1/me/devices/current`도 둔다.
- **Rationale**: 기기와 세션의 수명을 같게 만들면 "로그아웃한 기기에 알림" 버그가 구조적으로 사라진다.

## R10. 응답 가능 시간

- **Decision**: `teacher_availability`에 요일별 한 구간(`day_of_week`, `start_time`, `end_time`, 시작 < 종료, 한국 시간)을 둔다. 판정: 지금(Asia/Seoul) 요일의 구간에 들고 `start ≤ now < end`면 응답 가능. 설정이 하나도 없으면 항상 응답 가능(FR-029). 다음 시작 시각은 오늘부터 7일 안에서 가장 가까운 구간 시작이다. 설정 변경은 이후 문의부터 적용하고, 이미 잡힌 묶음 알림 시각은 바꾸지 않는다.
- **Rationale**: FR-027~031, Edge Case(시작 포함·종료 미포함, 자정을 넘는 구간은 두 요일로).

## R11. 원문 열람과 감사 기록 (F5 뼈대)

- **Decision**: `GET /api/v1/teacher/inquiries/{id}/content`(긴급 문의만, F2 범위)에서 원문을 돌려주기 전에 `audit.port.in.AuditLogUseCase.record(actor, INQUIRY_CONTENT_VIEWED, INQUIRY, inquiryId)`를 같은 트랜잭션에서 남긴다. `audit_log`는 `link_history`처럼 append-only(엔티티 `@Immutable`, 저장·조회만 있는 리포지토리, 리플렉션 테스트). 처음 열람이면 단계를 `TEACHER_CONFIRMED`로 바꾸고 대기 중인 재알림을 취소한다. 일반 문의의 원문 열람은 F3 범위라 F2에서는 `INQUIRY_CONTENT_UNAVAILABLE`(409)로 응답한다.
- **Rationale**: 원칙 III(열람자·대상·시각 기록, append-only). 감사 기록이 남지 않으면 원문도 나가지 않는다(같은 트랜잭션).

## R12. 접근 권한과 응답 형식

- **Decision**: 경로는 `/api/v1/parent/inquiries/**`(학부모), `/api/v1/teacher/inquiries/**`·`/api/v1/teacher/notification-settings`(교사), `/api/v1/me/devices`(모든 역할). 역할은 F1 `SecurityConfig` 경로 규칙을 그대로 쓰고, 건별 검증은 서비스에서 토큰 주체 기준으로 한다: 학부모 = 본인이 작성한 문의, 교사 = 문의의 담당 학급의 **현재 담임**. 남의 문의는 존재 여부를 드러내지 않도록 404 `NOT_FOUND`. 슈퍼 관리자에게 문의 경로는 없다(F1 `/admin/**`에 문의 API 없음 + 역할 규칙으로 403).
  - 학부모에게 보이는 안내는 서버가 **코드**로 내려 준다(`RECEIVED`, `URGENT_DELIVERED`, `DEFERRED_TO_AVAILABLE_HOURS`)와 번호 목록(`hotlines`). 문구 자체는 계약에 고정 문자열로 정의하고 앱이 그대로 표시한다(원칙 V — 서버·앱 모두 고정 문구, AI 문장 없음).
- **Rationale**: 원칙 VII. 계약에 고정 문구를 적어 두면 리뷰에서 문구 변경이 보인다.

## R13. 새 에러 코드

- **Decision**: `NO_HOMEROOM_TEACHER`(409, "담임 선생님이 아직 연결되지 않았습니다."), `INQUIRY_CONTENT_UNAVAILABLE`(409, "아직 열람할 수 없는 문의입니다."). 1분 제한은 기존 `TOO_MANY_ATTEMPTS`(429)를 쓴다. 자녀가 아닌 학생으로 제출하면 404 `NOT_FOUND`.

## R14. 성능 목표

- **Decision**: 긴급 판정과 교사 목록 반영은 제출 요청 안에서 동기로 끝난다(별도 큐 없음) → SC-002(3초)는 응답 시간으로 검증한다. 긴급 알림은 커밋 직후 즉시 발송되고, 일반 알림과 즉시 발송이 실패한 긴급 알림은 발송기 주기(10초) 안에 나간다.
