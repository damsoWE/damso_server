# Research: 인증·회원가입·학급 연결 (F1)

**Feature**: [spec.md](./spec.md) · **Date**: 2026-10-18 · **기반**: [000-foundation research](../000-foundation/research.md)

각 항목은 Decision / Rationale / Alternatives considered 형식이다. CLAUDE.md "큰 영향 결정은 확인 후 진행"(DB 스키마·외부 시스템·시크릿) 대상인 R1·R3·R7·R9·R12는 **[팀 결정 2026-10-18]**로 확인을 마쳤다.

---

## R1. 스키마 관리 — JPA `ddl-auto=update` [팀 결정 2026-10-18]

- **Decision**: 마이그레이션 도구 없이 Hibernate가 엔티티 매핑으로 테이블을 만든다. 운영 `spring.jpa.hibernate.ddl-auto`를 `none` → `update`로 바꾼다. 테스트는 지금처럼 `create-drop`.
  - 유니크·NOT NULL·길이 제약은 모두 엔티티 매핑(`@Column(unique, nullable, length)`, `@Table(uniqueConstraints)`)에 선언해 DDL에 반영되게 한다.
  - Hibernate가 만들 수 없는 제약(부분 유니크 인덱스, CHECK, 트리거)은 쓰지 않고, 일반 유니크 컬럼으로 바꾸거나(R6) 애플리케이션 구조로 막는다(R10).
- **Rationale**: 팀 결정(Flyway 미도입). 1차 MVP는 테이블을 새로 만드는 단계라 `update`로 충분하다.
- **주의**: `update`는 컬럼 추가만 하고 이름 변경·삭제·타입 변경은 반영하지 않는다. 엔티티의 컬럼을 바꾸거나 지울 때는 운영 DB를 직접 정리해야 하므로 PR 설명에 적는다.
- **Alternatives considered**:
  - Flyway: 트리거·부분 인덱스를 쓸 수 있지만 팀이 도입하지 않기로 했다.
  - `schema.sql` 직접 관리: 컬럼이 바뀔 때마다 SQL을 손으로 맞춰야 한다.

## R2. 비밀번호 저장

- **Decision**: Spring Security `BCryptPasswordEncoder`(cost 10). 정책: 8~64자, 영문 1자 이상 + 숫자 1자 이상. 서버에서 검증한다(체크리스트 보안기능 9·16).
- **Rationale**: 솔트 내장 일방향 해시, 추가 의존성 없음. 64자 상한은 BCrypt 입력 72바이트 한계 안쪽이고 과도한 입력으로 인한 부하를 막는다.
- **Alternatives considered**: Argon2id: 더 강하지만 Spring 구현이 BouncyCastle 의존성을 요구한다. MVP 규모에서 BCrypt로 충분.

## R3. 초대 코드·인증 코드 생성과 저장 [팀 결정 2026-10-18]

- **Decision**:
  - 초대 코드: 영문 대문자·숫자 중 혼동 문자(0, O, 1, I, L)를 뺀 31자에서 `SecureRandom`으로 10자 생성(약 49.5비트).
  - 이메일 인증·비밀번호 재설정 코드: `SecureRandom`으로 6자리 숫자.
  - 저장은 원문 대신 **HMAC-SHA256(서버 키, 정규화한 코드)**. 키는 새 시크릿 `DAMSO_CODE_HASH_KEY`(Base64 32바이트 이상, 기동 시 검증). 초대 코드는 해시로 바로 조회한다(해시 컬럼 유니크).
- **Rationale**: 코드 공간이 작아(특히 6자리) 단순 SHA-256은 DB가 유출되면 전수 대입으로 바로 복원된다. 서버 키를 섞으면 DB만으로는 복원할 수 없다. HMAC은 결정적이라 조회 인덱스로 쓸 수 있다(BCrypt는 조회 불가).
- **Alternatives considered**:
  - BCrypt로 코드 해시: 조회 키로 쓸 수 없어, 입력마다 미사용 코드 전체를 비교해야 한다.
  - JWT 서명 키 재사용: 키 용도가 섞이면 한쪽이 유출될 때 피해가 번진다. 키를 분리한다.

## R4. 토큰·세션 구조

- **Decision**:
  - **접근 토큰**: JWT HS256(foundation 디코더 그대로), 수명 15분. 클레임 `sub`(사용자 UUID)·`role`·`sid`(세션 UUID)·`iat`·`exp`. 발급은 Nimbus `NimbusJwtEncoder`.
  - **갱신 토큰**: 256비트 무작위 opaque 값(Base64URL). DB에는 SHA-256 해시만 저장. 토큰 자체가 256비트 무작위라 키 없는 해시로 충분하다.
  - **세션(= 갱신 토큰 계열)**: 로그인 1회마다 `auth_session` 1건(FR-025a). 갱신하면 기존 갱신 토큰에 `used_at`을 찍고 새 토큰을 발급하며 세션 만료를 `now + 14일`로 미룬다(FR-025b).
  - **재사용 탐지**: 이미 `used_at`이 있는 토큰이 오면 그 세션을 폐기(`revoked_at`)한다. 회전은 `UPDATE refresh_token SET used_at = :now WHERE id = :id AND used_at IS NULL` 조건부 갱신으로 원자 처리한다. 갱신된 행이 0이면 재사용으로 본다(동시 갱신 경쟁도 같은 경로).
  - **즉시 폐기**: 접근 토큰 검증 시 `sid` 세션이 살아 있는지 DB에서 확인하는 `OAuth2TokenValidator`를 foundation 디코더에 추가한다. 로그아웃·비밀번호 재설정·탈퇴·재사용 탐지가 15분을 기다리지 않고 바로 반영된다.
- **Rationale**: 원칙 백엔드 요건(분리·1회 회전·재사용 시 계열 폐기·alg 고정)을 그대로 따른다. 요청마다 PK로 세션 1건을 조회하는 비용은 MVP 규모(1개교)에서 무시할 수준이고, "모든 기기 로그아웃"을 정확히 지킬 수 있다.
- **Alternatives considered**:
  - 갱신 토큰도 JWT: 폐기·재사용 탐지에 어차피 DB 상태가 필요해 이점이 없다.
  - 접근 토큰 무상태 검증만: 로그아웃·탈퇴 후 최대 15분 동안 토큰이 살아 있다.
  - Redis 세션 저장소: Redis는 실패 횟수용으로 들어오지만(R7), 세션은 사용자·탈퇴 처리와 한 트랜잭션으로 폐기해야 해서 PostgreSQL에 둔다.

## R5. 가입 흐름 — 이메일 인증 후 계정 확정

- **Decision**: 2단계.
  1. `POST /api/v1/auth/signup` — 초대 코드·이름·이메일·비밀번호·약관 동의를 받는다. 초대 코드를 검증하고(실패 횟수는 이메일 기준, FR-011), 통과하면 `pending_signup`(비밀번호는 BCrypt 해시, 이름은 암호화)과 6자리 인증 코드를 만들어 메일로 보낸다. 응답은 `pendingSignupId`.
  2. `POST /api/v1/auth/signup/verify` — `pendingSignupId` + 인증 코드. 맞으면 **한 트랜잭션에서** 계정 생성 → 초대 코드 사용 처리 → 학급·학생 연결 → 연결 이력 기록 → 토큰 발급.
  - 이미 가입된 이메일이면 1단계 응답은 똑같이 주고, 그 주소로 "이미 가입된 계정입니다" 안내 메일만 보낸다(가입 여부 추측 차단). 2단계는 진행되지 않는다.
  - 인증 코드 재요청은 `POST /api/v1/auth/signup/resend`. 이전 코드는 무효가 되고, 60초에 1회·시간당 5회로 제한한다(spec Assumptions에서 plan으로 넘긴 값). 이 발송 제한은 실패 횟수와 같은 Redis에 둔다(R7).
- **Rationale**: 초대 코드는 인증이 끝나야 소모돼야 한다(FR-016). 코드 원문을 1단계 이후 다시 받지 않으려고 대기 레코드에 초대 코드 id를 묶는다. 이 경우 2단계 시점에 코드가 재발행·만료됐는지 다시 확인한다(spec Edge Case).
- **Alternatives considered**:
  - 계정을 "인증 대기" 상태로 먼저 만들기: 인증하지 않은 계정이 이메일 유니크를 점유해 남의 이메일로 선점할 수 있다.
  - 인증 링크: 팀 결정(6자리 코드)과 다름.

## R6. 초대 코드 상태와 동시성

- **Decision**: `invite_code.status` ∈ {`ISSUED`, `USED`, `REVOKED`}, 만료는 `expires_at`으로 판정(별도 상태 전이 없음). 재발행은 같은 대상의 `ISSUED` 코드를 `REVOKED`로 바꾸고 새 코드를 만든다. 사용 처리는 `@Version` 낙관적 락으로 동시 사용 중 1건만 성공한다(SC-008).
  - **대상당 활성 코드 1개**: 부분 유니크 인덱스 대신 `active_target_key` 컬럼에 **일반 유니크 제약**을 건다. `ISSUED`일 때는 `"{kind}:{대상 UUID}"`, `USED`·`REVOKED`로 바뀔 때 `NULL`로 비운다. PostgreSQL 유니크는 NULL끼리 충돌하지 않으므로 같은 효과를 `ddl-auto`로 만들 수 있다(R1).
  - `kind`와 대상 컬럼의 짝(교사 코드는 학급, 학부모 코드는 학생)은 CHECK 대신 엔티티 생성 메서드(`InviteCode.forTeacher(classroom)` / `forParent(student)`)에서만 만들 수 있게 막는다.
  - 연결 제약도 DB가 보장한다: `classroom.homeroom_teacher_id` 유니크(교사–학급 1:1), `student.guardian_id`는 학생당 하나(컬럼 자체가 1개).
- **Rationale**: 서비스 코드 검사만으로는 경쟁 상황을 막지 못한다. 최종 방어선을 DB 제약에 둔다.
- **Alternatives considered**:
  - 부분 유니크 인덱스: `ddl-auto`로 만들 수 없다(R1).
  - 비관적 락: 1개교 규모에서 과하고 교착 위험.

## R7. 실패 횟수·대기·발송 제한 저장소 — Redis [팀 결정 2026-10-18]

- **Decision**: `spring-boot-starter-data-redis`(Lettuce). 실패 횟수와 대기·잠금, 메일 재발송 제한을 TTL이 있는 Redis 키로 둔다. 앱 계층은 출력 포트 `AttemptLimitPort`만 알고, 어댑터 `attempt.adapter.out.redis`가 구현한다.
  - **키 형식**: `damso:attempt:{kind}:{subjectHash}`. `subjectHash`는 이메일 또는 계정 UUID의 HMAC(R3 키)이라, 가입하지 않은 사람이 입력한 이메일이 평문으로 쌓이지 않는다.
  - **종류와 규칙**
    - `LOGIN`: 연속 실패 3회 30초 · 4회 1분 · 5회 2분 · 6회 5분 · 7회 이상 10분 대기(FR-024).
    - `INVITE_CODE`·`SIGNUP_CODE`·`RESET_CODE`: 5회 실패하면 10분 잠금(FR-011·030·032).
    - `MAIL_SEND`: 60초 1회·시간당 5회(R5).
  - **원자성**: 횟수 증가와 대기 시각 계산은 Lua 스크립트 한 번으로 처리해, 동시 요청이 몰려도 횟수가 새지 않게 한다.
  - **TTL**: 잠금·대기 키는 풀리는 시각까지, 실패 횟수 키는 마지막 실패 후 24시간. 성공하면 키를 지운다.
  - **장애 시**: Redis에 연결할 수 없으면 해당 요청을 `INTERNAL_ERROR`로 거부한다(fail-closed). 제한 없이 통과시키면 무차별 대입 방어가 꺼지기 때문이다.
  - 새 환경변수: `DAMSO_REDIS_HOST`·`DAMSO_REDIS_PORT`(필수), `DAMSO_REDIS_PASSWORD`(선택). 테스트는 Testcontainers Redis 컨테이너를 쓴다.
- **Rationale**: 팀 결정. 보안 체크리스트 보안기능 15의 대응("Redis")과 일치한다. TTL로 만료가 자동 처리되어 정리 작업이 필요 없고, 서버를 여러 대로 늘려도 같은 카운터를 공유한다.
- **Alternatives considered**: PostgreSQL 테이블: 외부 시스템은 줄지만 만료 행 정리가 필요하다. 팀이 Redis를 택했다.

## R8. 응답 통일과 시간차

- **Decision**:
  - 로그인: 없는 이메일이면 고정 더미 BCrypt 해시와 비교해 처리 시간을 맞춘다. 대기 규칙도 같게 적용한다(FR-024).
  - 비밀번호 재설정 요청·가입 1단계: 메일 발송은 트랜잭션 커밋 후 **비동기**로 보낸다. 응답 시간에 메일 서버 지연이 드러나지 않는다.
  - 초대 코드 실패는 사유와 관계없이 `INVALID_INVITE_CODE` 하나로 응답한다.
- **Rationale**: spec Edge Case "응답 시간 차이로도 드러내지 않는다", SC-003.

## R9. 이메일 발송 — SMTP `JavaMailSender` [팀 결정 2026-10-18]

- **Decision**: 로컬·운영 모두 `spring-boot-starter-mail`의 `JavaMailSender`로 SMTP 발송한다. 앱 계층은 출력 포트 `VerificationMailPort`만 알고, 어댑터 `adapter.out.mail`이 고정 템플릿(코드 6자리만 들어감)으로 보낸다.
  - 새 환경변수: `DAMSO_MAIL_HOST`·`DAMSO_MAIL_PORT`·`DAMSO_MAIL_FROM`(필수), `DAMSO_MAIL_USERNAME`·`DAMSO_MAIL_PASSWORD`(선택, 운영 SMTP 계정). 운영은 STARTTLS를 켠다.
  - 로컬은 Mailpit 컨테이너(인증 없음)에 SMTP로 보낸다. 테스트는 포트를 가짜 구현으로 바꿔 보낸 코드를 확인한다.
  - 발송 실패는 서버 로그에 대상 계정 UUID(또는 대기 건 UUID)와 실패 사실만 남긴다. 이메일 주소·코드는 남기지 않는다.
- **Rationale**: 팀 결정. 특정 업체 SDK에 묶이지 않고, SMTP 호스트만 바꾸면 메일 서비스를 바꿀 수 있다. 메일 본문에 이름·학생 정보를 넣지 않아 메일이 유출돼도 노출이 적다.
- **Alternatives considered**: 업체 HTTP API(SendGrid 등): SDK와 키가 추가되고 업체를 바꾸기 어렵다.

## R10. append-only 연결 이력

- **Decision**: `link_history` 테이블(계정 UUID, 대상 종류·UUID, 이벤트 `LINKED`/`UNLINKED`/`WITHDRAWN`, 시각). 이 테이블은 `BaseEntity`를 상속하지 않는다(수정 시각·버전이 의미 없음). id는 같은 방식(UUID v4)으로 만든다. 수정·삭제 경로는 애플리케이션 구조로 막는다.
  - 엔티티는 Hibernate `@Immutable`(UPDATE 미발행), 필드는 생성자에서만 설정하고 setter가 없다.
  - 리포지토리는 `CrudRepository`가 아닌 `Repository`를 상속하고 `save`·조회 메서드만 선언한다(`delete*` 없음).
  - 리플렉션 테스트로 이 리포지토리에 `delete`·`update`로 시작하는 메서드나 `@Modifying` 쿼리가 없음을 검사한다(추가 의존성 없음).
- **Rationale**: 원칙 III("수정·삭제 API 미제공"). `ddl-auto`로는 DB 트리거를 만들 수 없어(R1) 애플리케이션 계층에서 경로 자체를 없앤다.
- **한계**: DB에 직접 접속한 사람의 수정은 막지 못한다. 운영 DB 계정 권한(애플리케이션 계정에 이 테이블 UPDATE·DELETE 권한 미부여)은 배포 시 검토한다. F5 감사 로그도 같은 방식을 따른다.
- **Alternatives considered**: DB 트리거: 가장 강하지만 `ddl-auto`로 만들 수 없다.

## R11. 회원 탈퇴 처리

- **Decision**: 한 트랜잭션에서 ① 이메일을 `NULL`로, 이름을 고정 문구 "탈퇴한 사용자"로, 비밀번호 해시를 지우고 상태를 `WITHDRAWN`으로 바꾼다 ② 모든 세션 폐기 ③ 교사면 학급 담임 해제, 학부모면 모든 자녀의 보호자 해제 ④ 각 해제와 탈퇴를 `link_history`에 기록 ⑤ 교사 공개 프로필 삭제. 탈퇴할 때 현재 비밀번호를 다시 입력받는다.
  - 이메일 유니크 제약은 `NULL`을 중복으로 보지 않으므로 같은 이메일로 재가입할 수 있다(FR-038).
- **Rationale**: Clarifications Q2·FR-038a. 비밀번호 재확인은 잠깐 자리를 비운 기기에서 남이 탈퇴시키는 것을 막는다(spec에 없는 보조 장치라 contracts 리뷰에서 확인).
- **Alternatives considered**: 이메일을 `withdrawn+{uuid}@invalid`로 치환: 의미 없는 값이 남고 형식 검증을 우회하는 특수 경로가 생긴다.

## R12. 슈퍼 관리자 계정 생성 [팀 결정 2026-10-18]

- **Decision**: 선택 환경변수 `DAMSO_SUPER_ADMIN_EMAIL`·`DAMSO_SUPER_ADMIN_PASSWORD`가 **둘 다** 있으면 기동 시 `ApplicationRunner`가 해당 이메일의 계정이 없을 때만 슈퍼 관리자를 만든다. 비밀번호는 정책(R2)을 통과해야 하고, 통과하지 못하면 기동을 거부한다. 이미 있으면 아무것도 바꾸지 않는다. 운영자는 생성 후 변수를 지운다.
- **Rationale**: 팀 결정("서버에서 직접 생성, 환경변수로 주입", 원칙 VIII). 기본값이 없어 변수를 넣지 않으면 관리자는 생기지 않는다(`NoDefaultAccountTest` 유지).
- **Alternatives considered**: DB에 직접 INSERT: 운영자가 BCrypt 해시를 손으로 만들어야 해 실수 여지가 크다. 별도 CLI 명령: 실행 경로가 하나 더 늘어난다.

## R13. 개인정보 저장 형식

- **Decision**: 사용자 이름·학생 이름은 `SensitiveText`(자동 AES-256-GCM, 로그 가림)로 저장한다. 이메일은 소문자로 정규화해 평문으로 저장한다(유니크 조회 필요, 원칙 I상 연락처 아님). 교사 공개 프로필의 표시 이름·소개 문구는 공개 정보라 평문.
- **Rationale**: 학생 이름은 아동 개인정보이고 F3 가명화 사전의 원천이다. DB가 유출돼도 이름이 바로 드러나지 않게 한다.
- **Alternatives considered**: 이메일 암호화 + HMAC 조회 컬럼: 보호 효과는 있지만 MVP 범위에서 복잡도 대비 이점이 작다.

## R14. API 경로와 역할 구분

- **Decision**: 기능 경로는 `/api/v1` 아래 둔다. 공개 경로는 `/api/v1/auth/**` 중 로그인 전 단계만 명시적으로 `SecurityConfig.PUBLIC_PATHS`에 추가한다. 역할별 경로는 `/api/v1/admin/**`(SUPER_ADMIN), `/api/v1/teacher/**`(TEACHER), `/api/v1/parent/**`(PARENT), 공통 `/api/v1/me/**`. 경로 단위 역할 검사는 보안 설정에서, 건별 소유권은 서비스에서 한다(원칙 VII).
- **Rationale**: foundation의 기본 거부를 유지하면서 공개 경로를 한눈에 볼 수 있다. 역할을 경로로 나눠 관리자 API가 실수로 열리지 않는다.

## R15. 대기·잠금 응답 형식

- **Decision**: 새 에러 코드 `TOO_MANY_ATTEMPTS`(429). 남은 시간은 `Retry-After` 헤더(초)로 준다. `ErrorResponse` 형식은 바꾸지 않는다.
- **Rationale**: spec US4 "남은 대기 시간이 안내된다". 헤더 값은 서버가 계산한 정수라 응답 분할 위험이 없다(체크리스트 입력 13).
- **Alternatives considered**: `ErrorResponse`에 `retryAfterSeconds` 필드 추가: 공통 형식이 바뀌어 foundation 계약을 고쳐야 한다.

## R16. 가입 전 초대 코드 확인 (2026-10-25, 이슈 #11)

- **Decision**: 공개 경로 `POST /api/v1/auth/invite-codes/verify`. 요청은 `{ inviteCode, email }`, 성공 200 `{ role }`. 가입 1단계와 같은 검증 함수(`SignupService.validateInviteCode`: 잠금 확인 → `InviteRedeemUseCase.validate` → 무효면 이메일 기준 실패 기록)를 쓰므로 실패 응답·횟수·잠금이 가입과 완전히 같다. 코드를 소모하지 않고, 성공해도 실패 횟수를 초기화하지 않는다(초기화는 지금처럼 가입 확정 때).
- **Rationale**: Figma 가입 흐름이 코드 확인 결과로 화면을 나눈다. 같은 함수를 써서 두 경로의 규칙이 어긋나지 않게 했다. 응답에 역할만 담아 연결 대상(학급·학생)은 드러내지 않는다(원칙 VII).
- **Alternatives considered**: 실패 횟수를 IP로 세기: FR-011에서 기각(같은 와이파이 사용자 동시 잠금). 확인 단계는 세지 않기: "코드 입력 반복 실패 제한"과 어긋난다. 성공 시 횟수 초기화: 유효한 코드 하나로 다른 코드 대입 횟수를 계속 되돌릴 수 있어 하지 않는다.
