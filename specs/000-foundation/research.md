# Research: 공통 보안·구조 기반 (Foundation)

**Feature**: [spec.md](./spec.md) · **Date**: 2026-10-15

각 항목은 Decision / Rationale / Alternatives considered 형식이다.

---

## R1. 토큰 검증 방식

- **Decision**: Spring Security OAuth2 Resource Server의 JWT 지원(Nimbus)을 쓴다. 대칭키 HMAC-SHA256(HS256)으로 서명 알고리즘을 **고정**하고, `exp` 클레임을 필수로 검증한다. 토큰은 `Authorization: Bearer` 헤더로만 받는다.
- **Rationale**: 별도 JWT 라이브러리 없이 Spring 공식 경로로 검증 필터·에러 처리·인가 연동을 얻는다. `NimbusJwtDecoder.withSecretKey(...).macAlgorithm(HS256)`로 허용 알고리즘이 하나로 묶여 `alg=none`·알고리즘 혼동 공격이 차단된다(FR-021). F1의 발급도 같은 Nimbus 인코더로 맞출 수 있다. 서버 1대(기획서 아키텍처) 구성이라 비대칭키의 이점(검증자 분리)이 없다.
- **Alternatives considered**:
  - jjwt 등 별도 라이브러리 + 직접 만든 필터: 검증 누락·예외 처리 실수 위험이 커지고 체크리스트 근거가 약해진다.
  - RS256(비대칭): 키 쌍 관리 부담 대비 이점 없음. 서비스가 분리되면 재검토.

## R2. 에러 응답 형식

- **Decision**: 자체 공통 형식 `{ code, message, fieldErrors[] }` + `ErrorCode` enum. MVC 안의 예외는 `@RestControllerAdvice`, MVC 밖(보안 필터)은 `AuthenticationEntryPoint`·`AccessDeniedHandler`, 그 외 잔여 경로는 `/error`를 처리하는 `ErrorController`가 같은 형식으로 응답한다. Boot 기본 오류 노출 설정은 모두 끈다(`include-stacktrace=never`, `include-message=never`, `include-exception=false`, whitelabel 비활성).
- **Rationale**: 오류가 생기는 세 계층(MVC·보안 필터·서블릿 컨테이너)을 모두 덮어야 FR-002를 100% 만족한다(SC-001). 프론트(Flutter)가 하나의 형식만 파싱하면 된다.
- **Alternatives considered**:
  - RFC 9457 ProblemDetail: 표준이지만 `instance`·`type` 등 필드가 늘고 프론트 mock 작성 부담이 커진다. 필드 구성이 자체 형식과 크게 다르지 않아 이점이 적다.
  - `@RestControllerAdvice`만 사용: 보안 필터에서 나는 401/403과 404·413 일부가 빠진다.

## R3. 식별자(UUID) 생성

- **Decision**: 모든 엔티티는 공통 추상 클래스 `BaseEntity`를 상속하고, `@Id UUID id`는 **애플리케이션에서 생성 시점에** `UUID.randomUUID()`(v4)로 할당한다.
- **Rationale**: v4는 122비트 무작위값이고 내부적으로 `SecureRandom`을 써서 추측 불가(FR-008). 저장 전에 id가 정해져 `equals/hashCode`가 안정적이다. spec SC 요건("연속 생성 식별자 사이에 규칙성이 없다")을 만족한다.
- **Alternatives considered**:
  - UUID v7(시간 정렬): 인덱스 효율은 좋지만 앞부분이 시간이라 생성 순서·시각이 드러난다. "규칙성 없음" 요건과 충돌.
  - DB 시퀀스 + 별도 공개 UUID 컬럼: 컬럼이 두 개가 되어 실수로 순번이 노출될 여지가 생긴다.

## R4. 동시 수정 충돌

- **Decision**: `BaseEntity`에 `@Version Long version`. `OptimisticLockingFailureException`은 공통 에러 `CONFLICT`(409)로 변환한다.
- **Rationale**: constitution 백엔드 요건(낙관적 락)과 체크리스트 "시간 및 상태 1"을 그대로 충족. 상태 전이 경쟁이 잦지 않은 도메인이라 비관적 락이 필요 없다.
- **Alternatives considered**: 비관적 락(`SELECT … FOR UPDATE`): 교착·대기 위험, 이번 규모에 과함.

## R5. 컬럼 암호화

- **Decision**: AES-256-GCM. 값마다 12바이트 IV를 `SecureRandom`으로 새로 만들고, 태그 128비트. 저장 형식은 `Base64(버전 1바이트 ‖ IV ‖ 암호문+태그)`. JPA `AttributeConverter`로 구현하고 `autoApply = true`로 **`SensitiveText` 타입 필드에 자동 적용**한다(R6). 키는 환경변수 `DAMSO_COLUMN_ENCRYPTION_KEY`(Base64, 디코딩 32바이트).
- **Rationale**: GCM은 인증 암호화라 변조 시 복호화가 실패한다(US4 시나리오 4). 매번 새 IV라 같은 평문도 다른 암호문(US4 시나리오 3). 버전 바이트로 향후 키 교체 여지를 남긴다(이번 범위 밖). 타입 기반 자동 적용이라 개발자가 필드 타입만 정하면 된다(SC-009).
- **Alternatives considered**:
  - AES-CBC + HMAC: 구현 요소가 늘고 실수 여지가 크다.
  - DB 기능(pgcrypto): 키가 DB로 넘어가고 쿼리 로그에 노출될 수 있다.
  - Hibernate `@ColumnTransformer`: SQL에 키가 들어간다.

## R6. 로그 마스킹 ("민감 값 표시 수단")

- **Decision**: 불변 값 타입 `SensitiveText`를 둔다. `toString()`은 항상 `"[MASKED]"`를 반환하고, 원문은 이름이 분명한 메서드 `reveal()`로만 꺼낸다(게터 이름이 아니라 Jackson이 자동 직렬화하지 않음). 이 타입을 엔티티 필드에 쓰면 R5의 암호화가 자동 적용된다 — **"민감 = 암호화 + 로그 가림"이 타입 하나로 묶인다.** 추가로 요청 본문 로깅 필터를 등록하지 않고, Hibernate SQL·바인딩 파라미터 로그와 `org.springframework.web` DEBUG를 끈다.
- **Rationale**: 로그 레이아웃 정규식 마스킹은 자유 서술 본문을 알아볼 수 없다. 타입으로 막으면 `log.info("{}", entity)`, Lombok `@ToString`, 예외 메시지 결합 모두에서 원문이 안 나온다(SC-005). 문자열 직접 결합(`reveal()` 후 로그)은 코드 리뷰·정적 분석 대상으로 남긴다(spec Edge Case).
- **Alternatives considered**:
  - Logback `MaskingPatternLayout`(정규식): 전화번호 등 패턴은 잡지만 문의 본문은 못 잡는다.
  - `@ToString.Exclude` 규칙: 사람의 기억에 의존 → 원칙 VI의 "구조로 막는다"에 못 미친다.

## R7. 시크릿 검증과 기동 거부

- **Decision**: `EnvironmentPostProcessor`(`SecretsValidator`)가 빈 생성·프로퍼티 바인딩 **이전에** 필수 시크릿 6개의 존재와 키 길이를 검사한다. 실패하면 누락·부적합한 **이름 목록만** 담은 예외로 기동을 멈춘다.
  - `DAMSO_DB_URL`, `DAMSO_DB_USERNAME`, `DAMSO_DB_PASSWORD`
  - `DAMSO_COLUMN_ENCRYPTION_KEY` (Base64 → 32바이트 이상)
  - `DAMSO_JWT_SECRET` (Base64 → 32바이트 이상)
  - `DAMSO_OPENAI_API_KEY` (비어 있지 않음)
- **Rationale**: `@ConfigurationProperties` + `@Validated`로 하면 Boot의 바인딩 실패 리포트가 **거부된 값(Value)을 그대로 출력**할 수 있다 → FR-016 위반. 바인딩 전에 직접 검사하면 메시지를 완전히 통제한다.
- **Alternatives considered**:
  - `@Validated` 설정 클래스: 위 이유로 값 노출 위험.
  - `${VAR}` 플레이스홀더 미해결 오류에 맡기기: 키 길이를 못 본다.

## R8. 접근 통제

- **Decision**: `SecurityFilterChain`에서 `/actuator/health`만 `permitAll`, 나머지 `anyRequest().authenticated()`. 세션 `STATELESS`, `formLogin`·`httpBasic`·`logout` 비활성. CSRF 보호는 **비활성** — 쿠키를 쓰지 않고 헤더 토큰만 받으므로 브라우저가 자동으로 자격증명을 실어 보낼 경로가 없다. 보안 헤더(HSTS·X-Content-Type-Options·X-Frame-Options)는 기본값 유지.
- **Rationale**: 기본 거부(FR-019), 세션 쿠키 없음(FR-020), 체크리스트 입력 11(CSRF)의 근거가 "쿠키 미사용"으로 명확해진다.
- **Alternatives considered**: CSRF 토큰 유지: 쿠키가 없는 API에서 의미 없이 복잡도만 늘어난다.

## R9. 요청 크기 제한

- **Decision**: 요청 `Content-Length`가 **64KB**를 넘으면 필터에서 즉시 `PAYLOAD_TOO_LARGE`(413)로 거부한다. `Content-Length`가 없는(chunked) 요청은 읽은 바이트를 세어 같은 기준으로 끊는다.
- **Rationale**: 문의 본문 상한 2,000자(UTF-8 최대 약 6KB)에 여유를 둔 값. Tomcat `maxPostSize`는 폼 요청에만 적용돼 JSON을 못 막는다.
- **Alternatives considered**: Nginx `client_max_body_size`만 사용: 로컬·테스트 환경에서 검증 불가 → 앱에도 둔다(Nginx는 배포 시 이중 방어).

## R10. 진단·문서 화면

- **Decision**: Actuator는 `health`만 노출, 상세(`show-details`) `never`. springdoc(Swagger UI)은 이번 범위에 넣지 않는다 — API 원본은 `docs/api/openapi.yaml`(contract-first)이고, springdoc은 구현 완료 후 명세 diff 용도로 도입할 때 기본 비활성으로 추가한다.
- **Rationale**: FR-007, 체크리스트 캡슐화 2. 쓰지 않는 화면은 아예 없는 게 가장 안전하다.

## R11. 설정 파일 형식

- **Decision**: `application.properties` → `application.yml`로 바꾸고, 모든 민감 값은 `${DAMSO_…}` 참조만 둔다. 로컬 전용 설정은 `application-local.yml`(이미 `.gitignore`).
- **Rationale**: 인수인계 문서 "application.yml에는 `${ENV_VAR}` 참조만" 규칙. `.env.example`에 변수 이름만 추가한다(FR-018).

## R12. 테스트 전략과 DB

- **Decision**: 영속성 테스트(암호화 원본 확인·낙관적 락)는 **Testcontainers PostgreSQL**로 실제 DB에서 돌린다. 웹·보안·에러 테스트는 `@WebMvcTest`/MockMvc로 DB 없이 돌린다. 기동 거부 테스트는 `SpringApplication`을 직접 띄워 예외 메시지를 검증한다.
- **Rationale**: H2는 PostgreSQL과 타입·방언이 달라 "저장소 원본 값" 검증의 신뢰도가 떨어진다. 이 PC에 Docker 27.5.1이 있어 실행 가능.
- **Spring Boot 4 주의점** (다른 프로젝트에서 확인된 함정):
  - `testcontainers-bom`을 추가하지 않는다. Boot 4.1이 Testcontainers 2.x를 관리하며, BOM을 명시하면 1.x로 내려가 최신 Docker Desktop과 통신하지 못한다. 버전 없이 선언한다.
  - Testcontainers 2.x 아티팩트 이름은 `testcontainers-postgresql`, `testcontainers-junit-jupiter`.
  - Boot 4의 관리 Jackson은 Jackson 3(`tools.jackson.databind`)이다. `ObjectMapper`는 이 패키지로 주입한다.
- **Alternatives considered**: H2: 빠르지만 위 이유로 영속성 검증에는 부적합.

## R13. 로컬 빌드 환경

- **Decision**: `build.gradle`의 툴체인은 Java 21인데 이 PC의 기본 JDK는 17이다. `settings.gradle`에 `org.gradle.toolchains.foojay-resolver-convention` 플러그인을 추가해 Gradle이 JDK 21을 자동으로 받게 한다.
- **Rationale**: 팀원(Windows·Mac)마다 JDK를 맞추지 않아도 빌드가 된다.
- **Alternatives considered**: 각자 JDK 21 설치 안내: 초보 팀원에게 실패 지점이 하나 더 생긴다.
