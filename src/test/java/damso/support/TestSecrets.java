package damso.support;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 테스트용 시크릿을 실행 시점에 무작위로 만들어 시스템 프로퍼티로 넣는다.
 * 시크릿 값을 파일에 커밋하지 않기 위함이다 (Gitleaks, constitution 원칙 VIII).
 * DB 접속값은 더미이며 실제 접속은 Testcontainers @ServiceConnection이 덮어쓴다.
 */
public final class TestSecrets {

	private static final SecureRandom RANDOM = new SecureRandom();
	private static final byte[] JWT_KEY = randomBytes(32);
	private static final byte[] COLUMN_KEY = randomBytes(32);
	private static final byte[] CODE_HASH_KEY = randomBytes(32);

	static {
		System.setProperty("DAMSO_DB_URL", "jdbc:postgresql://overridden-by-testcontainers/damso");
		System.setProperty("DAMSO_DB_USERNAME", "test");
		System.setProperty("DAMSO_DB_PASSWORD", "test");
		System.setProperty("DAMSO_COLUMN_ENCRYPTION_KEY", Base64.getEncoder().encodeToString(COLUMN_KEY));
		System.setProperty("DAMSO_JWT_SECRET", Base64.getEncoder().encodeToString(JWT_KEY));
		System.setProperty("DAMSO_OPENAI_API_KEY", "test-not-a-real-key");
		// 테스트는 ExternalAiPort를 가짜로 바꿔 외부 AI에 접속하지 않는다
		System.setProperty("DAMSO_OPENAI_MODEL", "test-model");
		System.setProperty("DAMSO_CODE_HASH_KEY", Base64.getEncoder().encodeToString(CODE_HASH_KEY));
		// 실제 Firebase 값이 아닌 모양만 맞춘 JSON. 테스트는 PushSenderPort를 가짜로 바꿔 FCM에 접속하지 않는다.
		System.setProperty("DAMSO_FCM_CREDENTIALS", Base64.getEncoder().encodeToString(
				"{\"type\":\"service_account\",\"project_id\":\"damso-test\"}".getBytes(StandardCharsets.UTF_8)));
		// 메일은 테스트에서 가짜 포트로 대체되고, Redis 접속은 Testcontainers @ServiceConnection이 덮어쓴다.
		System.setProperty("DAMSO_MAIL_HOST", "localhost");
		System.setProperty("DAMSO_MAIL_PORT", "2525");
		System.setProperty("DAMSO_MAIL_FROM", "no-reply@damso.test");
		System.setProperty("DAMSO_REDIS_HOST", "overridden-by-testcontainers");
		System.setProperty("DAMSO_REDIS_PORT", "6379");
	}

	private TestSecrets() {
	}

	/** 클래스 로딩을 강제해 정적 초기화(시스템 프로퍼티 설정)를 실행한다. */
	public static void init() {
		// static initializer does the work
	}

	public static byte[] jwtKey() {
		return JWT_KEY.clone();
	}

	public static byte[] columnKey() {
		return COLUMN_KEY.clone();
	}

	public static byte[] codeHashKey() {
		return CODE_HASH_KEY.clone();
	}

	public static byte[] randomBytes(int length) {
		byte[] bytes = new byte[length];
		RANDOM.nextBytes(bytes);
		return bytes;
	}

}
