# Implementation Plan: 문의 접수·긴급 분류 (F2)

**Branch**: `features/f2-inquiry-urgent` (이슈 #4) | **Date**: 2026-10-18 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `specs/002-inquiry-intake-urgent/spec.md`

## Summary

학부모가 자녀를 골라 자유 서술로 문의하고, 원문은 암호화해 변경 불가로 남긴다. 안전 사안은 외부 AI·알림 장애와 무관하게 규칙으로 긴급 판정되어 담임에게 즉시 전달된다.

- **접수**: 입력 검증 → 중복 제출 확인(`Idempotency-Key`) → **긴급 판정(규칙)** → 일반이면 1분 제한(Redis) → 저장. 긴급은 1분 제한도 Redis도 거치지 않는다.
- **긴급 판정**: 서버 리소스 파일의 고정 표현 목록과 정규화한 본문의 포함 검사. 순수 함수, 목록이 비면 기동 거부.
- **처리 단계**: 긴급은 `RECEIVED → DELIVERED`(접수 시) `→ TEACHER_CONFIRMED`(교사 첫 원문 열람). 단계 이력은 append-only.
- **알림**: 문의와 같은 트랜잭션에서 알림 요청을 저장하고(아웃박스), 긴급은 커밋 직후 즉시 발송한다. 10초 주기 발송기가 일반 알림과 즉시 발송이 실패한 요청을 FCM으로 보내는 안전망이다(Redis Stream은 검토 후 보류, R7). 긴급은 확인될 때까지 1시간마다 재알림, 응답 가능 시간 밖 일반 문의는 시작 시각에 묶어서 한 번.
- **기기**: 접근 토큰의 세션(`sid`)에 묶어 등록 → 로그아웃·탈퇴하면 F1 코드 수정 없이 알림이 끊긴다.
- **원문 열람**: 긴급 문의만(F2), 감사 기록(F5 뼈대 `audit_log`)과 같은 트랜잭션.
- **위기 종류**: 분류 결과를 받는 연결 지점과 번호 선택 규칙만. F3 전에는 항상 기본 안내(112·109).

## Technical Context

**Language/Version**: Java 21

**Primary Dependencies**: Spring Boot 4.1.1 — F1 그대로 + **추가: `com.google.firebase:firebase-admin`**(FCM 발송, R8). 스케줄링은 F1의 `@EnableScheduling` 재사용.

**Storage**: PostgreSQL — 테이블 7개 추가(data-model.md), `ddl-auto=update`. Redis — F1 실패 제한에 `INQUIRY_SUBMIT` 키 추가.

**Testing**: JUnit 5, Spring Boot Test + MockMvc, Testcontainers PostgreSQL·Redis(F1 지원 클래스), 가짜 푸시 포트, `MutableClock`. 알림 발송기는 테스트에서 직접 호출한다.

**Target Platform**: Linux 컨테이너, 클라이언트는 Flutter 앱(Android·iOS)

**Project Type**: web-service (REST API)

**Performance Goals**: 긴급 문의 제출 → 교사 목록 반영 3초 이내(동기 처리, SC-002). 긴급 알림은 커밋 직후 즉시, 그 밖의 알림은 발송기 주기 10초 이내.

**Constraints**: 긴급 경로는 외부 AI·FCM·Redis 장애와 무관(원칙 IV). 본문·제목 로그 0건. 학부모 안내는 고정 문구 코드만(원칙 V).

**Scale/Scope**: 1개교, 학급당 하루 수 건~수십 건. 엔드포인트 10개, 새 도메인 3개(`inquiry`·`notification`·`audit`).

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| 원칙 | F2 대응 | 결과 |
|---|---|---|
| I. 연락처 미수집 | 문의·알림 어디에도 연락처 필드 없음. 교사 응답에 학부모 이메일 없음. 알림에 이름 없음 | ✅ |
| II. 가명화 게이트웨이 | F2는 외부 AI를 호출하지 않는다. 위기 종류 분류는 F3가 게이트웨이를 거쳐 연결 지점(`CrisisClassificationUseCase`)으로 결과만 넘긴다 | ✅ 해당 없음 |
| III. 원문 보존·열람 감사 | 본문 `SensitiveText` + `updatable=false`, 수정·삭제 API 없음. 단계 이력·감사 기록 append-only(`@Immutable`, 저장·조회만 있는 리포지토리). 원문 열람은 감사 기록과 같은 트랜잭션 | ✅ |
| IV. 긴급 fail-open / 일반 fail-closed | 긴급 판정은 규칙·순수 함수, AI 이전. 긴급 접수는 Redis·FCM을 거치지 않고, 알림은 아웃박스라 장애 시에도 요청이 남아 재시도 | ✅ |
| V. 자동 발송 없음·시스템 안내 고정 문구 | 학부모 안내는 `NoticeCode`·`HotlineCode`(계약에 고정 문구). 교사 명의 발신 없음. AI 문장 없음 | ✅ |
| VI. 민감정보 비노출 | 본문·제목·FCM 토큰 로그 금지, 로그는 UUID·상태만. 로그 캡처 테스트 | ✅ |
| VII. UUID·건별 소유권 | 학부모 = 본인 문의, 교사 = 현재 담임인 학급의 문의, 슈퍼 관리자 = 경로 없음. 남의 문의는 404. 요청의 사용자 ID 미신뢰(토큰 주체) | ✅ |
| VIII. 안전한 기본값 | `DAMSO_FCM_CREDENTIALS` 필수·형식 검증, 긴급 표현 목록 파일이 비면 기동 거부 | ✅ |
| 백엔드: LLM 출력 검증 | 위기 종류 결과는 허용값 4개만, 그 밖은 버림(연결 지점에서 강제) | ✅ |
| 백엔드: 낙관적 락 | 문의 단계 전이 `@Version`, 알림 요청은 `SKIP LOCKED` | ✅ |
| 백엔드: 암호화 | 본문·제목 AES-256-GCM(`SensitiveText`) | ✅ |
| 아키텍처: 헥사고날·DTO 규약 | `inquiry`·`notification`·`audit` 육각형, 도메인 간 `port.in`만. 컨트롤러 `adapter.in.controller`, DTO `dto.request`·`dto.response` 별도 파일(constitution 1.3.0) | ✅ |
| 개발 흐름: contracts 사람 리뷰 | `contracts/inquiry.openapi.yaml` 리뷰 후 `docs/api/openapi.yaml` 통합(0.3.0) | ✅ 리뷰 완료 2026-10-18 |
| 큰 영향 결정 확인(CLAUDE.md) | 팀 결정 2026-10-18: FCM SDK·필수 시크릿(R8), 테이블 7개(data-model), DB 아웃박스 + 긴급 즉시 발송 + 10초 폴링 안전망(R7, Redis Stream 보류), `Idempotency-Key` 필수 헤더(R6) | ✅ |

**설계 후 재점검(Phase 1 이후)**: 원칙 위반 없음. Complexity Tracking 해당 없음.

## Project Structure

### Documentation (this feature)

```text
specs/002-inquiry-intake-urgent/
├── spec.md
├── plan.md              # 이 파일
├── research.md          # R1~R14 결정 근거 (큰 영향 결정 팀 확인 완료)
├── data-model.md        # 테이블 7개, 단계·알림 상태 전이, Redis 키·에러 코드·시크릿 추가분
├── quickstart.md        # 검증 절차
├── contracts/
│   └── inquiry.openapi.yaml
├── checklists/
│   └── requirements.md
└── tasks.md             # /speckit-tasks에서 생성
```

### Source Code (repository root)

```text
build.gradle                          # + com.google.firebase:firebase-admin
.env.example                          # + DAMSO_FCM_CREDENTIALS (이름만)
src/main/resources/
├── urgent/expressions.txt            # 긴급 판정 표현 목록 (PR 리뷰로만 변경)
└── redis/mail_send.lua → rate_limit.lua  # 최소 간격·윈도·최대 건수 파라미터로 일반화

src/main/java/damso/
├── common/
│   ├── config/SecretsValidator.java  # (수정) DAMSO_FCM_CREDENTIALS 형식 검증
│   └── error/ErrorCode.java          # (수정) NO_HOMEROOM_TEACHER, INQUIRY_CONTENT_UNAVAILABLE
│
├── inquiry/
│   ├── domain/        Inquiry, InquiryStage(전이표), CrisisType, InquiryStageHistory, UrgentRule, InquiryContentPolicy(코드 포인트 길이), GuidancePolicy(위기 종류 → 번호)
│   ├── application/
│   │   ├── port/in/   SubmitInquiryUseCase, ParentInquiryQuery, TeacherInquiryQuery, ViewInquiryContentUseCase, CrisisClassificationUseCase(F3가 호출), UrgentReminderPolicy(알림이 재알림 여부·현재 받는 사람을 물음), DeferredInquiryCounter(묶음 알림 건수)
│   │   ├── port/out/  InquiryPort, InquiryStageHistoryPort, UrgentExpressionSource
│   │   └── service/
│   └── adapter/
│       ├── in/controller/     ParentInquiryController, TeacherInquiryController (+ dto/request, dto/response)
│       ├── out/persistence/   JPA 리포지토리
│       └── out/resource/      표현 목록 파일 로더(ClasspathUrgentExpressionSource)
│
├── notification/
│   ├── domain/        NotificationRequest(종류·상태 전이), DeviceRegistration, TeacherNotificationSetting, AvailabilityWindow, AvailabilityCalendar(응답 가능 판정·다음 시작 시각)
│   ├── application/
│   │   ├── port/in/   NotifyInquiryUseCase(문의가 호출: 긴급/일반 접수 알림), DeviceRegistrationUseCase, NotificationSettingsUseCase
│   │   ├── port/out/  NotificationRequestPort, DevicePort, NotificationSettingPort, PushSenderPort
│   │   └── service/   (유스케이스) + NotificationDispatcher(@Scheduled 10초 안전망, SKIP LOCKED) + UrgentDispatchTrigger(긴급 커밋 후 즉시 발송)
│   └── adapter/
│       ├── in/controller/     DeviceController, NotificationSettingsController (+ dto)
│       ├── out/persistence/
│       └── out/fcm/           FcmPushSender (Firebase Admin SDK)
│
├── audit/
│   ├── domain/        AuditLog (@Immutable), AuditAction
│   ├── application/   port/in/AuditLogUseCase, port/out/AuditLogPort, service/
│   └── adapter/out/persistence/  AuditLogRepository (Repository: save·조회만)
│
├── school/                           # (수정) inquiry가 쓰는 조회 포트 추가
│   └── application/   port/in/InquiryTargetQuery, service/InquiryTargetService
│
└── attempt/                          # (수정) INQUIRY_SUBMIT 종류, reserve(kind, subject) 일반화

src/test/java/damso/
├── inquiry/          제출·중복·1분 제한·긴급 판정·단계·조회 권한·원문 열람
├── notification/     기기 등록·세션 연동·설정·응답 가능 시간·발송기(긴급·재알림·묶음·재시도)
├── audit/            append-only 구조 테스트
└── support/          (기존) + FakePushSender
```

**Structure Decision**: 단일 Spring Boot 프로젝트, constitution의 도메인별 헥사고날.

- `inquiry`는 접수와 조회를, `notification`은 "언제 누구에게 무엇을 보낼지"를 맡는다.
  - 문의는 접수 트랜잭션 안에서 `NotifyInquiryUseCase`를 불러 알림 요청만 저장한다.
  - 발송기는 재알림을 보낼지 판단할 때 `inquiry`의 `UrgentReminderPolicy`(port.in)에 묻는다. 확인됐는지, 받는 사람이 아직 담임인지를 본다.
- 받는 사람이 현재 담임인지는 `inquiry`가 `school`의 `port.in`으로 확인한다. 다른 도메인의 리포지토리를 직접 부르지 않는다.
- `inquiry`와 `notification`은 서로의 `port.in`을 호출하는 양방향 의존이다(접수 → 알림 요청, 발송기 → 재알림 판단·묶음 건수). constitution이 금지하지 않고 빈 수준에서는 인터페이스 주입이라 순환이 없다. F4에서 같은 양방향이 늘어나는지 plan마다 확인한다.
- 기기의 세션 활성 여부는 F1의 `common.security.SessionStatusChecker`로 본다. `notification`은 `account`를 직접 참조하지 않는다.
- `audit`은 F5가 확장할 뼈대다. F2에서는 기록만 하고 조회 API는 두지 않는다.

## Complexity Tracking

해당 없음 — Constitution Check 위반 없음.
