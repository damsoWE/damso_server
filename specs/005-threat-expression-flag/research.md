# Research: 일반 문의 위협·폭언 표현 표시

**Feature**: [spec.md](./spec.md) · **Date**: 2026-10-26 · **기반**: [002 research R2](../002-inquiry-intake-urgent/research.md)(긴급 판정), [003 research](../003-pseudonymization-pipeline/research.md)(정리 상태·교사 화면)

각 항목은 Decision / Rationale / Alternatives considered 형식이다. CLAUDE.md "큰 영향 결정은 확인 후 진행"(DB 스키마·앱 계약) 대상은 **[확인 필요]**로 표시했다. 팀 확인을 받으면 **[팀 결정 날짜]**로 바꾼다.

---

## R1. 도메인 위치 — `inquiry` 안에 둔다

- **Decision**: 판정 규칙(`ThreatRule`), 표현 목록 포트(`ThreatExpressionSource`)와 리소스 어댑터(`ClasspathThreatExpressionSource`)를 모두 `inquiry`에 둔다. F2의 `UrgentRule`·`UrgentExpressionSource`·`ClasspathUrgentExpressionSource`와 같은 자리, 같은 모양이다.
- **Rationale**: 표시는 접수 때 한 번 정해져 문의에 붙는 값이고, 접수 트랜잭션(`SubmitInquiryService`) 안에서 계산된다. 새 도메인을 만들면 접수가 다른 도메인의 `port.in`을 하나 더 불러야 할 뿐 얻는 게 없다.
- **Alternatives considered**:
  - `gateway`에 두기: 게이트웨이는 외부 AI로 나가는 길이다. 이 판정은 외부 AI와 무관해야 하므로(원칙 IV, FR-005) 거기 두면 오해를 부른다.
  - 새 도메인 `moderation`: 쓰는 곳이 접수 하나뿐이라 이르다. F4 답변 점검이 같은 표현 목록을 쓰게 되면 그때 옮긴다.

## R2. 판정 방식 — 어절 시작 + 표현 안의 공백 자리만 허용 (코드 리뷰 뒤 변경)

- **변경 (2026-10-26, PR #13 코드 리뷰)**: 처음에는 F2와 같이 "공백을 모두 지우고 포함 검사"로 정했다(아래 원래 결정). 그러나 이 방식은 낱말 경계를 넘어 오탐을 냈다 — "아이 고모가 지금"의 '모가지', "질병 신고서"의 '병신', "우리 애 새끼손가락"의 '애새끼'. 표시는 바꿀 수 없어(R6) 오탐이 그대로 남으므로, 오탐 최소화(Clarification)에 맞게 바꾼다.
  - **새 결정**: 본문은 NFKC·소문자로만 맞추고 공백은 남긴다. 표현은 **어절 시작**(앞 글자가 글자·숫자가 아님 — 공백·문장부호 뒤 포함)에서 시작할 때만 맞고, 띄어쓰기 차이는 **표현에 공백을 둔 자리에서만** 허용한다("가만 두지 않겠" ↔ "가만두지 않겠"·"가만
두지 않겠"). 표현의 글자는 정규식 문자로 해석하지 않는다(`Pattern.quote`).
  - FR-006은 "표현에 공백을 둔 자리의 띄어쓰기 변형"으로 좁힌다. 글자마다 띄어 쓰는 우회("가 만 두 지")는 잡지 않는다(spec Edge Cases의 알려진 한계).
  - 같은 말이 피해 호소·걱정·안전 부탁에도 쓰이는 표현(가만 안 둬, 밤길 조심, 두고 보자, 후회하게 만들, 옷 벗게, 집 주소 알아, 그러고도 선생)은 판정 방식으로 막을 수 없으므로 목록에서 위협의 의지를 드러내는 활용형으로 좁혔고, 리뷰에서 찾은 문장을 `must-not-flag.txt`에 회귀 사례로 넣었다.
  - 긴급 판정(F2)은 놓침 최소화가 목표라 공백을 지우는 방식을 그대로 둔다. 두 규칙은 목적이 달라 정규화를 공유하지 않는다.
- **원래 결정 (변경 전)**: `ThreatRule`은 순수 함수다. 본문과 표현을 **F2와 같은 방식**(NFKC 전각→반각, 소문자, 공백·줄바꿈 제거)으로 정규화한 뒤, 목록의 표현이 하나라도 포함되면 표시한다. 정규화는 `UrgentRule.normalize`를 그대로 부른다(F2 코드는 고치지 않는다).
- **Rationale**:
  - 원칙 IV·FR-005: 외부 호출·AI·Redis 없이 같은 입력에 같은 결과(SC-003). 외부 AI가 꺼져도 동작한다(SC-002).
  - FR-006: 띄어쓰기 변형("가만두지 않"/"가만 두지 않")을 같이 잡는다.
  - 두 규칙이 정규화를 같이 쓰므로 "긴급 표현과 겹치는지"를 같은 기준으로 점검할 수 있다(R4).
- **오탐 최소화(Clarification)를 지키는 방법**: 규칙은 단순한 포함 검사이므로, 오탐은 **목록을 고르는 단계에서** 줄인다.
  - 짧은 어간(예: "미친")처럼 다른 낱말 안에 흔히 들어가는 표현은 넣지 않는다. 공백을 지우고 보므로 낱말 경계가 없다는 점을 목록 작성자가 알고 있어야 한다.
  - "표시하지 않을 대표 사례"를 테스트로 묶어, 목록에 표현을 더했을 때 정당한 문장이 걸리면 바로 실패하게 한다(R5).
- **Alternatives considered**:
  - 예외 표현 목록(걸려도 이 문맥이면 빼기, 예: "미친 듯이"): 규칙과 목록이 두 겹이 되어 팀이 리뷰하기 어려워진다. 대표 사례에서 오탐이 실제로 나오면 그때 더한다.
  - 정규식: 표현력은 크지만 팀원이 리뷰하기 어렵고, 잘못 쓴 패턴이 정당한 문장을 넓게 잡을 수 있다.
  - 형태소 분석기: 새 의존성이고 결과가 사전 버전에 따라 달라진다. MVP에 과하다.
  - 외부 AI 판정: 이 기능의 목적(외부 AI 편차와 무관)에 맞지 않는다.

## R3. 표현 목록 파일 — `threat/expressions.txt`, 비면 기동 거부

- **Decision**: `src/main/resources/threat/expressions.txt`에 표현을 한 줄씩 둔다. 빈 줄과 `#` 주석은 무시한다. 설정 키 `damso.inquiry.threat-expressions`(기본 `classpath:threat/expressions.txt`)로 바꿀 수 있다. 파일을 못 읽거나 표현이 하나도 없으면 기동을 거부한다.
- **Rationale**: F2 긴급 목록과 똑같이 운영한다(FR-001, spec Edge Cases). 목록이 빈 채로 뜨면 표시가 조용히 꺼진다. 변경은 PR 리뷰·배포로만 하고 앱에서 편집하지 않는다.
- **Alternatives considered**:
  - DB 테이블: 앱 편집 경로가 생기고 변경 이력이 리뷰 밖으로 나간다. FR-001에 어긋난다.
  - 긴급 목록 파일에 섹션으로 같이 두기: 두 판정이 서로 다른 목적(긴급은 놓침 최소화, 위협은 오탐 최소화)이라 같은 파일에 두면 한쪽 기준으로 다른 쪽을 고치게 된다.

## R4. 긴급과의 관계 — 긴급이면 판정하지 않는다

- **Decision**: 접수 순서는 F2 그대로 ① 입력 검증 ② 중복 제출 확인 ③ 대상 확인 ④ **긴급 판정**이고, 긴급이 아닐 때만 ⑤ **위협·폭언 판정**을 한다. 긴급 문의의 표시는 항상 false다. 판정은 긴급 판정 결과를 바꾸지 않는다(FR-005a).
- **알아 둘 점 — 이미 긴급으로 가는 표현**: F2 긴급 목록(`urgent/expressions.txt`)의 "위협·흉기" 묶음에 `협박`·`위협`·`죽이겠`·`죽여버리`·`칼을`·`흉기`가, "괴롭힘" 묶음에 `학교폭력`이 들어 있다. 이 표현이 든 문의는 지금도 긴급이 되므로 위협·폭언 표시 대상이 아니다. 따라서 이 기능의 목록은 **긴급 목록에 없는 폭언·비폭력 위협**(모욕, 신상 위협, 직위 위협 등)이 중심이 된다.
  - 같은 표현을 두 목록에 다 넣으면 위협 쪽 항목은 영원히 쓰이지 않는다. 그래서 테스트로 "위협 목록의 각 표현은 긴급 판정에 걸리지 않는다"를 확인해, 쓰이지 않는 항목이 목록에 남지 않게 한다(R5).
- **Rationale**: Clarification "긴급만 처리". 긴급은 즉시 전달·재알림으로 이미 가장 앞에 있다.
- **Alternatives considered**: 긴급 문의에도 표시 — 팀이 고르지 않았다(spec Clarifications).

## R5. 대표 사례와 테스트 — 표시할 문장 / 표시하지 않을 문장

- **Decision**: F2의 `must-be-urgent.txt` 방식을 따른다.
  - `src/test/resources/threat/must-flag.txt`: 반드시 표시되어야 하는 문장.
  - `src/test/resources/threat/must-not-flag.txt`: 절대 표시되면 안 되는 문장. 교육청 민원·면담 요청·교장 상담·정식 이의 제기 같은 **정당한 의사 표현**이 반드시 들어간다(FR-003).
  - `ThreatRuleTest`가 확인하는 것:
    1. 목록의 모든 표현이 표시된다.
    2. `must-flag.txt`의 모든 문장이 표시된다.
    3. `must-not-flag.txt`의 어떤 문장도 표시되지 않는다.
    4. 목록의 어떤 표현도 긴급 판정에 걸리지 않는다(R4).
    5. 띄어쓰기·전각 변형이 같은 결과를 낸다.
    6. 빈 목록은 거부한다.
- **Rationale**: SC-001을 그대로 테스트로 옮긴다. 목록이 바뀌면 같은 PR에서 테스트가 돌아 어긋나면 머지되지 않는다(US2 시나리오 4).
- **누가 정하나**: 표현 목록과 두 사례 파일의 **내용**은 구현 전에 팀이 정한다(FR-002). 구현자가 혼자 만든 목록을 구현자의 테스트로만 검증하지 않는다. **[사용자 결정 2026-10-26: D3]** — Claude가 F2 긴급 목록처럼 `[초안] 팀 리뷰 전` 표시를 붙여 초안을 쓰고, 팀이 PR에서 리뷰·확정한다. 머지 전 리뷰한 사람·날짜를 PR 설명에 적는다.

## R6. 저장 — `inquiry.threat_flagged`, 바꿀 수 없음

- **Decision**: `inquiry`에 `threat_flagged boolean not null default false`를 더하고 `updatable = false`로 둔다. 접수 때 `Inquiry.receive`가 값을 받고, setter는 없다. 판정에 걸린 표현·위치는 어디에도 저장하지 않는다. **[사용자 승인 2026-10-26: D1]**
- **Rationale**:
  - FR-007: 한 번 정해진 표시는 정리·열람·재시도·목록 변경으로 바뀌지 않는다. `urgent`와 같은 방식으로 컬럼 수준에서 막는다.
  - FR-013·원칙 VI: 걸린 표현은 본문의 일부이므로 저장·로그 대상이 아니다.
  - `default false`: 행이 있는 개발 DB에 `ddl-auto=update`로 NOT NULL 컬럼을 더할 때 실패하지 않게 한다(F3 `deferred_notice`와 같은 이유). 기존 문의는 false로 남고 다시 판정하지 않는다(spec Assumptions).
- **Alternatives considered**:
  - 별도 테이블(`inquiry_threat_flag`): 문의 1건에 값 하나라 조인만 늘어난다.
  - 걸린 표현을 암호화해 저장해 교사에게 강조: spec Assumptions에서 범위 밖으로 두었다.

## R7. 판정 실패 처리 — 표시 없이 접수

- **Decision**: `SubmitInquiryService`가 `ThreatRule.isFlagged` 호출을 `RuntimeException`으로 감싸고, 실패하면 false로 접수를 계속한다. 로그는 `log.warn("위협·폭언 판정에 실패해 표시 없이 접수합니다")` 한 줄만 남기며 본문·표현·예외 메시지는 남기지 않는다.
- **Rationale**: FR-005b, FR-008(접수를 막지 않음), Clarification "오탐 최소화"(실패 시 표시 없음). 규칙은 순수 함수라 실제로 실패할 길은 거의 없지만, 판정 추가가 접수 실패의 새 원인이 되지 않게 막는다. 예외 메시지에 본문 일부가 섞일 수 있어 메시지를 남기지 않는다(원칙 VI).
- **Alternatives considered**: 예외를 그대로 올려 500 — 학부모의 문의권을 판정 기능이 막게 된다.

## R8. 교사 응답 — 목록·상세에 `threatFlagged`

- **Decision**: `TeacherInquiryQuery.InquirySummary`·`InquiryDetail`과 응답 DTO에 `boolean threatFlagged`를 더한다. 계약에서는 필수 필드다. 긴급 문의는 항상 false다. 목록 정렬(긴급 먼저, 그다음 최신순)은 바꾸지 않는다. **[사용자 승인 2026-10-26: D2]**
- **Rationale**:
  - FR-010: 정리 상태와 상관없이 보인다. 문의 행에서 바로 읽으므로 추가 조회가 없다.
  - 권한은 F2·F3의 목록·상세 그대로다(FR-011). 새 엔드포인트가 없어 새 권한 경로도 없다.
  - nullable(긴급은 null) 대신 boolean으로 둔다. "긴급이라 판정 안 함"과 "판정했는데 없음"을 앱이 구분할 필요가 없고, 긴급 여부는 이미 `urgent`로 알 수 있다.
  - 정렬을 바꾸지 않는 이유: 표시는 참고 표시이고(spec Assumptions), 정렬이 바뀌면 학부모 응답에는 없는 값이 교사 화면 순서를 바꾸는 셈이라 해석이 갈린다. 필요하면 앱이 표시로 강조한다.
- **Alternatives considered**:
  - `threatLevel` enum: Clarification "하나로"에 맞지 않는다.
  - 목록 필터(`?threatFlagged=true`): 요청이 없었다. 범위 밖.

## R9. 학부모·알림 비노출

- **Decision**: 학부모 DTO(`ParentInquirySummary`·`ParentInquiryDetail`·`InquirySubmitted`)와 알림 문구는 고치지 않는다. 테스트로 확인만 한다.
  - `InquiryExposureTest` 확장: 표시가 붙은 문의와 붙지 않은 문의의 학부모 응답에서 키 집합이 같고 `threat`가 든 키가 없음(FR-012, SC-004).
  - `InquirySensitiveLoggingTest` 확장: 표시가 붙은 문의를 접수·조회하는 동안 로그에 본문·걸린 표현이 없음(FR-013, SC-005).
- **Rationale**: 원칙 V. 학부모 응답은 F2 고정 안내 코드로만 만들어지므로 새 값이 끼어들 자리가 없다. 회귀만 막으면 된다.

## R10. 앱 계약 버전 — 다음 마이너 버전

- **결과 (2026-10-26)**: F4(PR #10)가 먼저 병합되어 0.5.0을 가져갔으므로 이 기능은 **0.6.0**이다. 같은 이유로 spec 폴더도 F4의 `specs/004-answer-review-send`와 겹치지 않게 `specs/005-threat-expression-flag`로 옮겼다. 병합하면서 F4의 재문의도 판정 대상에 넣고(FR-005c) `TeacherFollowUpView`에 `threatFlagged`를 더했다.
- **Decision**: 교사 목록·상세에 필수 필드가 하나씩 늘어나므로 `docs/api/openapi.yaml`의 마이너 버전을 올리고 `api-vX.Y` 태그를 찍는다(contract-first). **F4 브랜치(`features/f4-answer-review-send`)가 이미 0.5.0을 쓰고 있다.** 먼저 main에 병합되는 쪽이 0.5.0을 갖고, 나중 쪽은 병합 전에 0.6.0으로 올린다. **[사용자 승인 2026-10-26: D2]**
- **Rationale**: 버전 충돌을 병합 시점에 정리한다. 이 계약 조각(`contracts/threat-flag.openapi.yaml`)은 버전과 무관하게 필드 추가만 담는다.
- **Alternatives considered**: F4와 같은 0.5.0에 합치기 — 두 PR의 병합 순서가 정해져 있지 않아 태그 시점이 꼬인다.
