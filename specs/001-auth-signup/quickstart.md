# Quickstart: F1 인증·회원가입·학급 연결 검증

**Feature**: [spec.md](./spec.md) · 설계: [data-model.md](./data-model.md) · 약속: [contracts/](./contracts/auth.openapi.yaml) · 기반: [000-foundation quickstart](../000-foundation/quickstart.md)

구현이 끝난 뒤 이 순서로 spec의 성공 기준(SC)을 확인한다.

## 사전 준비

- Docker 실행 중 (테스트는 PostgreSQL·Redis 컨테이너를, 수동 확인은 Redis·Mailpit 컨테이너를 쓴다)
- foundation 시크릿에 더해 F1 환경변수를 `.env`에 둔다(이름은 `.env.example` 참고, 값은 직접 생성)

```bash
openssl rand -base64 32   # → DAMSO_CODE_HASH_KEY

# 로컬 Redis (실패 횟수·발송 제한)
docker run -d --name damso-redis -p 6379:6379 redis:7-alpine
# DAMSO_REDIS_HOST=localhost, DAMSO_REDIS_PORT=6379

# 로컬 메일 서버 (웹 화면 http://localhost:8025 에서 받은 메일 확인)
docker run -d --name damso-mailpit -p 1025:1025 -p 8025:8025 axllent/mailpit
# DAMSO_MAIL_HOST=localhost, DAMSO_MAIL_PORT=1025, DAMSO_MAIL_FROM=no-reply@damso.local

# 슈퍼 관리자 1회 생성용 (생성 후 지운다, research R12)
# DAMSO_SUPER_ADMIN_EMAIL=..., DAMSO_SUPER_ADMIN_PASSWORD=... (영문+숫자 8자 이상)
```

## 1. 자동화 테스트 — SC-001 ~ SC-008

```bash
./gradlew test          # Windows: .\gradlew.bat test
```

테스트는 메일 출력 포트를 가짜 구현으로 바꿔, 발송된 6자리 코드를 테스트 안에서 읽는다.

| 확인 대상 | 기대 결과 | SC |
|---|---|---|
| 코드 발행 → 가입 1·2단계 → 연결 확인 | 학급 담임·학생 보호자로 확정, 코드 `USED` | SC-001 |
| 초대 코드 없이·공개 경로 우회로 계정 생성 시도 | 생성 0건 | SC-002 |
| 없는·만료·사용됨·무효·교사↔학부모 코드 | 응답 본문 완전히 동일(`INVALID_INVITE_CODE`) | SC-003 |
| 없는 이메일 vs 틀린 비밀번호 로그인, 가입·미가입 재설정 요청 | 응답 본문 동일 | SC-003 |
| 코드 입력 6번째 실패 / 10분 경과 | 429 + `Retry-After` / 다시 시도 가능 | SC-004 |
| 로그인 3~7회 연속 실패 후 대기 중 시도 | 429, `Retry-After` ≤ 600 | SC-004 |
| 다른 학급 학부모·교사가 학생·프로필·현황 조회, 교사·학부모가 `/admin/**` 호출 | 403/404 100% | SC-005 |
| 모든 응답 본문·로그 스캔 | 다른 사용자 이메일·코드 원문·비밀번호 0건 | SC-006 |
| 회전된 갱신 토큰 재사용 | 그 세션의 갱신·접근 토큰 모두 401, 다른 기기 세션은 정상 | SC-007 |
| 같은 초대 코드로 동시 가입 확정 2건 | 정확히 1건 성공 | SC-008 |
| `link_history` 리포지토리 | 수정·삭제 메서드 0개(리플렉션 테스트), 엔티티 변경 후 저장해도 UPDATE 미발행 | FR-038a |
| Redis 중지 상태에서 로그인·코드 입력 | 제한 없이 통과하지 않고 거부(fail-closed) | R7 |

시간 의존 테스트(만료·대기)는 주입 가능한 `Clock`으로 시간을 앞당겨 확인한다. 실패가 있으면 숨기지 말고 테스트 이름과 함께 공유한다.

## 2. 수동 확인 — 앱 없이 전체 흐름

서버를 띄운 뒤(`./gradlew bootRun`) [contracts](./contracts/auth.openapi.yaml) 순서대로 호출한다.

1. 슈퍼 관리자 로그인 → `POST /api/v1/admin/classrooms` → `POST …/students` → `POST …/teacher-invite`, `POST /api/v1/admin/students/{id}/parent-invite`
   - 기대: 응답에만 `code` 원문이 있고, `GET /api/v1/admin/invite-codes`에는 원문이 없다.
2. `POST /api/v1/auth/signup`(교사 코드) → Mailpit에서 6자리 코드 확인 → `POST /api/v1/auth/signup/verify`
   - 기대: 토큰 발급, `GET /api/v1/teacher/classroom`에 담당 학급이 보인다.
3. 학부모 코드로 같은 흐름 → `GET /api/v1/parent/children`
   - 기대: 자녀와 담임 공개 프로필(이메일 없음).
4. 두 번째 자녀 코드 → `POST /api/v1/parent/children` → 자녀 2명.
5. 같은 계정으로 두 번 로그인(기기 2대 흉내) → 한쪽 `POST /api/v1/auth/logout` → 다른 쪽은 계속 동작.
6. `POST /api/v1/auth/password-reset` → 코드 → `…/confirm` → 5의 남은 세션도 401.
7. `POST /api/v1/me/withdrawal` → 같은 이메일로 로그인 불가, 관리자 화면에서 해당 학생 `guardianLinked=false`, 새 코드 발행 가능.

## 3. 보안 체크리스트 갱신

구현 후 `docs/security-checklist.md`에서 아래 항목의 상태와 근거(코드·테스트)를 채운다.

| 항목 | 근거가 될 것 |
|---|---|
| 입력 15 보안기능 결정에 사용되는 부적절한 입력값 | 역할·연결은 코드·토큰 주체로만 판단 |
| 보안기능 2 부적절한 인가 | 역할별 경로 + 서비스 건별 검증 테스트 |
| 보안기능 9 취약한 비밀번호 허용 | 비밀번호 정책 검증 |
| 보안기능 15 반복된 인증시도 제한 | Redis 실패 횟수·대기 (research R7) |
| 보안기능 16 솔트 없이 일방향 해쉬 | BCrypt, 코드는 키가 있는 HMAC |
| 시간 및 상태 1 TOCTOU | 갱신 토큰 조건부 회전, 초대 코드 `@Version` |
