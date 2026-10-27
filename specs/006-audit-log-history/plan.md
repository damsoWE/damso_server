# Implementation Plan: 감사 로그·이력 보존 (F5)

**Branch**: `features/f5-audit-log` (이슈 #20) | **Date**: 2026-10-27 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `specs/006-audit-log-history/spec.md`

## Summary

F2가 만든 뼈대(`audit_log` 원문 열람 기록, `inquiry_stage_history` 타임라인)를 넓힌다. **새 테이블·컬럼은 만들지 않는다.** 시스템 행위(접수 단계·긴급 전달·정리 시도)는 이미 F2~F4의 불변 기록에 있으므로 다시 저장하지 않고, 행위자가 빠져 있던 **사람의 행위 4가지**(번역본 열람, 재시도 요청, 점검 요청, 종료)만 `audit_log`에 더한다(research R1). 운영자는 문서로 정한 조회문으로 이 기록들을 시간순으로 엮어 본다(R7). 슈퍼 관리자에게는 학급별 문의 현황을 긴급 여부가 드러나지 않는 3개 묶음으로 준다(R6).

- **F5 신규 코드**: `AuditAction` 값 6개, 관리자 현황 API 1개, 운영자 조회문 문서 + 검증 테스트.
- **F3 코드 변경**: `ViewRefinementService`(번역본 열람 기록), `RetryRefinementService`(재시도 요청 기록).
- **F4 코드 변경**: `RequestAnswerReviewService`(점검 요청 기록, 읽기 전용 트랜잭션 해제), `CloseInquiryService`(종료 기록 — 앱 미사용 경로).
- **종료 미사용 반영**: 종료 API 동작은 그대로 두고 기록만 더한다. 앱의 "답변 완료"는 "답변을 보냈다"는 뜻이므로 관리자 현황의 `answered`도 답변 전송 여부로 센다(R2·R6, `docs/decisions.md` 2026-10-27, PR #19 OPEN).
- **PR 순서**: 위 결정 행은 PR #19에만 있다. **PR #19 merge → 이 브랜치에 main 반영 → F5 PR** 순서로 올린다(tasks "PR 순서").
- **거부된 열람 요청**: 다른 학급·없는 문의(404)는 원문·번역본 모두 기록하지 않는다. F2 원문 열람과 같은 규칙이며 존재 여부가 기록·응답으로 갈리지 않게 한다(R3).

## Technical Context

**Language/Version**: Java 21 (Spring Boot 4.1, Gradle Wrapper) — 기존과 동일

**Primary Dependencies**: 기존 스택만. 새 의존성 없음

**Storage**: PostgreSQL(JPA, `ddl-auto=update`). **새 테이블·컬럼 없음.** `audit_log.action`에 새 enum 값이 들어간다 — 기존 DB에 CHECK 제약이 있으면 배포 조치가 필요하다(**확인 대상**, research R8)

**Testing**: JUnit 5, Spring Boot Test + MockMvc, Testcontainers(PostgreSQL·Redis) — 기존 패턴

**Target Platform**: 기존 백엔드 서버(Linux, 단일 Spring Boot 애플리케이션)

**Project Type**: 웹 API(헥사고날 모놀리스) — 기존 `audit`·`inquiry` 도메인 확장, 새 도메인 없음

**Performance Goals**: SC-004(운영자가 스레드 기록을 5분 안에 확인) — 기존 인덱스(`idx_audit_target`, `idx_stage_history_inquiry`, `idx_answer_thread`, `idx_inquiry_thread_root`)로 충족. 관리자 현황은 학급 수 × 스레드 수 규모의 집계 1회

**Constraints**: 원칙 III(append-only), VI(기록·현황에 본문·개인정보 없음), VII(관리자 메타데이터만), 백엔드 SQL 요건(운영자 조회문도 값 바인딩)

**Scale/Scope**: 새 엔드포인트 1개(관리자), 설명만 바뀌는 엔드포인트 4개, 서비스 수정 4개(F3 2 · F4 2)

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| 원칙 | F5 대응 | 결과 |
|---|---|---|
| I. 연락처 미수집 | 새 필드는 UUID와 건수뿐. 연락처 없음 | ✅ |
| II. 가명화 게이트웨이 | 외부 AI 호출 경로를 추가·변경하지 않음. 점검 요청 기록은 게이트웨이 호출 **전에** 로컬 DB에만 남김 | ✅ 해당 없음 |
| III. 원문 보존·열람 감사 | 원문 열람 기록 유지, 번역본 열람까지 확장. `audit_log`는 계속 save·조회만(`AuditLogAppendOnlyTest`). 수정·삭제 API 없음 | ✅ |
| IV. 긴급 fail-open | 긴급 접수·전달 경로를 바꾸지 않음. 새 기록은 모두 교사 요청 경로에만 있음 | ✅ |
| V. 제안만 한다 | 점검 결과·전송 동작 변경 없음. 점검 요청 기록이 실패해도 전송은 별개 경로 | ✅ |
| VI. 민감정보 비노출 | 기록은 식별자·종류·시각만. 운영자 조회문은 본문 컬럼을 읽지 않음. 관리자 현황에 제목·이름 없음. 기록 실패 시 에러는 `INTERNAL_ERROR` 고정 문구 | ✅ |
| VII. UUID·건별 권한 | 관리자 현황은 `/api/v1/admin/**` = `SUPER_ADMIN`(SecurityConfig), 학급 UUID만 노출. 교사 경로는 기존 담임 검증(404) 그대로 | ✅ |
| VIII. 안전한 기본값 | 새 시크릿 없음 | ✅ 해당 없음 |
| 백엔드: SQL | 앱 쿼리는 파생 쿼리·JPQL 바인딩. 운영자 조회문은 `psql` 변수(`:'var'`)로만 값 전달 | ✅ |
| 백엔드: 동시성 | 새 상태 전이 없음. 기존 확인 전이의 낙관적 락 처리 그대로 | ✅ |
| 아키텍처 | 관리자 현황은 `inquiry` 도메인 `port.in`에 두고, 학급 목록은 school의 `ClassroomAdminUseCase`(port.in)로 받는다. 감사는 `AuditLogUseCase`(port.in)로만 | ✅ |
| contracts 사람 리뷰 | `contracts/audit.openapi.yaml`·`operator-queries.md` 리뷰 후 `docs/api/openapi.yaml` 통합(0.7.0) | ⏳ 이 plan 리뷰에서 진행 |
| 큰 영향 결정 확인(CLAUDE.md) | 새 테이블·컬럼 없음. **enum CHECK 제약 배포 조치 가능성**은 R8에서 확인 후 사용자 확인 | ⏳ 확인 대상 |

**설계 후 재점검(Phase 1 이후)**: 위반 없음. Complexity Tracking 해당 없음.

## 변경 범위 — F5 신규 / F3 변경 / F4 변경 (팀 공유용)

### A. F5 신규 코드

| 파일 | 내용 |
|---|---|
| `audit/domain/AuditAction.java` | 값 6개 추가: `INQUIRY_REFINEMENT_VIEW_ATTEMPTED`·`_VIEWED`·`_VIEW_FAILED`, `REFINEMENT_RETRY_REQUESTED`, `ANSWER_REVIEW_REQUESTED`, `INQUIRY_CLOSED`. 클래스 주석 갱신 |
| `inquiry/application/port/in/InquiryStatusQuery.java` | 신규. `List<ClassroomInquiryStatus> statusByClassroom()` |
| `inquiry/application/service/InquiryStatusService.java` | 신규. school `ClassroomAdminUseCase.listClassrooms()` 순서대로, 학급마다 waiting·confirmed·answered 집계(R6). 읽기 전용 트랜잭션 |
| `inquiry/application/port/out/InquiryPort.java` + 어댑터·리포지토리 | 집계 쿼리 1개 추가(루트 문의만, 답변 존재 여부 포함, JPQL 바인딩). 본문 컬럼 미조회 |
| `inquiry/adapter/in/controller/AdminInquiryController.java` | 신규. `GET /api/v1/admin/inquiry-status` |
| `inquiry/adapter/in/controller/dto/response/InquiryStatusSummaryResponseDTO.java`, `ClassroomInquiryStatusResponseDTO.java` | 신규 DTO (별도 파일, 헌법 DTO 규칙) |
| `specs/006-audit-log-history/contracts/operator-queries.md` | 운영자 조회문 (문서) |
| 테스트 | `OperatorQueriesTest`(조회문 실행·본문 컬럼 부재), `UrgentRecordUnderOutageTest`(FR-006·SC-006, 외부 장애 중 긴급 기록·기록 실패 시 롤백), `AdminInquiryStatusApiTest`(묶음·0건 학급·긴급 은닉·403), `AuditEnumConstraintTest`(R8) |
| `docs/api/openapi.yaml` | 0.7.0: 관리자 현황 경로·스키마 추가, 아래 B·C 경로 설명 갱신 |

### B. F3 코드 변경 (가명화·정제 파이프라인 담당 공유)

| 파일 | 지금 | 바뀐 뒤 |
|---|---|---|
| `inquiry/application/service/ViewRefinementService.java` | 번역본 열람은 감사 대상이 아님(클래스 주석·openapi 설명에 명시). 담임 확인 → 준비 확인 → 첫 열람 확인 전이 → 반환 | `AuditLogUseCase` 주입. 담임 확인(404, **기록 없음**) → 준비 확인(409, **기록 없음**) → `INQUIRY_REFINEMENT_VIEW_ATTEMPTED` → 확인 전이(낙관적 락 충돌은 기존처럼 무시) → `INQUIRY_REFINEMENT_VIEWED` → 반환. 확인 전이가 다른 이유로 실패하면 `_VIEW_FAILED`를 남기고 전파. `ViewInquiryContentService`와 같은 구조(R3) |
| `inquiry/application/service/RetryRefinementService.java` | 작업 재개·`teacher_retry_used` 설정. 누가 요청했는지 기록 없음 | 재시도가 받아들여진 같은 트랜잭션에서 `REFINEMENT_RETRY_REQUESTED`(교사, 대상 문의). 409로 거부된 요청은 기록하지 않음 |
| 바뀌지 않는 F3 코드 | `RefinementRunner`·`RefinementAttempt`·`InquiryRefinement`·가명화 게이트웨이 | 그대로. 정리 시도 기록은 운영자 조회문이 읽기만 한다 |
| 영향받는 F3 테스트 | `TeacherRefinementApiTest.firstTranslationViewConfirmsOnceAndIsNotAudited` — 지금 `audit_log` 0건을 단언(120행) | **이 단언이 F5와 정면으로 충돌한다.** 이름·단언을 "첫 열람에 ATTEMPTED·VIEWED 2건"으로 바꾼다. 재시도 테스트에 `REFINEMENT_RETRY_REQUESTED` 단언 추가(`TransactionTemplate` 안에서 기록하므로 동시 요청 409 쪽은 롤백되어 기록 없음) |
| 문서 | `specs/003-…`·openapi `/refinement` 설명의 "번역본 열람은 감사 기록 대상이 아니다" | openapi는 0.7.0에서 갱신. `specs/003`은 고치지 않고 F5 spec이 대체한다고 이 plan에 남긴다 |

### C. F4 코드 변경 (답변 작성·점검·전송 담당 공유)

| 파일 | 지금 | 바뀐 뒤 |
|---|---|---|
| `inquiry/application/service/RequestAnswerReviewService.java` | `@Transactional(readOnly = true)`. 점검 포트 실패는 삼켜서 fail-open | 쓰기 트랜잭션으로 바꾸고, 담임 확인 뒤 **점검 포트 호출 전에** `ANSWER_REVIEW_REQUESTED`(교사, 스레드 루트). 기록이 실패하면 500(점검만 실패, 전송은 영향 없음). 포트 실패 fail-open은 그대로(R4) |
| `inquiry/application/service/CloseInquiryService.java` | 종료 시 단계 ANSWERED만 남김(행위자 없음) | 같은 트랜잭션에서 `INQUIRY_CLOSED`(교사, 스레드 루트). 종료 API·FR-021·022 동작은 그대로. **앱은 이 경로를 쓰지 않는다**(decisions.md 2026-10-27) |
| 바뀌지 않는 F4 코드 | `SendAnswerService`·`Answer`·`SubmitFollowUpService` | 그대로. 답변 전송·재문의 접수는 `answer`·`inquiry` 행 자체가 기록이므로 감사 호출을 더하지 않음(R1) |
| 영향받는 F4 테스트 | `AnswerReviewApiTest`, `AnswerReviewFailOpenTest`, `CloseInquiryApiTest` | 기록 단언 추가. fail-open 테스트는 "포트 실패 시에도 기록은 1건"으로 보강 |

## Project Structure

### Documentation (this feature)

```text
specs/006-audit-log-history/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   ├── audit.openapi.yaml       # 관리자 현황(신규) + /refinement 설명 변경
│   └── operator-queries.md      # 운영자 조회문 (앱 API 없음)
├── checklists/requirements.md
└── tasks.md                     # /speckit-tasks
```

### Source Code (repository root)

```text
src/main/java/damso/
├── audit/domain/AuditAction.java                          # [F5] 값 추가
└── inquiry/
    ├── application/
    │   ├── port/in/InquiryStatusQuery.java                # [F5] 신규
    │   ├── port/out/InquiryPort.java                      # [F5] 집계 메서드 추가
    │   └── service/
    │       ├── InquiryStatusService.java                  # [F5] 신규
    │       ├── ViewRefinementService.java                 # [F3 변경]
    │       ├── RetryRefinementService.java                # [F3 변경]
    │       ├── RequestAnswerReviewService.java            # [F4 변경]
    │       └── CloseInquiryService.java                   # [F4 변경]
    └── adapter/
        ├── in/controller/AdminInquiryController.java      # [F5] 신규
        ├── in/controller/dto/response/…StatusResponseDTO  # [F5] 신규
        └── out/persistence/…                              # [F5] 집계 쿼리

src/test/java/damso/
├── audit/AuditEnumConstraintTest.java                     # [F5] R8
├── audit/OperatorQueriesTest.java                         # [F5] R7
├── inquiry/UrgentRecordUnderOutageTest.java               # [F5] FR-006·SC-006
└── inquiry/AdminInquiryStatusApiTest.java                 # [F5]
    (+ F3·F4 기존 테스트 보강)
```

**Structure Decision**: 새 도메인 없이 `audit`(값)·`inquiry`(현황·서비스 수정)만 바꾼다. 관리자 현황을 school의 `AdminController`가 아니라 inquiry 도메인에 두는 이유는, 집계 대상이 문의 데이터이고 다른 도메인 리포지토리를 직접 부르지 않는다는 헌법 규칙 때문이다(학급 목록만 school `port.in`으로 받는다).

## Complexity Tracking

해당 없음.
