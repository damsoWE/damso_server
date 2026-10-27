# 운영자 조회문 (F5, spec FR-010·011, research R7)

앱에는 감사 기록 조회 API가 없다. 분쟁이 생기면 서버 운영자가 **읽기 전용 DB 계정**으로 아래 조회문을 `psql`에서 실행한다.

- 값은 `\set`으로 넣고 `:'변수'`로만 참조한다. 조회문에 값을 문자열로 이어 붙이지 않는다(백엔드 보안 요건 — SQL).
- 결과에는 **식별자·종류·시각만** 나온다. `inquiry.content`·`inquiry.title`·`answer.content`·`inquiry_refinement.*` 같은 본문 컬럼은 어떤 조회문에도 쓰지 않는다(원칙 VI).
- 이름·이메일이 필요하면 조회문을 바꾸지 말고, 학교가 오프라인에서 계정 UUID로 당사자를 확인한다(F1 결정: 감사 기록에 개인정보를 넣지 않는다).
- 이 문서의 조회문은 `OperatorQueriesTest`(tasks)가 Testcontainers에서 그대로 실행해 검증한다. 스키마가 바뀌면 이 문서를 함께 고친다.

## Q1. 스레드 하나의 전 과정 (FR-011 (1))

```sql
\set thread_root_id '00000000-0000-0000-0000-000000000000'

WITH msg AS (
  SELECT id, thread_root_id, parent_id, submitted_at
  FROM inquiry
  WHERE id = :'thread_root_id'::uuid OR thread_root_id = :'thread_root_id'::uuid
)
SELECT * FROM (
  -- 접수 (학부모)
  SELECT m.submitted_at AS occurred_at, 0 AS seq,
         'PARENT' AS actor_kind, m.parent_id AS actor_id,
         CASE WHEN m.thread_root_id IS NULL THEN 'INQUIRY_SUBMITTED' ELSE 'FOLLOW_UP_SUBMITTED' END AS event,
         m.id AS message_id, NULL::text AS detail
  FROM msg m
  UNION ALL
  -- 처리 단계 (시스템)
  SELECT h.occurred_at,
         CASE h.stage WHEN 'RECEIVED' THEN 1 WHEN 'DELIVERED' THEN 2 WHEN 'REFINING' THEN 6
                      WHEN 'TEACHER_CONFIRMED' THEN 8 WHEN 'ANSWERED' THEN 11 END,
         'SYSTEM', NULL::uuid, 'STAGE_' || h.stage, h.inquiry_id, NULL
  FROM inquiry_stage_history h JOIN msg m ON m.id = h.inquiry_id
  UNION ALL
  -- 정리·위기 분류 시도 (시스템). 같은 시각이면 AUTO → AUTO_RETRY → TEACHER
  SELECT a.occurred_at,
         CASE a.trigger WHEN 'AUTO' THEN 3 WHEN 'AUTO_RETRY' THEN 4 ELSE 5 END,
         'SYSTEM', NULL::uuid,
         j.kind || '_ATTEMPT', j.inquiry_id, a.trigger || ':' || a.outcome
  FROM refinement_attempt a
  JOIN refinement_job j ON j.id = a.job_id
  JOIN msg m ON m.id = j.inquiry_id
  UNION ALL
  -- 사람의 행위 (교사)
  SELECT l.occurred_at,
         CASE WHEN l.action LIKE '%\_ATTEMPTED' ESCAPE '\' THEN 7
              WHEN l.action = 'INQUIRY_CLOSED' THEN 12
              ELSE 9 END,
         'ACCOUNT', l.actor_id, l.action, l.target_id, NULL
  FROM audit_log l JOIN msg m ON m.id = l.target_id
  WHERE l.target_type = 'INQUIRY'
  UNION ALL
  -- 답변 전송 (교사)
  SELECT s.sent_at, 10, 'ACCOUNT', s.teacher_id,
         'ANSWER_SENT', s.id, CASE WHEN s.reviewed THEN 'reviewed' ELSE 'not_reviewed' END
  FROM answer s
  WHERE s.thread_root_id = :'thread_root_id'::uuid
) t
ORDER BY occurred_at, seq, message_id;
```

`seq`는 **같은 시각에 남은 기록끼리의 순서**만 정한다(research R5). 시각이 다르면 시각 순서가 먼저다.

| seq | 기록 | 같은 시각이 되는 경우 |
|---|---|---|
| 0 | 접수 (`INQUIRY_SUBMITTED`·`FOLLOW_UP_SUBMITTED`) | 접수 트랜잭션이 같은 `now`로 RECEIVED(·DELIVERED)를 남긴다 |
| 1·2 | `STAGE_RECEIVED` · `STAGE_DELIVERED` | 긴급 접수 |
| 3·4·5 | 정리 시도 `AUTO` · `AUTO_RETRY` · `TEACHER` | 자동 정리는 시도 여러 건과 REFINING을 같은 `now`로 남긴다 |
| 6 | `STAGE_REFINING` | 위와 같음 — 시도가 끝난 뒤의 결과 |
| 7 | `*_ATTEMPTED` (열람 시도) | 첫 열람: 시도 → 확인 전이 → 완료 |
| 8 | `STAGE_TEACHER_CONFIRMED` | 위와 같음 |
| 9 | `*_VIEWED` · `*_VIEW_FAILED` · `REFINEMENT_RETRY_REQUESTED` · `ANSWER_REVIEW_REQUESTED` | |
| 10 | `ANSWER_SENT` | |
| 11·12 | `STAGE_ANSWERED` → `INQUIRY_CLOSED` | 종료 API: 단계 이력 뒤에 감사 기록 |

결과 컬럼: `occurred_at` · `seq`(같은 시각의 논리 순서, research R5) · `actor_kind`(PARENT/ACCOUNT/SYSTEM) · `actor_id`(계정 UUID, 시스템은 NULL) · `event` · `message_id`(문의·재문의·답변 UUID) · `detail`(결과 코드만).

## Q2. 계정 하나가 한 행위 (FR-011 (2))

```sql
\set account_id '00000000-0000-0000-0000-000000000000'

SELECT * FROM (
  SELECT l.occurred_at, l.action AS event, l.target_id AS message_id
  FROM audit_log l WHERE l.actor_id = :'account_id'::uuid
  UNION ALL
  SELECT i.submitted_at,
         CASE WHEN i.thread_root_id IS NULL THEN 'INQUIRY_SUBMITTED' ELSE 'FOLLOW_UP_SUBMITTED' END,
         i.id
  FROM inquiry i WHERE i.parent_id = :'account_id'::uuid
  UNION ALL
  SELECT s.sent_at, 'ANSWER_SENT', s.id
  FROM answer s WHERE s.teacher_id = :'account_id'::uuid
) t
ORDER BY occurred_at, message_id;
```

탈퇴해 비식별화된 계정도 UUID가 그대로이므로 같은 조회문으로 나온다(spec FR-008).

## 결과 해석 메모

- `STAGE_ANSWERED`·`INQUIRY_CLOSED`는 종료 API가 호출된 경우에만 나온다. 앱은 종료 기능을 쓰지 않는다(`docs/decisions.md` 2026-10-27).
- `*_VIEW_ATTEMPTED` 뒤에 `*_VIEWED`가 없고 `*_VIEW_FAILED`가 있으면, 열람을 시도했지만 내용이 나가지 않은 것이다.
- 담당 학급이 아니거나 없는 문의라서 거부된(404) 열람 요청은 기록되지 않는다(spec FR-002). 그래서 이 조회문에 다른 학급 교사의 열람 시도가 나오는 일은 없다. 담임이 바뀐 경우 이전 담임의 과거 기록은 그대로 나온다.
- `event`는 `REFINE_ATTEMPT`(3요소 정리) 또는 `CLASSIFY_CRISIS_ATTEMPT`(긴급 위기 종류 분류)다. `REFINE_ATTEMPT`의 `detail`이 `TEACHER:SUCCESS`면 교사 재시도로 정리가 끝난 것이다. 바로 앞에 `REFINEMENT_RETRY_REQUESTED`(누가 요청했는지)가 있다.
