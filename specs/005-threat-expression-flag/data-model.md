# Data Model: 일반 문의 위협·폭언 표현 표시

**Feature**: [spec.md](./spec.md) · **Research**: [research.md](./research.md)

- 규칙은 F2·F3와 같다: `BaseEntity` 상속, 엔티티 매핑 + `ddl-auto=update`.
- 새 테이블은 없다. `inquiry`에 컬럼 **1개 추가** [사용자 승인 2026-10-26: D1].
- 판정에 걸린 표현·위치는 어떤 테이블에도 없다(FR-013, 원칙 VI).

---

## inquiry (변경)

| 필드 | 타입 | 규칙 | 근거 |
|---|---|---|---|
| `threat_flagged` | boolean, NOT NULL, `default false` | **추가**. 일반 문의를 접수할 때 위협·폭언 판정 결과. 긴급 문의는 항상 false. `updatable = false`, setter 없음 — 한 번 정해지면 바뀌지 않는다 | FR-005·005a·007, R6 |

- 값은 `Inquiry.receive(..., urgent, threatFlagged, ...)`와 재문의용 `Inquiry.followUp(..., urgent, threatFlagged, ...)`(F4)로만 들어간다. 생성자가 `urgent`이면 `threatFlagged`를 무시하고 false로 둔다(FR-005a를 엔티티에서도 보장).
- 기존 행: `default false`로 채워지고 다시 판정하지 않는다(spec Assumptions).
- 인덱스: 추가하지 않는다. 표시로 필터·정렬하지 않는다(R8).

### 바뀌지 않는 것

- `stage`·`refinement_status`·`urgent`·알림 관련 컬럼: 표시와 무관하다(FR-008·009).
- `inquiry_stage_history`·`audit_log`: 표시는 단계 이력·감사 기록에 남지 않는다. 처리 단계가 아니라 접수 때 정해지는 속성이다.

## 표현 목록 (리소스, DB 아님)

| 파일 | 내용 | 규칙 |
|---|---|---|
| `src/main/resources/threat/expressions.txt` | 위협·폭언 표현, 한 줄에 하나. `#` 주석·빈 줄 무시 | 비거나 못 읽으면 기동 거부. PR 리뷰로만 변경. 긴급 목록과 겹치는 표현은 넣지 않는다(R4) |
| `src/test/resources/threat/must-flag.txt` | 반드시 표시되어야 하는 대표 문장 | 팀이 정한다(D3) |
| `src/test/resources/threat/must-not-flag.txt` | 절대 표시되면 안 되는 대표 문장(정당한 의사 표현 포함) | 팀이 정한다(D3) |

## 판정 (순수 함수)

```
ThreatRule(expressions)          # 비어 있으면 IllegalArgumentException
  isFlagged(content):
    content가 null·공백뿐 → false
    NFKC·소문자로 맞춘 본문(공백 유지)에서 표현 하나라도 맞으면 → true
      맞음 = 어절 시작(앞 글자가 글자·숫자가 아님)에서 시작하고, 표현의 공백 자리에서만 공백 0개 이상 허용
    그 밖 → false
normalizeExpression = NFKC, 소문자, 공백 한 칸으로 모음(표현 안의 공백 자리는 남김)   # research R2 변경
```

## 접수 흐름에서의 위치

```
① 입력 검증 → ② 중복 제출 확인 → ③ 대상 확인
→ ④ urgent = UrgentRule.isUrgent(content)
→ ⑤ threatFlagged = urgent ? false : (ThreatRule.isFlagged(content), 예외면 false)   # R7
→ ⑥ 일반이면 1분 제한 → ⑦ 저장(Inquiry.receive)
```

- 중복 제출 재전송(같은 Idempotency-Key)은 ①~③에서 저장된 문의를 돌려주므로 다시 판정하지 않는다.

## 응답 모델 (변경)

| 모델 | 변경 | 근거 |
|---|---|---|
| `TeacherInquiryQuery.InquirySummary` / `TeacherInquirySummaryResponseDTO` | `boolean threatFlagged` 추가 | FR-010, R8 |
| `TeacherInquiryQuery.InquiryDetail` / `TeacherInquiryDetailResponseDTO` | `boolean threatFlagged` 추가 | FR-010, R8 |
| `TeacherInquiryQuery.FollowUpView` / `TeacherFollowUpViewResponseDTO`(F4) | `boolean threatFlagged` 추가 | FR-005c |
| 학부모 응답(`ParentInquirySummary`·`ParentInquiryDetail`·`InquirySubmitted`) | **변경 없음** | FR-012, R9 |
| 교사 알림 문구 | **변경 없음** | FR-014 |

## 에러 코드·설정

- 새 `ErrorCode` 없음.
- 설정 추가: `damso.inquiry.threat-expressions`(기본 `classpath:threat/expressions.txt`). 시크릿이 아니므로 환경변수 필수 항목이 아니다.
