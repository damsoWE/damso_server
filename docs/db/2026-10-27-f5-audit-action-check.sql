-- F5 감사 로그·이력 보존 (specs/006-audit-log-history, research R8)
-- audit_log.action의 허용값 CHECK 제약을 AuditAction 값 9개로 다시 만든다.
-- ddl-auto=update는 기존 CHECK 제약을 넓히지 않으므로, 이 SQL 없이 배포하면 새 감사 기록 INSERT가 실패한다.
--
-- 실행: F5 배포 전에 운영 DB에서 한 번 실행한다. 새 목록은 기존 3개 값을 포함하므로 이전 버전 앱이 돌고 있어도 안전하다.
-- 두 번 실행해도 결과가 같다(DROP IF EXISTS + ADD, 한 트랜잭션).
-- 확인: SELECT pg_get_constraintdef(oid) FROM pg_constraint WHERE conname = 'audit_log_action_check';

BEGIN;

ALTER TABLE audit_log DROP CONSTRAINT IF EXISTS audit_log_action_check;

ALTER TABLE audit_log ADD CONSTRAINT audit_log_action_check CHECK (action IN (
    'INQUIRY_CONTENT_VIEW_ATTEMPTED',
    'INQUIRY_CONTENT_VIEWED',
    'INQUIRY_CONTENT_VIEW_FAILED',
    'INQUIRY_REFINEMENT_VIEW_ATTEMPTED',
    'INQUIRY_REFINEMENT_VIEWED',
    'INQUIRY_REFINEMENT_VIEW_FAILED',
    'REFINEMENT_RETRY_REQUESTED',
    'ANSWER_REVIEW_REQUESTED',
    'INQUIRY_CLOSED'
));

COMMIT;
