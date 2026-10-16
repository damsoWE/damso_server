---
description: 팀 이슈 템플릿으로 GitHub 이슈를 만들고, 이슈와 연결된 작업 브랜치(features/… 또는 fix/…)까지 생성·체크아웃한다. F1~F5 번호를 주면 docs/features.md에서 읽어 이슈로 옮긴다
argument-hint: <F번호 또는 작업 설명, 예: F1 / fix 로그인 잠금 횟수 초기화 오류 / Docs speckit 가이드 수정>
allowed-tools: Bash(gh issue create:*), Bash(gh issue develop:*), Bash(gh auth status:*), Bash(git status:*), Bash(git switch:*), Bash(git pull:*), Bash(git branch:*), Read
---

이슈 `$ARGUMENTS`를 만든다. 흐름은 **이슈 → 브랜치 → 커밋 → PR**이고, 규칙 원본은 `.claude/skills/git-conventions/SKILL.md`다. **커밋·푸시·PR은 이 명령에서 하지 않는다.**

## 0. 입력 해석

- `$ARGUMENTS`가 비어 있으면 멈추고 무엇을 할지 묻는다.
- 첫 단어가 `Feat` / `Fix` / `Docs` / `Chore`(대소문자 무시)면 그 타입으로 쓴다. 아니면 `Feat`로 본다. 애매하면 한 번만 묻는다.
- `F1`~`F5` 같은 `F<숫자>`가 있으면 **기능 번호 입력**이다 (여러 개면 번호마다 이슈 하나).
  - `docs/features.md`를 읽고 해당 `## F<번호>.` 섹션과 맨 위 상태 표의 그 행(기능명·spec 경로·상태)만 사용한다.
  - 섹션이 없으면 멈추고 알린다. 내용을 추측해서 만들지 않는다.
- 번호 뒤에 범위 설명(예: `F1 — 초대코드 검증 API만`)이 있으면 그 범위로 이슈를 좁힌다. 없으면 기능 하나 = 이슈 하나.

## 1. 초안 작성

`.github/ISSUE_TEMPLATE/feature_request.md`의 섹션 순서와 제목 형식을 그대로 쓴다.

- 제목: `[타입] 요약` — 기능 번호 입력이면 `[Feat] F1 인증·회원가입·학급 연결`
- `### 요청 유형`: 해당 항목 하나만 `- [x]` (새로운 기능 추가 / 기존 기능 개선 / 성능 개선). Fix·Docs·Chore는 "기존 기능 개선"
- `### 어떤 기능인가요?`: 기능 번호 입력이면 섹션의 **왜**를 1~3문장으로. 무엇/왜 중심이고 기술 스택은 쓰지 않는다
- `### 작업 상세 내용`: `- [ ]` 체크리스트 3~8개. 기능 번호 입력이면 **사용자 시나리오**·**결정됨**에서 뽑는다. 확실하지 않은 항목은 지어내지 말고 `- [ ] TODO`
- `### 유의사항`: **지켜야 할 것**의 관련 constitution 원칙, `spec 경로`(상태 표 기준, 미생성이면 "미생성"), **미정** 항목은 "clarify에서 결정"으로 옮긴다. 없으면 비운다
- 문의·답변 본문, 토큰, 키, 개인정보 등 민감한 값은 이슈에 넣지 않는다

## 2. 확인

초안(제목·본문·라벨·브랜치 이름)을 보여 주고 **사용자 승인을 받은 뒤에만** 만든다. 이슈는 팀 전체에 보이는 게시물이다.

## 3. 이슈 생성

라벨: `Feat` → `enhancement`, `Fix` → `bug`, `Docs` → `documentation`, `Chore` → 없음.

```bash
gh issue create --title "[Feat] …" --label enhancement --body-file - <<'EOF'
(본문)
EOF
```

출력된 URL의 이슈 번호를 기억한다.

## 4. 브랜치 연결

- 사용자가 "이슈만"이라고 했으면 건너뛴다.
- `git status`로 커밋 안 된 변경이 있는지 본다. 있으면 알리고 진행 여부를 묻는다.
- 브랜치 이름: 기능은 `features/<영문-소문자-하이픈>`, 수정은 `fix/<…>` (예: `features/f1-auth-signup`). `Docs`·`Chore`는 성격에 따라 둘 중 하나.

```bash
git switch main
git pull
gh issue develop <이슈 번호> --name features/<이름> --base main --checkout
```

- 여러 이슈를 만들었으면 체크아웃할 브랜치는 하나만 고르게 한다.

## 5. 결과 보고

이슈 URL, 브랜치 이름, 다음 할 일(예: `/speckit-specify`)을 짧게 알린다.

## 하지 않는 것

- 승인 없이 이슈를 만들지 않는다.
- 이슈 제목·본문에 Claude 등 AI 기여자 표시를 넣지 않는다.
