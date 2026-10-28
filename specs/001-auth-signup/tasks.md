---

description: "Task list for 001-auth-signup"
---

# Tasks: 인증·회원가입·학급 연결 (F1)

**Input**: Design documents from `specs/001-auth-signup/`

**Prerequisites**: [plan.md](./plan.md), [spec.md](./spec.md), [research.md](./research.md), [data-model.md](./data-model.md), [contracts/](./contracts/auth.openapi.yaml), [quickstart.md](./quickstart.md)

**Tests**: 포함한다. spec의 성공 기준(SC-001~008)이 자동화 테스트로 확인할 수 있는 형태이고, 보안약점 체크리스트의 `조치` 근거로 테스트 이름이 필요하다(foundation과 같은 방식).

**Organization**: spec의 사용자 스토리(US1~US7) 단위로 묶었다. Phase 2가 끝나면 각 스토리를 독립적으로 구현·검증할 수 있다. 단, US3·US6·US7은 US2가 만든 가입·연결 흐름 위에서 동작한다(아래 Dependencies).

## Format: `[ID] [P?] [Story] Description`

- **[P]**: 다른 파일이고 미완료 작업에 의존하지 않아 병렬 가능
- **[Story]**: 해당 사용자 스토리 (US1~US7)

## Path Conventions

- 운영 코드: `src/main/java/damso/{common,account,school,attempt}/…` (plan.md Project Structure)
- 도메인 패키지 안은 `domain` / `application.port.in` / `application.port.out` / `application.service` / `adapter.in.controller` / `adapter.out.{persistence,redis,mail,token}` (constitution 아키텍처)
- 테스트: `src/test/java/damso/…`, 테스트 전용 도구는 `src/test/java/damso/support/`에만
- **테스트용 시크릿 값을 파일에 커밋하지 않는다.** `TestSecrets`가 실행 시 `SecureRandom`으로 만든다
- 다른 도메인 기능은 그 도메인의 `port.in`으로만 호출한다. 다른 도메인의 리포지토리를 직접 쓰지 않는다
- 컨트롤러 패키지는 `adapter.in.controller`, 클래스 이름은 `…Controller`다. DTO 이름은 요청은 `…RequestDTO`, 응답은 `…ResponseDTO`로 끝낸다 (팀 결정 2026-10-18, 이전 이름 `adapter.in.web`)
- DTO는 컨트롤러 안의 중첩 타입이 아니라 별도 파일의 record로 두고, 요청은 `adapter.in.controller.dto.request`, 응답은 `adapter.in.controller.dto.response`에 둔다. 엔티티를 응답으로 직접 내보내지 않는다
- 이름 필드(`UserAccount.name`, `Student.name`)는 `SensitiveText`. 응답 DTO로 옮길 때만 `reveal()`한다

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: 의존성·설정 준비, API 명세 선반영(Contract-first)

- [X] T001 작업 브랜치 확인: `git branch --show-current`가 `features/f1-auth-signup`(이슈 #2 연결)인지 확인한다. 아니면 `git switch features/f1-auth-signup`
- [X] T002 `build.gradle`에 의존성 추가: `implementation 'org.springframework.boot:spring-boot-starter-mail'`, `implementation 'org.springframework.boot:spring-boot-starter-data-redis'`. 버전은 적지 않는다(Boot 4.1.1 BOM 관리). Redis 테스트 컨테이너는 기존 `testcontainers` 코어의 `GenericContainer`를 쓰므로 추가 아티팩트 없음 (research R7·R9)
- [X] T003 `src/main/resources/application.yml` 수정: `spring.jpa.hibernate.ddl-auto: none` → `update`(research R1) · `spring.mail.host: ${DAMSO_MAIL_HOST}`, `port: ${DAMSO_MAIL_PORT}`, `username: ${DAMSO_MAIL_USERNAME:}`, `password: ${DAMSO_MAIL_PASSWORD:}`, `properties.mail.smtp.auth`·`starttls.enable`은 `${DAMSO_MAIL_STARTTLS:true}`(로컬 Mailpit은 false) · `spring.data.redis.host: ${DAMSO_REDIS_HOST}`, `port: ${DAMSO_REDIS_PORT}`, `password: ${DAMSO_REDIS_PASSWORD:}` · `damso.crypto.code-hash-key: ${DAMSO_CODE_HASH_KEY}` · `damso.mail.from: ${DAMSO_MAIL_FROM}` · `damso.auth.access-token-ttl: 15m`, `damso.auth.session-ttl: 14d` · `damso.super-admin.email: ${DAMSO_SUPER_ADMIN_EMAIL:}`, `damso.super-admin.password: ${DAMSO_SUPER_ADMIN_PASSWORD:}`
- [X] T004 [P] `.env.example`에 변수 **이름만** 추가: `DAMSO_CODE_HASH_KEY`(옆에 `# openssl rand -base64 32`), `DAMSO_MAIL_HOST`, `DAMSO_MAIL_PORT`, `DAMSO_MAIL_FROM`, `DAMSO_MAIL_USERNAME`, `DAMSO_MAIL_PASSWORD`, `DAMSO_MAIL_STARTTLS`, `DAMSO_REDIS_HOST`, `DAMSO_REDIS_PORT`, `DAMSO_REDIS_PASSWORD`, `DAMSO_SUPER_ADMIN_EMAIL`, `DAMSO_SUPER_ADMIN_PASSWORD`(주석: "선택. 둘 다 넣으면 기동 시 1회 생성, 생성 후 지운다")
- [X] T005 `./gradlew build -x test`로 컴파일 확인 (Windows: `.\gradlew.bat build -x test`)
- [X] T006 **Contract-first — 구현 전에** `docs/api/openapi.yaml`에 `specs/001-auth-signup/contracts/auth.openapi.yaml`을 통합: 경로 24개·스키마 추가, 공통 `ErrorCode` enum에 F1 코드 6개(`INVALID_INVITE_CODE`, `INVALID_VERIFICATION_CODE`, `INVALID_CREDENTIALS`, `TOO_MANY_ATTEMPTS`, `CLASSROOM_HAS_TEACHER`, `STUDENT_HAS_GUARDIAN`) 추가, `TooManyAttempts` 응답 추가, contract의 외부 `$ref`(`../../../docs/api/openapi.yaml#…`)는 내부 참조로 바꾼다, `info.version` `0.1.0` → `0.2.0`. `npx -y @redocly/cli@1.34.5 lint docs/api/openapi.yaml`이 valid여야 한다. 커밋 `Feat : F1 API 명세 추가`. 태그 `api-v0.2`는 PR merge 후 main에서 `/api-release api-v0.2`로 찍는다 (constitution Contract-first, docs/speckit-guide.md 5단계)
- [ ] T007 프론트엔드 공지: 프론트 레포(`damsoWE/damso_app`) 담당에게 F1 API 명세(`docs/api/openapi.yaml` 0.2.0, 변경 경로·에러 코드 6개·`Retry-After` 헤더)를 알리고, 공지한 날짜·채널을 PR 설명에 적는다. 이후 명세가 바뀌면 T006을 다시 하고 다시 공지한다 (constitution Contract-first: 명세 수정 → 프론트 공지 → 코드 수정)

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: 모든 스토리가 쓰는 에러 코드·시크릿·코드 생성/해시·계정·세션 토큰·실패 제한·메일 포트·테스트 지원

**⚠️ CRITICAL**: 이 단계가 끝나야 스토리 작업을 시작한다

### 공통 기반 확장 (`damso.common`)

- [X] T008 [P] `src/main/java/damso/common/error/ErrorCode.java`에 F1 코드 6개 추가(기존 9개 의미 불변, data-model.md "에러 코드 추가" 그대로): `INVALID_INVITE_CODE`(400, "코드를 확인해 주세요."), `INVALID_VERIFICATION_CODE`(400, "인증 코드가 올바르지 않거나 만료되었습니다."), `INVALID_CREDENTIALS`(401, "이메일 또는 비밀번호가 올바르지 않습니다."), `TOO_MANY_ATTEMPTS`(429, "잠시 후 다시 시도해 주세요."), `CLASSROOM_HAS_TEACHER`(409, "이미 담임이 연결된 학급입니다."), `STUDENT_HAS_GUARDIAN`(409, "이미 보호자가 연결된 학생입니다."). 새 코드는 기존 9개 **뒤에** 추가한다 — `fromStatus`는 선언 순서대로 찾으므로 400·401·409에 대해 계속 기존 공통 코드(`INVALID_INPUT`·`UNAUTHORIZED`·`CONFLICT`)를 돌려준다
- [X] T009 [P] `src/main/java/damso/common/error/TooManyAttemptsException.java` — `BusinessException(TOO_MANY_ATTEMPTS)`을 상속하고 `retryAfterSeconds`(1~600으로 보정)를 가진다. `src/main/java/damso/common/error/GlobalExceptionHandler.java`에 핸들러를 추가해 429 응답에 `Retry-After: {초}` 헤더를 붙인다. 본문은 기존 `ErrorResponse` 그대로 (research R15)
- [X] T010 [P] `src/main/java/damso/common/config/SecretsValidator.java` 확장: `REQUIRED_KEYS`에 `DAMSO_CODE_HASH_KEY`(Base64 → 32바이트 이상), `REQUIRED`에 `DAMSO_MAIL_HOST`·`DAMSO_MAIL_FROM`·`DAMSO_REDIS_HOST`, 포트 검사 `DAMSO_MAIL_PORT`·`DAMSO_REDIS_PORT`(정수 1~65535, 아니면 `(형식 오류)`), `DAMSO_SUPER_ADMIN_EMAIL`·`DAMSO_SUPER_ADMIN_PASSWORD`는 **둘 다 있거나 둘 다 없어야** 함(하나만 있으면 `(쌍 누락)`). 메시지에는 이름과 사유만, 값은 절대 넣지 않는다 (data-model.md 시크릿 표, 원칙 VIII)
- [X] T011 [P] `src/test/java/damso/common/config/SecretsValidatorTest.java`에 T010 케이스 추가: 코드 해시 키 누락·짧음, 포트 `0`·`70000`·`abc`, 슈퍼 관리자 변수 하나만 설정 → 각각 이름만 포함된 실패, 출력에 값 0건
- [X] T012 [P] `src/test/java/damso/support/TestSecrets.java`에 시스템 프로퍼티 추가: `DAMSO_CODE_HASH_KEY`(무작위 32바이트 Base64), `DAMSO_MAIL_HOST=localhost`, `DAMSO_MAIL_PORT=2525`, `DAMSO_MAIL_FROM=no-reply@damso.test`, `DAMSO_REDIS_HOST`·`DAMSO_REDIS_PORT`(더미, 실제 접속은 T032 컨테이너가 덮어씀). 코드 해시 키를 `codeHashKey()`로 노출
- [X] T013 [P] `src/main/java/damso/common/crypto/CodeHasher.java` — `damso.crypto.code-hash-key`(Base64)로 HMAC-SHA256. `hash(String normalized)` → 소문자 hex 64자. 입력값을 예외 메시지·로그에 넣지 않는다 (research R3). 테스트 `src/test/java/damso/common/crypto/CodeHasherTest.java`: 같은 입력 같은 해시, 키가 다르면 다른 해시, 길이 64
- [X] T014 [P] `src/main/java/damso/common/crypto/SecureCodes.java` — `SecureRandom` 기반. `inviteCode()`: 알파벳 `ABCDEFGHJKMNPQRSTUVWXYZ23456789`(31자, 0·O·1·I·L 제외)에서 10자, `sixDigits()`: `000000`~`999999` 0 채움, `refreshToken()`: 32바이트 Base64URL(패딩 없음). 테스트 `src/test/java/damso/common/crypto/SecureCodesTest.java`: 1,000회 생성 시 길이·허용 문자만 사용, 혼동 문자 0건 (FR-006)
- [X] T015 [P] `src/main/java/damso/common/config/ClockConfig.java` — `Clock` 빈(`Clock.systemUTC()`). 만료·대기 계산은 모두 이 빈을 주입받아 쓴다(`Instant.now()` 직접 호출 금지)
- [X] T016 [P] `src/main/java/damso/common/security/SessionStatusChecker.java` — 인터페이스 `boolean isActive(UUID sessionId)`. `common`은 도메인을 모르므로 구현은 `account`가 한다 (plan Structure Decision)
- [X] T017 [P] `src/main/java/damso/common/security/CurrentUser.java` — record `(UUID userId, String role, UUID sessionId)`, 정적 `from(Jwt)`(`sub`·`role`·`sid`). `src/main/java/damso/common/security/CurrentUserArgumentResolver.java` + `WebMvcConfigurer` 등록으로 컨트롤러 파라미터 `CurrentUser`를 주입한다. 요청 본문·파라미터의 사용자 ID는 쓰지 않는다 (원칙 VII)
- [X] T018 `src/main/java/damso/common/security/JwtDecoderConfig.java` 수정: 기존 검증기에 `sid` 필수 + `SessionStatusChecker.isActive(sid)`가 거짓이면 실패하는 `OAuth2TokenValidator<Jwt>`를 추가한다(실패 → 기존 401 `UNAUTHORIZED`). 로그아웃·재설정·탈퇴·재사용 탐지가 즉시 반영된다 (research R4) (depends on T016)
- [X] T019 `src/main/java/damso/common/security/SecurityConfig.java` 수정: `PUBLIC_PATHS`에 `/api/v1/auth/signup`, `/api/v1/auth/signup/verify`, `/api/v1/auth/signup/resend`, `/api/v1/auth/login`, `/api/v1/auth/token/refresh`, `/api/v1/auth/password-reset`, `/api/v1/auth/password-reset/verify`, `/api/v1/auth/password-reset/confirm`을 **하나씩 명시**(와일드카드 금지, `/api/v1/auth/logout`은 인증 필요). 역할 규칙: `/api/v1/admin/**` → `hasRole("SUPER_ADMIN")`, `/api/v1/teacher/**` → `hasRole("TEACHER")`, `/api/v1/parent/**` → `hasRole("PARENT")`, 그 외 `authenticated()` (research R14)

### 실패 제한 (`damso.attempt`, Redis)

- [X] T020 [P] `src/main/java/damso/attempt/domain/AttemptKind.java`(`LOGIN`, `INVITE_CODE`, `SIGNUP_CODE`, `RESET_CODE`, `MAIL_SEND`)와 `src/main/java/damso/attempt/domain/AttemptPolicy.java` — 순수 규칙. data-model.md "Redis 키" 그대로: `LOGIN` 연속 실패 "3회 30초 · 4회 1분 · 5회 2분 · 6회 5분 · 7회 이상 10분 대기", 대기 중 시도는 횟수에 더하지 않음 / `INVITE_CODE`·`SIGNUP_CODE`·`RESET_CODE` "5회 실패 → 10분 잠금, 풀리면 0회부터" / `MAIL_SEND` "60초 안 재발송 거부, 1시간 안 5회 초과 거부". 테스트 `src/test/java/damso/attempt/domain/AttemptPolicyTest.java`: 2·3·4·5·6·7·10회 경계값
- [X] T021 [P] `src/main/java/damso/attempt/application/port/out/AttemptLimitPort.java` — `State get(kind, subjectHash)`, `State recordFailure(kind, subjectHash, Instant now)`, `void reset(kind, subjectHash)`, `boolean tryRecordSend(subjectHash, Instant now)`
- [X] T022 `src/main/java/damso/attempt/adapter/out/redis/RedisAttemptLimitAdapter.java` — 키 `damso:attempt:{kind}:{subjectHash}`. 증가·대기 시각 계산은 **Lua 스크립트 1회**로 원자 처리(`src/main/resources/redis/attempt_record_failure.lua`, `redis/mail_send.lua`). 대기·잠금 판정은 **앱 `Clock` 기준 시각을 값으로 저장해 비교**하고, TTL은 정리용(잠금 해제 시각 또는 마지막 실패 + 24시간, `MAIL_SEND`는 1시간)이다. Redis 연결 실패(`RedisConnectionFailureException` 등)는 `BusinessException(INTERNAL_ERROR)`로 바꿔 요청을 거부한다(fail-closed, research R7) (depends on T020, T021)
- [X] T023 `src/main/java/damso/attempt/application/port/in/AttemptLimitUseCase.java` + `src/main/java/damso/attempt/application/service/AttemptLimitService.java` — 대상(소문자 이메일 또는 계정 UUID 문자열)을 `CodeHasher`로 해시해 포트에 넘긴다. `ensureAllowed(kind, subject)`: 대기 중이면 `TooManyAttemptsException(남은 초)`, `recordFailure(kind, subject)`, `reset(kind, subject)`, `ensureMailSendAllowed(subject)` (depends on T013, T022)

### 계정·세션 골격 (`damso.account`)

- [X] T024 [P] `src/main/java/damso/account/domain/Role.java`(`SUPER_ADMIN`, `TEACHER`, `PARENT`), `AccountStatus.java`(`ACTIVE`, `WITHDRAWN`), `UserAccount.java` — `BaseEntity` 상속, 테이블 `user_account`. data-model.md 그대로: `email` varchar(254) NULL 허용 **유니크**(소문자 정규화, 탈퇴 시 NULL) · `password_hash` varchar(100) NULL 허용(BCrypt, 탈퇴 시 NULL) · `name` `SensitiveText` "1~20자(공백 제거 후)" · `role` varchar(20) 변경 불가 · `status` varchar(20) · `terms_agreed_at` NULL 허용 · `withdrawn_at` NULL 허용. 정적 생성 `signUp(email, passwordHash, name, role, agreedAt)`, `superAdmin(email, passwordHash, name)`. 메서드 `changePassword`, `rename`, `withdraw(now)`(email NULL, name "탈퇴한 사용자", password_hash NULL, `ACTIVE → WITHDRAWN` 단방향)
- [X] T025 [P] `src/main/java/damso/account/domain/PasswordPolicy.java` — "8~64자, 영문 1자 이상 + 숫자 1자 이상". 위반 시 `INVALID_INPUT`(fieldErrors `password`, 고정 사유). 테스트 `src/test/java/damso/account/domain/PasswordPolicyTest.java`: 7자·65자·숫자만·영문만 거부, `abcd1234` 허용 (FR-028, 체크리스트 보안기능 9)
- [X] T026 [P] `src/main/java/damso/account/domain/AuthSession.java`(테이블 `auth_session`: `user_id` FK, `expires_at` "마지막 갱신 + 14일", `revoked_at` NULL 허용, `revoke_reason` varchar(20) `LOGOUT`·`REUSE_DETECTED`·`PASSWORD_RESET`·`WITHDRAWAL`; `isActive(now)`, `extend(now, ttl)`, `revoke(reason, now)`)와 `RefreshToken.java`(테이블 `refresh_token`: `session_id` FK, `token_hash` char(64) **유니크**, `used_at` NULL 허용)
- [X] T027 [P] `src/main/java/damso/account/application/port/out/UserAccountPort.java`, `SessionPort.java` + `src/main/java/damso/account/adapter/out/persistence/`의 Spring Data 리포지토리와 포트 구현: `findByEmail(소문자)`, `existsByEmail`, 세션 `findById`, `revokeAllByUser(userId, reason, now)`, 갱신 토큰 `findByTokenHash`, **조건부 회전** `@Modifying UPDATE RefreshToken SET usedAt = :now WHERE id = :id AND usedAt IS NULL`(갱신 행 수 반환, research R4)
- [X] T028 `src/main/java/damso/account/adapter/out/persistence/JpaSessionStatusChecker.java` — `SessionStatusChecker` 구현: 세션이 존재하고 `revoked_at IS NULL ∧ expires_at > now`, 사용자 `status = ACTIVE`일 때만 참 (depends on T016, T027)
- [X] T029 [P] `src/main/java/damso/account/application/port/out/AccessTokenIssuerPort.java` + `src/main/java/damso/account/adapter/out/token/JwtAccessTokenIssuer.java` — `NimbusJwtEncoder`로 HS256(`damso.jwt.secret`, 디코더와 같은 키). 클레임 `sub`(사용자 UUID), `role`, `sid`(세션 UUID), `iat`, `exp`(= iat + `damso.auth.access-token-ttl`)
- [X] T030 `src/main/java/damso/account/application/service/SessionIssuer.java` — 로그인·가입 공용: 세션 생성(`expires_at = now + 14일`) → `SecureCodes.refreshToken()` 발급, SHA-256 hex로 저장 → 접근 토큰 발급 → `TokenResponse`(contract 필드: `accessToken`, `accessTokenExpiresInSeconds`, `refreshToken`, `refreshTokenExpiresInSeconds`, `role`) (depends on T026, T027, T029)
- [X] T031 [P] `src/main/java/damso/account/adapter/out/persistence/PasswordEncoderConfig.java` — `BCryptPasswordEncoder(10)` 빈. `UserDetailsService`는 등록하지 않는다(`NoDefaultAccountTest` 유지)

### 메일 포트

- [X] T032 [P] `src/main/java/damso/account/application/port/out/VerificationMailPort.java` — `sendSignupCode(email, code)`, `sendAlreadyRegistered(email)`, `sendPasswordResetCode(email, code)`. `src/main/java/damso/account/adapter/out/mail/SmtpVerificationMailSender.java` — `JavaMailSender`, 발신자 `damso.mail.from`, **고정 문구 템플릿**(코드 6자리만 삽입, 이름·학생 정보 없음). 트랜잭션 커밋 후 비동기 발송(`@TransactionalEventListener(phase = AFTER_COMMIT)` + `@Async`, `@EnableAsync` 설정 `src/main/java/damso/common/config/AsyncConfig.java`). 발송 실패는 WARN 로그에 이벤트 종류만, 이메일·코드 미기록 (research R8·R9, 원칙 V·VI)

### 슈퍼 관리자 생성

- [X] T033 `src/main/java/damso/account/application/service/SuperAdminBootstrap.java` — `ApplicationRunner`. `damso.super-admin.email`·`password`가 둘 다 비어 있지 않을 때만 동작: 비밀번호가 `PasswordPolicy`를 통과하지 못하면 기동 실패(메시지에 값 없음), 해당 이메일 계정이 없으면 `UserAccount.superAdmin(...)` 생성(이름 "관리자"), 있으면 아무것도 바꾸지 않는다. 테스트 `src/test/java/damso/account/SuperAdminBootstrapTest.java`: 미설정 시 계정 0건, 설정 시 1건, 두 번 기동해도 1건 (research R12) (depends on T024, T025, T031)

### 테스트 지원

- [X] T034 [P] `src/test/java/damso/support/IntegrationTestSupport.java`에 Redis 컨테이너 추가: `GenericContainer<>("redis:7-alpine").withExposedPorts(6379)` + `@ServiceConnection(name = "redis")`, 정적 시작. 각 테스트 전에 `damso:attempt:*` 키 삭제
- [X] T035 [P] `src/test/java/damso/support/FakeVerificationMailPort.java` — `@TestConfiguration`의 `@Primary` 빈. 이메일별 마지막 코드·안내 메일 여부를 기록하고 `lastSignupCode(email)`, `lastResetCode(email)`, `alreadyRegisteredSent(email)`, `clear()` 제공. 동기 호출
- [X] T036 [P] `src/test/java/damso/support/MutableClock.java` — `@Primary Clock` 테스트 빈, `advance(Duration)`·`reset()`
- [X] T037 `src/test/java/damso/support/IntegrationTestSupport.java`에 헬퍼 추가: `createAccount(Role role)`(무작위 이메일·비밀번호 `Passw0rd!`로 `ACTIVE` 계정 저장), `tokenFor(UserAccount)`(`SessionIssuer`로 실제 세션 생성 후 접근 토큰 반환). foundation 테스트 5개(`AccessControlTest`, `ErrorResponseTest`, `InputValidationTest`, `SensitiveLoggingTest`, `BaseEntityTest`)에서 **유효 토큰**용 `TestJwtIssuer.valid(...)` 호출을 `tokenFor(createAccount(…))`로 바꾼다(이제 `sid` 없는 토큰은 401). 위조·만료·서명 없음 등 음성 테스트는 그대로 둔다 (depends on T018, T030, T034~T036)
- [X] T038 `./gradlew test`로 foundation 테스트 전체가 그대로 통과하는지 확인한다(새 시크릿·Redis 컨테이너·`sid` 검증 반영 후 회귀 없음)

**Checkpoint**: 컨텍스트가 뜨고, 실제 세션이 있는 토큰만 통과하며, foundation 테스트가 모두 녹색이다

---

## Phase 3: User Story 1 - 슈퍼 관리자가 학급·명단을 만들고 초대 코드를 발행한다 (Priority: P1) 🎯 MVP

**Goal**: 학급 생성·학생 등록·교사/학부모 코드 발행·재발행·발행 현황 (FR-002~009, FR-012)

**Independent Test**: `createAccount(SUPER_ADMIN)` 토큰으로 관리자 API를 호출하고, 코드 원문이 발행 응답에만 있는지 확인한다

### Tests for User Story 1

- [X] T039 [P] [US1] `src/test/java/damso/school/AdminClassroomApiTest.java` — 학급 생성 201(`grade` "1~6", `classNumber` "1~20" 밖이면 400), 같은 학년·반 재생성 409 `CONFLICT`, 학생 등록(`names` 1~40개, 각 "1~20자") 201, 41명 400, 목록·명단 조회(`guardianLinked=false`), 없는 학급 UUID 404, 교사·학부모 토큰으로 `/api/v1/admin/**` 호출 403 (US1 시나리오 1·6, SC-005)
- [X] T040 [P] [US1] `src/test/java/damso/school/InviteIssueApiTest.java` — 교사 코드 발행 201: `code`가 `^[A-HJKMNP-Z2-9]{10}$`, `GET /api/v1/admin/invite-codes?classroomId=`에 원문 없음, DB `invite_code.code_hash` ≠ 원문(SC-006) · 담임 있는 학급 → 409 `CLASSROOM_HAS_TEACHER` · 학부모 코드 발행, 보호자 있는 학생 → 409 `STUDENT_HAS_GUARDIAN` · 재발행 시 이전 코드 `REVOKED`·`active_target_key` NULL, 새 코드 1개만 `UNUSED` · `MutableClock` 24시간 경과 → `EXPIRED` 표시 (US1 시나리오 2~5, FR-007~009)

### Implementation for User Story 1

- [X] T041 [P] [US1] `src/main/java/damso/school/domain/Classroom.java` — 테이블 `classroom`, `grade` smallint "1~6", `class_number` smallint "1~20", `homeroom_teacher_id` UUID FK NULL 허용 **유니크**, `@Table(uniqueConstraints = (grade, class_number))`. 메서드 `assignTeacher(teacherId)`(이미 있으면 거부), `releaseTeacher()`, `hasTeacher()`
- [X] T042 [P] [US1] `src/main/java/damso/school/domain/Student.java` — 테이블 `student`, `classroom_id` FK 필수, `name` `SensitiveText` "1~20자", `guardian_id` UUID FK NULL 허용. `assignGuardian(parentId)`(이미 있으면 거부), `releaseGuardian()`, `hasGuardian()`. 같은 학급 동명이인 허용
- [X] T043 [P] [US1] `src/main/java/damso/school/domain/InviteKind.java`(`TEACHER`, `PARENT`), `InviteStatus.java`(`ISSUED`, `USED`, `REVOKED`), `InviteCode.java` — 테이블 `invite_code`. data-model.md 그대로: `classroom_id`(TEACHER만), `student_id`(PARENT만), `code_hash` char(64) **유니크**, `status`, `active_target_key` varchar(50) NULL 허용 **유니크**(`ISSUED`면 `"{kind}:{대상 UUID}"`, `USED`·`REVOKED`면 NULL), `expires_at` "발행 + 24시간", `issued_by`, `used_by`, `used_at`. 생성은 `forTeacher(classroom, codeHash, issuer, now)` / `forParent(student, codeHash, issuer, now)`로만. `isUsable(now)`, `markUsed(userId, now)`, `revoke()`, `displayStatus(now)`(→ `UNUSED`/`USED`/`EXPIRED`/`REVOKED`)
- [X] T044 [US1] `src/main/java/damso/school/application/port/out/ClassroomPort.java`, `StudentPort.java`, `InviteCodePort.java` + `src/main/java/damso/school/adapter/out/persistence/`의 리포지토리·포트 구현 (`findByCodeHash`, `findActiveByTargetKey`, 학급별 코드 목록, 학급별 학생 목록·수) (depends on T041~T043)
- [X] T045 [US1] `src/main/java/damso/school/application/port/in/ClassroomAdminUseCase.java` + `src/main/java/damso/school/application/service/ClassroomAdminService.java` — 학급 생성(중복 → `CONFLICT`), 학생 일괄 등록, 학급 목록(`homeroomAssigned`, `studentCount`), 학생 명단(`guardianLinked`) (depends on T044)
- [X] T046 [US1] `src/main/java/damso/school/application/port/in/InviteIssueUseCase.java` + `src/main/java/damso/school/application/service/InviteIssueService.java` — 대상 검사(담임 있음 → `CLASSROOM_HAS_TEACHER`, 보호자 있음 → `STUDENT_HAS_GUARDIAN`) → 같은 대상의 `ISSUED` 코드 `revoke()` 후 **flush**(유니크 충돌 방지) → `SecureCodes.inviteCode()` → `CodeHasher.hash` 저장 → 원문은 반환값으로만 넘긴다(로그 금지). 발행 현황 조회 (depends on T044, T013, T014)
- [X] T047 [US1] `src/main/java/damso/school/adapter/in/controller/AdminController.java` + 요청·응답 DTO(`dto.request`·`dto.response`) — contract의 `/api/v1/admin/classrooms`(GET·POST), `/api/v1/admin/classrooms/{classroomId}/students`(GET·POST), `/api/v1/admin/classrooms/{classroomId}/teacher-invite`, `/api/v1/admin/students/{studentId}/parent-invite`, `/api/v1/admin/invite-codes?classroomId=`. 응답 스키마 `AdminClassroom`, `StudentSummary`, `IssuedInviteCode`, `InviteCodeStatus` 그대로. 담임 이름·이메일은 내보내지 않는다 (depends on T045, T046)

**Checkpoint**: T039·T040 통과. 관리자가 코드를 발행할 수 있다

---

## Phase 4: User Story 2 - 교사가 초대 코드로 가입해 담임으로 확정된다 (Priority: P1)

**Goal**: 가입 2단계(코드 검증·메일 인증 → 계정 확정·연결·이력·토큰) (FR-010·011, FR-013~019, FR-032, FR-038a의 LINKED)

**Independent Test**: US1으로 발행한 교사 코드로 가입·인증하고, 담임 연결과 코드 `USED`를 확인한다

### Tests for User Story 2

- [X] T048 [P] [US2] `src/test/java/damso/account/SignupTeacherApiTest.java` — 정상 흐름: `POST /api/v1/auth/signup` 202(`pendingSignupId`, `codeExpiresInSeconds=600`) → `FakeVerificationMailPort` 코드로 `/signup/verify` 201 `TokenResponse(role=TEACHER)` → 학급 `homeroom_teacher_id` 설정, 코드 `USED`, `teacher_profile.display_name` = 이름, `link_history` `LINKED`/`CLASSROOM` 1건 · 인증 전에는 `user_account` 0건·코드 `ISSUED` 유지 · `termsAgreed=false` 400 · 연락처 필드 등 알 수 없는 필드 400 (US2 시나리오 1·5, FR-014·016·018)
- [X] T049 [P] [US2] `src/test/java/damso/account/SignupInviteCodeFailureTest.java` — 없는·만료(24시간 경과)·사용됨·재발행으로 무효인 코드 4종의 응답 본문이 **바이트 단위로 동일**(`INVALID_INVITE_CODE`) · 같은 이메일로 5회 실패 후 6번째는 맞는 코드여도 429 + `Retry-After` · 10분 경과 후 다시 가능 · 1·2단계 사이에 코드 재발행 → `verify`가 `INVALID_INVITE_CODE` (US2 시나리오 2·3, Edge Case, SC-003·004)
- [X] T050 [P] [US2] `src/test/java/damso/account/SignupVerificationTest.java` — 이미 가입된 이메일로 1단계: 202 동일 응답 + `alreadyRegisteredSent`, `verify`는 `INVALID_VERIFICATION_CODE` · 틀린 인증 코드 5회 → 429 · 10분 지난 코드 거부 · `/signup/resend` 후 이전 코드 무효 · 60초 안 재발송 429 · 이메일 대소문자 무관 동일 취급 (US2 시나리오 4, FR-017·032, Edge Case)
- [X] T051 [P] [US2] `src/test/java/damso/account/SignupConcurrencyTest.java` — 같은 초대 코드로 대기 건 2개를 만든 뒤 `verify` 2건을 동시에 실행 → 정확히 1건 성공, 다른 1건 `INVALID_INVITE_CODE`, 계정 1개 (SC-008)

### Implementation for User Story 2

- [X] T052 [P] [US2] `src/main/java/damso/account/domain/PendingSignup.java` — 테이블 `pending_signup`: `email` varchar(254) 소문자, `name` `SensitiveText`, `password_hash` varchar(100), `invite_code_id` UUID, `code_hash` char(64), `code_expires_at` "발송 + 10분", `completed_at` NULL 허용. `matches(codeHash, now)`, `reissueCode(codeHash, now)`, `complete(now)`. `src/main/java/damso/account/application/port/out/PendingSignupPort.java` + persistence 어댑터(같은 이메일의 미완료 건 삭제 포함)
- [X] T053 [P] [US2] `src/main/java/damso/school/domain/TeacherProfile.java` — 테이블 `teacher_profile`: `teacher_id` **유니크**, `display_name` varchar(20) "1~20자", `introduction` varchar(60) "0~60자". `src/main/java/damso/school/application/port/out/TeacherProfilePort.java` + persistence 어댑터
- [X] T054 [P] [US2] `src/main/java/damso/school/domain/LinkHistory.java` — `BaseEntity` 미상속, `@Immutable`, setter 없음, 생성자에서 `id = UUID.randomUUID()`. 테이블 `link_history`: `account_id` UUID(FK 없음), `target_type` `STUDENT`·`CLASSROOM`·`NONE`, `target_id` NULL 허용, `event` `LINKED`·`UNLINKED`·`WITHDRAWN`, `occurred_at`. 개인정보 컬럼 없음. `src/main/java/damso/school/adapter/out/persistence/LinkHistoryRepository.java`는 `org.springframework.data.repository.Repository`만 상속하고 `save`·`findByAccountIdOrderByOccurredAt`만 선언 (research R10)
- [X] T055 [US2] `src/main/java/damso/school/application/port/in/InviteRedeemUseCase.java` + `src/main/java/damso/school/application/service/InviteRedeemService.java` — `validate(rawCode)`: 대문자 정규화 → 해시 조회 → `isUsable(now)` ∧ 대상 비어 있음, 아니면 `INVALID_INVITE_CODE`(사유 구분 없음) → `InviteTarget(inviteCodeId, kind, targetId)` 반환(소모 안 함). `redeem(inviteCodeId, accountId)`: 같은 검사 재수행 → `markUsed` → **교사**: `classroom.assignTeacher` + `TeacherProfile` 생성(표시 이름 = 계정 이름) + `LinkHistory(LINKED, CLASSROOM)`. 낙관적 락 충돌은 `INVALID_INVITE_CODE`로 변환. 학부모 분기는 US3에서 추가 (depends on T044, T053, T054)
- [X] T056 [US2] `src/main/java/damso/account/application/port/in/SignupUseCase.java` + `src/main/java/damso/account/application/service/SignupService.java` — `start`: 이메일 소문자화 → `AttemptLimitUseCase.ensureAllowed(INVITE_CODE, email)` → `validate` 실패 시 `recordFailure` 후 재던짐 → `PasswordPolicy` → `termsAgreed` 확인 → 가입된 이메일이면 `sendAlreadyRegistered` 후 **저장하지 않은 무작위 UUID**로 같은 응답 → 아니면 기존 미완료 건 삭제, `PendingSignup` 저장(BCrypt), `ensureMailSendAllowed`, 6자리 코드 발송. `resend`: 대기 건이 없어도 202, 있으면 발송 제한 확인 후 새 코드. `verify`: `ensureAllowed(SIGNUP_CODE, email)` → 코드 불일치·만료면 `recordFailure` + `INVALID_VERIFICATION_CODE` → **한 트랜잭션**에서 `UserAccount.signUp` 저장 → `InviteRedeemUseCase.redeem` → `complete` → 실패 횟수 초기화 → `SessionIssuer` 토큰 (research R5) (depends on T023, T030, T032, T052, T055)
- [X] T057 [US2] `src/main/java/damso/account/adapter/in/controller/AuthController.java` + DTO(`dto.request`·`dto.response`) — `POST /api/v1/auth/signup`(202 `PendingSignupResponse`), `/signup/verify`(201 `TokenResponse`), `/signup/resend`(202). 요청 DTO는 contract 스키마(`SignupRequest` 등)의 길이·패턴을 Bean Validation으로 선언하고, 알 수 없는 필드는 거부한다 (depends on T056)
- [X] T058 [US2] `src/main/java/damso/account/application/service/ExpiredVerificationCleanup.java` — `@Scheduled(cron = "0 0 4 * * *")`(매일 04:00, `@EnableScheduling`은 `src/main/java/damso/common/config/SchedulingConfig.java`)로 `pending_signup` 중 `completed_at` 또는 `code_expires_at`이 24시간 넘게 지난 건을 삭제한다(data-model.md "확정·만료 후 24시간이 지난 건은 하루 1회 정리 작업이 지운다"). 삭제 건수만 INFO 로그(이메일 없음). 테스트 `src/test/java/damso/account/ExpiredVerificationCleanupTest.java`: `MutableClock`으로 25시간 경과 건만 삭제 (depends on T052)

**Checkpoint**: T048~T051 통과. 교사가 가입해 담임이 된다

---

## Phase 5: User Story 3 - 학부모가 초대 코드로 가입해 자녀의 보호자로 확정된다 (Priority: P1)

**Goal**: 학부모 가입·자녀 추가·자녀 목록(담임 공개 프로필 포함) (FR-020·021, FR-034, FR-036)

**Independent Test**: 학부모 코드로 가입 → 두 번째 자녀 코드 추가 → `GET /api/v1/parent/children`에 2명

### Tests for User Story 3

- [X] T059 [P] [US3] `src/test/java/damso/school/ParentChildrenApiTest.java` — 학부모 코드 가입 → `student.guardian_id` 설정, `link_history` `LINKED`/`STUDENT` · `POST /api/v1/parent/children`로 둘째 연결 201 → 목록 2명(학급이 달라도 각각 표시) · 이미 보호자가 있는 학생·교사 코드·사용된 코드 → 동일한 `INVALID_INVITE_CODE` · 로그인 계정 기준 5회 실패 → 429 · 교사 토큰으로 `/api/v1/parent/**` 403 · 응답의 `teacher`는 `displayName`·`introduction`만(이메일 문자열 0건), 담임 없으면 `null` (US3 시나리오 1~5, US6 시나리오 3, SC-005·006)

### Implementation for User Story 3

- [X] T060 [US3] `src/main/java/damso/school/application/service/InviteRedeemService.java`에 학부모 분기 추가: `student.assignGuardian(accountId)` + `LinkHistory(LINKED, STUDENT)`. `redeemForExistingParent(rawCode, parentId)`: `validate`에서 `kind=PARENT`가 아니면 `INVALID_INVITE_CODE` (depends on T055)
- [X] T061 [US3] `src/main/java/damso/school/application/port/in/ParentChildrenUseCase.java` + `src/main/java/damso/school/application/service/ParentChildrenService.java` — `list(parentId)`: 본인이 보호자인 학생만, 학급(`ClassroomRef`)과 담임 `TeacherProfile`(없으면 null). `link(parentId, rawCode)`: `AttemptLimitUseCase`를 **계정 UUID 기준** `INVITE_CODE`로 확인·기록 → `redeemForExistingParent` (depends on T060, T023)
- [X] T062 [US3] `src/main/java/damso/school/adapter/in/controller/ParentController.java` + DTO(`dto.request`·`dto.response`) — `GET /api/v1/parent/children`, `POST /api/v1/parent/children`(201 `ChildResponse`). 사용자 ID는 `CurrentUser`에서만 (depends on T061)

**Checkpoint**: T059 통과. 학부모가 자녀 여럿을 연결한다

---

## Phase 6: User Story 4 - 로그인하고 세션을 안전하게 유지한다 (Priority: P1)

**Goal**: 로그인·단계별 대기·갱신 토큰 회전·재사용 탐지·기기별 로그아웃·14일 만료 (FR-022~027)

**Independent Test**: 로그인 → 갱신 → 이전 갱신 토큰 재사용 → 그 기기 세션만 폐기

### Tests for User Story 4

- [X] T063 [P] [US4] `src/test/java/damso/account/LoginApiTest.java` — 성공 시 `role` 포함 · 없는 이메일·틀린 비밀번호·탈퇴 계정 응답 본문 동일(`INVALID_CREDENTIALS`) · 연속 실패 2회까지 즉시 재시도 가능, 3회 후 30초 안 시도는 맞는 비밀번호여도 429 + `Retry-After`(1~30) · `MutableClock` 30초 경과 후 성공 → 횟수 초기화 · 7회 이상 대기 ≤ 600초 · 대기 중 시도는 횟수 증가 없음 · 없는 이메일에도 같은 대기 적용 (US4 시나리오 1~5, FR-023·024, SC-003·004)
- [X] T064 [P] [US4] `src/test/java/damso/account/SessionApiTest.java` — 같은 계정 두 번 로그인(기기 A·B) · A 갱신 → 새 토큰, 이전 갱신 토큰 사용 불가 · A의 이전 갱신 토큰 재사용 → 401, A 세션의 접근·갱신 토큰 모두 401, **B는 정상** · A 로그아웃 → A 접근 토큰 즉시 401, B 정상 · 마지막 갱신 후 14일 경과 → 갱신 401, 14일 안 갱신 시 만료 연장 · 응답에 `Set-Cookie` 없음 (US4 시나리오 6~10, FR-025·025a·025b·026·027, SC-007)
- [X] T065 [P] [US4] `src/test/java/damso/account/LoginTimingTest.java` — 없는 이메일과 틀린 비밀번호 로그인을 각각 20회(매번 다른 이메일로 대기 규칙 회피) 실행해 처리 시간 중앙값을 비교: 두 값의 비율이 0.5~2.0 안이다. 느슨한 기준으로 BCrypt 비교 생략 여부만 잡는다(spec Edge Case "응답 시간 차이로도 드러내지 않는다", research R8)

### Implementation for User Story 4

- [X] T066 [US4] `src/main/java/damso/account/application/port/in/LoginUseCase.java` + `src/main/java/damso/account/application/service/LoginService.java` — 이메일 소문자화 → `ensureAllowed(LOGIN, email)` → 계정 없음·탈퇴·불일치면 **고정 더미 BCrypt 해시와 비교해 시간 맞춤** 후 `recordFailure` + `INVALID_CREDENTIALS` → 성공 시 `reset` + `SessionIssuer` (research R8) (depends on T023, T030)
- [X] T067 [US4] `src/main/java/damso/account/application/port/in/RefreshUseCase.java` + `src/main/java/damso/account/application/service/RefreshService.java` — SHA-256으로 조회 → 없으면 401 → 조건부 회전 갱신 행 0(이미 사용) → 그 세션 `revoke(REUSE_DETECTED)` 후 401 → 세션 비활성·사용자 탈퇴면 401 → 새 갱신 토큰 발급, `expires_at = now + 14일`, 새 접근 토큰. 실패 사유는 모두 `UNAUTHORIZED` (research R4) (depends on T027, T030)
- [X] T068 [US4] `src/main/java/damso/account/application/port/in/LogoutUseCase.java` + `src/main/java/damso/account/application/service/LogoutService.java` — `CurrentUser.sessionId` 세션만 `revoke(LOGOUT)` (depends on T027)
- [X] T069 [US4] `src/main/java/damso/account/adapter/in/controller/AuthController.java`에 `POST /api/v1/auth/login`(200), `/api/v1/auth/token/refresh`(200), `/api/v1/auth/logout`(204) 추가 (depends on T066~T068)

**Checkpoint**: T063·T064 통과. 여기까지가 P1(시연 가능한 MVP)

---

## Phase 7: User Story 5 - 비밀번호를 재설정한다 (Priority: P2)

**Goal**: 재설정 코드 요청·확인·새 비밀번호, 모든 기기 로그아웃 (FR-029~031)

**Independent Test**: 가입·미가입 이메일 요청 응답이 같고, 받은 코드로 바꾼 뒤 새 비밀번호로만 로그인된다

### Tests for User Story 5

- [X] T070 [P] [US5] `src/test/java/damso/account/PasswordResetApiTest.java` — 가입·미가입 이메일 요청 202 본문 동일, 메일은 가입 이메일만 · `verify` 204(코드 소모 안 함) · `confirm` 후 옛 비밀번호 `INVALID_CREDENTIALS`, 새 비밀번호 성공 · 재설정 전 로그인해 둔 두 기기 세션 모두 401 · 같은 코드로 두 번째 `confirm` 거부 · 5회 실패 → 429 · 10분 경과 코드 거부 · 재요청 후 이전 코드 무효 · 정책 위반 새 비밀번호 400 (US5 시나리오 1~5, SC-003)

### Implementation for User Story 5

- [X] T071 [P] [US5] `src/main/java/damso/account/domain/PasswordReset.java` — 테이블 `password_reset`: `user_id` FK, `code_hash` char(64), `expires_at` "발송 + 10분", `used_at` NULL 허용(1회용). `src/main/java/damso/account/application/port/out/PasswordResetPort.java` + persistence 어댑터(같은 사용자의 이전 건 삭제)
- [X] T072 [US5] `src/main/java/damso/account/application/port/in/PasswordResetUseCase.java` + `src/main/java/damso/account/application/service/PasswordResetService.java` — `request`: 가입·활성 계정일 때만 이전 건 삭제·새 코드 저장·`ensureMailSendAllowed`·발송(발송 제한에 걸려도 응답은 같은 202). `verify`/`confirm`: `ensureAllowed(RESET_CODE, email)` → 불일치·만료·사용됨·미가입은 모두 `INVALID_VERIFICATION_CODE` + `recordFailure` → `confirm`은 `PasswordPolicy` → `changePassword` → `used_at` → `SessionPort.revokeAllByUser(PASSWORD_RESET)` (FR-031). 정리 작업(T058)에 `password_reset` 중 `used_at` 또는 `expires_at`이 24시간 넘게 지난 건 삭제를 추가한다 (depends on T023, T032, T071, T058)
- [X] T073 [US5] `src/main/java/damso/account/adapter/in/controller/AuthController.java`에 `POST /api/v1/auth/password-reset`(202), `/password-reset/verify`(204), `/password-reset/confirm`(204) 추가 (depends on T072)

**Checkpoint**: T070 통과

---

## Phase 8: User Story 6 - 교사 공개 프로필과 연결 현황 (Priority: P2)

**Goal**: 교사 프로필 수정, 담당 학급 학생별 학부모 연결 현황 (FR-033·035·036)

**Independent Test**: 교사가 프로필을 바꾸면 연결된 학부모의 자녀 목록에 반영되고, 현황에 학부모 이메일이 없다

### Tests for User Story 6

- [X] T074 [P] [US6] `src/test/java/damso/school/TeacherApiTest.java` — `GET/PUT /api/v1/teacher/profile`: 표시 이름 "1~20자", 소개 "0~60자", 61자 400 · 수정 내용이 연결 학부모의 `GET /api/v1/parent/children`에 반영 · `GET /api/v1/teacher/classroom`: 본인 학급만, 학생별 `guardianLinked`, 응답에 학부모 이메일·이름 0건 · 학부모·관리자 토큰으로 `/api/v1/teacher/**` 403 (US6 시나리오 1~5, SC-005·006)

### Implementation for User Story 6

- [X] T075 [US6] `src/main/java/damso/school/application/port/in/TeacherProfileUseCase.java`, `TeacherClassroomQuery.java` + `src/main/java/damso/school/application/service/TeacherService.java` — 프로필 조회·수정(본인 `teacher_id`만), 담당 학급(`homeroom_teacher_id = 본인`)과 학생 요약 (depends on T053, T044)
- [X] T076 [US6] `src/main/java/damso/school/adapter/in/controller/TeacherController.java` + DTO(`dto.request`·`dto.response`) — `GET/PUT /api/v1/teacher/profile`(`TeacherProfile`), `GET /api/v1/teacher/classroom`(`TeacherClassroomResponse`) (depends on T075)

**Checkpoint**: T074 통과

---

## Phase 9: User Story 7 - 마이페이지에서 내 정보와 계정을 관리한다 (Priority: P3)

**Goal**: 내 정보 조회·이름 수정·회원 탈퇴(비식별화·연결 해제·이력) (FR-037·038·038a)

**Independent Test**: 탈퇴 후 그 계정으로 로그인할 수 없고, 연결 이력은 UUID로만 남으며, 같은 이메일로 새 코드 가입이 된다

### Tests for User Story 7

- [X] T077 [P] [US7] `src/test/java/damso/account/MyAccountApiTest.java` — `GET /api/v1/me`(본인 이메일만, `MeResponse`), `PATCH /api/v1/me` 이름 "1~20자" 검증 · 마이페이지 연결 학급: 교사는 `GET /api/v1/teacher/classroom`, 학부모는 `GET /api/v1/parent/children`으로 본인 학급·자녀만 보인다(FR-037) (US7 시나리오 1)
- [X] T078 [P] [US7] `src/test/java/damso/account/WithdrawalApiTest.java` — 틀린 비밀번호 401 `INVALID_CREDENTIALS` · 학부모 탈퇴: 모든 기기 세션 401, `email` NULL, `name` "탈퇴한 사용자", 로그인 불가, 자녀들 `guardian_id` NULL, 관리자 명단 `guardianLinked=false`, 새 학부모 코드 발행 가능, `link_history`에 자녀별 `UNLINKED` + `WITHDRAWN`(이메일·이름 컬럼 없음) · 교사 탈퇴: 담임 해제, `teacher_profile` 삭제 · 같은 이메일로 새 코드 가입 성공 · 슈퍼 관리자 탈퇴 403 (US7 시나리오 2~7, FR-038·038a)
- [X] T079 [P] [US7] `src/test/java/damso/school/LinkHistoryAppendOnlyTest.java` — 리플렉션으로 `LinkHistoryRepository`에 `delete`·`update`로 시작하는 메서드와 `@Modifying` 메서드가 없음을 검사, `LinkHistory`에 public setter 없음, `@Immutable` 붙음 (research R10, 원칙 III)

### Implementation for User Story 7

- [X] T080 [US7] `src/main/java/damso/school/application/port/in/MembershipReleaseUseCase.java` + `src/main/java/damso/school/application/service/MembershipReleaseService.java` — `releaseAll(accountId, role)`: 교사면 `classroom.releaseTeacher()` + `TeacherProfile` 삭제 + `LinkHistory(UNLINKED, CLASSROOM)`, 학부모면 자녀마다 `releaseGuardian()` + `LinkHistory(UNLINKED, STUDENT)`, 마지막에 `LinkHistory(WITHDRAWN, NONE)` (depends on T054, T044, T053)
- [X] T081 [US7] `src/main/java/damso/account/application/port/in/MyAccountUseCase.java`, `WithdrawUseCase.java` + `src/main/java/damso/account/application/service/MyAccountService.java`, `WithdrawService.java` — 조회·이름 수정. 탈퇴: `SUPER_ADMIN`이면 `FORBIDDEN` → 비밀번호 불일치 `INVALID_CREDENTIALS` → **한 트랜잭션**에서 `MembershipReleaseUseCase.releaseAll` → `revokeAllByUser(WITHDRAWAL)` → `UserAccount.withdraw(now)` (research R11) (depends on T080, T027)
- [X] T082 [US7] `src/main/java/damso/account/adapter/in/controller/MeController.java` + DTO(`dto.request`·`dto.response`) — `GET /api/v1/me`, `PATCH /api/v1/me`, `POST /api/v1/me/withdrawal`(204) (depends on T081)

**Checkpoint**: T077~T079 통과. 모든 스토리 완료

---

## Phase 10: Polish & Cross-Cutting Concerns

- [X] T083 [P] `src/test/java/damso/account/AuthSensitiveLoggingTest.java` — 가입·로그인·재설정·코드 발행 전체 흐름 동안 로그를 캡처해 초대 코드 원문, 6자리 코드, 비밀번호, 갱신 토큰, 이메일 문자열이 0건인지 확인 (SC-006, 원칙 VI). 기존 `SensitiveLoggingTest` 방식을 따른다
- [X] T084 [P] `src/test/java/damso/school/CrossAccountExposureTest.java` — 교사 A·학부모 B·관리자 응답 전체(`/teacher/**`, `/parent/**`, `/admin/**`)에서 다른 사용자의 이메일 문자열 0건, 연락처 필드 0건 · 다른 학급 교사가 상대 학급 정보를 얻을 경로 없음 (SC-005·006, FR-036)
- [X] T085 [P] `docs/security-checklist.md` 갱신 — 입력 15, 보안기능 2·9·15·16, 시간 및 상태 1의 상태를 `조치`로 바꾸고 근거(클래스·테스트 이름) 기재. 보안기능 15 대응 문구는 "Redis 실패 횟수·대기(계정/이메일 기준)"로 맞춘다 (quickstart.md 3)
- [X] T086 [P] `docs/features.md` 상태 표 F1 행 상태를 `구현 완료`로 갱신
- [X] T087 `quickstart.md` 2절 수동 확인(Redis·Mailpit 컨테이너, 슈퍼 관리자 생성 → 1~7단계)을 실행하고 결과를 이 파일 하단 "구현 메모"에 적는다
- [X] T088 `./gradlew test` 전체 실행 — 실패가 있으면 숨기지 말고 테스트 이름과 함께 보고한다
- [X] T089 이 파일 하단에 "구현 메모" 절을 만들어 계획과 달라진 점(예: Boot 4 스타터 이름, 추가 클래스)을 foundation tasks.md와 같은 형식으로 기록한다

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: 바로 시작
- **Foundational (Phase 2)**: Setup 이후. **모든 스토리를 막는다**
- **US1 (Phase 3)**: Phase 2 이후 독립
- **US2 (Phase 4)**: Phase 2 이후. 테스트 데이터(초대 코드)를 만들려면 US1의 엔티티·발행 서비스(T041~T046)가 필요
- **US3 (Phase 5)**: US2의 가입 흐름·`InviteRedeemService`(T055~T057) 위에 학부모 분기를 더한다
- **US4 (Phase 6)**: Phase 2 이후 독립(계정은 `createAccount` 헬퍼로 만든다)
- **US5 (Phase 7)**: Phase 2 이후 독립. 세션 폐기 확인에 US4 로그인(T066)을 쓰면 편하다
- **US6 (Phase 8)**: `TeacherProfile`(T053, US2)과 학부모 자녀 목록(T061, US3)에 의존
- **US7 (Phase 9)**: US2·US3의 연결 데이터와 `LinkHistory`(T054)에 의존
- **Polish (Phase 10)**: 원하는 스토리 완료 후

### User Story Dependencies

```
Phase 2 ─┬─ US1 ── US2 ── US3 ─┬─ US6
         │                     └─ US7
         ├─ US4
         └─ US5
```

### Within Each User Story

- 테스트를 먼저 쓰고 실패를 확인한 뒤 구현한다
- 엔티티 → 포트·어댑터 → 서비스 → 컨트롤러 순
- 스토리가 끝날 때마다 Checkpoint 테스트를 돌리고 커밋한다(`Feat : …`, git-conventions)

### Parallel Opportunities

- Phase 2: T008~T017, T020·T021, T024~T026, T029, T031, T032, T034~T036는 서로 다른 파일이라 병렬 가능
- 각 스토리의 테스트 작업([P])은 서로 병렬 가능
- US1 엔티티 T041~T043 병렬
- Phase 2 이후 US1과 US4·US5는 병렬 진행 가능

---

## Parallel Example: Phase 2 이후

```text
개발자 A: US1 → US2 → US3 (T039~T062)
개발자 B: US4 → US5 (T063~T073)
```

> constitution 개발 흐름상 **한 기능은 한 사람이 끝까지** 맡는 것이 원칙이므로 F1은 한 명이 순서대로 진행하는 것을 기본으로 한다. 위 예시는 일정이 급할 때의 선택지다.

---

## Implementation Strategy

### MVP First (P1: US1~US4)

1. Phase 1 → Phase 2 (T038에서 foundation 회귀 없음 확인)
2. US1 → US2 → US3 → US4
3. **멈추고 확인**: T039~T064 통과, quickstart 2절 1~5단계 수동 확인
4. 이 시점에 F2(문의 접수) plan을 시작해도 된다 — 학부모·교사·학급 연결과 인증 주체가 준비됨

### Incremental Delivery

1. P1 완료 → 시연 가능(코드 발행 → 가입 → 연결 → 로그인)
2. US5·US6 (P2) → 비밀번호 재설정, 교사 프로필·현황
3. US7 (P3) → 마이페이지·탈퇴
4. Phase 10 → 로그·노출 점검, 체크리스트 갱신 (API 명세 통합·공지는 Phase 1에서 이미 끝남)

---

## Notes

- [P] = 다른 파일, 미완료 작업 의존 없음
- **테스트 실행 전 Docker를 켠다.** PostgreSQL·Redis 컨테이너를 쓴다
- 시간 의존 테스트는 `MutableClock`으로 시간을 앞당긴다. Redis TTL은 실제 시간이라 판정에 쓰지 않는다(T022)
- `ddl-auto=update`는 컬럼 추가만 반영한다. 구현 중 엔티티 컬럼 이름·타입을 바꿨다면 로컬 DB를 지우고 다시 만든다(research R1)
- 코드·비밀번호·갱신 토큰·이메일을 로그·예외 메시지에 넣지 않는다
- AI가 만든 코드도 설명할 수 있을 때까지 읽는다 (대회 진단·발표 대비)
- FR-004(슈퍼 관리자는 민원 내용 접근 불가)는 F1에 민원 경로가 없어 검증할 대상이 없다. **F2 tasks에서 관리자 토큰으로 문의 원문·정제본·답변 경로 접근이 403인지 테스트를 넣는다**

---

## 구현 메모 (2026-10-18)

계획과 달라졌거나 구현 중 추가된 것:

- **T007 미완료**: 프론트엔드 공지는 사람이 해야 하는 작업이라 하지 않았다. 담당자에게 `docs/api/openapi.yaml` 0.2.0(경로 24개, 에러 코드 6개, `Retry-After` 헤더)을 알리고 날짜·채널을 PR 설명에 적는다. `api-v0.2` 태그는 PR이 main에 merge된 뒤 `/api-release api-v0.2`로 찍는다.
- **커밋·푸시 안 함**: CLAUDE.md 규칙(요청이 있을 때만 커밋)에 따라 T006의 "커밋" 단계를 포함해 모든 변경은 작업 트리에 있다. 커밋은 `git-conventions` 규칙으로 따로 요청한다.
- **FK 대신 UUID 컬럼**: `auth_session.user_id`, `refresh_token.session_id`, `student.classroom_id`, `classroom.homeroom_teacher_id` 등은 `@ManyToOne` 없이 UUID 컬럼으로 두었다(도메인 간 엔티티 참조 금지, 마이그레이션 도구 없이 `ddl-auto=update`). 유니크 제약은 그대로 DB에 걸린다. `link_history.account_id`는 계획대로 FK가 없다.
- **포트 메서드 이름**: 한 어댑터가 여러 포트를 구현하므로 겹치는 이름을 구분했다(`findPendingById`, `deleteStalePending`, `deleteStaleResets`, `findClassroomById` 등).
- **학부모 분기를 US2에서 함께 구현**: `InviteRedeemService`에 `redeem`의 학부모 분기와 `redeemForExistingParent`를 한 번에 넣었고(T060), US3 테스트(T059)로 검증했다. 가입 확정 단계용으로 `resolve(inviteCodeId)`를 추가했다.
- **`MembershipReleaseUseCase.releaseAll(accountId)`**: 계획의 `role` 인자를 뺐다. school이 account의 `Role`을 알 필요가 없고, 교사 연결·보호자 연결을 둘 다 찾아 해제하면 되기 때문이다.
- **가입 1단계 순서**: 입력 검증(비밀번호 정책·약관) → 실패 제한 확인 → 초대 코드 검증. 잘못된 비밀번호가 초대 코드 실패 횟수를 올리지 않게 했다. 비밀번호 BCrypt 해시는 가입된 이메일이든 아니든 항상 먼저 계산해 처리 시간으로 가입 여부가 드러나지 않게 했다.
- **탈퇴 비밀번호 확인도 로그인 실패 횟수 규칙을 공유**한다(spec에 없던 보조 장치). 탈취된 접근 토큰으로 비밀번호를 대입하지 못하게 한다.
- **갱신 토큰 재사용 탐지**: `RefreshService.refresh`는 `@Transactional(noRollbackFor = BusinessException.class)`다. 401을 던지면서도 세션 폐기(REUSE_DETECTED)는 커밋해야 하기 때문이다.
- **공통 기반 추가**: `InvalidInputException`(서비스에서 fieldErrors와 함께 400), `TooManyAttemptsException`(429 + `Retry-After`), `DataIntegrityViolationException`을 409 `CONFLICT`로 매핑(동시에 같은 학급·같은 대상의 코드를 만든 경우), `Digests`(SHA-256), `CurrentUserArgumentResolver`.
- **설정 추가**: `spring.jackson.deserialization.fail-on-unknown-properties: true`(계약의 `additionalProperties: false`, 연락처 같은 필드를 몰래 받지 않음), `management.health.mail.enabled: false`(SMTP 장애가 서버 상태 DOWN으로 번지지 않게), `spring.data.redis.repositories.enabled: false`.
- **`LinkHistory`는 `Persistable`을 구현**한다. 직접 할당한 id라 Spring Data가 `merge`(SELECT 후 INSERT)를 하지 않고 바로 `persist`하게 하기 위해서다.
- **첫 기동의 Hibernate WARN**: `ddl-auto=update`가 처음 테이블을 만들 때 "constraint ... does not exist, skipping" 경고가 나온다. 제약을 먼저 지우려는 시도가 실패하는 것이라 무해하다.
- **테스트 지원**: `IntegrationTestSupport`가 테스트마다 모든 테이블을 TRUNCATE하고(학년·반 유니크 등 충돌 방지), 가입 흐름 헬퍼(`signUpWith`, `issueTeacherCode` 등)와 `validToken(role)`을 제공한다. foundation 테스트 5개의 "유효한 토큰"은 `validToken`으로 바꿨다(이제 `sid` 세션이 있어야 통과). 같은 이메일로 인증 메일을 다시 받는 테스트는 `clock.advance(61초)`가 필요하다(메일 재발송 제한).
- **T087 수동 확인(2026-10-18)**: PostgreSQL·Redis·Mailpit 컨테이너와 로컬 서버(포트 18080, 슈퍼 관리자는 환경변수로 생성)로 quickstart 2절의 1~7단계와 보안 응답 점검을 스크립트로 실행해 **17/17 통과**했다. 서버 로그에는 이메일·코드·비밀번호·토큰이 없었다. 확인 후 서버와 컨테이너는 정리했다.
- **이슈 #11 가입 전 코드 확인 (2026-10-25)**: 프론트 요청으로 FR-011a를 추가했다(research R16). 아래 작업을 했다.
  - [X] T089 `SignupUseCase.checkInviteCode` + `SignupService` 가입 1단계와 공통 검증 함수 `validateInviteCode`로 묶기
  - [X] T090 `AuthController` `POST /api/v1/auth/invite-codes/verify`, `InviteCodeCheckRequestDTO`·`InviteCodeCheckResponseDTO`, `SecurityConfig.PUBLIC_PATHS`에 경로 추가
  - [X] T091 `InviteCodeCheckApiTest`: 역할만 응답, 코드 미소모 후 가입 성공, 쓸 수 없는 코드 응답 통일(가입과 동일), 확인·가입 실패 합산 잠금, 형식 오류는 횟수 미포함
  - [X] T092 `docs/api/openapi.yaml`·`contracts/auth.openapi.yaml` 경로·스키마 추가
  - [ ] T093 프론트 공지와 `api-vX.Y` 태그(머지 후)
- **이슈 #22 비밀번호 재설정 발송 대기 429 (2026-10-28)**: 재설정 요청이 메일 발송 대기에 걸리면 조용히 202로 끝나 메일이 오지 않는데 앱은 "보냈습니다"를 보여 줬다(배포 서버 재현, damsoWE/damso_app#13 Q1-7). FR-029에 대기 중 동작을 추가했다. speckit 전체 단계 대신 간소화해 진행했다(사용자 결정).
  - [X] T094 `docs/api/openapi.yaml`·`contracts/auth.openapi.yaml` — `/auth/password-reset`에 `'429': TooManyAttempts`와 설명 추가, 공통 `TooManyAttempts`의 `Retry-After` 최대값 600 → 3600(메일 발송 시간당 5회 제한에 걸리면 최대 1시간 — 가입 인증 재발송도 같은 문제였다), `info.version` 0.7.0 → 0.8.0
  - [X] T095 프론트 공지: 2026-10-28 Slack(사용자). 코드 수정은 공지 뒤에 했다
  - [X] T096 `PasswordResetService.request()` — `reserveMailSend` 결과를 버리던 것을 `ensureMailSendAllowed`로 바꿔 계정 조회 **전에** 대기를 확인하고 대기 중이면 `TooManyAttemptsException`(429 + `Retry-After`). 대기가 없으면 지금처럼 가입된 활성 계정일 때만 코드 생성·발송, 아니면 202
  - [X] T097 `PasswordResetApiTest` — `requestingAgainWithinAMinuteSendsNoSecondMailButStillLooksTheSame`(202 기대)를 `requestingAgainWithinAMinuteIsTooManyAttemptsForRegisteredAndUnregisteredAlike`로 바꿈(가입·미가입 모두 429·같은 본문·`Retry-After` 1~60, 61초 뒤 정상 발송), 재현 테스트 `resetRightAfterASignupMailSharesTheSameWait` 추가
  - [X] T097a 코드 리뷰 반영: `TooManyAttemptsException.MAX_SECONDS` 600 → 3600(명세만 바꾸고 코드가 600에서 잘라 시간당 제한 대기를 짧게 알리던 문제), `PasswordResetUseCase.request()` Javadoc·`AuthController` 주석을 429 동작에 맞춤, 테스트 `sixthRequestWithinAnHourReportsTheRealHourlyWait`(시간당 6번째 요청의 `Retry-After`가 601~3600) 추가
  - [ ] T098 `api-v0.8` 태그(머지 후)
- **T088 전체 테스트**: `./gradlew test` — 37개 클래스, **246개 테스트 모두 통과**(실패·건너뜀 0).
