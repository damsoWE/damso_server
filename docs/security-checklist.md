# 보안약점 체크리스트

행정안전부 「소프트웨어 개발보안 가이드」(2021.11 개정)의 구현단계 보안약점 제거 기준 **49개 항목**을 담소 서버 기준으로 관리한다.

## 상태 값

| 상태 | 의미 |
|---|---|
| `미착수` | 대응 설계만 있고 구현 전 |
| `조치` | 구현 완료 + 근거(코드·테스트·CI 결과) 기재 |
| `미조치` | 구현했으나 대응 미완료 — 사유와 계획 기재 |
| `해당없음` | 해당 기능·기술을 쓰지 않음 — 사유 기재 |

항목을 `조치`로 바꿀 때는 **근거** 칸에 파일 경로·테스트 이름·커밋을 남긴다. 발견 경로 → 조치 커밋 → 재검증 결과를 이력으로 남긴다.

---

## 1. 입력데이터 검증 및 표현 (17)

| # | 보안약점 | 담소 대응 | 상태 | 근거 |
|---|---|---|---|---|
| 1 | SQL 삽입 | JPA·QueryDSL 파라미터 바인딩만 사용, 문자열 연결 쿼리 금지, LLM 출력을 쿼리 경로에 사용 금지 | 미착수 | |
| 2 | 코드 삽입 | 동적 코드 평가(ScriptEngine·SpEL 사용자 입력) 사용 금지 | 미착수 | |
| 3 | 경로 조작 및 자원 삽입 | 사용자 입력으로 파일 경로·자원 식별자를 구성하지 않음 | 미착수 | |
| 4 | 크로스사이트 스크립트 | JSON API만 제공, 앱은 네이티브 렌더링(WebView 미사용), 응답 Content-Type 고정 | 미착수 | |
| 5 | 운영체제 명령어 삽입 | OS 명령 실행 기능 없음 | 해당없음 | 후보 — 구현 후 확인 |
| 6 | 위험한 형식 파일 업로드 | MVP에서 파일 업로드 제외 | 해당없음 | 후보 — 기획서 MVP 제외 범위 |
| 7 | 신뢰되지 않는 URL 주소로 자동접속 연결 | 리다이렉트 응답 미제공 | 미착수 | |
| 8 | 부적절한 XML 외부 개체 참조 | XML 입력 미처리(JSON만) | 해당없음 | 후보 — 구현 후 확인 |
| 9 | XML 삽입 | XML 입력 미처리(JSON만) | 해당없음 | 후보 — 구현 후 확인 |
| 10 | LDAP 삽입 | LDAP 미사용 | 해당없음 | 후보 — 구현 후 확인 |
| 11 | 크로스사이트 요청 위조 | 쿠키 세션 미사용, Authorization 헤더로 토큰 전달 | 해당없음 | 쿠키·세션 미사용, 헤더 Bearer 토큰만 인정 — `common/security/SecurityConfig.java`(STATELESS, CSRF 비활성 근거 주석), `AccessControlTest`(Set-Cookie 0건) |
| 12 | 서버사이드 요청 위조 | 외부 호출 대상 화이트리스트 고정(LLM API만), 사용자 입력 URL 호출 금지 | 미착수 | |
| 13 | HTTP 응답분할 | 사용자 입력을 응답 헤더에 넣지 않음 | 미착수 | |
| 14 | 정수형 오버플로우 | 페이지 크기·길이 등 수치 입력 범위 검증(@Valid) | 조치 | 선언적 범위 검증 + 고정 사유 응답 — `common/error/GlobalExceptionHandler.java`, `ValidationReasons.java` / `InputValidationTest.outOfRangeNumberReportsRange` |
| 15 | 보안기능 결정에 사용되는 부적절한 입력값 | 역할·소유자 판단은 토큰 주체와 DB 기준, 요청 본문의 역할·ID 값 신뢰 금지 | 미착수 | |
| 16 | 메모리 버퍼 오버플로우 | Java 런타임 경계 검사, 네이티브 코드 미사용 | 해당없음 | 후보 — 구현 후 확인 |
| 17 | 포맷 스트링 삽입 | 사용자 입력을 포맷 문자열로 사용 금지 | 미착수 | |

## 2. 보안기능 (16)

| # | 보안약점 | 담소 대응 | 상태 | 근거 |
|---|---|---|---|---|
| 1 | 적절한 인증 없는 중요기능 허용 | Spring Security 기본 거부, 공개 엔드포인트 명시 허용 | 조치 | 기본 거부(`anyRequest().authenticated()`), 공개 경로 `/actuator/health`만 — `common/security/SecurityConfig.java` / `AccessControlTest` |
| 2 | 부적절한 인가 | UUID + 서비스 계층 건별 소유권 검증, 교사는 담당 학급만, 관리자는 내용 열람 권한 없음 | 미착수 | |
| 3 | 중요한 자원에 대한 잘못된 권한 설정 | 컨테이너 비루트 실행, 시크릿 파일 미사용(환경변수 주입) | 미착수 | |
| 4 | 취약한 암호화 알고리즘 사용 | AES-256-GCM, 서명 알고리즘 고정·alg=none 차단 | 조치 | AES-256-GCM, JWT HS256 고정·alg=none 거부 — `common/crypto/AesGcmCipher.java`, `common/security/JwtDecoderConfig.java` / `AesGcmCipherTest`, `AccessControlTest.unsignedAlgNoneTokenIsRejected` |
| 5 | 암호화되지 않은 중요정보 | 문의·답변 본문 컬럼 암호화(AttributeConverter), HTTPS·HSTS | 조치 | `SensitiveText` 타입 필드 자동 컬럼 암호화 — `common/crypto/SensitiveTextConverter.java` / `SensitiveTextPersistenceTest` (HTTPS·HSTS는 배포 시 Nginx) |
| 6 | 하드코드된 중요정보 | 시크릿은 환경변수만, 미설정 시 기동 거부, Gitleaks(pre-commit + CI) | 조치 | 시크릿은 `${DAMSO_*}` 환경변수만, 미설정 시 기동 거부 — `application.yml`, `common/config/SecretsValidator.java` / `SecretsValidatorTest`, `StartupRefusalTest`, Gitleaks(pre-commit + CI) |
| 7 | 충분하지 않은 키 길이 사용 | AES 256bit, JWT 서명 키 길이 기동 시 검증 | 조치 | 컬럼 키·JWT 키 256비트 미만이면 기동 거부 — `SecretsValidator.java`, `AesGcmCipher.java` / `SecretsValidatorTest.refusesShortKeyWithoutPrintingIt`, `AesGcmCipherTest.rejectsKeysShorterThan256Bits` |
| 8 | 적절하지 않은 난수 값 사용 | 토큰·키·IV 생성에 SecureRandom 사용 | 조치 | IV는 `SecureRandom`, 식별자는 `UUID.randomUUID()`(SecureRandom 기반) — `AesGcmCipher.java`, `common/entity/BaseEntity.java` / `AesGcmCipherTest.samePlaintextProducesDifferentCiphertext` |
| 9 | 취약한 비밀번호 허용 | 비밀번호 정책(길이·조합) 서버 검증 | 미착수 | |
| 10 | 부적절한 전자서명 확인 | JWT 서명 검증 필수, 허용 알고리즘 고정 | 조치 | JWT 서명 검증 필수, 위조·만료·exp 없음·서명 없음 거부 — `JwtDecoderConfig.java` / `AccessControlTest` |
| 11 | 부적절한 인증서 유효성 검증 | 외부 HTTPS 호출 시 기본 인증서 검증 유지(검증 우회 코드 금지) | 미착수 | |
| 12 | 사용자 하드디스크에 저장되는 쿠키를 통한 정보 노출 | 쿠키 미사용 | 해당없음 | 쿠키를 발급·사용하지 않음 — `SecurityConfig.java`(STATELESS) / `AccessControlTest`(Set-Cookie 0건) |
| 13 | 주석문 안에 포함된 시스템 주요정보 | 주석에 키·계정·내부 주소 기재 금지, 코드 리뷰 확인 | 미착수 | |
| 14 | 무결성 검사 없는 코드 다운로드 | 런타임 코드 다운로드 없음, 빌드 의존성은 Gradle 검증 | 미착수 | |
| 15 | 반복된 인증시도 제한 기능 부재 | 로그인 실패 횟수 제한(Redis), Nginx Rate Limit | 미착수 | |
| 16 | 솔트 없이 일방향 해쉬함수 사용 | 비밀번호는 BCrypt/Argon2(솔트 내장) | 미착수 | |

## 3. 시간 및 상태 (2)

| # | 보안약점 | 담소 대응 | 상태 | 근거 |
|---|---|---|---|---|
| 1 | 경쟁조건: 검사 시점과 사용 시점(TOCTOU) | 낙관적 락(@Version)으로 상태 전이 검증, RT 회전 원자 처리 | 조치 | `BaseEntity` `@Version` 낙관적 락, 충돌 시 409 — `common/entity/BaseEntity.java`, `GlobalExceptionHandler.java` / `BaseEntityTest.secondUpdateFromSameVersionIsRejected`, `conflictIsReportedAs409` |
| 2 | 종료되지 않는 반복문 또는 재귀 함수 | 재시도 횟수 상한(정제 1회 재시도), 외부 호출 타임아웃 명시 | 미착수 | |

## 4. 에러처리 (3)

| # | 보안약점 | 담소 대응 | 상태 | 근거 |
|---|---|---|---|---|
| 1 | 오류 메시지 정보노출 | 전역 예외 처리, 스택트레이스·내부 경로 미노출, 에러 코드만 응답 | 조치 | MVC·보안 필터·/error 3계층 공통 `ErrorResponse`, 내부 정보 미노출 — `GlobalExceptionHandler.java`, `JsonErrorController.java`, `JsonAuthenticationEntryPoint.java` / `ErrorResponseTest`, `ErrorResponseAssertions` |
| 2 | 오류상황 대응 부재 | LLM 실패 시 명시적 상태 전이(fail-closed), 긴급 경로는 fail-open | 미착수 | |
| 3 | 부적절한 예외 처리 | 광범위 catch 후 무시 금지, 예외별 처리 | 조치 | 예외 유형별 처리, 미처리 예외는 스택트레이스를 서버 로그에만(본문·쿼리스트링 제외) — `GlobalExceptionHandler.java` / `ErrorResponseTest`, `SensitiveLoggingTest` |

## 5. 코드오류 (5)

| # | 보안약점 | 담소 대응 | 상태 | 근거 |
|---|---|---|---|---|
| 1 | Null Pointer 역참조 | Optional 사용, 정적분석(SpotBugs) | 미착수 | |
| 2 | 부적절한 자원 해제 | try-with-resources, 커넥션 풀 관리 위임 | 미착수 | |
| 3 | 해제된 자원 사용 | Java GC 환경, 닫힌 스트림 재사용 금지 | 미착수 | |
| 4 | 초기화되지 않은 변수 사용 | 생성자 주입·final 필드, 컴파일러 검사 | 미착수 | |
| 5 | 신뢰할 수 없는 데이터의 역직렬화 | Java 네이티브 직렬화 미사용, JSON 역직렬화는 DTO 타입 고정 | 미착수 | |

## 6. 캡슐화 (4)

| # | 보안약점 | 담소 대응 | 상태 | 근거 |
|---|---|---|---|---|
| 1 | 잘못된 세션에 의한 데이터 정보 노출 | 무상태 인증, 싱글톤 빈에 요청별 상태 저장 금지 | 미착수 | |
| 2 | 제거되지 않고 남은 디버그 코드 | 운영 프로필에서 Swagger UI 비활성/인증, 디버그 엔드포인트 금지, 민원 본문 로그 출력 금지 | 조치 | 진단 엔드포인트는 health만 노출(상세 never), 테스트용 컨트롤러는 `src/test`에만, 본문 로깅 필터 없음 — `application.yml` / `AccessControlTest.diagnosticEndpointsAreNotExposedEvenWhenAuthenticated`, `SensitiveLoggingTest` |
| 3 | Private 배열에 Public 데이터 할당 | 엔티티 직접 노출 금지, DTO 변환 강제, 방어적 복사 | 미착수 | |
| 4 | Public 메소드부터 반환된 Private 배열 | 불변 컬렉션 반환 | 미착수 | |

## 7. API 오용 (2)

| # | 보안약점 | 담소 대응 | 상태 | 근거 |
|---|---|---|---|---|
| 1 | DNS lookup에 의존한 보안결정 | 호스트명 기반 인가 판단 금지 | 미착수 | |
| 2 | 취약한 API 사용 | 의존성 취약점 스캔(OWASP Dependency-Check·Trivy), 폐기 API 사용 금지 | 미착수 | |

## 가이드 외 위협 (LLM 연동)

| # | 위협 | 담소 대응 | 상태 | 근거 |
|---|---|---|---|---|
| L1 | 프롬프트 인젝션 | 시스템 지시·사용자 입력 역할 분리, Structured Outputs(strict) + 서버 재검증 | 미착수 | |
| L2 | LLM 출력 신뢰 | 출력 스키마 위반 시 폐기(fail-closed), 출력을 쿼리·경로에 사용 금지 | 미착수 | |
| L3 | 외부 AI로 식별정보 유출 | 가명화 게이트웨이 경유만 허용, 복원표 메모리 전용·미저장 | 미착수 | |
