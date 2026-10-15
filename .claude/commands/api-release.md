---
description: docs/api/openapi.yaml을 검사하고 이전 태그와 비교한 뒤, 확인을 받아 api-vX.Y 태그를 찍고 프론트 공지 문구를 만든다
argument-hint: <새 태그, 예: api-v0.2>
allowed-tools: Bash(git status:*), Bash(git fetch:*), Bash(git tag -l:*), Bash(git rev-parse:*), Bash(git diff:*), Bash(git log:*), Bash(npx -y @redocly/cli@1.34.5 lint:*), Read
---

API 명세 새 버전 `$ARGUMENTS`를 배포한다. 태그 푸시는 되돌리기 어려우므로 **5단계에서 반드시 사용자 확인을 받은 뒤에만** 태그를 찍는다.

## 1. 확인

- `$ARGUMENTS`가 비어 있거나 `api-v<숫자>.<숫자>` 형식이 아니면 멈추고 묻는다.
- `git fetch --tags origin` 후, 같은 태그가 이미 있으면 멈춘다.
- 현재 브랜치가 `main`이고 `origin/main`과 같은지, 작업 트리가 깨끗한지 확인한다. 아니면 멈추고 이유를 알린다 (태그는 merge된 main에만 찍는다).

## 2. 형식 검사

```bash
npx -y @redocly/cli@1.34.5 lint docs/api/openapi.yaml
```

오류가 하나라도 있으면 멈춘다. 경고 중 `no-unused-components`(공통 스키마)는 정상이다.

## 3. 버전 일치

`docs/api/openapi.yaml`의 `info.version`이 태그와 맞는지 본다 (`api-v0.2` → `0.2.0`). 다르면 멈추고 `info.version`부터 고쳐 merge하라고 안내한다.

## 4. 이전 태그와 비교

- 이전 태그: `git tag -l "api-v*" --sort=-v:refname`의 첫 번째
- `git diff <이전 태그> HEAD -- docs/api/openapi.yaml`을 읽고 한국어로 요약한다:
  - 새로 생긴 / 바뀐 / 사라진 API (메서드·경로)
  - 요청·응답 필드 변경, 새 에러 코드
  - **프론트가 깨질 수 있는 변경**(필드 삭제·이름 변경·필수화)은 따로 강조
- `git log --oneline <이전 태그>..HEAD -- docs/api/openapi.yaml specs/`로 관련 커밋도 보여 준다.

## 5. 사용자 확인 (필수)

요약을 보여 준 뒤 이렇게 묻고 **답을 기다린다**:

> `$ARGUMENTS` 태그를 현재 main(`<커밋 해시>`)에 찍고 푸시할까요?

"예" 같은 명시적 승인이 없으면 여기서 끝낸다.

## 6. 태그 찍기 (승인 후)

```bash
git tag -a $ARGUMENTS -m "API 명세 $ARGUMENTS: <한 줄 요약>"
git push origin $ARGUMENTS
```

## 7. 프론트 공지 문구

아래 형식으로 만들어 그대로 붙여 넣을 수 있게 보여 준다.

```
백엔드 API 명세 <태그> 나왔습니다.
프론트 레포에서 `/fetch-api <태그>` 를 실행해 주세요.

바뀐 점
- <API 변경 요약 2~5줄>

주의 (프론트 수정 필요)
- <깨질 수 있는 변경, 없으면 "없음">
```
