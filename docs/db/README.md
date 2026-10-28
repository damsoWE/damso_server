# 운영 DB 수동 SQL

이 레포는 마이그레이션 도구 없이 `ddl-auto=update`로 스키마를 만든다(F1 research R1, 팀 결정 2026-10-18). `update`가 반영하지 못하는 변경은 이 폴더에 SQL로 남긴다.

## 규칙

- `ddl-auto=update`는 기존 CHECK 제약을 넓히지 않는다. **기존 enum(`@Enumerated(STRING)`) 컬럼에 값을 추가하면** 그 컬럼의 CHECK 제약을 다시 만드는 SQL을 이 폴더에 남기고, PR 설명에 **"배포 전 실행: docs/db/<파일>"**을 적는다.
- 컬럼 이름 변경·삭제·타입 변경처럼 `update`가 하지 않는 변경도 같은 방식으로 남긴다.
- 파일 이름: `YYYY-MM-DD-<기능>-<내용>.sql`. 한 트랜잭션으로 묶고, 두 번 실행해도 결과가 같게 쓴다(`IF EXISTS` 등).
- 운영 DB 스키마 조치이므로 실행 전에 팀 확인을 받는다.

## 목록

| 파일 | 내용 | 실행 시점 |
|---|---|---|
| `2026-10-27-f5-audit-action-check.sql` | `audit_log.action` CHECK 제약을 `AuditAction` 9개 값으로 다시 만든다(F5) | F5 배포 전 |
| `2026-10-28-password-reset-unique-user.sql` | `password_reset.user_id` 중복 행을 정리하고 인덱스를 유니크로 다시 만든다(이슈 #26) | 이슈 #26 배포 전 |
