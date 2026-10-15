# Data Model: 공통 보안·구조 기반 (Foundation)

**Feature**: [spec.md](./spec.md) · **Research**: [research.md](./research.md)

이 기반은 업무 테이블을 만들지 않는다. 모든 기능의 엔티티가 따르는 **공통 구조**와 **값 타입**, 그리고 **에러 코드 목록**을 정의한다.

---

## BaseEntity (추상, 모든 엔티티가 상속)

| 필드 | 타입 | 규칙 | 근거 |
|---|---|---|---|
| `id` | UUID | PK. 생성 시 애플리케이션이 v4로 할당, 변경 불가 | FR-008·009, R3 |
| `createdAt` | Instant (UTC) | 최초 저장 시 자동 기록, 변경 불가 | FR-008 |
| `updatedAt` | Instant (UTC) | 저장할 때마다 자동 갱신 | FR-008 |
| `version` | Long | 낙관적 락. 저장할 때마다 증가, 불일치 시 `CONFLICT` | FR-010, R4 |

- `equals`/`hashCode`는 `id` 기준(id가 생성 시점에 정해지므로 안정적).
- `toString`은 클래스명과 `id`만 출력한다(하위 클래스가 민감 필드를 노출하지 않도록).
- DB 컬럼 이름은 snake_case(`created_at`, `updated_at`).

## SensitiveText (값 타입)

| 항목 | 내용 |
|---|---|
| 의미 | 문의·답변 본문, 정제 결과 등 **민감 텍스트** |
| 생성 | `SensitiveText.of(String)` — null 불가, 빈 문자열 허용 |
| 원문 접근 | `reveal()` 메서드로만 (게터 이름 아님) |
| `toString()` | 항상 `"[MASKED]"` |
| `equals`/`hashCode` | 원문 기준 (해시값을 로그로 출력하지 않는다) |
| 저장 | JPA 컨버터가 **타입 기준 자동 적용** → AES-256-GCM 암호문(Base64)으로 저장 |
| DB 컬럼 타입 | `TEXT` |
| 근거 | FR-005·011·012, R5·R6 |

### 암호문 저장 형식

```
Base64( version(1B) ‖ IV(12B) ‖ ciphertext ‖ tag(16B) )
```

- version은 현재 `0x01`. 다른 값이면 복호화 실패로 처리한다.
- 빈 문자열 → 빈 문자열로 저장(암호화하지 않음, spec Edge Case).
- 복호화 실패(변조·키 불일치) → 내부 예외 → 응답은 `INTERNAL_ERROR`, 로그에는 엔티티 id만.

## 필수 시크릿 목록 (기동 시 검증)

| 환경변수 | 요건 | 쓰임 |
|---|---|---|
| `DAMSO_DB_URL` | 비어 있지 않음 | DB 접속 |
| `DAMSO_DB_USERNAME` | 비어 있지 않음 | DB 접속 |
| `DAMSO_DB_PASSWORD` | 비어 있지 않음 | DB 접속 |
| `DAMSO_COLUMN_ENCRYPTION_KEY` | Base64, 디코딩 32바이트 이상 | 컬럼 암호화 |
| `DAMSO_JWT_SECRET` | Base64, 디코딩 32바이트 이상 | 토큰 서명 검증 |
| `DAMSO_OPENAI_API_KEY` | 비어 있지 않음 | 외부 AI(F3) |

실패 시 메시지 예: `필수 시크릿 검증 실패: DAMSO_JWT_SECRET(키 길이 부족), DAMSO_OPENAI_API_KEY(누락)` — **값은 절대 포함하지 않는다.**

## ErrorCode (고정 목록)

| code | HTTP | 일반 메시지 | 발생 |
|---|---|---|---|
| `INVALID_INPUT` | 400 | 입력값이 올바르지 않습니다. | 검증 실패, 본문 파싱 실패, 타입 불일치 |
| `UNAUTHORIZED` | 401 | 인증이 필요합니다. | 토큰 없음·위조·만료 |
| `FORBIDDEN` | 403 | 권한이 없습니다. | 인가 실패 |
| `NOT_FOUND` | 404 | 요청한 대상을 찾을 수 없습니다. | 없는 경로·리소스 |
| `METHOD_NOT_ALLOWED` | 405 | 허용되지 않은 요청 방식입니다. | 지원하지 않는 HTTP 메서드 |
| `CONFLICT` | 409 | 다른 요청과 충돌했습니다. 다시 시도해 주세요. | 낙관적 락 실패 |
| `PAYLOAD_TOO_LARGE` | 413 | 요청 크기가 너무 큽니다. | 64KB 초과 |
| `UNSUPPORTED_MEDIA_TYPE` | 415 | 지원하지 않는 형식입니다. | Content-Type 불일치 |
| `INTERNAL_ERROR` | 500 | 일시적인 오류가 발생했습니다. | 그 외 모든 예외 |

- 기능별 에러 코드는 각 기능 spec에서 이 목록에 **추가**한다. 기존 코드의 의미는 바꾸지 않는다.
- 메시지는 고정 문구다. 예외 메시지·입력값을 메시지에 넣지 않는다.

## ErrorResponse (응답 형식)

| 필드 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `code` | string (ErrorCode) | ✅ | 위 목록 중 하나 |
| `message` | string | ✅ | 해당 코드의 고정 문구 |
| `fieldErrors` | array | ❌ | `INVALID_INPUT`일 때만. `{ field, reason }` 목록 |

- `reason`은 검증 규칙 이름 수준의 고정 문구다(예: "필수 값입니다", "최대 2000자입니다"). **입력값은 넣지 않는다**(FR-024).
- 상세 스키마: [contracts/foundation.openapi.yaml](./contracts/foundation.openapi.yaml)

## 인증 주체 (토큰 클레임 — 검증 측 약속)

발급은 F1이 하지만, 기반의 검증기가 기대하는 최소 형식을 여기서 고정한다.

| 클레임 | 규칙 |
|---|---|
| `alg` (헤더) | `HS256`만 허용 |
| `sub` | 사용자 UUID |
| `role` | `PARENT` · `TEACHER` · `SUPER_ADMIN` 중 하나 → 권한 `ROLE_{role}` |
| `exp` | 필수. 지나면 `UNAUTHORIZED` |
| `iat` | 필수 |
