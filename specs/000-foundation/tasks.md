---

description: "Task list for 000-foundation"
---

# Tasks: 공통 보안·구조 기반 (Foundation)

**Input**: Design documents from `specs/000-foundation/`

**Prerequisites**: [plan.md](./plan.md), [spec.md](./spec.md), [research.md](./research.md), [data-model.md](./data-model.md), [contracts/](./contracts/foundation.openapi.yaml), [quickstart.md](./quickstart.md)

**Tests**: 포함한다. spec의 성공 기준(SC-001~006)이 "자동화 테스트로 확인"을 요구하고, 보안약점 체크리스트의 `조치` 근거로 테스트 이름이 필요하다.

**Organization**: spec의 사용자 스토리(US1~US7) 단위로 묶었다. 각 스토리는 Phase 2 완료 후 독립적으로 구현·검증할 수 있다.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: 다른 파일이고 미완료 작업에 의존하지 않아 병렬 가능
- **[Story]**: 해당 사용자 스토리 (US1~US7)

## Path Conventions

- 운영 코드: `src/main/java/damso/global/…`
- 테스트: `src/test/java/damso/…`
- 테스트 전용 엔티티·컨트롤러·토큰 발급기는 **`src/test`에만** 둔다(운영 코드에 진단용 경로를 남기지 않음, 체크리스트 캡슐화 2).
- **테스트용 시크릿 값을 파일에 커밋하지 않는다.** 실행 시 `SecureRandom`으로 생성해 시스템 프로퍼티로 넣는다(Gitleaks 오탐·실제 유출 방지).

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: 빌드·설정 파일 준비

- [ ] T001 작업 브랜치 생성: 저장소 루트에서 `git switch main && git pull && git switch -c feat/000-foundation` (constitution 머지 게이트, 이후 모든 커밋은 이 브랜치에)
- [ ] T002 `settings.gradle`에 `org.gradle.toolchains.foojay-resolver-convention` 플러그인을 추가해 JDK 21을 자동으로 받게 한다 (research R13)
- [ ] T003 `build.gradle`에 의존성 추가: `spring-boot-starter-oauth2-resource-server`, `spring-boot-starter-actuator`, `runtimeOnly org.postgresql:postgresql`, 테스트용 `spring-boot-testcontainers`, `org.testcontainers:testcontainers-postgresql`, `org.testcontainers:testcontainers-junit-jupiter`. **`testcontainers-bom`은 추가하지 않고 버전을 적지 않는다**(Boot 4.1이 Testcontainers 2.x를 관리, research R12)
- [ ] T004 `src/main/resources/application.properties`를 삭제하고 `src/main/resources/application.yml`을 만든다. 내용: `spring.application.name: damso` · datasource는 `${DAMSO_DB_URL}`·`${DAMSO_DB_USERNAME}`·`${DAMSO_DB_PASSWORD}` 참조만 · `damso.crypto.column-key: ${DAMSO_COLUMN_ENCRYPTION_KEY}` · `damso.jwt.secret: ${DAMSO_JWT_SECRET}` · `damso.openai.api-key: ${DAMSO_OPENAI_API_KEY}` · `spring.jpa.open-in-view: false` · `spring.jpa.hibernate.ddl-auto: none` · `server.error.include-stacktrace: never`, `include-message: never`, `include-binding-errors: never`, `include-exception: false`, `whitelabel.enabled: false` · `management.endpoints.web.exposure.include: health`, `management.endpoint.health.show-details: never` · `logging.level`: `org.hibernate.SQL: OFF`, `org.hibernate.orm.jdbc.bind: OFF`, `org.springframework.web: INFO`
- [ ] T005 [P] `.env.example`에 변수 **이름만** 추가: `DAMSO_DB_URL`, `DAMSO_DB_USERNAME`, `DAMSO_DB_PASSWORD`, `DAMSO_COLUMN_ENCRYPTION_KEY`, `DAMSO_JWT_SECRET`, `DAMSO_OPENAI_API_KEY`. 키 2개 옆에 생성 명령 주석 `# openssl rand -base64 32` (FR-018)
- [ ] T006 `./gradlew build -x test`로 컴파일이 되는지 확인한다(JDK 21 자동 다운로드 포함)

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: 모든 스토리가 쓰는 에러 형식·보안 골격·엔티티 기반·테스트 지원

**⚠️ CRITICAL**: 이 단계가 끝나야 스토리 작업을 시작한다

- [ ] T007 [P] `src/main/java/damso/global/error/ErrorCode.java` — enum. 각 상수는 HTTP 상태와 고정 메시지를 가진다. data-model.md 표 그대로: `INVALID_INPUT`(400, "입력값이 올바르지 않습니다."), `UNAUTHORIZED`(401, "인증이 필요합니다."), `FORBIDDEN`(403, "권한이 없습니다."), `NOT_FOUND`(404, "요청한 대상을 찾을 수 없습니다."), `METHOD_NOT_ALLOWED`(405, "허용되지 않은 요청 방식입니다."), `CONFLICT`(409, "다른 요청과 충돌했습니다. 다시 시도해 주세요."), `PAYLOAD_TOO_LARGE`(413, "요청 크기가 너무 큽니다."), `UNSUPPORTED_MEDIA_TYPE`(415, "지원하지 않는 형식입니다."), `INTERNAL_ERROR`(500, "일시적인 오류가 발생했습니다.")
- [ ] T008 [P] `src/main/java/damso/global/error/ErrorResponse.java` — record `{ code, message, fieldErrors }`와 중첩 record `FieldError { field, reason }`. `fieldErrors`는 비어 있으면 JSON에서 생략. 팩토리 `of(ErrorCode)`, `of(ErrorCode, List<FieldError>)`. 메시지는 항상 `ErrorCode`의 고정 문구 (contract `ErrorResponse`, `additionalProperties: false`)
- [ ] T009 [P] `src/main/java/damso/global/error/BusinessException.java` — `ErrorCode`를 담는 런타임 예외. 예외 메시지에 입력값·민감 값을 넣지 않는다
- [ ] T010 [P] `src/main/java/damso/global/entity/BaseEntity.java` — `@MappedSuperclass`. 필드: `@Id UUID id`(생성자에서 `UUID.randomUUID()`로 할당, 변경 불가), `createdAt`(Instant, `@CreatedDate`, `updatable=false`), `updatedAt`(Instant, `@LastModifiedDate`), `@Version Long version`. `equals/hashCode`는 `id` 기준, `toString()`은 `클래스명[id=…]`만 출력 (data-model.md BaseEntity)
- [ ] T011 [P] `src/main/java/damso/global/config/JpaAuditingConfig.java` — `@EnableJpaAuditing`
- [ ] T012 [P] `src/main/java/damso/global/security/JwtDecoderConfig.java` — `damso.jwt.secret`(Base64)을 디코딩해 `NimbusJwtDecoder.withSecretKey(…).macAlgorithm(MacAlgorithm.HS256)`로 **알고리즘 고정**. 검증기: 기본 타임스탬프 + `exp`·`iat` 필수 클레임 검증 추가. `role` 클레임 → 권한 `ROLE_{role}` 변환기(`PARENT`·`TEACHER`·`SUPER_ADMIN`) (research R1, data-model 인증 주체)
- [ ] T013 `src/main/java/damso/global/security/SecurityConfig.java` — `SecurityFilterChain`: `dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()`로 오류 디스패치가 원래 상태 코드를 유지하게 함(인증된 요청의 500이 401로 바뀌지 않도록, T020에서 검증), 세션 `STATELESS`, `csrf`·`formLogin`·`httpBasic`·`logout` 비활성(근거: 쿠키 미사용, research R8), 공개 경로 상수 `PUBLIC_PATHS = { "/actuator/health" }`만 `permitAll`, 나머지 `anyRequest().authenticated()`, `oauth2ResourceServer(jwt)`. 기동 로그에 "generated security password"가 나오지 않는지 확인 (depends on T012)
- [ ] T014 [P] `src/test/java/damso/support/TestSecrets.java` — 32바이트 키 2개를 `SecureRandom`으로 생성해 `DAMSO_COLUMN_ENCRYPTION_KEY`·`DAMSO_JWT_SECRET`·`DAMSO_OPENAI_API_KEY`(더미) 시스템 프로퍼티로 설정하는 정적 초기화 유틸. **`DAMSO_DB_URL`·`DAMSO_DB_USERNAME`·`DAMSO_DB_PASSWORD`에도 더미 값을 넣는다** — `SecretsValidator`(T025)는 이 셋도 검사하고, 실제 접속은 Testcontainers `@ServiceConnection`(T016)이 덮어쓴다. 생성한 JWT 키를 다른 테스트 지원 클래스가 읽을 수 있게 노출
- [ ] T015 [P] `src/test/java/damso/support/TestJwtIssuer.java` — `TestSecrets`의 키로 HS256 토큰을 발급하는 테스트 전용 도구. 옵션: `sub`(UUID), `role`, 만료 시각, 서명 키 교체(위조용), `alg=none` 토큰 생성
- [ ] T016 [P] `src/test/java/damso/support/IntegrationTestSupport.java` — `@SpringBootTest` 기반 추상 클래스. `TestSecrets` 적용, Testcontainers PostgreSQL(`org.testcontainers.postgresql.PostgreSQLContainer`, Testcontainers 2.x 패키지) + `@ServiceConnection`, 테스트 프로파일에서 `spring.jpa.hibernate.ddl-auto: create-drop`, `MockMvc` 제공
- [ ] T017 [P] `src/test/java/damso/support/ErrorResponseAssertions.java` — 공통 검증 헬퍼 `assertSafeErrorResponse(body, expectedCode)`: 키가 `code`·`message`(·`fieldErrors`)뿐인지, `code`가 기대값인지, 본문에 `"Exception"`, `"at damso."`, `".java"`, `"SELECT"`, `"org.springframework"`, `"trace"`, `"/home/"`가 없는지 검사 (SC-001을 모든 에러 테스트에 적용)
- [ ] T018 `src/test/java/damso/DamsoApplicationTests.java` — 기존 `contextLoads`를 `IntegrationTestSupport` 상속으로 바꿔 시크릿·DB가 있는 상태에서 컨텍스트가 뜨는지 확인 (depends on T016)

**Checkpoint**: 컨텍스트가 뜨고, 모든 경로가 기본 거부된다(응답 형식은 아직 기본값)

---

## Phase 3: User Story 1 - 오류를 유발해도 내부 정보가 보이지 않는다 (Priority: P1) 🎯 MVP

**Goal**: MVC·서블릿 계층의 모든 오류가 `ErrorResponse` 형식이고 금지 정보가 없다 (FR-001~004)

**Independent Test**: 테스트 전용 컨트롤러로 예외·잘못된 요청을 유발해 응답 본문을 검사한다

### Tests for User Story 1

- [ ] T019 [P] [US1] `src/test/java/damso/support/ErrorProbeController.java` — 테스트 전용 컨트롤러(인증 필요 경로 `/test/errors/**`). 경로별로 `RuntimeException`, `IllegalStateException`(메시지에 `"/home/app/secret.java"`·`"SELECT * FROM"` 포함), `BusinessException(NOT_FOUND)`, `OptimisticLockingFailureException`(409 확인용)을 던진다. GET 전용 경로 하나, **`@RequestBody`(JSON)를 받아 `RuntimeException`을 던지는 POST 경로 하나**(415·400·로그 표식 테스트용)를 둔다
- [ ] T020 [US1] `src/test/java/damso/global/error/ErrorResponseTest.java` — 유효 토큰으로 호출하고 모든 응답을 `ErrorResponseAssertions.assertSafeErrorResponse`로 검증: 내부 예외 → 500 `INTERNAL_ERROR`, `BusinessException(NOT_FOUND)` → 404, 없는 경로 → 404 `NOT_FOUND`, 허용 안 된 메서드 → 405 `METHOD_NOT_ALLOWED`, 깨진 JSON → 400 `INVALID_INPUT`, 잘못된 Content-Type → 415 `UNSUPPORTED_MEDIA_TYPE`, **유효 토큰으로 유발한 500이 401로 바뀌지 않음**(T013 오류 디스패치). **모든 응답 본문에** `"Exception"`, `"at damso."`, `".java"`, `"SELECT"`, `"org.springframework"`, `"trace"`, `"/home/"` 문자열이 없고, 키가 `code`·`message`(·`fieldErrors`)뿐인지 확인 (SC-001) (depends on T019)

### Implementation for User Story 1

- [ ] T021 [US1] `src/main/java/damso/global/error/GlobalExceptionHandler.java` — `@RestControllerAdvice`. 처리: `BusinessException` → 해당 코드, `NoResourceFoundException` → `NOT_FOUND`, `HttpRequestMethodNotSupportedException` → `METHOD_NOT_ALLOWED`, `HttpMediaTypeNotSupportedException` → `UNSUPPORTED_MEDIA_TYPE`, `HttpMessageNotReadableException`·`MethodArgumentTypeMismatchException` → `INVALID_INPUT`(파서 메시지 비노출), `Exception` → `INTERNAL_ERROR`. 500은 서버 로그에만 ERROR로 스택트레이스·요청 메서드·경로를 남긴다. **요청 본문과 쿼리스트링은 남기지 않는다** (FR-003, 응답에는 여전히 고정 문구만)
- [ ] T022 [US1] `src/main/java/damso/global/error/JsonErrorController.java` — `/error`를 처리하는 `ErrorController`. 서블릿 오류 속성의 상태 코드만 보고 `ErrorCode`로 매핑해 `ErrorResponse` 반환(매핑 없으면 `INTERNAL_ERROR`). Boot 기본 `BasicErrorController`를 대체. 응답 생성 중 다시 예외가 나도 최소 `INTERNAL_ERROR` JSON을 직접 쓰는 최종 대비 처리를 둔다 (spec Edge Case)

**Checkpoint**: T020 통과 — US1 단독으로 동작

---

## Phase 4: User Story 2 - 시크릿이 빠지면 서버가 뜨지 않는다 (Priority: P1)

**Goal**: 필수 시크릿 6개를 바인딩 전에 검사, 실패 시 이름만 출력하고 기동 거부 (FR-014~018)

**Independent Test**: 시크릿을 하나씩 빼거나 짧게 만들어 검증기를 실행하고 결과·메시지를 확인한다

### Tests for User Story 2

- [ ] T023 [P] [US2] `src/test/java/damso/global/config/SecretsValidatorTest.java` — `MockEnvironment`로 단위 테스트: 6개 각각 누락 시 실패, `DAMSO_COLUMN_ENCRYPTION_KEY`·`DAMSO_JWT_SECRET`이 "Base64, 디코딩 32바이트 이상"을 못 채우면(31바이트·Base64 아님) 실패, 모두 정상이면 통과. **실패 메시지에 변수 이름은 있고 넣은 값은 없는지** 확인 (SC-002)
- [ ] T024 [P] [US2] `src/test/java/damso/global/config/StartupRefusalTest.java` — `SpringApplication`을 직접 실행해 시크릿 누락 시 기동이 예외로 끝나는지, 예외 메시지·출력에 값이 없는지 확인

### Implementation for User Story 2

- [ ] T025 [US2] `src/main/java/damso/global/config/SecretsValidator.java` — `EnvironmentPostProcessor`. 검사 목록(data-model 필수 시크릿 표 그대로): `DAMSO_DB_URL`·`DAMSO_DB_USERNAME`·`DAMSO_DB_PASSWORD`·`DAMSO_OPENAI_API_KEY` "비어 있지 않음", `DAMSO_COLUMN_ENCRYPTION_KEY`·`DAMSO_JWT_SECRET` "Base64, 디코딩 32바이트 이상". 실패를 모두 모아 `필수 시크릿 검증 실패: 이름(누락|키 길이 부족|형식 오류), …` 한 번에 예외. **값은 절대 메시지·로그에 넣지 않는다** (research R7)
- [ ] T026 [US2] `src/main/resources/META-INF/spring.factories`에 `SecretsValidator` 등록. Spring Boot 4.1의 `EnvironmentPostProcessor` 등록 키(패키지 이동 여부)를 확인하고 맞는 키를 쓴다 (depends on T025)
- [ ] T027 [US2] `src/test/java/damso/global/config/NoDefaultAccountTest.java` — 컨텍스트에 `UserDetailsService`·`InMemoryUserDetailsManager` 빈이 없고 기본 비밀번호가 생성되지 않는지 확인 (FR-017)

**Checkpoint**: T023·T024·T027 통과

---

## Phase 5: User Story 3 - 명시되지 않은 경로는 모두 인증을 요구한다 (Priority: P1)

**Goal**: 기본 거부, 헤더 토큰만, 세션 쿠키 없음, 인증·인가 실패도 `ErrorResponse` (FR-019~022)

**Independent Test**: 무인증·위조·만료·`alg=none` 토큰으로 호출하고 응답 형식·쿠키를 확인한다

### Tests for User Story 3

- [ ] T028 [P] [US3] `src/test/java/damso/support/AuthProbeController.java` — 테스트 전용: `/test/auth/any`(인증만 필요), `/test/auth/teacher-only`(`ROLE_TEACHER` 필요)
- [ ] T029 [US3] `src/test/java/damso/global/security/AccessControlTest.java` — 검증: 무인증 `/test/auth/any`·임의 경로 → 401 `UNAUTHORIZED`, `/actuator/health` 무인증 → 200 `{"status":"UP"}`(details 없음), 다른 키로 서명한 토큰·만료 토큰·`alg=none` 토큰·`exp` 없는 토큰 → 401, 유효 토큰 → 200, `PARENT` 토큰으로 `/test/auth/teacher-only` → 403 `FORBIDDEN`, **공개 경로에 위조 토큰을 붙여도 200**, 모든 응답에 `Set-Cookie` 헤더 없음, 에러 응답은 `assertSafeErrorResponse` 통과, 유효 토큰으로 `/actuator/env`·`/actuator/beans`·`/actuator/configprops` 호출 시 설정값·빈 목록이 응답되지 않음(404 `NOT_FOUND`) (SC-003, FR-007, spec Edge Case) (depends on T028)

### Implementation for User Story 3

- [ ] T030 [P] [US3] `src/main/java/damso/global/security/JsonAuthenticationEntryPoint.java` — 401 `ErrorResponse(UNAUTHORIZED)`를 JSON으로 쓴다. `WWW-Authenticate` 헤더에 토큰 오류 상세를 넣지 않는다
- [ ] T031 [P] [US3] `src/main/java/damso/global/security/JsonAccessDeniedHandler.java` — 403 `ErrorResponse(FORBIDDEN)` JSON
- [ ] T032 [US3] `src/main/java/damso/global/security/SecurityConfig.java` — T030·T031를 `exceptionHandling`과 `oauth2ResourceServer`의 entry point로 연결. `BearerTokenResolver`가 `PUBLIC_PATHS` 요청에서는 토큰을 무시하도록 설정 (depends on T030, T031)

**Checkpoint**: T029 통과 — 여기까지가 P1(MVP) 범위

---

## Phase 6: User Story 4 - 민감 본문은 저장소에 평문으로 남지 않는다 (Priority: P2)

**Goal**: `SensitiveText` 타입 필드가 자동으로 AES-256-GCM 암호화 저장 (FR-011~013)

**Independent Test**: 테스트 엔티티를 저장하고 DB 원본을 직접 조회한다

### Tests for User Story 4

- [ ] T033 [P] [US4] `src/test/java/damso/global/crypto/AesGcmCipherTest.java` — 단위: 왕복 일치, 같은 평문 두 번 암호화 결과가 다름, 암호문 1바이트 변조 시 예외, 버전 바이트가 `0x01`이 아니면 예외, 다른 키로 복호화 시 예외, 빈 문자열 → 빈 문자열
- [ ] T034 [P] [US4] `src/test/java/damso/support/SecretNote.java` — 테스트 전용 엔티티(`BaseEntity` 상속, 필드 `SensitiveText content`, 컬럼 타입 `TEXT`)와 `SecretNoteRepository`
- [ ] T035 [US4] `src/test/java/damso/global/crypto/SensitiveTextPersistenceTest.java` — `IntegrationTestSupport` 상속. 저장 후 네이티브 쿼리로 원본 컬럼을 읽어 평문과 다르고 Base64 형식인지, 같은 평문 두 건의 원본이 서로 다른지, 리포지토리 재조회 값이 원문과 같은지 확인 (SC-004) (depends on T034)

### Implementation for User Story 4

- [ ] T036 [P] [US4] `src/main/java/damso/global/crypto/SensitiveText.java` — 불변 값 타입. `of(String)`(null 불가, 빈 문자열 허용), 원문은 `reveal()`로만, `toString()`은 항상 `"[MASKED]"`, `equals/hashCode`는 원문 기준. 게터 이름(`getX`)을 쓰지 않는다 (data-model SensitiveText)
- [ ] T037 [P] [US4] `src/main/java/damso/global/crypto/AesGcmCipher.java` — AES-256-GCM, IV 12바이트(`SecureRandom`, 값마다 새로), 태그 128비트. 형식 `Base64( version(1B)=0x01 ‖ IV(12B) ‖ ciphertext ‖ tag(16B) )`. 키는 `damso.crypto.column-key`(Base64 32바이트). 빈 문자열은 암호화하지 않음. 복호화 실패는 원인 메시지 없이 내부 예외로
- [ ] T038 [US4] `src/main/java/damso/global/crypto/SensitiveTextConverter.java` — `@Converter(autoApply = true)` `AttributeConverter<SensitiveText, String>`, Spring 빈으로 `AesGcmCipher` 주입(Boot 4의 Hibernate–Spring 빈 컨테이너 연동 확인) (depends on T036, T037)

**Checkpoint**: T033·T035 통과

---

## Phase 7: User Story 5 - 민감 본문은 로그에 남지 않는다 (Priority: P2)

**Goal**: 민감 값이 어떤 로그 경로로도 원문 출력되지 않음 (FR-005·006)

**Independent Test**: 로그를 메모리로 받아 원문 문자열을 검색한다

### Tests for User Story 5

- [ ] T039 [US5] `src/test/java/damso/global/logging/SensitiveLoggingTest.java` — Logback `ListAppender`로 루트 로그 수집. 고유 표식 문자열(예: `"PII-MARKER-7f3a"`)로 `SensitiveText`·`SecretNote` 엔티티·이를 담은 예외 메시지를 `log.info("{}", …)`로 출력하고, `ErrorProbeController`에 표식이 든 본문을 보내 500을 유발한 뒤, **수집된 모든 로그에 표식이 0건**인지 확인 (SC-005) (depends on T034, T036, T021)

### Implementation for User Story 5

- [ ] T040 [US5] `src/main/resources/application.yml` 로그 설정 재확인: Hibernate SQL·바인딩 파라미터 `OFF`, 요청 본문 로깅 필터(`CommonsRequestLoggingFilter`) 미등록. 테스트 실패 시 원인 경로를 막는다

**Checkpoint**: T039 통과

---

## Phase 8: User Story 6 - 식별자는 추측 불가, 동시 수정은 충돌로 감지 (Priority: P2)

**Goal**: UUID v4 식별자, `@Version` 충돌 → 409 `CONFLICT` (FR-008~010)

**Independent Test**: 엔티티를 대량 생성해 식별자를 검사하고, 같은 버전 기준 두 번 수정한다

### Tests for User Story 6

- [ ] T041 [US6] `src/test/java/damso/global/entity/BaseEntityTest.java` — `IntegrationTestSupport` 상속. `SecretNote` 1,000개 생성 시 id가 모두 UUID v4(`version()==4`)·중복 0건, 저장 후 `createdAt`·`updatedAt`·`version` 자동 설정, 같은 엔티티를 두 트랜잭션에서 같은 버전으로 읽어 각각 수정하면 두 번째가 실패, `ErrorProbeController`의 409 경로가 `CONFLICT`로 응답되고 `assertSafeErrorResponse`를 통과하는지 확인 (SC-006) (depends on T034)

### Implementation for User Story 6

- [ ] T042 [US6] `src/main/java/damso/global/error/GlobalExceptionHandler.java` — `OptimisticLockingFailureException`(및 JPA `OptimisticLockException`) → `CONFLICT` 처리 추가 (depends on T021)

**Checkpoint**: T041 통과

---

## Phase 9: User Story 7 - 잘못된 입력은 진입 지점에서 걸러진다 (Priority: P3)

**Goal**: 선언적 검증 실패 → `INVALID_INPUT` + `fieldErrors`, 64KB 초과 → 413 (FR-023~025)

**Independent Test**: 검증 규칙이 걸린 테스트 경로에 잘못된 입력을 보낸다

### Tests for User Story 7

- [ ] T043 [P] [US7] `src/test/java/damso/support/ValidationProbeController.java` — 테스트 전용 `POST /test/validation`, 요청 DTO: `content`(`@NotBlank`, `@Size(max = 2000)`), `count`(`@Min(1)`)
- [ ] T044 [US7] `src/test/java/damso/global/error/InputValidationTest.java` — 2,001자 `content` → 400 `INVALID_INPUT`, `fieldErrors[0].field == "content"`, `reason == "최대 2000자입니다"`, **응답 본문에 보낸 입력값이 없음**. 빈 `content` → `reason == "필수 값입니다"`. 깨진 JSON → 400, 파서 메시지 없음. 본문 65,537바이트(Content-Length 지정) → 413 `PAYLOAD_TOO_LARGE`, chunked 전송 65,537바이트 → 413. 모든 에러 응답은 `assertSafeErrorResponse` 통과 (depends on T043)

### Implementation for User Story 7

- [ ] T045 [US7] `src/main/java/damso/global/error/GlobalExceptionHandler.java` — `MethodArgumentNotValidException`·`HandlerMethodValidationException`·`ConstraintViolationException` → `INVALID_INPUT` + `fieldErrors`. `reason`은 제약 종류별 고정 문구: `NotNull`·`NotBlank`·`NotEmpty` → "필수 값입니다", `Size(max)` → "최대 {max}자입니다", `Size(min)` → "최소 {min}자입니다", `Min`/`Max` → "허용 범위를 벗어났습니다", `Pattern`·`Email` → "형식이 올바르지 않습니다", 그 외 → "올바르지 않은 값입니다". 거부된 값은 넣지 않는다 (depends on T042)
- [ ] T046 [US7] `src/main/java/damso/global/config/RequestSizeLimitFilter.java` — 최우선 순서 서블릿 필터. `Content-Length` > 65,536이면 즉시 413 `ErrorResponse(PAYLOAD_TOO_LARGE)` JSON. `Content-Length`가 없으면 입력 스트림을 감싸 읽은 바이트가 65,536을 넘는 순간 중단하고 413 (research R9)

**Checkpoint**: T044 통과 — 모든 스토리 완료

---

## Phase 10: Polish & Cross-Cutting Concerns

- [ ] T047 `./gradlew test` 전체 실행, 실패 0건 확인 (실패를 숨기거나 테스트를 지우지 않는다)
- [ ] T048 `gitleaks git . --redact`로 검출 0건 확인 (SC-007)
- [ ] T049 quickstart.md 2절(수동 기동 확인)을 그대로 실행해 결과 기록
- [ ] T050 `docs/security-checklist.md` — 12개 항목을 `조치`로 바꾸고 근거(파일 경로·테스트 클래스명) 기재: 에러처리 1·3, 보안기능 1·4·5·6·7·8·10, 시간 및 상태 1, 캡슐화 2, 입력데이터 검증 14. 입력데이터 검증 11(CSRF)·보안기능 12(쿠키)는 `해당없음`(근거: 쿠키·세션 미사용, `AccessControlTest`)으로 갱신 (SC-008)
- [ ] T051 `specs/000-foundation/contracts/foundation.openapi.yaml`의 `components`(schemas·responses·securitySchemes)와 `/actuator/health`를 `docs/api/openapi.yaml`에 통합하고 `api-v0.1` 태그 → 프론트 공지 (contract-first, 사람 확인 후)
- [ ] T052 SC-009 확인: `src/main/java/damso` 아래에서 `Cipher`·`[MASKED]`를 직접 쓰는 곳이 `damso.global.crypto` 밖에 0곳인지 검색
- [ ] T053 `feat/000-foundation`을 push하고 GitHub PR 생성 → 팀원 리뷰 → CI(Gitleaks) 통과 후 main에 merge (constitution 머지 게이트)

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1 Setup** → **Phase 2 Foundational** → 스토리(Phase 3~9) → **Phase 10 Polish**
- Phase 2가 끝나야 어떤 스토리도 시작할 수 있다.

### User Story Dependencies

| 스토리 | 의존 | 비고 |
|---|---|---|
| US1 오류 응답 (P1) | Phase 2 | `GlobalExceptionHandler` 생성 |
| US2 시크릿 (P1) | Phase 2 | 독립 |
| US3 접근 통제 (P1) | Phase 2 | 독립 (`SecurityConfig` 수정) |
| US4 암호화 (P2) | Phase 2 | 독립. `SecretNote`(T034)를 US5·US6이 재사용 |
| US5 로그 (P2) | US1(T021), US4(T034·T036) | 오류 경로·민감 타입이 있어야 검증 가능 |
| US6 UUID·충돌 (P2) | US1(T021), US4(T034) | `GlobalExceptionHandler`에 추가 |
| US7 입력 검증 (P3) | US6(T042) | 같은 파일 `GlobalExceptionHandler` 순차 수정 |

### Same-file 순서 (병렬 금지)

- `GlobalExceptionHandler.java`: T021 → T042 → T045
- `SecurityConfig.java`: T013 → T032
- `application.yml`: T004 → T040

### Parallel Opportunities

- Phase 2: T007·T008·T009·T010·T011·T012·T014·T015·T016·T017 동시 가능 (서로 다른 파일)
- Phase 2 이후 US1·US2·US3·US4 동시 진행 가능
- 스토리 안의 `[P]` 테스트 지원 파일과 구현 파일은 동시 작성 가능

---

## Parallel Example: Phase 2 이후

```text
개발자 A: US1 (T019 → T020 → T021 → T022)
개발자 B: US2 (T023, T024 → T025 → T026 → T027)
개발자 C: US4 (T033, T034, T036, T037 → T038 → T035)
```

> constitution 개발 흐름상 **한 기능은 한 사람이 끝까지** 맡는 것이 원칙이므로, 000-foundation은 한 명이 순서대로 진행하는 것을 기본으로 한다. 위 예시는 일정이 급할 때의 선택지다.

---

## Implementation Strategy

### MVP First (P1: US1~US3)

1. Phase 1 → Phase 2
2. US1 → US2 → US3 (P1 세 개)
3. **멈추고 확인**: T020·T023·T024·T027·T029 통과, 수동 기동 확인
4. 이 시점에 F1(인증) plan을 시작해도 된다 — 에러 형식·보안 골격·시크릿 검증이 준비됨

### Incremental Delivery

1. P1 완료 → F1 착수 가능
2. US4·US5·US6 (P2) → F2(문의 접수)가 쓰는 암호화·로그 가림·충돌 감지 준비
3. US7 (P3) → 입력 검증 형식 통일
4. Phase 10 → 체크리스트 갱신, API 명세 통합

---

## Notes

- [P] = 다른 파일, 미완료 작업 의존 없음
- 각 Checkpoint에서 테스트를 돌리고 커밋한다
- AI가 만든 코드도 설명할 수 있을 때까지 읽는다 (대회 진단·발표 대비)
- **테스트 실행 전 Docker를 켠다.** 대부분의 테스트가 Testcontainers PostgreSQL을 쓴다
- Windows에서 spec-kit 스크립트가 `UnicodeEncodeError: 'cp949'`로 실패하면 `PYTHONIOENCODING=utf-8`을 붙여 실행한다
