# Quickstart: 공통 기반 검증

**Feature**: [spec.md](./spec.md) · 설계: [data-model.md](./data-model.md) · 약속: [contracts/](./contracts/foundation.openapi.yaml)

구현이 끝난 뒤 이 순서로 spec의 성공 기준(SC)을 확인한다.

## 사전 준비

- Docker 실행 중 (영속성 테스트가 PostgreSQL 컨테이너를 띄운다)
- JDK는 Gradle이 자동으로 21을 받는다(R13). 처음 한 번은 다운로드 시간이 걸린다.
- 로컬 실행용 환경변수 (값은 예시가 아니라 **직접 생성**한다)

```bash
# 32바이트 무작위 키 2개 생성 (Git Bash / macOS)
openssl rand -base64 32   # → DAMSO_COLUMN_ENCRYPTION_KEY
openssl rand -base64 32   # → DAMSO_JWT_SECRET
```

필요한 변수 이름은 `.env.example` 참고. 실제 값은 `.env`(커밋 금지)에 둔다.

## 1. 자동화 테스트 — SC-001 ~ SC-006

```bash
./gradlew test          # Windows: .\gradlew.bat test
```

| 확인 대상 | 기대 결과 |
|---|---|
| 에러 응답 9종 | 모두 `ErrorResponse` 형식, 금지 정보 0건 (SC-001) |
| 시크릿 6종 누락·키 길이 부족 | 기동 거부 100%, 출력에 값 0건 (SC-002) |
| 공개 경로 외 무인증 호출 | `UNAUTHORIZED` 100%, `Set-Cookie` 0건 (SC-003) |
| 민감 필드 저장 후 DB 원본 조회 | 평문 일치 0건, 재조회 시 원문 100% 일치 (SC-004) |
| 민감 값 로그 출력 | 로그에 원문 0건 (SC-005) |
| 같은 버전 동시 수정 | 두 번째 요청 `CONFLICT` 100% (SC-006) |

실패가 있으면 숨기지 말고 실패한 테스트 이름과 함께 공유한다.

## 2. 수동 확인 — 서버 기동

```bash
# 시크릿 없이 기동 → 실패해야 정상
./gradlew bootRun
# 기대: "필수 시크릿 검증 실패: DAMSO_DB_URL(누락), ..." — 이름만, 값 없음
```

```bash
# 시크릿을 넣고 기동 (PostgreSQL이 떠 있어야 한다)
./gradlew bootRun
curl -i http://localhost:8080/actuator/health          # 200, {"status":"UP"}
curl -i http://localhost:8080/anything                 # 401, {"code":"UNAUTHORIZED",...}, Set-Cookie 없음
curl -i -X POST http://localhost:8080/actuator/health  # 401 또는 405, ErrorResponse 형식
```

## 3. 시크릿 스캔 — SC-007

```bash
gitleaks git . --redact
# 기대: no leaks found
```

## 4. 체크리스트 갱신 — SC-008

`docs/security-checklist.md`에서 아래 12개 항목을 `조치`로 바꾸고 근거(파일 경로·테스트 이름)를 적는다.

에러처리 1·3 · 보안기능 1·4·5·6·7·8·10 · 시간 및 상태 1 · 캡슐화 2 · 입력데이터 검증 14

## 5. 개발자 사용성 — SC-009

새 엔티티를 만들 때 아래만 하면 되는지 확인한다.

- `BaseEntity` 상속 → UUID·생성/수정 시각·버전 자동
- 민감 필드는 타입을 `SensitiveText`로 → 암호화·로그 가림 자동
- 기능 코드 안에 암호화·마스킹 코드를 직접 쓴 곳이 0곳
