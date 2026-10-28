# Quickstart: 감사 로그·이력 보존 (F5) 검증 가이드

**Feature**: `specs/006-audit-log-history`

## 준비

- Docker 실행 중(Testcontainers: PostgreSQL·Redis)
- 전체 테스트: `./gradlew test` (Windows `.\gradlew.bat test`)

## 시나리오 → 확인 방법

| # | 시나리오 (spec) | 확인 | 기대 결과 |
|---|---|---|---|
| 1 | 번역본 열람 기록 (US2-6, FR-003) | 정리 완료 문의에 `GET /teacher/inquiries/{id}/refinement` | 200, `audit_log`에 `INQUIRY_REFINEMENT_VIEW_ATTEMPTED` → `INQUIRY_REFINEMENT_VIEWED` |
| 2 | 번역본 없음 (US2-7) | 정리 중·원문 확인 필요·긴급 문의에 같은 요청 | 409 `REFINEMENT_NOT_READY`, 열람 기록 0건 |
| 3 | 기록 실패 시 원문 미반환 (FR-005, SC-005) | 감사 포트가 실패하게 바꾼 뒤 `/content` | 500 `INTERNAL_ERROR`, 응답에 본문 없음 |
| 3a | 기록 실패 시 번역본은 반환 (FR-005, SC-005) | 감사 포트가 실패하게 바꾼 뒤 `/refinement` | 200 번역본, `WARN` 로그에 `inquiry=`·`action=`·`type=`만 있고 본문·번역본·제목 없음 |
| 4 | 재시도 요청 기록 (FR-001) | 원문 확인 필요 문의에 `POST .../refinement/retry` | 202, `REFINEMENT_RETRY_REQUESTED` 1건. 409로 거부된 요청은 기록 없음. 감사 저장이 실패해도 202·재시도 진행, `WARN` 로그(본문 없음)만 남음 |
| 5 | 점검 요청 기록 (FR-001) | `POST .../answer-reviews` | 200, `ANSWER_REVIEW_REQUESTED` 1건. 점검 포트가 실패해도 기록은 남고 응답은 `available=false`. 감사 저장이 실패해도 200이고 `WARN` 로그(본문 없음)만 남음 |
| 6 | 종료 기록 — 앱 미사용 경로 (FR-001) | `POST .../close` 직접 호출 | 200, `INQUIRY_CLOSED` 1건 + 단계 ANSWERED. 감사 저장이 실패해도 200·ANSWERED, `WARN` 로그(본문 없음)만 남음 |
| 7 | 슈퍼 관리자 현황 (US3, FR-014~016) | 학급 2개에 단계가 다른 문의(긴급 포함)를 만든 뒤 `GET /admin/inquiry-status` | 묶음 건수 일치, 긴급 문의는 waiting에 합쳐짐, 문의 없는 학급은 0, 응답 필드는 4개뿐 |
| 8 | 현황 권한 (US3-3) | 교사·학부모 토큰으로 같은 요청 | 403 `FORBIDDEN` |
| 9 | 타임라인 분리 (US4, FR-012) | 1~5 뒤 학부모·교사 상세 조회 | `timeline`에 단계만, 감사 항목 없음 |
| 10 | 운영자 조회문 (US1, FR-011, SC-004) | `contracts/operator-queries.md` Q1·Q2를 테스트 DB에서 실행 | 접수→단계→열람→답변 순서로 나옴, 본문·이름·이메일 컬럼 없음 |
| 11 | append-only (FR-007, SC-002) | `AuditLogAppendOnlyTest` | 리포지토리에 수정·삭제 메서드 없음 |
| 12 | 기존 DB의 enum 제약 (research R8) | 이전 `AuditAction`으로 만든 `audit_log`에 새 값 INSERT | 결과에 따라 배포 조치 결정(아래) |
| 13 | 거부된 열람 요청 (FR-002, research R3) | 다른 학급 교사·없는 문의 UUID로 `/refinement`·`/content` | 둘 다 같은 404 `NOT_FOUND`, `audit_log` 0건 |
| 14 | 외부 장애 중 긴급 기록 (FR-006, SC-006) | 외부 AI·푸시를 모두 실패시킨 뒤 긴급 문의 | 201, 단계 이력 RECEIVED·DELIVERED 있음. 단계 이력 저장을 실패시키면 문의도 저장되지 않음 |

## 배포 시 확인 (research R8)

시나리오 12는 재현됐다(tasks 구현 메모). 배포 전에 운영 DB에서 `docs/db/2026-10-27-f5-audit-action-check.sql`을 실행해 `audit_log`의 action CHECK 제약을 새 값 목록으로 다시 만든다. 두 번 실행해도 안전하고, 이전 버전 앱이 돌고 있어도 된다. **운영 DB 스키마 조치이므로 실행 전에 팀 확인을 받는다**(규칙: `docs/db/README.md`).

## 운영자 조회 실습

1. 시나리오 1~6을 거친 문의 하나의 루트 UUID를 확인한다.
2. 읽기 전용 계정으로 `psql`에 접속해 `contracts/operator-queries.md`의 Q1을 실행한다.
3. 결과가 5분 안에 나오고(SC-004) 본문이 없는지 확인한다.
