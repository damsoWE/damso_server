# Implementation Plan: 인증·회원가입·학급 연결 (F1)

**Branch**: `features/f1-auth-signup` (이슈 #2) | **Date**: 2026-10-18 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `specs/001-auth-signup/spec.md`

## Summary

초대 코드로만 가입하고, 코드가 역할과 연결 대상(학급 담임 / 학생 보호자)을 정한다. 이 연결이 이후 모든 권한 판단의 기준이 된다.

- **코드**: `SecureRandom` 10자리(혼동 문자 제외), 원문 대신 서버 키 HMAC만 저장, 1회용·24시간·대상당 활성 1개(`active_target_key` 유니크 컬럼).
- **가입**: 2단계. 1단계에서 초대 코드를 검증하고 6자리 메일 인증 코드를 보낸다. 2단계에서 계정 생성·코드 소모·연결·이력 기록을 한 트랜잭션으로 처리한다.
- **세션**: 접근 토큰 JWT 15분(foundation 디코더 + 세션 활성 검사) + 갱신 토큰 opaque 1회 회전. 기기마다 세션 1개, 재사용 탐지 시 그 세션만 폐기, 14일 슬라이딩 만료.
- **반복 실패 제한**: Redis TTL 키(Lua로 원자 처리). 코드는 5회 → 10분 잠금, 로그인은 단계별 대기(30초~10분), 메일 재발송 60초·시간당 5회.
- **메일**: SMTP `JavaMailSender`(로컬 Mailpit, 운영 SMTP 계정).
- **스키마**: 마이그레이션 도구 없이 `ddl-auto=update`. 제약은 엔티티 매핑으로 선언 가능한 것만 쓴다.
- **탈퇴**: 이메일·이름 비식별화, 연결 해제, UUID 기반 append-only `link_history`(`@Immutable` + 저장·조회만 있는 리포지토리).

## Technical Context

**Language/Version**: Java 21 (Gradle 툴체인, foundation R13)

**Primary Dependencies**: Spring Boot 4.1.1 — 기존(webmvc, security, oauth2-resource-server, data-jpa, validation, actuator) + **추가: spring-boot-starter-mail(`JavaMailSender`), spring-boot-starter-data-redis(Lettuce)**. 비밀번호는 Spring Security `BCryptPasswordEncoder`, JWT 발급은 Nimbus `NimbusJwtEncoder`(resource-server에 포함).

**Storage**: PostgreSQL 17 — 테이블 9개(data-model.md), 운영 `ddl-auto=update`로 생성(R1). Redis — 실패 횟수·대기·발송 제한 TTL 키(R7).

**Testing**: JUnit 5, Spring Boot Test + MockMvc, Testcontainers PostgreSQL(foundation 지원 클래스 재사용) + Redis 컨테이너, 가짜 메일 포트, 고정 가능한 `Clock` 빈.

**Target Platform**: Linux 컨테이너(Docker Compose + Nginx). 클라이언트는 Flutter 앱(관리자 화면 포함).

**Project Type**: web-service (REST API)

**Performance Goals**: 로그인·가입 API 응답 1초 이내(BCrypt cost 10 ≈ 100ms). SC-001(가입 3분)은 사용자 입력 시간이 대부분.

**Constraints**: 쿠키·세션 없음(헤더 토큰만). 코드·비밀번호·갱신 토큰 원문 저장·로그 0건. 실패 사유가 응답 내용과 시간으로 드러나지 않음(R8).

**Scale/Scope**: 1개교, 학급 수십 개, 사용자 수백 명. 엔드포인트 24개.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| 원칙 | F1 대응 | 결과 |
|---|---|---|
| I. 연락처 미수집 | 가입 필드는 코드·이름·이메일·비밀번호·약관만. 연락처 컬럼 없음. 다른 사용자 응답에 이메일 없음(교사 현황·학부모 홈·관리자 화면 모두) | ✅ |
| II. 가명화 게이트웨이 | F1은 OpenAI를 호출하지 않는다. 가명화 게이트웨이와 OpenAI 정제는 F3 범위다. F1이 저장하는 학생 명단이 게이트웨이 1차 사전의 원천이 되므로 학생 이름은 암호화(`SensitiveText`)해 저장한다(research R13) | ✅ 해당 없음 |
| III. append-only·열람 감사 | 연결·해제·탈퇴 이력을 `link_history`에 append-only(`@Immutable` + 저장·조회만 있는 리포지토리, research R10). 탈퇴 시 원문·이력 삭제 없음 | ✅ |
| IV. 긴급 fail-open | 범위 밖(F2) | ✅ 해당 없음 |
| V. 자동 발송 없음·시스템 안내 고정 문구 | 메일은 고정 템플릿(코드 6자리만 삽입). 교사 명의 발신 없음 | ✅ |
| VI. 민감정보 비노출 | 이름은 `SensitiveText`. 코드·비밀번호·갱신 토큰 로그 금지. 에러는 고정 문구, 실패 사유 미구분 | ✅ |
| VII. UUID·건별 소유권 | 모든 식별자 UUID. 역할은 경로(`/admin`·`/teacher`·`/parent`)로, 소유권은 서비스에서 토큰 주체 + DB 연결로만 검증. 요청 본문의 역할·ID 미신뢰(초대 코드가 연결 대상 결정). 슈퍼 관리자는 민원 내용 경로 없음 | ✅ |
| VIII. 안전한 기본값 | 새 시크릿 `DAMSO_CODE_HASH_KEY`·메일·Redis 설정 기동 시 검증. 슈퍼 관리자는 선택 환경변수로만 생성, 기본값 없음 | ✅ |
| 백엔드: 토큰 | 접근·갱신 분리, 1회 회전, 재사용 시 계열 폐기, HS256 고정(foundation) | ✅ |
| 백엔드: 파라미터 바인딩 | JPA·조건부 UPDATE 모두 바인딩 | ✅ |
| 백엔드: 낙관적 락 | 초대 코드·세션 상태 전이 `@Version`, 갱신 토큰은 조건부 UPDATE | ✅ |
| 백엔드: SecureRandom | 초대 코드·인증 코드·갱신 토큰 생성 | ✅ |
| 아키텍처: 헥사고날 | `account`·`school`·`attempt` 도메인 육각형. 도메인 간 호출은 `port.in`만. `common`은 도메인을 참조하지 않음(세션 검사는 `common` 인터페이스를 `account`가 구현) | ✅ |
| 개발 흐름: contracts 사람 리뷰 | `contracts/auth.openapi.yaml` 리뷰 후 `docs/api/openapi.yaml` 통합 + `api-v0.2` 태그. 리뷰용 HTML `build/contracts/001.html`로 리뷰 완료(2026-10-18). 통합은 구현 전에 한다(tasks T006) | ✅ |
| 큰 영향 결정 확인(CLAUDE.md) | 팀 결정 2026-10-18: Flyway 미도입·`ddl-auto=update`(R1), SMTP `JavaMailSender`(R9), 실패 횟수 Redis(R7), 새 시크릿 `DAMSO_CODE_HASH_KEY`(R3), 슈퍼 관리자 선택 환경변수 생성(R12) | ✅ |

**설계 후 재점검(Phase 1 이후)**: 원칙 위반 없음. spec에 없는 보조 장치 2개(탈퇴 시 비밀번호 재확인 R11, 가입된 이메일로 가입 시 안내 메일 R5)는 원칙을 강화하는 방향이라 contracts 리뷰에서 함께 확인한다. Complexity Tracking 해당 없음.

## Project Structure

### Documentation (this feature)

```text
specs/001-auth-signup/
├── spec.md
├── plan.md              # 이 파일
├── research.md          # R1~R15 결정 근거 (큰 영향 결정 5건 팀 확인 완료)
├── data-model.md        # 테이블 9개, Redis 키, 상태 전이, 에러 코드·시크릿 추가분
├── quickstart.md        # 검증 절차
├── contracts/
│   └── auth.openapi.yaml
├── checklists/
│   └── requirements.md
└── tasks.md             # /speckit-tasks에서 생성
```

### Source Code (repository root)

```text
build.gradle                         # + spring-boot-starter-mail, spring-boot-starter-data-redis
.env.example                         # + DAMSO_CODE_HASH_KEY, DAMSO_MAIL_*, DAMSO_REDIS_*, DAMSO_SUPER_ADMIN_* (이름만)
src/main/resources/
└── application.yml                  # ddl-auto none → update, mail, redis, 토큰 수명

src/main/java/damso/
├── common/
│   ├── config/
│   │   ├── SecretsValidator.java          # (수정) 새 시크릿 검증
│   │   └── ClockConfig.java               # 테스트에서 시간 고정
│   ├── crypto/
│   │   └── CodeHasher.java                # HMAC-SHA256 (R3)
│   ├── error/ErrorCode.java               # (수정) F1 코드 6개 추가, 429 Retry-After 처리
│   └── security/
│       ├── SecurityConfig.java            # (수정) 공개 경로 추가, 역할별 경로 규칙
│       ├── JwtDecoderConfig.java          # (수정) 세션 활성 검사 validator 연결
│       ├── SessionStatusChecker.java      # 인터페이스 — account가 구현 (common → 도메인 참조 없음)
│       └── CurrentUser.java               # 토큰 주체(사용자 UUID·역할·sid) 꺼내기
│
├── account/                               # 계정·가입·로그인·세션·비밀번호·탈퇴
│   ├── domain/        UserAccount, Role, AccountStatus, PendingSignup, PasswordReset, AuthSession, RefreshToken, PasswordPolicy
│   ├── application/
│   │   ├── port/in/   SignupUseCase, LoginUseCase, RefreshUseCase, LogoutUseCase, PasswordResetUseCase, MyAccountUseCase, WithdrawUseCase
│   │   ├── port/out/  UserAccountPort, PendingSignupPort, PasswordResetPort, SessionPort, AccessTokenIssuerPort, VerificationMailPort
│   │   └── service/   (유스케이스 구현) + SuperAdminBootstrap (R12)
│   └── adapter/
│       ├── in/controller/     AuthController, MeController
│       │   └── dto/           request/…RequestDTO(10개), response/…ResponseDTO(3개) — 별도 파일
│       ├── out/persistence/   JPA 리포지토리, SessionStatusChecker 구현
│       ├── out/token/         JwtAccessTokenIssuer (NimbusJwtEncoder)
│       └── out/mail/          SmtpVerificationMailSender (커밋 후 비동기 발송)
│
├── school/                                # 학급·학생·초대 코드·연결·교사 프로필·연결 이력
│   ├── domain/        Classroom, Student, InviteCode, InviteKind, InviteStatus, TeacherProfile, LinkHistory
│   ├── application/
│   │   ├── port/in/   ClassroomAdminUseCase, InviteIssueUseCase, InviteRedeemUseCase(account 가입에서 호출),
│   │   │              MembershipReleaseUseCase(account 탈퇴에서 호출), TeacherProfileUseCase, TeacherClassroomQuery, ParentChildrenUseCase
│   │   ├── port/out/  ClassroomPort, StudentPort, InviteCodePort, TeacherProfilePort, LinkHistoryPort
│   │   └── service/
│   └── adapter/
│       ├── in/controller/     AdminController, TeacherController, ParentController
│       │   └── dto/           request/…RequestDTO(4개), response/…ResponseDTO(8개) — 별도 파일
│       └── out/persistence/
│
└── attempt/                               # 반복 실패 제한 (account·school 공용)
    ├── domain/        AttemptKind, AttemptPolicy(5회·10분 / 로그인 단계별 대기)
    ├── application/   port/in/AttemptLimitUseCase, port/out/AttemptLimitPort, service/
    └── adapter/out/redis/   RedisAttemptLimitAdapter (Lua 스크립트로 원자 증가·판정)

src/test/java/damso/
├── account/          가입·로그인·갱신·재사용·재설정·탈퇴 (SC-001·003·004·006·007)
├── school/           발행·재발행·현황·자녀 추가·교사/학부모 조회 권한 (SC-002·005·008)
├── attempt/          잠금·단계별 대기 경계값, Redis 장애 시 거부 (SC-004)
└── support/          (기존) + Redis 컨테이너, FakeVerificationMailPort, MutableClock
```

**Structure Decision**: 단일 Spring Boot 프로젝트, constitution의 **도메인별 헥사고날 + 실용형 JPA**.

- 도메인을 셋으로 나눈다. `account`(누가 로그인하는가), `school`(누가 어디에 연결되는가), `attempt`(반복 실패 제한).
  - 가입 확정은 `account` 서비스가 `school`의 `InviteRedeemUseCase`를 호출해 한 트랜잭션에서 처리한다.
  - 탈퇴는 `account`가 `school`의 `MembershipReleaseUseCase`를 호출한다.
  - 다른 도메인의 리포지토리는 직접 부르지 않는다.
- `attempt`를 따로 둔 이유: 로그인·가입(`account`)과 자녀 추가(`school`)가 같은 규칙을 쓴다. 어느 한쪽에 두면 반대쪽이 그 도메인 내부에 의존하게 된다.
- `common`은 여전히 도메인을 모른다. 접근 토큰의 세션 활성 검사는 `common.security.SessionStatusChecker` 인터페이스로 두고 `account` 어댑터가 구현한다.
- 메일·JWT 발급은 외부 시스템 성격이라 `account.application.port.out` 뒤의 어댑터(`out.mail`, `out.token`)로 둔다(constitution 아키텍처).

## Complexity Tracking

해당 없음 — Constitution Check 위반 없음.
