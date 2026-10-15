package damso.support;

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

	static {
		System.setProperty("DAMSO_DB_URL", "jdbc:postgresql://overridden-by-testcontainers/damso");
		System.setProperty("DAMSO_DB_USERNAME", "test");
		System.setProperty("DAMSO_DB_PASSWORD", "test");
		System.setProperty("DAMSO_COLUMN_ENCRYPTION_KEY", Base64.getEncoder().encodeToString(COLUMN_KEY));
		System.setProperty("DAMSO_JWT_SECRET", Base64.getEncoder().encodeToString(JWT_KEY));
		System.setProperty("DAMSO_OPENAI_API_KEY", "test-not-a-real-key");
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

	public static byte[] randomBytes(int length) {
		byte[] bytes = new byte[length];
		RANDOM.nextBytes(bytes);
		return bytes;
	}

}
