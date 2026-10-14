# 담소 (DAMSO) — Server

> **D**ialogue **A**nalysis & **M**ediation for **S**afe **O**utreach
> 학부모·교사 간 안전한 소통을 위한 AI 민원 정제 앱의 백엔드 서버

학부모 문의가 교사에게 닿기 전 감정과 사실을 분리해 구조화하고, 교사 답변이 학부모에게 닿기 전 오해 소지를 점검합니다.
소통 채널과 민원 정제 기능을 하나의 앱으로 통합했으며, 외부 AI에는 식별정보가 제거된 텍스트만 전달합니다.

2026 소프트웨어 개발보안 시큐어코딩 경진대회 트랙 A(자유주제) 출품작 · 팀 **아기하마**

📱 프론트엔드(Flutter 앱): [damsoWE/damso_app](https://github.com/damsoWE/damso_app)

## 주요 기능

| 기능 | 설명 |
|---|---|
| 문의 정제 | 학부모 문의를 **사실관계 / 요청사항 / 정서강도** 3요소로 구조화해 교사에게 전달 |
| 답변 점검 | 교사 답변의 오해 소지·근거 누락을 표시. 제안만 하며 자동 발송은 없음 |
| 긴급 분류 | 학생 안전·학대·자해 사안은 LLM 호출 전 규칙 기반으로 판정해 즉시 전달 |
| 가명화 게이트웨이 | 학생명·학교명 등을 토큰으로 치환한 뒤 외부 AI 호출, 응답 후 복원 |
| 이력 보존 | 원문·정제본·처리 이력을 append-only로 기록, 원문 열람은 감사 로그에 남김 |

## 보안 설계

- **연락처 미수집** — 전화번호·주소를 수집·저장하지 않아 양측 모두 상대 연락처를 알 수 없음
- **외부 AI 식별정보 미전송** — 가명화 복원표는 메모리에만 존재하며 저장하지 않음
- **fail-open / fail-closed** — 긴급 경로는 의심되면 즉시 전달, 일반 정제는 실패 시 "원문 확인 필요" 알림
- **출력 스키마 강제** — LLM 출력은 Structured Outputs + 서버 재검증, 쿼리 경로에 사용하지 않음
- **안전한 기본값** — 기본 계정 미제공, 시크릿 미설정 시 기동 거부

전체 원칙: [`.specify/memory/constitution.md`](.specify/memory/constitution.md) · 보안약점 점검: [`docs/security-checklist.md`](docs/security-checklist.md)

## 기술 스택

Java 21 · Spring Boot 4.1 · Spring Security (JWT) · Spring Data JPA · PostgreSQL · Redis · OpenAI API · Nginx · Docker Compose

## 문서

| 문서 | 위치 |
|---|---|
| API 명세 | [`docs/api/openapi.yaml`](docs/api/openapi.yaml) |
| 기획서 대비 변경 이력 | [`docs/decisions.md`](docs/decisions.md) |

## 개발 방식

- [spec-kit](https://github.com/github/spec-kit) 기반 명세 주도 개발(SDD): 기능별 명세는 [`specs/`](specs/)에 있습니다.
- Claude Code 등 AI 코딩 도구를 활용하며, 사용 사실을 공개합니다.
- 보안 원칙은 constitution에서 관리하고, AI 생성 코드는 PR 리뷰와 CI 보안 검증(Gitleaks 등)을 거쳐 머지합니다.

## License

[Apache License 2.0](LICENSE)
