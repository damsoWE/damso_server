# Implementation Plan: 가명화·정제 파이프라인 (F3)

**Branch**: `features/f3-pseudonymization-pipeline` (이슈 #6) | **Date**: 2026-10-25 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `specs/003-pseudonymization-pipeline/spec.md`

## Summary

일반 문의를 식별정보를 가린 뒤 외부 AI(OpenAI)로 사실관계·요청사항·정서강도로 정리해 교사에게 "번역본"으로 보여 준다. 아동 개인정보는 외부로 나가지 않고, 검증되지 않은 결과는 교사에게 보이지 않는다.

- **가명화 게이트웨이**(새 도메인 `gateway`, 상태 없음): 1차 학급 명단 사전 → 2차 패턴(전화·주소·이메일·주민번호 형태) → 남은 값 자기 점검 → 외부 AI(Structured Outputs strict) → 서버 재검증 → 메모리에서 복원. 외부 AI 출력 포트는 게이트웨이 안에서만 쓴다.
- **정리 파이프라인**(`inquiry`): 접수 트랜잭션에서 정리 작업을 함께 저장(아웃박스)하고, 커밋 직후 즉시 실행 + 10초 안전망. 외부 AI 호출은 트랜잭션 밖. 실패하면 곧바로 자동 1회 재시도, 그래도 실패하면 "원문 확인 필요" + 교사 재시도 1회.
- **처리 단계**: 일반 `RECEIVED → REFINING`(정리 성공) `→ TEACHER_CONFIRMED`(번역본 첫 열람). 정리에 실패한 문의는 원문 첫 열람 때 `RECEIVED → TEACHER_CONFIRMED`.
- **알림(F2 규칙 변경)**: 일반 문의 알림을 접수 때가 아니라 정리 확정(성공·실패) 때 요청한다. 응답 가능 시간·묶음 규칙은 그대로, 기준 시각만 바뀐다. 학부모 안내는 접수 때 미리 판정해 `deferred_notice`로 고정한다.
- **위기 종류 분류**: 긴급 문의도 분류 작업(1회)을 만들고, 게이트웨이 → F2 연결 지점(`CrisisClassificationUseCase`)으로 넘긴다. 긴급 전달·알림은 기다리지 않는다.
- **원문 열람**: 일반 문의에도 연다. 감사 기록은 F2의 3단계 방식 그대로.

## Technical Context

**Language/Version**: Java 21

**Primary Dependencies**: Spring Boot 4.1.1 — F2 그대로. **새 의존성 없음**: 외부 AI는 Spring `RestClient`로 OpenAI HTTP API를 직접 호출한다(R5) [확인 필요]. 스케줄링은 F1 `@EnableScheduling`, 비동기는 F1 `AsyncConfig` 재사용.

**Storage**: PostgreSQL — 테이블 3개 추가(`refinement_job`, `inquiry_refinement`, `refinement_attempt`), `inquiry`에 컬럼 2개(`refinement_status`, `deferred_notice`), `ddl-auto=update` [확인 필요]. Redis 변경 없음.

**Testing**: JUnit 5, Spring Boot Test + MockMvc, Testcontainers PostgreSQL·Redis, 가짜 `ExternalAiPort`(받은 본문 기록·결과 조작), F2 가짜 푸시 포트, `MutableClock`. 정리 실행기·알림 발송기는 테스트에서 직접 호출한다. 대표 가명화 사례는 리소스 파일로 둔다.

**Target Platform**: Linux 컨테이너, 클라이언트는 Flutter 앱(Android·iOS)

**Project Type**: web-service (REST API)

**Performance Goals**: 접수 → 번역본 표시 평균 10초 이내(FR-031). 파이프라인 자체 지연(가명화·검증·저장)은 1초 미만, 나머지는 외부 AI 응답 시간. 외부 AI 호출 상한 15초(FR-013a).

**Constraints**: 외부로 나가는 글은 게이트웨이만 통과(원칙 II). 대응표 저장·로그 0건. 검증 안 된 결과 교사 노출 0건(원칙 IV). 긴급 경로는 분류·외부 AI와 무관(원칙 IV). 본문·정리 결과 로그 0건(원칙 VI). 외부 AI 대상 주소 고정(SSRF).

**Scale/Scope**: 1개교, 학급당 하루 수 건~수십 건 → 외부 AI 호출도 같은 규모(문의당 1~3회). 엔드포인트 2개 추가·1개 변경, 새 도메인 1개(`gateway`).

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| 원칙 | F3 대응 | 결과 |
|---|---|---|
| I. 연락처 미수집 | 연락처를 저장하지 않는다. 문의에 쓰인 전화번호·주소·이메일은 외부로 나가기 전에 가리고, 정리 결과는 원문처럼 암호화 저장. 교사 응답에 학부모 이메일 없음 | ✅ |
| II. 가명화 게이트웨이 경유 | `ExternalAiPort`는 `gateway` 안에서만 쓰이고, 공개 입구는 가명화를 먼저 하는 두 메서드뿐(구조 테스트). 대상: 학생·보호자·교사·학교·학급 + 전화·주소 패턴. 대응표는 메모리 전용·`toString` 가림·직렬화 금지, 저장 전에 복원 후 폐기. 못 가렸으면 호출하지 않음 | ✅ |
| III. 원문 보존·열람 감사 | `inquiry_refinement`·`refinement_attempt`는 `@Immutable` + 저장·조회만. 재시도는 새 결과를 덧붙임. 일반 문의 원문 열람도 F2의 3단계 감사 기록 | ✅ |
| IV. 긴급 fail-open / 일반 fail-closed | 긴급은 정리 없음, 분류는 별도 작업 1회이고 긴급 알림과 서로 기다리지 않음. 일반은 검증 실패 → 1회 재시도 → "원문 확인 필요" + 교사 알림, 미검증 결과 비노출 | ✅ |
| V. 자동 발송 없음·고정 문구 | 정리 결과는 교사에게만 보인다. 학부모에게는 고정 안내 코드와 번호뿐, AI 문장 없음. 알림은 고정 문구 | ✅ |
| VI. 민감정보 비노출 | 본문·가린 본문·정리 결과·제목·대응표 로그 금지, 외부 AI 오류 본문 버림, `RestClient` 로깅 끔. 로그 캡처 테스트 확장 | ✅ |
| VII. UUID·건별 소유권 | 번역본·재시도·원문 모두 현재 담임만, 남의 문의 404, 슈퍼 관리자 403(F2 규칙 재사용) | ✅ |
| VIII. 안전한 기본값 | `DAMSO_OPENAI_API_KEY`(기존)·`DAMSO_OPENAI_MODEL`(추가) 필수, 학교 용어·프롬프트 파일 비면 기동 거부 | ✅ |
| 백엔드: LLM 출력 검증 | Structured Outputs strict + 서버 재검증(필드·길이·enum·라벨 수·토큰), 위반 시 폐기 | ✅ |
| 백엔드: LLM 출력 격리 | 결과는 저장·표시만. 시스템 지시는 고정 파일(system 역할), 본문은 user 역할 | ✅ |
| 백엔드: 낙관적 락 | 문의 단계 `@Version`, 정리 작업 확보 `SKIP LOCKED`, 교사 재시도 `@Version` | ✅ |
| 백엔드: 암호화 | 사실관계·요청사항·제목 `SensitiveText` | ✅ |
| 아키텍처: 헥사고날·DTO 규약 | `gateway` 육각형(port.in 2개, port.out `ExternalAiPort`, adapter.out.openai). 도메인 간 `port.in`만. 새 DTO는 `dto.request`·`dto.response` 별도 파일 | ✅ |
| 아키텍처: 외부 LLM 출력 포트는 게이트웨이 경유 | constitution 문구 그대로 구조로 구현 | ✅ |
| 개발 흐름: contracts 사람 리뷰 | `contracts/refinement.openapi.yaml` 리뷰 후 `docs/api/openapi.yaml` 통합(0.4.0) | ✅ 리뷰 완료 2026-10-25 |
| 큰 영향 결정 확인(CLAUDE.md) | 아래 "확인이 필요한 결정" 5건 모두 확정 | ✅ 팀 결정 2026-10-25 |

**설계 후 재점검(Phase 1 이후)**: 원칙 위반 없음. Complexity Tracking 해당 없음. contracts 리뷰와 큰 영향 결정 확인을 마쳤다(2026-10-25).

### 확인이 필요한 결정 (tasks 전에)

| # | 결정 | 근거 | 상태 |
|---|---|---|---|
| D1 | 외부 AI 호출을 새 의존성 없이 `RestClient`로 직접 구현(`RestTemplate`·공식 SDK·Spring AI 대신) | R5 | ✅ 팀 결정 2026-10-25 |
| D2 | 새 필수 환경변수 `DAMSO_OPENAI_MODEL`(모델은 배포 환경에서 정함) | R5 | ✅ 팀 결정 2026-10-25 |
| D3 | DB: 테이블 3개 + `inquiry` 컬럼 2개, `notification_deferred` 의미 변경 | data-model | ✅ 팀 결정 2026-10-25 |
| D4 | 앱 계약 0.4.0: 번역본·재시도 API 추가, 교사 목록·상세 필드 추가, 일반 문의 원문 열람 허용 | R12, contracts | ✅ 팀 결정 2026-10-25 |
| D5 | 학교 이름 가명 "한양초등학교", `school-terms.txt` 표기 변형 4개(한양초등학교·한양 초등학교·한양초교·한양초), F2 대기 알림·기존 일반 문의 미이관(F3 배포 전 개발 DB 정리로 갈음) | R2, R10 | ✅ 팀 결정 2026-10-25 |

## Project Structure

### Documentation (this feature)

```text
specs/003-pseudonymization-pipeline/
├── spec.md
├── plan.md              # 이 파일
├── research.md          # R1~R14 결정 근거
├── data-model.md        # 테이블 3개·inquiry 컬럼 2개, 단계·정리 상태 전이, 에러 코드·설정 추가분
├── quickstart.md        # 검증 절차
├── contracts/
│   └── refinement.openapi.yaml
├── checklists/
│   └── requirements.md
└── tasks.md             # /speckit-tasks에서 생성
```

### Source Code (repository root)

```text
.env.example                          # + DAMSO_OPENAI_MODEL (이름만)
src/main/resources/
├── application.yml                   # + damso.openai.model·timeout, damso.refinement.dispatch-interval
├── pseudonymization/school-terms.txt # 학교 이름·줄임말 (팀이 정함, 비면 기동 거부)
└── gateway/prompts/
    ├── refine.txt                    # 고정 시스템 지시 (PR 리뷰로만 변경)
    └── classify-crisis.txt

src/main/java/damso/
├── common/
│   ├── config/SecretsValidator.java  # (수정) DAMSO_OPENAI_MODEL
│   └── error/ErrorCode.java          # (수정) REFINEMENT_NOT_READY, REFINEMENT_RETRY_UNAVAILABLE
│
├── gateway/                          # 새 도메인 — 가명화 게이트웨이 (상태 없음)
│   ├── domain/        Roster, Pseudonymizer(1차 사전·2차 패턴·토큰 모양 입력 무력화), PseudonymMap, MaskedText,
│   │                  MaskingVerifier(남은 값 자기 점검), RefinementResult(검증 규칙), EmotionLevel, EmotionLabel
│   ├── application/
│   │   ├── port/in/   RefineInquiryGateway, ClassifyCrisisGateway
│   │   ├── port/out/  ExternalAiPort (유일한 외부 AI 출력 포트)
│   │   └── service/   RefineInquiryGatewayService, ClassifyCrisisGatewayService
│   └── adapter/
│       ├── out/openai/       OpenAiClient (RestClient, 고정 주소, Structured Outputs strict, 로깅 없음)
│       └── out/resource/     프롬프트·학교 용어 파일 로더 (비면 기동 거부)
│
├── inquiry/                          # (수정·추가)
│   ├── domain/        Inquiry(+refinementStatus, deferredNotice, 전이), InquiryStage(전이표), RefinementStatus,
│   │                  RefinementJob, InquiryRefinement(@Immutable), RefinementAttempt(@Immutable)
│   ├── application/
│   │   ├── port/in/   ViewRefinementUseCase, RetryRefinementUseCase (+ 기존 TeacherInquiryQuery 확장)
│   │   ├── port/out/  RefinementJobPort, InquiryRefinementPort, RefinementAttemptPort
│   │   └── service/   RefinementRunner(작업 확보·게이트웨이 호출·결과 반영), RefinementDispatchTrigger(커밋 후 즉시),
│   │                  RosterAssembler(school·account port.in으로 사전 재료 모음), ViewRefinementService,
│   │                  RetryRefinementService, (수정) SubmitInquiryService·ViewInquiryContentService·
│   │                  TeacherInquiryService·ParentInquiryService
│   └── adapter/
│       ├── in/controller/     TeacherInquiryController(+refinement, retry) + dto/response
│       └── out/persistence/   JPA 리포지토리(결과·시도는 저장·조회만)
│
├── notification/                     # (수정) NotifyInquiryUseCase.previewGeneral 추가(저장 없음)
├── school/                           # (수정) port.in RosterQuery: 학급 학생 이름·보호자 UUID·담임·학년·반·교사 표시 이름
└── account/                          # (수정) port.in UserNameQuery: 계정 UUID → 이름 (로그 금지)

src/test/java/damso/
├── gateway/          가명화 사례·자기 점검·출력 검증·복원·구조(외부 AI 포트 단일 경로)
├── inquiry/          파이프라인(성공·재시도·실패·교사 재시도·중복 실행), 단계 전이, 번역본·원문 권한, 학부모 비노출,
│                     알림 시점, 위기 종류 분류, 로그 캡처
└── support/          FakeExternalAi
src/test/resources/pseudonymization/cases.txt   # 대표 사례(가려져야 할 것 / 알려진 한계)
```

**Structure Decision**: 단일 Spring Boot 프로젝트, constitution의 도메인별 헥사고날.

- `gateway`는 "글을 가리고, 외부 AI에 묻고, 검증·복원한다"만 한다. 저장소가 없고 다른 도메인을 부르지 않는다. 그래서 F4(답변 점검)가 같은 게이트웨이를 그대로 쓸 수 있다.
- `inquiry`는 언제 정리할지, 결과를 어디에 두고 누구에게 보일지를 맡는다. 사전 재료는 `school`·`account`의 `port.in`으로 모은다(다른 도메인 리포지토리 직접 호출 없음).
- `inquiry ↔ notification` 양방향(F2) 외에 새 양방향 의존은 없다. `inquiry → gateway`, `inquiry → account`는 한 방향이다.
- F2 코드 수정 범위: 접수 시 일반 알림 호출 제거·미리 판정, 학부모 안내 기준 컬럼, 원문 열람의 일반 문의 허용·단계 전이, 교사 목록·상세 필드. 긴급 경로는 분류 작업 저장 한 줄만 더한다.

## Complexity Tracking

해당 없음 — Constitution Check 위반 없음.
