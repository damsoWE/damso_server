---
name: git-conventions
description: 담소 팀의 GitHub 작업 규칙(이슈 → 브랜치 → 커밋 → PR). 브랜치를 만들거나, 커밋 메시지를 쓰거나, 이슈·PR을 만들 때 반드시 따른다. "브랜치 만들어줘", "커밋해줘", "PR 올려줘", "이슈 만들어줘"에 사용한다.
---

# 담소 Git 규칙

모든 개발은 **이슈 → 브랜치 → 커밋 → PR → main 병합** 순서로 한다. **main에 직접 푸시하지 않는다.**

## 1. 이슈

- 작업을 시작하기 전에 GitHub 이슈를 만든다. 템플릿: `.github/ISSUE_TEMPLATE/feature_request.md` ("기능 요청")
- 제목: `[타입] 설명` — 타입은 아래 커밋 타입과 같다 (`[Feat]`, `[Fix]`, `[Docs]`, `[Chore]`)
- 예: `[Feat] F1 인증·회원가입·학급 연결`

```bash
gh issue create --template feature_request.md --title "[Feat] F1 인증·회원가입·학급 연결"
```

## 2. 브랜치

| 종류 | 형식 | 예 |
|---|---|---|
| 기능 개발 | `features/{기능 개발 명}` | `features/f1-auth-signup` |
| 수정 | `fix/{수정 명}` | `fix/login-lock-count` |

- 문서·설정 작업(`Docs`, `Chore`)은 성격에 맞춰 `features/…`(새로 추가) 또는 `fix/…`(고침) 중 하나를 쓴다.

- 브랜치는 **이슈와 연결해서** 만든다. GitHub 이슈 화면의 "Development"에 브랜치가 표시된다.

```bash
gh issue develop <이슈 번호> --name features/f1-auth-signup --base main --checkout
```

- 기능 개발 명은 영어 소문자와 하이픈(`-`)으로 쓰는 것을 권장한다.

## 3. 커밋

형식: `{타입} : {내용}` — 타입 뒤에 **공백, 콜론, 공백**.

| 타입 | 언제 | 예 |
|---|---|---|
| `Feat` | 새 기능, 기능 개선 | `Feat : 초대코드 검증 API 추가` |
| `Fix` | 버그·오류 수정 | `Fix : 로그인 실패 횟수 초기화 오류 수정` |
| `Docs` | 문서만 변경 (spec·plan·가이드·README·체크리스트 등) | `Docs : speckit 가이드에 Git 규칙 반영` |
| `Chore` | 설정·빌드·의존성·CI·템플릿 등 기능 외 작업 | `Chore : 이슈 템플릿 추가` |

- 내용은 한국어로, 무엇을 했는지 한 줄로 쓴다.
- 커밋 메시지에 `Co-Authored-By` 등 **AI(Claude) 기여자 표시를 넣지 않는다.**
- 커밋 전 Gitleaks 검사(pre-commit)가 자동으로 돈다. 막히면 시크릿을 지우고 `.env`로 옮긴다. 검사를 건너뛰지 않는다(`--no-verify` 금지).
- 커밋은 사용자가 요청했을 때만 한다.

## 4. PR

- `main`으로 PR을 만든다. 본문은 `.github/pull_request_template.md`를 채운다.
- PR 제목: `[타입] 설명` (`[Feat]`, `[Fix]`, `[Docs]`, `[Chore]`)
- 본문에 `Closes #<이슈 번호>`를 넣어 병합 시 이슈가 자동으로 닫히게 한다.
- 리뷰 승인과 CI(Gitleaks) 통과 후 병합한다. 백엔드 팀원 PR은 이현우가 리뷰한다.

```bash
git push -u origin features/f1-auth-signup
gh pr create --base main --title "[Feat] F1 인증·회원가입·학급 연결" --body-file <채운 템플릿>
```

## 5. 하지 말 것

- `main`에 직접 커밋·푸시
- `git push --force`로 공유 브랜치 덮어쓰기
- 이슈 없이 브랜치 만들기
- 규칙에 없는 커밋 타입 임의 사용 — 필요하면 팀에서 정한 뒤 이 문서에 추가
