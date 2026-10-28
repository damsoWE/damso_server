-- 비밀번호 재설정 코드 사용자당 1건 보장 (이슈 #26)
-- password_reset.user_id 인덱스(idx_password_reset_user)를 유니크 인덱스로 다시 만든다.
-- ddl-auto=update는 이름이 같은 기존 인덱스를 유니크로 바꾸지 않으므로, 이 SQL 없이 배포하면 운영 DB에는 유니크 제약이 생기지 않는다.
-- 동시 발급으로 이미 같은 user_id 행이 여러 개 있으면 인덱스를 만들 수 없으므로, 가장 최근(created_at, id) 1건만 남기고 지운다.
--
-- 실행: 배포 전에 운영 DB에서 한 번 실행한다. 이전 버전 앱이 돌고 있어도 안전하다(사용자당 1건만 쓰는 동작은 같다).
-- 두 번 실행해도 결과가 같다(중복 정리 + DROP IF EXISTS + CREATE, 한 트랜잭션).
-- 실행 전 중복 확인: SELECT user_id, count(*) FROM password_reset GROUP BY user_id HAVING count(*) > 1;
-- 실행 후 확인: SELECT indexdef FROM pg_indexes WHERE indexname = 'idx_password_reset_user';

BEGIN;

DELETE FROM password_reset older
USING password_reset newer
WHERE older.user_id = newer.user_id
  AND (older.created_at, older.id) < (newer.created_at, newer.id);

DROP INDEX IF EXISTS idx_password_reset_user;

CREATE UNIQUE INDEX idx_password_reset_user ON password_reset (user_id);

COMMIT;
