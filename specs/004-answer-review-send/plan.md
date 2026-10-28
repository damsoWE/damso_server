# Implementation Plan: 답변 작성·점검·전송 (F4)

**Branch**: `features/f4-answer-review-send` (이슈 #7) | **Date**: 2026-10-25 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `specs/004-answer-review-send/spec.md`

## Summary

교사가 담당 학급 문의에 답변을 작성해 전송한다. 전송 전 점검(선택)은 답변 + 가명화된 문의 맥락을 함께 참고해 오해 소지 표현·근거 누락을 짚어주지만, 가명화 게이트웨이(F3)가 아직 없으므로 이번 구현은 **포트 인터페이스 + 항상 "점검 실패"를 반환하는 스텁 어댑터**로만 둔다(사용자 확인 2026-10-25). 문의는 스레드로 운영되어 학부모가 재문의하면 F2의 긴급 분류·1분 제한을 그대로 거치고, 교사는 "답변 완료"로 명시적으로 종료한다(2026-10-28부터 종료 경로는 운영에서 막힘 — 아래 "2026-10-28 추가" 절). 답변 도착 알림은 F2가 만든 FCM 아웃박스·기기 등록을 재사용하고 기본값은 켜짐(on)이다.

- **답변**: 새 `answer` 테이블(암호화 본문, 불변). 점검 여부 없이도 바로 전송 가능.
- **점검(임시 상태, F3 연동 전)**: `AnswerReviewPort`(port.out) + `UnavailableAnswerReviewAdapter`(스텁, 항상 `Optional.empty()`) → FR-012 fail-open 경로. **F3가 완성되기 전까지는 교사가 점검을 요청하든 안 하든 모든 답변이 실질적인 점검 없이 전송된다** — `reviewed` 플래그는 "점검을 요청했는지"만 기록하고 "점검이 유의미한 결과를 줬는지"를 보증하지 않는다. F3가 완성되면 `UnavailableAnswerReviewAdapter`만 실제 가명화 게이트웨이 호출로 교체한다.
- **스레드**: 기존 `inquiry` 테이블에 `thread_root_id`(nullable) 추가. 재문의는 새 `Inquiry` row(threadRootId=루트 id)로 저장되며 F2의 `SubmitInquiryService` 파이프라인(멱등키·1분 제한·긴급 판정)을 재사용한다.
- **종료**: 스레드 루트 `Inquiry`만 기존 `InquiryStage.TEACHER_CONFIRMED → ANSWERED` 전이를 교사의 명시적 호출로 수행한다(F2 data-model이 이미 이 전이를 F4 몫으로 예정해 둠).
- **알림**: `notification` 도메인에 `ParentNotificationSetting`(답변 알림 on/off, 기본 on) + `NotifyAnswerUseCase` 추가, 기존 아웃박스·FCM 발송기·기기 등록 재사용.

## Technical Context

**Language/Version**: Java 21 (Spring Boot 4.1.1, Gradle Wrapper) — F1·F2와 동일

**Primary Dependencies**: 기존 스택만 재사용한다. 새 외부 의존성 추가 없음 — `AnswerReviewPort`는 스텁이라 실제 LLM·HTTP 클라이언트를 호출하지 않는다 (사용자 확인 2026-10-25).

**Storage**: PostgreSQL(JPA, `ddl-auto=update`) — 새 테이블 `answer`, `parent_notification_setting`, 기존 `inquiry`에 컬럼 추가. Redis — F2의 `INQUIRY_SUBMIT` 제한 키를 재문의에도 그대로 재사용(새 키 없음, 클래리파이 Q2).

**Testing**: JUnit 5, Spring Boot Test + MockMvc, Testcontainers(Postgres·Redis) — F1·F2와 같은 패턴. `AnswerReviewPort`는 테스트에서 성공·실패 두 시나리오를 넣을 수 있는 가짜 포트로 교체한다.

**Target Platform**: 기존 백엔드 서버(Linux, Spring Boot 단일 애플리케이션). 클라이언트(Flutter)는 이 레포 범위 밖.

**Project Type**: 웹 API(헥사고날 모놀리스) — 기존 `inquiry`·`notification` 도메인 확장, 새 도메인 추가 없음.

**Performance Goals**: SC-002(점검 요청 90%가 10초 이내 결과/실패 응답 — 스텁은 즉시 응답하므로 자동 충족), SC-006(재문의 중 긴급은 3초 이내 교사 화면 반영, F2 로직 그대로).

**Constraints**: 원칙 I~VIII 전체 적용. 특히 II(점검 입력에 가명화 전 원문 금지 — 스텁 단계에서는 외부 호출 자체가 없어 자동 충족), III(답변 암호화·불변), V(자동 발송 없음·제안만), VI(로그 비노출), VII(담임만 답변·종료).

**Scale/Scope**: 학급 단위 트래픽, MVP 규모. 새 테이블 2개 + 컬럼 1개 추가, 새 엔드포인트 교사용 3개 + 학부모용 2개.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| 원칙 | F4 대응 | 결과 |
|---|---|---|
| I. 연락처 미수집 | 답변·알림 어디에도 연락처 필드 없음. 알림은 고정 문구만 | ✅ |
| II. 가명화 게이트웨이 경유 | 점검은 포트 인터페이스만 두고 실제 외부 호출이 없다(스텁은 항상 실패 반환, 가명화 전 원문을 어디로도 보내지 않는다). **F3 연동 전까지의 임시 상태이며, 이 기간 동안 모든 답변이 실질적인 점검 없이 전송된다**(fail-open, FR-012). F3가 실제 어댑터를 넣을 때 이 게이트웨이를 거치도록 강제한다(FR-008a) | ✅ 해당 없음(스텁, 임시) |
| III. 원문 보존·열람 감사 | `answer.content`는 `SensitiveText` + `updatable=false`, 수정·삭제 API 없음(FR-003). 재문의도 `Inquiry`라 F2와 같은 불변 규칙을 받는다 | ✅ |
| IV. 긴급 fail-open / 일반 fail-closed | 재문의의 긴급 판정은 F2의 `UrgentRule`을 그대로 재사용(FR-015). 점검 실패도 fail-open(FR-012, 답변 전송을 막지 않음) | ✅ |
| V. 제안만 한다 | 점검 결과는 하이라이트·이유·제안만 반환하고 답변을 대신 고쳐 저장하지 않는다(FR-010). 교사의 명시적 전송 없이는 어떤 경로로도 발송되지 않는다(FR-006) | ✅ |
| VI. 민감정보 비노출 | `answer.content`·점검 결과 로그·예외 금지(`SensitiveText`/커스텀 `toString`). 알림 내용은 고정 문구만 | ✅ |
| VII. UUID·건별 소유권 | 답변 작성·점검·종료는 `targets.isCurrentHomeroomTeacher`로 매 요청 검증(FR-005·022). 슈퍼 관리자 경로 없음 | ✅ |
| VIII. 안전한 기본값 | 새 시크릿 없음(스텁 단계). 추후 F3가 LLM 키를 추가하면 `SecretsValidator`에 더한다 | ✅ 해당 없음 |
| 백엔드: LLM 출력 검증 | 스텁은 항상 빈 결과만 반환해 검증 대상이 없다. 실제 어댑터가 들어올 때 서버 재검증을 추가해야 한다(F3 작업으로 이월) | ✅ 해당 없음(이월 기록) |
| 백엔드: 동시성(낙관적 락) | 스레드 종료(`ANSWERED` 전이)는 기존 `@Version` 메커니즘 그대로 사용 | ✅ |
| 백엔드: 암호화 | `answer.content` AES-256-GCM(`SensitiveText` 재사용) | ✅ |
| 아키텍처: 헥사고날·DTO 규약 | 새 타입은 모두 기존 `inquiry`·`notification` 패키지 안에 둔다. 컨트롤러는 `adapter.in.controller`, DTO는 `dto.request`·`dto.response` 별도 파일 | ✅ |
| 개발 흐름: contracts 사람 리뷰 | `contracts/answer.openapi.yaml` 작성 후 사람 리뷰 → `docs/api/openapi.yaml` 반영은 구현 단계에서 진행 | ⏳ 구현 단계에서 리뷰 예정 |
| 큰 영향 결정 확인(CLAUDE.md) | 사용자 확인 2026-10-25: ① 점검은 포트+스텁만 지금 구현 ② 재문의에 F2와 같은 1분 제한 적용 ③ 답변 알림 기본값 켜짐. DB 스키마 변경(새 테이블 2개·컬럼 1개)은 이 plan 문서로 먼저 공유함 | ✅ |

**설계 후 재점검(Phase 1 이후)**: 원칙 위반 없음. 알려진 범위 제한 — 일반(비긴급) 문의는 F3의 정제·확인 전이가 없어 `TEACHER_CONFIRMED`에 아직 도달하지 못하므로, "답변 완료" 종료(FR-020)는 F3가 그 전이를 채우기 전까지 **긴급 문의 스레드에서만** 끝까지 테스트할 수 있다. 답변 작성·전송·재문의·알림 자체는 문의 단계와 무관하게 동작하므로 일반 문의에서도 바로 쓸 수 있다. 이 제한은 F2 data-model.md가 이미 "REFINING → TEACHER_CONFIRMED는 F3 몫"이라 적어 둔 것과 같은 선이며, F4가 F3의 책임을 앞당겨 만들지 않는다. Complexity Tracking 해당 없음.

## Project Structure

### Documentation (this feature)

```text
specs/004-answer-review-send/
├── spec.md
├── plan.md              # 이 파일
├── research.md          # R1~R9 결정 근거
├── data-model.md        # 테이블 2개 추가 + inquiry 컬럼 1개, 상태 전이, 에러 코드 추가분
├── quickstart.md        # 검증 절차
├── contracts/
│   └── answer.openapi.yaml
├── checklists/
│   └── requirements.md
└── tasks.md             # /speckit-tasks에서 생성
```

### Source Code (repository root)

```text
src/main/java/damso/
├── common/
│   └── error/ErrorCode.java            # (수정) THREAD_CLOSED·INQUIRY_NOT_READY_TO_CLOSE 추가
│
├── inquiry/
│   ├── domain/
│   │   ├── Answer.java                 # (신규) 작성 교사·스레드 루트·본문(암호화·불변)·점검 사용 여부·전송 시각
│   │   ├── AnswerReviewResult.java     # (신규) 하이라이트 목록, 저장하지 않는 값 객체
│   │   └── Inquiry.java                # (수정) thread_root_id 필드, 재문의 생성 팩토리
│   ├── application/
│   │   ├── port/in/
│   │   │   ├── SendAnswerUseCase.java      # (신규) 답변 작성·전송
│   │   │   ├── RequestAnswerReviewUseCase.java  # (신규) 점검 요청
│   │   │   ├── SubmitFollowUpUseCase.java  # (신규) 재문의 제출 (F2 SubmitInquiryUseCase와 자매 인터페이스)
│   │   │   ├── CloseInquiryUseCase.java    # (신규) "답변 완료" 종료
│   │   │   ├── ParentInquiryQuery.java     # (수정) 스레드 타임라인(답변·재문의) 포함
│   │   │   └── TeacherInquiryQuery.java    # (수정) 〃
│   │   ├── port/out/
│   │   │   ├── AnswerPort.java         # (신규)
│   │   │   └── AnswerReviewPort.java   # (신규) review(content, context) → Optional<AnswerReviewResult>
│   │   └── service/
│   │       ├── SendAnswerService.java
│   │       ├── SubmitFollowUpService.java   # SubmitInquiryService와 거의 동일 — 대상은 루트의 studentId로 다시 조회(F2와 같은 규칙, research R5)
│   │       └── CloseInquiryService.java
│   └── adapter/
│       ├── in/controller/
│       │   ├── TeacherAnswerController.java   # POST .../answer-reviews, POST .../answers, POST .../close
│       │   └── ParentFollowUpController.java  # POST .../follow-ups
│       ├── out/persistence/   AnswerPersistenceAdapter, SpringDataAnswerRepository
│       └── out/review/        UnavailableAnswerReviewAdapter (항상 Optional.empty())
│
└── notification/
    ├── domain/
    │   ├── ParentNotificationSetting.java  # (신규) answer_notify_enabled, 기본 true
    │   └── NotificationKind.java            # (수정) ANSWER 추가
    ├── application/
    │   ├── port/in/NotifyAnswerUseCase.java, ParentNotificationSettingsUseCase.java   # (신규)
    │   ├── port/out/ParentNotificationSettingPort.java                                 # (신규)
    │   └── service/NotifyAnswerService.java, ParentNotificationSettingsService.java    # (신규)
    └── adapter/
        ├── in/controller/ParentNotificationSettingsController.java   # (신규)
        └── out/persistence/  (기존 패턴 재사용)
```

**Structure Decision**: 새 도메인을 만들지 않고 기존 `inquiry`·`notification` 헥사곤에 확장한다. `Answer`는 `Inquiry`와 같은 bounded context(문의-답변 대화)에 속하고, 다른 도메인 리포지토리를 직접 참조하지 않는 constitution 규칙을 지키려면 같은 패키지에 두는 것이 맞다.

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

해당 없음.

---

## 2026-10-28 추가 — 목록의 답변 여부(US6)·종료 경로 차단(US7)

**Branch**: `features/f4-close-not-used` (PR #19, 이슈 #18) | **Date**: 2026-10-28 | **근거**: `docs/decisions.md` 2026-10-27 미결 ①②, spec Clarifications Session 2026-10-28

### Summary

- **목록의 답변 여부(US6)**: 학부모·교사 문의 목록 항목에 `hasAnswer`·`lastAnsweredAt`을 더한다. 저장하지 않고, 목록에 나온 루트 id 묶음으로 `answer`에서 `max(sent_at)`을 한 번에 집계한다(교사 목록 `threadThreatFlagged`와 같은 패턴). 스키마 변경 없음(research R10·R11).
- **종료 경로 차단(US7)**: `CloseInquiryService.close()` 첫 줄에서 `damso.inquiry.close-enabled`(기본 false)가 꺼져 있으면 404 `NOT_FOUND`. 종료 전이·검증 코드는 그대로 둔다. 종료 성공을 전제로 한 테스트 5개 클래스는 설정을 켜서 FR-021·022 검증을 계속 확인하고, 꺼진 동작은 새 테스트로 확인한다(research R12·R13).
- **명세**: Contract-first로 `docs/api/openapi.yaml` 0.6.0 → 0.7.0(research R15).

### Technical Context (변경분)

- **Storage**: 스키마 변경 없음. 기존 인덱스 `idx_answer_thread(thread_root_id, sent_at)` 사용.
- **Configuration**: 새 설정 `damso.inquiry.close-enabled`(boolean, 기본 false). 시크릿 아님.
- **Testing**: 기존 `IntegrationTestSupport` 재사용. 종료 테스트 5개 클래스에 `@TestPropertySource(properties = "damso.inquiry.close-enabled=true")` — 테스트 컨텍스트 1개 증가.
- **Performance**: 목록 요청당 쿼리 1개 추가(교사는 페이지 최대 50개 루트, 학부모는 본인 문의 전체). 인덱스만으로 집계.
- **Dependencies**: 새 의존성 없음.

### Constitution Check (변경분)

| 원칙 | 대응 | 결과 |
|---|---|---|
| III. 원문 보존 | `answer`는 읽기만 한다. `SpringDataAnswerRepository`에 `find…` 읽기 메서드만 추가(`AnswerAppendOnlyTest` 유지) | ✅ |
| VI. 민감정보 비노출 | 집계 쿼리는 `thread_root_id`·`sent_at`만 읽고 본문을 읽지 않는다. 목록 응답에 본문 없음(SC-013) | ✅ |
| VII. 건별 소유권 | 집계 대상은 이미 소유권 검증을 거친 목록의 루트 id뿐(FR-030). 종료 경로는 막혀도 담당 교사 검증 코드·테스트를 유지(FR-032, PR #19 리뷰 P0) | ✅ |
| VIII. 안전한 기본값 | 종료 설정 기본값은 코드·yml 모두 false — 설정이 빠지면 막힌 쪽 | ✅ |
| 백엔드: 동시성 | 답변 저장 경로를 바꾸지 않아 루트 `@Version` 충돌이 늘지 않는다(R10 기각안 참고) | ✅ |
| 개발 흐름: Contract-first | `contracts/list-answer-status.openapi.yaml` → 사람 리뷰 → `docs/api/openapi.yaml` 0.7.0 반영 → 프론트 공지 → 코드 | ✅ contracts 사람 리뷰 완료(2026-10-28, 사용자). 공지 → 코드 순서는 tasks.md Dependencies에서 강제 |
| 큰 영향 결정 확인(CLAUDE.md) | 새 권한·외부 시스템·DB 스키마 변경 없음. 종료 차단 방식·응답 코드는 사용자 확인(2026-10-28, 클래리파이 Q2·Q3·Q4) | ✅ |

**설계 후 재점검**: 위반 없음. 남은 확인은 운영 DB의 닫힌 스레드 수(research R14, 사람 작업)뿐이다.

### Source Code (변경분)

```text
src/main/java/damso/inquiry/
├── application/
│   ├── port/in/ParentInquiryQuery.java      # (수정) InquirySummary에 hasAnswer·lastAnsweredAt
│   ├── port/in/TeacherInquiryQuery.java     # (수정) 〃
│   ├── port/out/AnswerPort.java             # (수정) 루트 id 묶음 → 마지막 답변 시각 Map
│   └── service/
│       ├── ParentInquiryService.java        # (수정) list()에서 집계 결과 합치기
│       ├── TeacherInquiryService.java       # (수정) list()·summaryOf()에서 〃
│       └── CloseInquiryService.java         # (수정) close-enabled 검사(첫 줄)
└── adapter/
    ├── in/controller/dto/response/ParentInquirySummaryResponseDTO.java   # (수정) 필드 2개
    ├── in/controller/dto/response/TeacherInquirySummaryResponseDTO.java  # (수정) 〃
    └── out/persistence/
        ├── SpringDataAnswerRepository.java  # (수정) group by 집계 읽기 메서드
        └── AnswerPersistenceAdapter.java    # (수정) 빈 입력이면 빈 Map

src/main/resources/application.yml           # (수정) damso.inquiry.close-enabled: false

src/test/java/damso/
├── inquiry/CloseDisabledApiTest.java        # (신규) 꺼진 상태 동작
├── inquiry/InquiryListAnswerStatusTest.java # (신규) 목록의 답변 여부
├── inquiry/TeacherInquiryApiTest.java       # (수정) 목록 필드 집합 검사에 2개 추가
└── (종료 테스트 5개 클래스)                   # (수정) @TestPropertySource close-enabled=true

docs/api/openapi.yaml                        # (수정) 0.7.0
```
