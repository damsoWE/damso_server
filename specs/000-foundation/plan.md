# Implementation Plan: 공통 보안·구조 기반 (Foundation)

**Branch**: `feat/000-foundation` (구현 시작 시 생성, PR로 main에 merge) | **Date**: 2026-10-15 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `specs/000-foundation/spec.md`

## Summary

이후 모든 기능(F1~F5)이 올라갈 보안·구조 기반을 만든다. 핵심은 **보안 원칙을 개발자의 기억이 아니라 구조로 강제**하는 것이다.

- 오류는 세 계층(MVC·보안 필터·서블릿)을 모두 하나의 `ErrorResponse`로 덮는다.
- 엔티티는 `BaseEntity`를 상속하면 UUID v4·생성/수정 시각·낙관적 락을 자동으로 얻는다.
- 민감 텍스트는 타입 `SensitiveText` 하나로 **AES-256-GCM 자동 암호화 + 로그 가림**이 동시에 적용된다.
- 필수 시크릿은 바인딩 이전에 검사해 하나라도 빠지면 이름만 알리고 기동을 거부한다.
- 접근은 기본 거부, 헤더 Bearer 토큰(HS256 고정)만 인정, 세션·쿠키 없음.

## Technical Context

**Language/Version**: Java 21 (Gradle 툴체인, foojay 자동 다운로드)

**Primary Dependencies**: Spring Boot 4.1.1 — webmvc, security, oauth2-resource-server(Nimbus JWT), data-jpa, validation, actuator / Lombok

**Storage**: PostgreSQL (이번 범위는 테이블 없음, 테스트 엔티티만)

**Testing**: JUnit 5, Spring Boot Test, MockMvc, Spring Security Test, Testcontainers 2.x(PostgreSQL)

**Target Platform**: Linux 컨테이너(Docker Compose + Nginx, 기획서 아키텍처). 개발은 Windows·macOS

**Project Type**: web-service (REST API, 클라이언트는 Flutter 앱)

**Performance Goals**: 기반 자체 목표 없음. 암호화·검증 오버헤드가 정제 평균 10초 목표(기획서)에 영향을 주지 않는 수준

**Constraints**: 요청 본문 최대 64KB, 시크릿은 환경변수만, 오류 응답에 내부 정보 0건, 민감 본문 로그 0건

**Scale/Scope**: MVP 시범 1개교. 공통 클래스 약 20개 + 테스트

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| 원칙 | 이 기반에서의 대응 | 결과 |
|---|---|---|
| I. 연락처 미수집 | 이번 범위에 사용자 데이터 없음. `BaseEntity`에 연락처 필드 없음 | ✅ 해당 없음(위반 없음) |
| II. 가명화 게이트웨이 | 외부 AI 호출 없음(F3). API 키만 기동 시 검증 | ✅ 해당 없음 |
| III. 원문 보존·암호화 | `SensitiveText` + AES-256-GCM 자동 암호화 컨버터 제공 (append-only 강제는 F2·F5) | ✅ |
| IV. 긴급 fail-open / 일반 fail-closed | 범위 밖(F2·F3) | ✅ 해당 없음 |
| V. 교사 답변 자동 발송 없음 | 범위 밖(F4) | ✅ 해당 없음 |
| VI. 민감정보 비노출 | 3계층 공통 에러 처리, `SensitiveText.toString()` 가림, 본문·SQL 로그 비활성 | ✅ |
| VII. UUID·건별 소유권 | `BaseEntity` UUID v4, API에 UUID만. 건별 소유권 검증은 각 기능에서 구현(기반은 403/404 코드 제공) | ✅ |
| VIII. 안전한 기본값 | `EnvironmentPostProcessor` 시크릿 검증·기동 거부, 기본 계정 없음, `.env.example` 이름만 | ✅ |
| 백엔드: LLM 출력 검증 | 범위 밖(F3) | ✅ 해당 없음 |
| 백엔드: 파라미터 바인딩 | JPA만 사용, 문자열 결합 쿼리 없음 | ✅ |
| 백엔드: 토큰 | 검증 측: HS256 고정·`alg=none` 거부·`exp` 필수. 발급·회전은 F1 | ✅ |
| 백엔드: 낙관적 락 | `@Version` + `CONFLICT` 변환 | ✅ |
| 백엔드: 암호화·SecureRandom | AES-256-GCM, IV는 `SecureRandom` | ✅ |
| 아키텍처: 헥사고날·의존 방향 | 공통 기반은 `damso.common`, 도메인 패키지 없음. `common`은 어떤 도메인 패키지도 참조하지 않음 | ✅ |
| 개발 흐름: contracts 사람 리뷰 | `contracts/foundation.openapi.yaml` 리뷰 후 `docs/api/openapi.yaml`에 통합 | ⏳ 리뷰 대기 |

**설계 후 재점검(Phase 1 이후)**: 위반 없음. CSRF 비활성은 원칙 위반이 아니라 "쿠키 미사용"의 결과이며 research R8에 근거를 남겼다. Complexity Tracking 해당 없음.

## Project Structure

### Documentation (this feature)

```text
specs/000-foundation/
├── spec.md
├── plan.md              # 이 파일
├── research.md          # R1~R13 결정 근거
├── data-model.md        # BaseEntity, SensitiveText, ErrorCode, 시크릿 목록
├── quickstart.md        # 검증 절차
├── contracts/
│   └── foundation.openapi.yaml
├── checklists/
│   └── requirements.md
└── tasks.md             # /speckit-tasks에서 생성
```

### Source Code (repository root)

```text
build.gradle                        # oauth2-resource-server, actuator, testcontainers 추가
settings.gradle                     # foojay toolchain resolver
.env.example                        # DAMSO_* 변수 이름만
src/main/resources/
├── application.yml                 # ${DAMSO_*} 참조만 (application.properties 대체)
└── META-INF/spring.factories       # SecretsValidator 등록 (Boot 4의 EnvironmentPostProcessor 키 확인)

src/main/java/damso/
├── DamsoApplication.java
└── common/                         # 공통 기반 (constitution 아키텍처: damso.common)
    ├── config/
    │   ├── SecretsValidator.java          # 기동 전 시크릿 검증 (R7)
    │   ├── JpaAuditingConfig.java
    │   └── RequestSizeLimitFilter.java    # 64KB 제한 (R9)
    ├── error/
    │   ├── ErrorCode.java
    │   ├── ErrorResponse.java
    │   ├── BusinessException.java         # 기능 코드가 ErrorCode로 던지는 예외
    │   ├── GlobalExceptionHandler.java    # @RestControllerAdvice (모든 웹 어댑터 공통)
    │   └── JsonErrorController.java       # /error 잔여 경로
    ├── security/
    │   ├── SecurityConfig.java            # 기본 거부, STATELESS, 공개 경로 목록
    │   ├── JwtDecoderConfig.java          # HS256 고정, exp 필수
    │   ├── JsonAuthenticationEntryPoint.java
    │   └── JsonAccessDeniedHandler.java
    ├── entity/
    │   └── BaseEntity.java                # UUID v4, createdAt, updatedAt, @Version
    └── crypto/
        ├── SensitiveText.java             # toString() → [MASKED]
        ├── AesGcmCipher.java
        └── SensitiveTextConverter.java    # autoApply

src/test/java/damso/
├── common/error/                   # SC-001
├── common/config/                  # SC-002 (기동 거부)
├── common/security/                # SC-003
├── common/crypto/                  # SC-004·005 (Testcontainers)
├── common/entity/                  # SC-006 (Testcontainers)
└── support/                        # 테스트 전용 엔티티·컨트롤러·토큰 발급기
```

**Structure Decision**: 단일 Spring Boot 프로젝트, constitution "아키텍처"의 **헥사고날(도메인별 육각형) + 실용형 JPA**를 따른다.
- 이번 기능은 도메인이 아니라 공통 기반이라 `damso.common`에 둔다. 포트·어댑터 계층이 필요 없는 횡단 관심사(에러 형식, 보안 필터, 암호화 컨버터, 엔티티 기반)만 있다.
- 이후 기능은 `damso.<도메인>.{domain, application.port.in, application.port.out, application.service, adapter.in.web, adapter.out.persistence}`에 둔다. 도메인 엔티티는 `damso.common.entity.BaseEntity`를 상속하고, 민감 필드는 `damso.common.crypto.SensitiveText` 타입을 쓴다.
- `GlobalExceptionHandler`는 모든 웹 어댑터에 공통으로 적용되므로 `common.error`에 둔다.
- 테스트 전용 엔티티·컨트롤러는 `src/test`에만 둬서 운영 코드에 진단용 경로가 남지 않게 한다(체크리스트 캡슐화 2).

## Complexity Tracking

해당 없음 — Constitution Check 위반 없음.
