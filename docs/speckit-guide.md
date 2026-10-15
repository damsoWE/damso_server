# spec-kit 개발 가이드

> 팀 공유 문서. 처음 합류한 팀원은 "개발 흐름"부터 읽는다.
> 스킬 정의: `.claude/skills/speckit-*/SKILL.md` · 산출물: `specs/NNN-기능명/`

## 처음 보는 사람을 위한 용어

| 용어 | 뜻 |
|---|---|
| constitution | 이 프로젝트의 **절대 규칙**. 기획서·spec보다 우선한다. `.specify/memory/constitution.md` |
| spec | 기능이 **무엇을, 왜** 하는지. 기술 이야기는 없다. `spec.md` |
| plan | 그 기능을 **어떻게** 만들지. 기술 스택·DB 구조·API가 여기서 정해진다. `plan.md` |
| contract | 프론트와 백엔드가 주고받는 API 약속(요청·응답 형식). `contracts/` → `docs/api/openapi.yaml` |
| tasks | plan을 실제 작업 단위로 쪼갠 체크리스트. `tasks.md` |
| PR | Pull Request. 내 브랜치의 변경을 main에 합쳐 달라고 요청하고 리뷰받는 것 |

---

## 개발 흐름 — 기능 하나를 처음부터 끝까지

> 기능 하나(F1, F2 …)마다 아래 순서를 한 바퀴 돈다. **한 기능은 한 사람이 끝까지** 맡는다.

### 0. 시작 전

```bash
git switch main
git pull
git switch -c feat/F1-auth        # 기능마다 브랜치를 새로 만든다
```

### 1. 무엇을 만들지 확인 — `docs/features.md`

- 맡은 기능 섹션(예: F1)을 끝까지 읽는다. 사용법은 아래 [features.md 사용법](#featuresmd-사용법) 참고.
- constitution의 원칙 I~VIII도 한 번 훑는다. 모든 기능에 적용된다.

### 2. spec 작성 — `/speckit-specify`

- features.md 섹션 내용을 입력으로 넣는다(넣는 법은 아래 참고).
- `specs/001-기능명/spec.md`가 생긴다. **AI가 쓴 걸 그대로 믿지 말고 끝까지 읽는다.** features.md와 다른 내용이 있으면 고친다.

### 3. 모호한 곳 정리 — `/speckit-clarify`

- AI가 최대 5개 질문을 한다. 답하면 spec에 반영된다.
- 모르는 질문은 혼자 정하지 말고 팀에 묻는다.
- 새로 정한 내용이 기획서·IA와 다르면 `docs/decisions.md`에 한 줄 추가한다.

### 4. 설계 — `/speckit-plan`

- 여기서 처음으로 기술 스택을 말한다.
  ```
  /speckit-plan Spring Boot 4.1, Java 21, Spring Security + JWT(AT/RT 분리),
  JPA + QueryDSL, PostgreSQL, Redis. 패키지 루트는 damso.
  ```
- plan이 constitution 위반 여부를 스스로 점검한다(Constitution Check). 위반이 나오면 무시하지 말고 팀에 공유한다.

### 5. API 약속 리뷰 — 사람이 직접 (중요)

yaml 원문은 읽기 어려우니 **웹페이지로 바꿔서** 본다(Node.js 필요).

```bash
# 형식 검사 — "valid"가 나와야 한다. 경고는 내용을 읽고 판단한다
npx -y @redocly/cli@1.34.5 lint specs/NNN-기능명/contracts/<파일>.yaml

# 웹페이지로 만들기 — build/는 .gitignore라 커밋되지 않는다
npx -y @redocly/cli@1.34.5 build-docs specs/NNN-기능명/contracts/<파일>.yaml -o build/contracts/NNN.html
```

`build/contracts/NNN.html`을 브라우저로 열어 아래를 하나씩 확인한다.

> lint 경고 중 "no-unused-components"(정의만 하고 안 쓴 컴포넌트)는 공통 스키마라면 정상이다.

- [ ] 모든 ID가 UUID인가 (숫자 1, 2, 3 같은 순번 ID 금지)
- [ ] 남의 데이터에 접근하면 403/404가 정의돼 있나
- [ ] 에러 응답에 스택트레이스·내부 경로·SQL이 없나
- [ ] 문의·답변 본문이 필요 없는 곳에 들어가 있지 않나
- [ ] 연락처(전화번호·주소) 필드가 없나

확인이 끝나면:

```bash
# contracts 내용을 docs/api/openapi.yaml에 합친 뒤
git add docs/api/openapi.yaml
git commit -m "API 명세: F1 인증·회원가입"
git tag api-v0.1                 # 명세가 바뀔 때마다 숫자를 올린다
git push origin api-v0.1
```

→ 프론트 팀에 "api-v0.1 나왔어요"라고 공지한다. **API를 바꿀 땐 항상 openapi.yaml 먼저 → 공지 → 코드** 순서.

### 6. 작업 목록 — `/speckit-tasks` → `/speckit-analyze`

- `tasks`가 `tasks.md`를 만든다.
- `analyze`는 spec·plan·tasks가 서로 맞는지 **읽기만** 하고 리포트를 준다. 지적이 나오면 해당 파일을 고치고 다시 돌린다.

### 7. 구현 — `/speckit-implement`

- tasks.md 순서대로 코드를 만들고 완료 항목을 체크한다.
- AI가 만든 코드도 **내가 설명할 수 있을 때까지** 읽는다. 대회 발표·진단 때 질문받는다.

### 8. 확인

```bash
./gradlew test              # Windows: .\gradlew.bat test
```

- 실패를 숨기거나 테스트를 지워서 통과시키지 않는다.
- `/speckit-converge`로 spec 대비 빠진 작업이 없는지 확인한다. 빠진 게 있으면 tasks.md에 추가되니 다시 implement.

### 9. 문서 갱신

- `docs/security-checklist.md`: 이번 기능과 관련된 항목을 `조치`로 바꾸고 **근거**(파일 경로·테스트 이름)를 적는다.
- `docs/features.md`: 상태 표의 spec 경로와 상태를 바꾼다.

### 10. 올리기

```bash
git add .
git commit -m "F1 인증·회원가입·학급 연결 구현"   # 커밋 전에 Gitleaks가 자동 검사한다
git push -u origin feat/F1-auth
```

- GitHub에서 PR을 만든다 → 팀원 리뷰 → CI(Gitleaks) 통과 → main에 merge.
- Gitleaks가 커밋을 막으면 비밀번호·키가 들어간 것이다. **그 값을 지우고 `.env`로 옮긴다.** 이미 push했다면 팀에 바로 알리고 키를 교체한다.

### 프론트 팀원은

> 프론트 레포(`damsoWE/damso_app`)는 아직 환경 구축 전이다. 구축 후 이 절을 보완한다.

1. 백엔드의 `api-vX.Y` 공지를 받으면 그 태그의 `docs/api/openapi.yaml`을 받는다.
2. 프론트 spec에는 **대응하는 백엔드 spec 경로**(예: `damso_server/specs/001-…`)를 꼭 적는다. 번호가 레포마다 따로 매겨지기 때문.
3. 실서버가 나오기 전엔 mock으로 개발한다. 정상 응답만 넣지 말고 401(토큰 만료)·403(남의 문의)·정제 진행 중·"원문 확인 필요"·긴급 문의 케이스를 꼭 넣는다.

---

## features.md 사용법

### 이 문서는 뭔가

- 만들 기능 5개(F1~F5)의 **목록이자 결정 기록**이다.
- `/speckit-specify`에 넣을 **입력 원본**이다.
- 기획서·IA·팀 회의에서 정해진 것을 한곳에 모아 둔다.

### 섹션 읽는 법

| 항목 | 뜻 | specify에 넣나 |
|---|---|---|
| **왜** | 이 기능이 필요한 이유 | 넣는다 |
| **사용자 시나리오** | 누가 무엇을 하고 어떤 결과가 나오는지 | 넣는다 |
| **지켜야 할 것** | 관련 constitution 원칙 | 넣는다 |
| **결정됨** | 팀이 확정한 세부 규칙 | 넣는다 |
| **IA와 달라지는 점** | 화면 설계와 다른 부분 | 넣는다 (spec이 IA를 따라가지 않게) |

문장 끝의 태그는 출처다: **[기획]** 개발기획서 · **[IA]** 화면 설계 · **[원칙]** constitution · **[팀 결정]** 회의에서 정함

### specify에 넣는 법

1. 맡은 기능 섹션을 통째로 복사한다.
2. **기술 이름만 지운다** — Spring, Redis, JWT, PostgreSQL, FCM 같은 단어. ("해시 저장", "10분 잠금" 같은 규칙은 남긴다.)
3. 출처 태그(`[IA]` 등)는 지워도 되고 남겨도 된다.
4. 붙여 넣는다.
   ```
   /speckit-specify (복사한 F1 섹션 내용)
   ```

### 새로운 결정이 생기면

1. features.md의 해당 기능 **결정됨**에 한 줄 추가하고 끝에 `[팀 결정 날짜]`를 붙인다.
2. 기획서·IA와 다르면 `docs/decisions.md`에도 한 줄 추가한다.
   ```
   | 2026-10-20 | [IA] 원래 내용 → 바뀐 내용 | 이유 |
   ```
3. constitution과 충돌하면 **멈추고 팀에 공유한다.** 원칙은 혼자 바꾸지 않는다.

### 상태 표 갱신

| 상태 | 언제 |
|---|---|
| spec 준비됨 | 결정이 다 끝나 specify를 시작할 수 있음 |
| spec 작성 중 | specify ~ plan 진행 중 → **spec 경로**도 같이 적는다 |
| 구현 중 | implement 진행 중 |
| 완료 | main에 merge됨 |

---

## 스킬 한눈에 보기

```
constitution (레포당 1회)
      │
specify → clarify → plan → tasks → analyze → implement → converge
  무엇/왜    모호점     어떻게    작업목록   교차점검     구현       빠진 것 확인
                        │
                    checklist (선택)
```

| 스킬 | 언제 | 입력 | 산출물 |
|---|---|---|---|
| `/speckit-constitution` | 레포당 1회, 원칙 바꿀 때(팀 합의 후) | 원칙 목록 | `.specify/memory/constitution.md` |
| `/speckit-specify` | 새 기능 시작 | features.md 섹션 | `specs/NNN-기능명/spec.md`, `checklists/requirements.md` |
| `/speckit-clarify` | spec에 모호한 곳이 있을 때 | 없음 (질문에 답) | spec.md에 결정 반영 (최대 5문항) |
| `/speckit-plan` | spec 확정 후 | 기술 스택 | `plan.md`, `research.md`, `data-model.md`, `contracts/`, `quickstart.md` |
| `/speckit-tasks` | plan 확정 후 | 없음 | `tasks.md` |
| `/speckit-analyze` | tasks 후, 구현 전 | 없음 | 점검 리포트 (파일 수정 없음) |
| `/speckit-implement` | analyze 이상 없을 때 | 없음 | 코드 + tasks.md 체크 |
| `/speckit-converge` | 구현 후 | 없음 | 빠진 작업을 tasks.md에 추가 |
| `/speckit-checklist` | plan 이후 (선택) | 주제 (예: "보안") | 요구사항 품질 체크리스트 |
| `/speckit-taskstoissues` | — | — | **deprecated**, 쓰지 않음 |

## 막히면

- AI 결과가 이상하면 그대로 진행하지 말고 팀에 공유한다.
- "이거 constitution에 맞아?"가 헷갈리면 해당 원칙 번호를 찾아 같이 본다.
- 명령이 안 먹으면 Claude Code를 이 레포 루트에서 열었는지 확인한다(`.claude/skills/`가 보여야 한다).
- **Windows에서 `UnicodeEncodeError: 'cp949' codec can't encode …`가 나며 스킬이 멈추면**: spec-kit 스크립트가 한글·이모지를 Windows 콘솔 인코딩(cp949)으로 출력하다 실패한 것이다. 명령 앞에 `PYTHONIOENCODING=utf-8`을 붙여 다시 실행한다.
  ```bash
  PYTHONIOENCODING=utf-8 python .specify/scripts/python/setup_tasks.py --json
  ```
  매번 붙이기 번거로우면 Windows 환경변수에 `PYTHONIOENCODING=utf-8`을 등록한다.
- **테스트가 `Could not find a valid Docker environment`로 실패하면**: Docker Desktop을 켠다. 대부분의 테스트가 PostgreSQL 컨테이너를 띄운다.
