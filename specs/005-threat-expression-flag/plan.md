# Implementation Plan: 일반 문의 위협·폭언 표현 표시

**Branch**: `features/f3-threat-expression-flag` (이슈 #9) | **Date**: 2026-10-26 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `specs/005-threat-expression-flag/spec.md`

## Summary

일반 문의를 접수할 때 서버의 고정 표현 목록으로 원문을 검사해 "위협·폭언 표현 포함" 표시를 정하고 문의에 저장한다. 교사 목록·상세에만 보이고, 외부 AI 정리 결과와 무관하며, 한 번 정해지면 바뀌지 않는다.

- **판정**: 순수 함수 `ThreatRule` — 어절 시작에서만, 표현의 공백 자리에서만 띄어쓰기 차이를 허용(오탐 최소화, research R2 변경). 표현 목록은 `threat/expressions.txt`이고, 비면 기동을 거부한다.
- **순서**: 긴급 판정 → 긴급이 아니면 위협·폭언 판정. 긴급 문의는 항상 false다. 판정이 실패하면 false로 두고 접수를 계속한다.
- **저장**: `inquiry.threat_flagged`(변경 불가). 걸린 표현은 저장하지도 로그에 남기지도 않는다.
- **노출**: 교사 목록·상세(재문의 항목 포함)에 `threatFlagged` 필드를 더한다. 재문의(F4)도 같은 규칙으로 판정한다(FR-005c). 학부모 응답과 알림은 그대로 두고 테스트로 확인한다.
- **검증 기준**: 팀이 정한 대표 사례(표시할 문장 / 정당한 의사 표현을 포함한 표시하지 않을 문장)를 테스트로 묶는다.

## Technical Context

**Language/Version**: Java 21

**Primary Dependencies**: Spring Boot 4.1.1 — F3까지와 같다. **새 의존성 없음**.

**Storage**: PostgreSQL — `inquiry`에 컬럼 1개(`threat_flagged boolean not null default false`), `ddl-auto=update` [사용자 승인 2026-10-26: D1]. Redis 변경 없음.

**Testing**: JUnit 5, Spring Boot Test + MockMvc, Testcontainers PostgreSQL·Redis, F3 가짜 `ExternalAiPort`(외부 AI 중단·편차 재현). 대표 사례는 리소스 파일 2개(`threat/must-flag.txt`, `threat/must-not-flag.txt`).

**Target Platform**: Linux 컨테이너, 클라이언트는 Flutter 앱(Android·iOS)

**Project Type**: web-service (REST API)

**Performance Goals**: 판정은 본문 2,000자 × 표현 수십~수백 개의 포함 검사라 1ms 단위다. 접수 응답 시간에 영향이 없다(SC-006).

**Constraints**:
- 외부 AI와 무관하게 동작한다(원칙 IV).
- 학부모 노출 0건(원칙 V).
- 걸린 표현은 저장·로그 0건(원칙 VI).
- 판정이 접수를 막지 않는다.

**Scale/Scope**:
- 엔드포인트 추가 없음, 응답 필드 2곳 추가.
- 새 클래스 3개(규칙·포트·리소스 어댑터)와 리소스 파일 3개.
- 수정 대상: 접수 서비스, 엔티티, 교사 조회, DTO 2개.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| 원칙 | 이 기능의 대응 | 결과 |
|---|---|---|
| I. 연락처 미수집 | 새로 저장하는 값은 boolean 하나뿐이다. 연락처 없음 | ✅ |
| II. 가명화 게이트웨이 경유 | 외부 AI를 부르지 않는다. 판정은 서버 안에서만 한다 | ✅ (해당 없음) |
| III. 원문 보존·열람 감사 | 원문은 읽기만 한다. 표시는 `updatable = false`이고 수정·삭제 API 없음. 원문 열람 감사는 F2·F3 그대로 | ✅ |
| IV. 긴급 우선 | 긴급 판정이 먼저이고, 위협 판정은 긴급이 아닐 때만 한다. 규칙 기반이며 외부 AI·네트워크와 무관하다. 판정 실패가 접수·긴급 경로를 막지 않는다 | ✅ |
| V. 고정 문구·자동 발송 없음 | 표시는 교사에게만 보인다. 학부모 응답·알림 문구는 바뀌지 않는다(노출 테스트) | ✅ |
| VI. 민감정보 비노출 | 걸린 표현·위치는 저장하지 않는다. 실패 로그는 고정 문구 한 줄이고 예외 메시지를 남기지 않는다. 로그 캡처 테스트를 확장한다 | ✅ |
| VII. UUID·건별 소유권 | 새 엔드포인트가 없다. 목록·상세의 현재 담임 검증을 그대로 쓴다 | ✅ |
| VIII. 안전한 기본값 | 표현 목록이 비거나 못 읽으면 기동을 거부한다. 새 시크릿 없음 | ✅ |
| 백엔드: LLM 출력 검증·격리 | LLM을 쓰지 않는다. 정리 결과는 표시에 영향을 주지 않는다(FR-009) | ✅ (해당 없음) |
| 백엔드: 낙관적 락 | 표시는 상태 전이가 아니라 접수 때 한 번 쓰는 값이다. 기존 `@Version` 그대로 | ✅ |
| 아키텍처: 헥사고날·DTO 규약 | `inquiry` 안에 F2 긴급 규칙과 같은 자리(domain·port.out·adapter.out.resource). 응답 DTO는 기존 파일에 필드만 추가 | ✅ |
| 개발 흐름: contract-first | `contracts/threat-flag.openapi.yaml`을 `docs/api/openapi.yaml`에 통합(0.5.0, 구현 전). 태그·프론트 공지는 merge 뒤(R10) | ✅ 통합 2026-10-26, 태그·공지 대기 |
| 개발 흐름: contracts 사람 리뷰 | 소유권(현재 담임만, 변경 없음)·에러 형식(변경 없음)·UUID 노출(새 식별자 없음) | 🟡 사용자가 tasks·implement 진행을 지시(2026-10-26). 리뷰 결과는 PR 리뷰에서 확인 |
| 큰 영향 결정 확인(CLAUDE.md) | 아래 "확인이 필요한 결정" D1~D3 | ✅ D1·D2 승인, D3는 초안 + 팀 리뷰로 결정 (2026-10-26) |

**설계 후 재점검(Phase 1 이후)**: 원칙 위반이 없어 Complexity Tracking은 해당 없다. 남은 게이트는 contracts 사람 리뷰와, 머지 전 D3 목록·사례 팀 리뷰다.

### 확인이 필요한 결정 (tasks 전에)

| # | 결정 | 근거 | 상태 |
|---|---|---|---|
| D1 | DB: `inquiry.threat_flagged boolean not null default false`(변경 불가). 기존 행은 false, 다시 판정하지 않음 | R6, data-model | ✅ 사용자 승인 2026-10-26 |
| D2 | 앱 계약: 교사 목록·상세에 `threatFlagged`(boolean, 필수) 추가, 정렬 변경 없음. 버전은 F4와 병합 순서로 0.5.0 또는 0.6.0 | R8, R10, contracts | ✅ 사용자 승인 2026-10-26 |
| D3 | 위협·폭언 표현 목록과 대표 사례 2개 파일의 **내용**. 긴급 목록(`협박`·`위협`·`죽이겠`·`죽여버리`·`칼을`·`흉기`·`학교폭력` 등)과 겹치는 표현은 넣지 않는다 | R3~R5, FR-002 | 🟡 Claude가 초안(`[초안] 팀 리뷰 전` 표시)을 쓰고 팀이 PR에서 리뷰·확정(사용자 결정 2026-10-26). 머지 전 리뷰한 사람·날짜를 PR 설명에 적는다 |

## Project Structure

### Documentation (this feature)

```text
specs/005-threat-expression-flag/
├── spec.md
├── plan.md              # 이 파일
├── research.md          # R1~R10 결정 근거
├── data-model.md        # inquiry 컬럼 1개, 판정·접수 흐름, 응답 모델 변경
├── quickstart.md        # 검증 절차
├── contracts/
│   └── threat-flag.openapi.yaml
├── checklists/
│   └── requirements.md
└── tasks.md             # /speckit-tasks에서 생성
```

### Source Code (repository root)

```text
src/main/resources/
└── threat/expressions.txt               # 위협·폭언 표현 (팀이 정함, 비면 기동 거부)

src/main/java/damso/inquiry/
├── domain/
│   ├── ThreatRule.java                  # 어절 시작 + 표현의 공백 자리만 허용, 빈 목록 거부
│   └── Inquiry.java                     # (수정) threat_flagged 컬럼, receive 인자, 긴급이면 false
├── application/
│   ├── port/out/ThreatExpressionSource.java
│   ├── port/in/TeacherInquiryQuery.java # (수정) InquirySummary·InquiryDetail에 threatFlagged
│   └── service/
│       ├── ThreatFlagScreener.java      # ThreatRule을 감싼 컴포넌트(테스트에서 실패 상황을 바꿔 끼움)
│       ├── SubmitInquiryService.java    # (수정) 긴급 판정 뒤 위협 판정, 실패 시 false
│       └── TeacherInquiryService.java   # (수정) 표시 값 전달
└── adapter/
    ├── in/controller/dto/response/
    │   ├── TeacherInquirySummaryResponseDTO.java  # (수정)
    │   └── TeacherInquiryDetailResponseDTO.java   # (수정)
    └── out/resource/ClasspathThreatExpressionSource.java

docs/api/openapi.yaml                    # (수정, contracts 리뷰 후) 스키마 2곳 + info.version

src/test/java/damso/inquiry/
├── domain/ThreatRuleTest.java           # 목록·대표 사례·긴급 겹침·정규화·빈 목록
├── domain/InquiryThreatFlagTest.java    # 긴급이면 false, 변경 메서드 없음
├── adapter/out/resource/ThreatExpressionSourceTest.java  # 빈 파일·못 읽는 파일 기동 거부
├── ThreatFlagApiTest.java               # 접수→교사 목록·상세, 외부 AI 중단·편차, 불변, 긴급 겹침, 판정 실패
├── InquiryExposureTest.java             # (수정) 학부모 응답에 표시 없음, 키 집합 동일
├── InquirySensitiveLoggingTest.java     # (수정) 걸린 표현·본문 로그 없음
└── TeacherInquiryApiTest.java           # (수정) 응답 필드 계약
src/test/resources/threat/
├── must-flag.txt                        # 팀이 정함
└── must-not-flag.txt                    # 팀이 정함 (정당한 의사 표현 필수)
```

**Structure Decision**: 단일 Spring Boot 프로젝트이고, constitution의 도메인별 헥사고날 구조를 따른다.

- 새 코드는 모두 `inquiry` 안에 둔다. F2 긴급 판정(`UrgentRule`·`UrgentExpressionSource`·`ClasspathUrgentExpressionSource`)과 같은 자리, 같은 모양이라 팀원이 하나를 알면 다른 하나도 읽을 수 있다.
- 다른 도메인 의존은 새로 생기지 않는다. `notification`·`gateway`·`school`은 고치지 않는다.
- F2 코드는 `UrgentRule.normalize`를 부르기만 하고 고치지 않는다.
- F2·F3 수정 범위는 세 곳이다: 접수 서비스에 판정 한 단계, 엔티티 컬럼 하나, 교사 조회 결과 필드 하나.

## Complexity Tracking

해당 없음 — Constitution Check에 위반이 없다.
