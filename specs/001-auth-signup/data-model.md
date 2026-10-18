# Data Model: 인증·회원가입·학급 연결 (F1)

**Feature**: [spec.md](./spec.md) · **Research**: [research.md](./research.md)

- 별도 표시가 없으면 모든 엔티티는 `BaseEntity`(UUID v4 `id`, `created_at`, `updated_at`, `version`)를 상속한다.
- 시각은 모두 UTC `Instant`(`timestamptz`). DB 컬럼은 snake_case.
- `SensitiveText` 필드는 AES-256-GCM 암호문(`TEXT`)으로 저장되고 로그에 `[MASKED]`로 찍힌다(foundation R5·R6).
- 어떤 테이블에도 전화번호·주소 컬럼을 두지 않는다(원칙 I).
- 스키마는 엔티티 매핑으로 Hibernate `ddl-auto=update`가 만든다(R1). 아래 제약은 모두 `@Column`·`@Table(uniqueConstraints)`로 선언 가능한 것만 쓴다. 부분 인덱스·CHECK·트리거는 쓰지 않는다.
- 실패 횟수·대기·발송 제한은 DB가 아니라 Redis에 둔다(맨 아래 "Redis 키", R7).

---

## 관계 개요

```
user_account ─1:0..1─ classroom.homeroom_teacher_id     (교사–학급 1:1)
user_account ─1:0..N─ student.guardian_id               (학부모 1 : 자녀 N, 학생 1 : 보호자 0..1)
classroom    ─1:N──── student
user_account ─1:0..1─ teacher_profile
invite_code  ──N:1─── classroom (교사 코드) | student (학부모 코드)
user_account ─1:N──── auth_session ─1:N─ refresh_token
pending_signup ─N:1── invite_code
user_account ─1:N──── link_history (append-only)
```

---

## user_account

| 필드 | 타입 | 규칙 | 근거 |
|---|---|---|---|
| `email` | varchar(254), NULL 허용 | 소문자 정규화, **유니크**(NULL 중복 허용). 탈퇴 시 NULL | FR-017, FR-038, R11 |
| `password_hash` | varchar(100), NULL 허용 | BCrypt. 탈퇴 시 NULL | FR-028, R2 |
| `name` | SensitiveText | 1~20자(공백 제거 후). 탈퇴 시 "탈퇴한 사용자" | FR-014, R13 |
| `role` | varchar(20) | `SUPER_ADMIN` · `TEACHER` · `PARENT`. 변경 불가 | FR-015 |
| `status` | varchar(20) | `ACTIVE` · `WITHDRAWN` | FR-038 |
| `terms_agreed_at` | timestamptz, NULL 허용 | 가입 시 동의 시각. 슈퍼 관리자는 NULL | FR-018 |
| `withdrawn_at` | timestamptz, NULL 허용 | | FR-038 |

**상태 전이**: `ACTIVE → WITHDRAWN` (단방향). 탈퇴 계정은 로그인·토큰 갱신·모든 보호 기능 거부.

> 가입 인증 전 상태는 이 테이블에 없다. 인증이 끝난 계정만 만들어진다(R5).

## classroom

| 필드 | 타입 | 규칙 | 근거 |
|---|---|---|---|
| `grade` | smallint | 1~6 | FR-002 |
| `class_number` | smallint | 1~20 | FR-002 |
| `homeroom_teacher_id` | UUID FK → user_account, NULL 허용 | **유니크**. 담임 확정 시 설정, 교사 탈퇴 시 NULL | FR-019, R6 |

- `(grade, class_number)` 유니크(단일 학교 가정, spec Assumptions).
- 담임이 있으면 교사 초대 코드 발행 거부(`CLASSROOM_HAS_TEACHER`).

## student

| 필드 | 타입 | 규칙 | 근거 |
|---|---|---|---|
| `classroom_id` | UUID FK → classroom | 필수 | FR-002 |
| `name` | SensitiveText | 1~20자 | FR-002, R13 |
| `guardian_id` | UUID FK → user_account, NULL 허용 | 보호자 확정 시 설정, 학부모 탈퇴 시 NULL | FR-020 |

- 보호자가 있으면 학부모 초대 코드 발행 거부(`STUDENT_HAS_GUARDIAN`).
- 같은 학급의 동명이인은 허용한다(구분은 UUID).

## teacher_profile

| 필드 | 타입 | 규칙 | 근거 |
|---|---|---|---|
| `teacher_id` | UUID FK → user_account | **유니크** | FR-033 |
| `display_name` | varchar(20) | 1~20자. 가입 시 이름으로 초기화 | FR-033 |
| `introduction` | varchar(60) | 0~60자 | FR-033 |

교사 가입이 확정될 때 함께 만들고, 탈퇴 시 삭제한다.

## invite_code

| 필드 | 타입 | 규칙 | 근거 |
|---|---|---|---|
| `kind` | varchar(10) | `TEACHER` · `PARENT` | FR-005 |
| `classroom_id` | UUID FK, NULL 허용 | `kind=TEACHER`일 때만 필수 | FR-005 |
| `student_id` | UUID FK, NULL 허용 | `kind=PARENT`일 때만 필수 | FR-005 |
| `code_hash` | char(64) | HMAC-SHA256 hex, **유니크**. 원문 저장 안 함 | FR-007, R3 |
| `status` | varchar(10) | `ISSUED` · `USED` · `REVOKED` | FR-008·009 |
| `active_target_key` | varchar(50), NULL 허용 | **유니크**. `ISSUED`면 `"{kind}:{대상 UUID}"`, `USED`·`REVOKED`면 NULL | FR-009, R6 |
| `expires_at` | timestamptz | 발행 + 24시간 | FR-008 |
| `issued_by` | UUID FK → user_account | 발행한 슈퍼 관리자 | |
| `used_by` | UUID FK, NULL 허용 | 사용한 계정 | |
| `used_at` | timestamptz, NULL 허용 | | |

- `kind`에 맞는 대상 컬럼 하나만 채워진다. DB CHECK 대신 생성 메서드 `InviteCode.forTeacher(classroom)` / `forParent(student)`로만 만든다(R6).
- 대상당 활성 코드 1개는 `active_target_key` 유니크로 보장한다. NULL끼리는 충돌하지 않아 사용·무효 코드는 몇 개든 남을 수 있다(R6).
- **표시 상태**(발행 현황): `USED` → 사용됨, `REVOKED` → 무효, `ISSUED` + `expires_at ≤ now` → 만료, 그 외 → 미사용.

**상태 전이**

```
ISSUED ──(가입·자녀 추가 확정)──→ USED
ISSUED ──(같은 대상 재발행)────→ REVOKED
```

사용 가능 조건: `status = ISSUED` ∧ `expires_at > now` ∧ 대상이 아직 비어 있음(학급 담임 없음 / 학생 보호자 없음). 하나라도 어기면 `INVALID_INVITE_CODE`(사유 구분 없음, FR-010).

## pending_signup (가입 인증 대기)

| 필드 | 타입 | 규칙 | 근거 |
|---|---|---|---|
| `email` | varchar(254) | 소문자 정규화 | FR-016 |
| `name` | SensitiveText | | |
| `password_hash` | varchar(100) | BCrypt | R2 |
| `invite_code_id` | UUID FK → invite_code | | R5 |
| `code_hash` | char(64) | 6자리 인증 코드 HMAC | FR-016, R3 |
| `code_expires_at` | timestamptz | 발송 + 10분 | FR-032 |
| `completed_at` | timestamptz, NULL 허용 | 가입 확정 시각 | |

- 1단계를 다시 하면 같은 이메일의 미완료 대기 건을 지우고 새로 만든다(사용자당 미완료 1건).
- 확정·만료 후 24시간이 지난 건은 하루 1회 정리 작업이 지운다(이력 데이터 아님). `password_reset`도 같은 작업에서 사용·만료 후 24시간이 지난 건을 지운다.
- 인증 코드 실패 횟수(`SIGNUP_CODE`)와 재발송 제한(`MAIL_SEND`)은 Redis에서 이메일 기준으로 센다.

## password_reset

| 필드 | 타입 | 규칙 | 근거 |
|---|---|---|---|
| `user_id` | UUID FK → user_account | | FR-029 |
| `code_hash` | char(64) | 6자리 재설정 코드 HMAC | R3 |
| `expires_at` | timestamptz | 발송 + 10분 | FR-030 |
| `used_at` | timestamptz, NULL 허용 | 1회용 | FR-030 |

- 다시 요청하면 이전 건을 지우고 새로 만든다(이전 코드 무효, FR-030).
- 가입되지 않은 이메일이면 행을 만들지 않고 같은 응답만 준다(FR-029).
- 재요청 제한(60초·시간당 5회)은 Redis `MAIL_SEND`로 센다.

## auth_session (세션 = 갱신 토큰 계열)

| 필드 | 타입 | 규칙 | 근거 |
|---|---|---|---|
| `user_id` | UUID FK → user_account | | FR-025a |
| `expires_at` | timestamptz | 마지막 갱신 + 14일, 갱신마다 연장 | FR-025b |
| `revoked_at` | timestamptz, NULL 허용 | | FR-025·026·031 |
| `revoke_reason` | varchar(20), NULL 허용 | `LOGOUT` · `REUSE_DETECTED` · `PASSWORD_RESET` · `WITHDRAWAL` | |

- `id`가 접근 토큰의 `sid` 클레임이다. 접근 토큰 검증 시 `revoked_at IS NULL ∧ expires_at > now`를 확인한다(R4).

**상태 전이**: `활성 → 폐기(revoked)` 또는 `활성 → 만료(expires_at 경과)`. 되돌릴 수 없다.

## refresh_token

| 필드 | 타입 | 규칙 | 근거 |
|---|---|---|---|
| `session_id` | UUID FK → auth_session | | R4 |
| `token_hash` | char(64) | SHA-256 hex, **유니크** | R4 |
| `used_at` | timestamptz, NULL 허용 | 회전 시 기록. 이미 있으면 재사용 → 세션 폐기 | FR-025 |

## link_history (append-only, BaseEntity 미상속)

| 필드 | 타입 | 규칙 | 근거 |
|---|---|---|---|
| `id` | UUID | v4, 앱에서 생성 | 원칙 VII |
| `account_id` | UUID | 대상 계정(탈퇴 후에도 유지, FK 없음) | FR-038a |
| `target_type` | varchar(10) | `STUDENT` · `CLASSROOM` · `NONE`(탈퇴) | FR-038a |
| `target_id` | UUID, NULL 허용 | 학생 또는 학급 UUID | FR-038a |
| `event` | varchar(10) | `LINKED` · `UNLINKED` · `WITHDRAWN` | FR-038a |
| `occurred_at` | timestamptz | | FR-038a |

- 이메일·이름 등 개인정보 컬럼 없음(원칙 VI).
- 엔티티 `@Immutable`·setter 없음, 리포지토리는 저장·조회 메서드만 선언(R10). DB 트리거는 쓰지 않는다(R1).

---

## Redis 키 (실패 횟수·대기·발송 제한, R7)

DB 테이블이 아니다. 모든 키는 TTL이 있어 자동으로 사라진다.

| 키 | 값 | TTL | 규칙 | 근거 |
|---|---|---|---|---|
| `damso:attempt:LOGIN:{h}` | 연속 실패 횟수, 대기 종료 시각 | 마지막 실패 + 24시간 | 3회 30초 · 4회 1분 · 5회 2분 · 6회 5분 · 7회 이상 10분 대기. 대기 중 시도는 횟수에 더하지 않음 | FR-024 |
| `damso:attempt:INVITE_CODE:{h}` | 연속 실패 횟수, 잠금 해제 시각 | 잠금 해제 시각 또는 마지막 실패 + 24시간 | 5회 실패 → 10분 잠금, 풀리면 0회부터 | FR-011 |
| `damso:attempt:SIGNUP_CODE:{h}` | 〃 | 〃 | 〃 | FR-032 |
| `damso:attempt:RESET_CODE:{h}` | 〃 | 〃 | 〃 | FR-030 |
| `damso:attempt:MAIL_SEND:{h}` | 최근 발송 시각 목록 | 1시간 | 60초 안 재발송 거부, 1시간 안 5회 초과 거부 | R5 |

- `{h}` = HMAC-SHA256(서버 키, 대상). 대상은 로그인·가입 단계·재설정이면 **소문자 이메일**, 로그인 상태의 자녀 추가(`INVITE_CODE`)면 **계정 UUID**(FR-011).
- 성공하면 해당 키를 지운다.
- 증가·판정은 Lua 스크립트 하나로 원자 처리한다.
- Redis 장애 시 요청을 거부한다(fail-closed, R7).

---

## 에러 코드 추가 (foundation `ErrorCode`에 추가, 기존 의미 불변)

| 코드 | HTTP | 메시지(고정) | 쓰임 |
|---|---|---|---|
| `INVALID_INVITE_CODE` | 400 | 코드를 확인해 주세요. | 초대 코드 없음·만료·사용됨·무효·대상 불일치 모두 (FR-010) |
| `INVALID_VERIFICATION_CODE` | 400 | 인증 코드가 올바르지 않거나 만료되었습니다. | 가입 인증·재설정 코드 (FR-016·030) |
| `INVALID_CREDENTIALS` | 401 | 이메일 또는 비밀번호가 올바르지 않습니다. | 로그인·탈퇴 비밀번호 확인 (FR-023) |
| `TOO_MANY_ATTEMPTS` | 429 | 잠시 후 다시 시도해 주세요. | 잠금·대기. `Retry-After` 헤더 (R15) |
| `CLASSROOM_HAS_TEACHER` | 409 | 이미 담임이 연결된 학급입니다. | 교사 코드 발행 (FR-019) |
| `STUDENT_HAS_GUARDIAN` | 409 | 이미 보호자가 연결된 학생입니다. | 학부모 코드 발행 (FR-020) |

- 갱신 토큰 무효·재사용·세션 폐기는 기존 `UNAUTHORIZED`를 쓴다(사유 구분 없음).
- 이미 가입된 이메일은 에러를 내지 않는다(R5, 안내 메일로 처리).

## 시크릿·환경변수 추가 (SecretsValidator)

| 환경변수 | 필수 | 요건 | 쓰임 |
|---|---|---|---|
| `DAMSO_CODE_HASH_KEY` | 필수 | Base64 → 32바이트 이상 | 코드·이메일 HMAC (R3·R7) |
| `DAMSO_REDIS_HOST` | 필수 | 비어 있지 않음 | 실패 횟수·발송 제한 (R7) |
| `DAMSO_REDIS_PORT` | 필수 | 1~65535 | 〃 |
| `DAMSO_REDIS_PASSWORD` | 선택 | | 〃 |
| `DAMSO_MAIL_HOST` | 필수 | 비어 있지 않음 | SMTP (R9) |
| `DAMSO_MAIL_PORT` | 필수 | 1~65535 | SMTP |
| `DAMSO_MAIL_FROM` | 필수 | 이메일 형식 | 발신 주소 |
| `DAMSO_MAIL_USERNAME` / `DAMSO_MAIL_PASSWORD` | 선택 | | SMTP 인증 |
| `DAMSO_MAIL_STARTTLS` | 선택(기본 `true`) | `true`·`false` | SMTP 인증·STARTTLS 사용 여부. 로컬 Mailpit만 `false` |
| `DAMSO_SUPER_ADMIN_EMAIL` / `DAMSO_SUPER_ADMIN_PASSWORD` | 선택(둘 다 있거나 둘 다 없음) | 비밀번호 정책 통과 | 슈퍼 관리자 생성 (R12) |
