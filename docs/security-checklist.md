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
| 15 | 보안기능 결정에 사용되는 부적절한 입력값 | 역할·소유자 판단은 토큰 주체와 DB 기준, 요청 본문의 역할·ID 값 신뢰 금지 | 조치 | 역할·연결 대상은 초대 코드로만 정하고(`account/application/service/SignupService.java`), 사용자는 토큰에서만 꺼낸다(`common/security/CurrentUser.java`) / `SignupTeacherApiTest.roleAndClassroomCannotBeChosenByTheRequest`, `ParentChildrenApiTest.listShowsOnlyOwnChildrenWithHomeroomProfileAndNoEmail`, `TeacherApiTest.classroomShowsOnlyOwnClassWithGuardianLinkedFlagsAndNoParentIdentity` |
| 16 | 메모리 버퍼 오버플로우 | Java 런타임 경계 검사, 네이티브 코드 미사용 | 해당없음 | 후보 — 구현 후 확인 |
| 17 | 포맷 스트링 삽입 | 사용자 입력을 포맷 문자열로 사용 금지 | 미착수 | |

## 2. 보안기능 (16)

| # | 보안약점 | 담소 대응 | 상태 | 근거 |
|---|---|---|---|---|
| 1 | 적절한 인증 없는 중요기능 허용 | Spring Security 기본 거부, 공개 엔드포인트 명시 허용 | 조치 | 기본 거부(`anyRequest().authenticated()`), 공개 경로는 `/actuator/health`와 로그인 전 단계 8개를 `SecurityConfig.PUBLIC_PATHS`에 하나씩 명시(와일드카드 없음, 로그아웃은 인증 필요) — `common/security/SecurityConfig.java` / `AccessControlTest`, `SignupTeacherApiTest.signupEndpointsArePublicButOtherAuthEndpointsAreNot` |
| 2 | 부적절한 인가 | UUID + 서비스 계층 건별 소유권 검증, 교사는 담당 학급만, 관리자는 내용 열람 권한 없음 | 조치 | F1 범위(학급·학생·프로필·연결 현황): 역할별 경로(`/admin`·`/teacher`·`/parent`)는 `SecurityConfig`가, 건별 범위는 토큰 주체 기준 서비스가(`school/application/service/ParentChildrenService.java`, `TeacherService.java`) 검증한다. 관리자 API에 민원 내용 경로는 없다. F2 범위(문의): 학부모는 본인이 쓴 문의만, 교사는 현재 담임인 학급의 문의만 보고(담임이 해제·변경되면 이전 교사는 못 본다), 남의 문의는 없는 문의와 똑같은 404이며, 슈퍼 관리자는 문의 경로가 모두 403이고 관리자 API 응답 스키마에 문의 내용·제목 필드가 없다 — `inquiry/application/service/ParentInquiryService.java`, `TeacherInquiryService.java`, `ViewInquiryContentService.java`, `school/application/service/InquiryTargetService.java` / `ParentInquiryQueryApiTest.anotherParentsInquiryIsNotFound`, `TeacherInquiryApiTest.teacherOfAnotherClassroomSeesNothingOfIt`, `TeacherInquiryApiTest.formerHomeroomTeacherLosesAccessAfterRelease`, `InquiryExposureTest.otherTeachersAndParentsCannotTellWhetherAnInquiryExists`, `SuperAdminInquiryAccessTest`, `SubmitInquiryApiTest.onlyParentsCanSubmit` / `AdminClassroomApiTest.otherRolesCannotUseAdminApis`, `TeacherApiTest.otherRolesCannotUseTeacherApis`, `ParentChildrenApiTest.otherRolesCannotUseParentApis`, `CrossAccountExposureTest.eachTeacherAndParentSeesOnlyTheirOwnClassroomAndChildren` |
| 3 | 중요한 자원에 대한 잘못된 권한 설정 | 컨테이너 비루트 실행, 시크릿 파일 미사용(환경변수 주입) | 미착수 | |
| 4 | 취약한 암호화 알고리즘 사용 | AES-256-GCM, 서명 알고리즘 고정·alg=none 차단 | 조치 | AES-256-GCM, JWT HS256 고정·alg=none 거부 — `common/crypto/AesGcmCipher.java`, `common/security/JwtDecoderConfig.java` / `AesGcmCipherTest`, `AccessControlTest.unsignedAlgNoneTokenIsRejected` |
| 5 | 암호화되지 않은 중요정보 | 문의·답변 본문 컬럼 암호화(AttributeConverter), HTTPS·HSTS | 조치 | `SensitiveText` 타입 필드 자동 컬럼 암호화 — `common/crypto/SensitiveTextConverter.java` / `SensitiveTextPersistenceTest`. F2: 문의 본문·제목(`Inquiry.content`·`title`)도 `SensitiveText`라 같은 방식으로 암호화되며 DB 값이 평문과 다르다 — `inquiry/domain/Inquiry.java` / `SubmitInquiryApiTest.submitsAGeneralInquiryAndStoresTheContentEncrypted` (HTTPS·HSTS는 배포 시 Nginx) |
| 6 | 하드코드된 중요정보 | 시크릿은 환경변수만, 미설정 시 기동 거부, Gitleaks(pre-commit + CI) | 조치 | 시크릿은 `${DAMSO_*}` 환경변수만, 미설정 시 기동 거부 — `application.yml`, `common/config/SecretsValidator.java` / `SecretsValidatorTest`, `StartupRefusalTest`, Gitleaks(pre-commit + CI) |
| 7 | 충분하지 않은 키 길이 사용 | AES 256bit, JWT 서명 키 길이 기동 시 검증 | 조치 | 컬럼 키·JWT 키 256비트 미만이면 기동 거부 — `SecretsValidator.java`, `AesGcmCipher.java` / `SecretsValidatorTest.refusesShortKeyWithoutPrintingIt`, `AesGcmCipherTest.rejectsKeysShorterThan256Bits` |
| 8 | 적절하지 않은 난수 값 사용 | 토큰·키·IV 생성에 SecureRandom 사용 | 조치 | IV는 `SecureRandom`, 식별자는 `UUID.randomUUID()`(SecureRandom 기반), 초대 코드·인증 코드·갱신 토큰은 `SecureRandom` — `AesGcmCipher.java`, `common/entity/BaseEntity.java`, `common/crypto/SecureCodes.java` / `AesGcmCipherTest.samePlaintextProducesDifferentCiphertext`, `SecureCodesTest` |
| 9 | 취약한 비밀번호 허용 | 비밀번호 정책(길이·조합) 서버 검증 | 조치 | 8~64자, 영문 1자 이상 + 숫자 1자 이상을 서버에서 검증(가입·재설정·슈퍼 관리자 생성), 사유 응답에 입력값 없음 — `account/domain/PasswordPolicy.java` / `PasswordPolicyTest`, `SignupTeacherApiTest.weakPasswordIsRejectedWithoutEchoingIt`, `PasswordResetApiTest.weakNewPasswordIsRejectedAndTheCodeStaysUsable`, `SuperAdminBootstrapTest.refusesToStartWithPasswordThatBreaksThePolicyWithoutPrintingIt` |
| 10 | 부적절한 전자서명 확인 | JWT 서명 검증 필수, 허용 알고리즘 고정 | 조치 | JWT 서명 검증 필수, 위조·만료·exp 없음·서명 없음 거부 — `JwtDecoderConfig.java` / `AccessControlTest` |
| 11 | 부적절한 인증서 유효성 검증 | 외부 HTTPS 호출 시 기본 인증서 검증 유지(검증 우회 코드 금지) | 미착수 | |
| 12 | 사용자 하드디스크에 저장되는 쿠키를 통한 정보 노출 | 쿠키 미사용 | 해당없음 | 쿠키를 발급·사용하지 않음 — `SecurityConfig.java`(STATELESS) / `AccessControlTest`(Set-Cookie 0건) |
| 13 | 주석문 안에 포함된 시스템 주요정보 | 주석에 키·계정·내부 주소 기재 금지, 코드 리뷰 확인 | 미착수 | |
| 14 | 무결성 검사 없는 코드 다운로드 | 런타임 코드 다운로드 없음, 빌드 의존성은 Gradle 검증 | 미착수 | |
| 15 | 반복된 인증시도 제한 기능 부재 | 로그인 실패 횟수 제한(Redis), Nginx Rate Limit | 조치 | Redis TTL 키에 실패 횟수·대기를 저장(로그인은 3회부터 30초~10분 단계 대기, 초대·인증·재설정 코드는 5회 실패 시 10분 잠금, 메일 재발송은 60초·시간당 5회). 대상은 계정 UUID 또는 이메일 해시이고 Lua 스크립트로 원자 처리, Redis 장애 시 요청 거부(fail-closed) — `attempt/adapter/out/redis/RedisAttemptLimitAdapter.java`, `attempt/domain/AttemptPolicy.java` / `AttemptLimitTest`, `LoginApiTest`, `SignupInviteCodeFailureTest`, `PasswordResetApiTest.sixthWrongCodeIsLockedAndANewRequestAfterTheLockWorks`, `ParentChildrenApiTest.sixthFailureForTheSameAccountIsLockedAndOtherAccountsAreNot` (Nginx Rate Limit은 배포 시) |
| 16 | 솔트 없이 일방향 해쉬함수 사용 | 비밀번호는 BCrypt/Argon2(솔트 내장) | 조치 | 비밀번호는 BCrypt(cost 10, 솔트 내장)만 저장, 코드 공간이 작은 초대·인증 코드는 서버 키가 섞인 HMAC-SHA256, 무작위 256비트 갱신 토큰은 SHA-256 — `account/adapter/out/persistence/PasswordEncoderConfig.java`, `common/crypto/CodeHasher.java`, `common/crypto/Digests.java` / `CodeHasherTest`, `SignupTeacherApiTest.teacherSignsUpAndBecomesTheHomeroomTeacher`, `SessionApiTest.refreshTokensAreStoredOnlyAsHashes`, `InviteIssueApiTest.teacherInviteShowsTheCodeOnlyInTheIssueResponse`, `PasswordResetApiTest.resetCodeIsStoredOnlyAsAHash` |

## 3. 시간 및 상태 (2)

| # | 보안약점 | 담소 대응 | 상태 | 근거 |
|---|---|---|---|---|
| 1 | 경쟁조건: 검사 시점과 사용 시점(TOCTOU) | 낙관적 락(@Version)으로 상태 전이 검증, RT 회전 원자 처리 | 조치 | `BaseEntity` `@Version` 낙관적 락, 충돌 시 409 — `common/entity/BaseEntity.java`, `GlobalExceptionHandler.java` / `BaseEntityTest.secondUpdateFromSameVersionIsRejected`, `conflictIsReportedAs409`. F1: 갱신 토큰은 조건부 UPDATE(`used_at IS NULL`)로 원자 회전하고, 같은 초대 코드의 동시 사용은 낙관적 락·유니크 제약으로 한 건만 성공한다 — `account/adapter/out/persistence/SpringDataRefreshTokenRepository.java`, `school/application/service/InviteRedeemService.java` / `SessionApiTest.reusingARotatedTokenRevokesOnlyThatDevice`, `SignupConcurrencyTest`. F2: 문의 단계 전이는 허용표(`InquiryStage.canMoveTo`)와 `@Version`으로 한 번만 성공하고(동시에 처음 열람해도 단계 이력은 한 줄), 같은 `Idempotency-Key`의 동시 제출은 유니크 제약 `(parent_id, idempotency_key)`로 한 건만 남으며, 알림 요청은 `FOR UPDATE SKIP LOCKED` + 임대(`SENDING`)로 두 발송기가 같은 요청을 동시에 보내지 못한다 — `inquiry/domain/InquiryStage.java`, `inquiry/application/service/InquiryConfirmationStep.java`, `notification/adapter/out/persistence/SpringDataNotificationRequestRepository.java` / `InquiryStageTest`, `InquiryStageConcurrencyTest`, `ViewInquiryContentAtomicityTest.concurrentFirstViewsConfirmOnceAndAuditBoth`, `SubmitInquiryRateLimitTest.concurrentSubmissionsWithTheSameKeyCreateOneInquiry`, `UrgentNotificationTest.concurrentDispatchersSendAnUnsentRequestOnlyOnce`, `UrgentNotificationTest.requestStuckInSendingIsTakenOverAfterTheLeaseExpires` |
| 2 | 종료되지 않는 반복문 또는 재귀 함수 | 재시도 횟수 상한(정제 1회 재시도), 외부 호출 타임아웃 명시 | 미착수 | |

## 4. 에러처리 (3)

| # | 보안약점 | 담소 대응 | 상태 | 근거 |
|---|---|---|---|---|
| 1 | 오류 메시지 정보노출 | 전역 예외 처리, 스택트레이스·내부 경로 미노출, 에러 코드만 응답 | 조치 | MVC·보안 필터·/error 3계층 공통 `ErrorResponse`, 내부 정보 미노출 — `GlobalExceptionHandler.java`, `JsonErrorController.java`, `JsonAuthenticationEntryPoint.java` / `ErrorResponseTest`, `ErrorResponseAssertions` |
| 2 | 오류상황 대응 부재 | LLM 실패 시 명시적 상태 전이(fail-closed), 긴급 경로는 fail-open | 조치 | F2 범위: 긴급 판정은 규칙·순수 함수라 AI·Redis·FCM 장애와 무관하고, 푸시 실패·기기 없음에도 문의는 접수되며 알림 요청은 DB(아웃박스)에 남아 1분·5분·15분 뒤, 그 뒤로는 1시간 간격으로 계속 재시도되고, 확인될 때까지 1시간마다 재알림한다. 1분 제한 저장소(Redis)를 쓸 수 없어도 일반 문의는 제한만 건너뛰고 접수한다 — `inquiry/domain/UrgentRule.java`, `inquiry/application/service/SubmitInquiryService.java`, `notification/application/service/NotificationDispatcher.java` / `UrgentInquiryApiTest.urgentIsAcceptedWhenThePushSenderThrows`, `UrgentInquiryApiTest.urgentSkipsTheLimitStoreAndGeneralStillWorksWhenItIsDown`, `SubmitInquiryRateLimitTest.generalInquiryIsAcceptedWhenTheLimitStoreIsDown`, `UrgentNotificationTest.retriesAfterOneFiveFifteenMinutesThenEveryHourForever`, `UrgentNotificationTest.remindsEveryHourWhileUnconfirmedAndStopsWhenConfirmed`. LLM 실패 시 fail-closed(원문 확인 필요 상태)는 F3에서 추가하고 재검증한다 |
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
| 2 | 제거되지 않고 남은 디버그 코드 | 운영 프로필에서 Swagger UI 비활성/인증, 디버그 엔드포인트 금지, 민원 본문 로그 출력 금지 | 조치 | 진단 엔드포인트는 health만 노출(상세 never), 테스트용 컨트롤러는 `src/test`에만, 본문 로깅 필터 없음 — `application.yml` / `AccessControlTest.diagnosticEndpointsAreNotExposedEvenWhenAuthenticated`, `SensitiveLoggingTest`. F2: 문의 제출(일반·긴급)·열람·알림 발송·기기 등록과 실패 경로(1분 제한 거부, 푸시 실패·예외, 감사 실패)를 지나는 동안 문의 본문·FCM 토큰·학생 이름이 로그에 0건이고, 본문을 가진 DTO·값 객체의 `toString`은 본문을 가린다 — `inquiry/adapter/in/controller/dto/**`, `inquiry/application/port/in/*` 값 객체, `notification/adapter/out/fcm/FcmPayload.java` / `InquirySensitiveLoggingTest`, `FcmMessageMappingTest.payloadAndSenderToStringHideTextIdsAndCredentials` |
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

## 배포 전 확인 (런타임·플랫폼 취약점)

코드 변경으로 해결되지 않고 배포 환경(JDK·베이스 이미지)을 확인해야 하는 항목이다. 배포 준비 때마다 아래를 확인하고, 새 공지가 나오면 행을 추가한다. 연관 항목: 보안기능 11(부적절한 인증서 유효성 검증), API 오용 2(취약한 API 사용).

| # | 취약점 | 영향 | 확인 방법 | 상태 | 근거 |
|---|---|---|---|---|---|
| R1 | CVE-2026-91403 (Java JSSE, CVSS 9.1) — 탈취된 eMudhra 중간 CA 인증서 8개를 JDK가 신뢰. JDK 8~27 전부 해당. 팀 공유(김휘래), 공지: https://openjdk.org/groups/vulnerability/advisories/2026-10-21 | 서버가 외부로 나가는 TLS 호출(FCM·LLM API·메일 등)이 중간자 공격에 노출될 수 있음 | 배포 대상 각 서버·컨테이너에서 `java -version` 확인, Temurin 17.0.21.0.2 이상이면 재빌드·재배포. 담소는 `Dockerfile`이 `eclipse-temurin:21-jdk`/`21-jre`(고정되지 않은 태그)를 쓰므로 21 라인의 수정 버전을 공지에서 확인하고, 이미지를 캐시 없이 다시 받아 빌드(`docker build --pull --no-cache`)한 뒤 컨테이너 안에서 버전을 확인한다 | 미착수 | 배포 준비 시 확인 — 확인한 버전·날짜를 이 칸에 기록 |
