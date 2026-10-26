package damso.common.config;

import java.util.ArrayList;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.PropertyResolver;
import org.springframework.util.StringUtils;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 필수 시크릿을 프로퍼티 바인딩 이전에 검사하고, 문제가 있으면 기동을 거부한다 (constitution 원칙 VIII).
 * 바인딩 단계에서 검증하면 Boot 실패 리포트가 거부된 값을 출력할 수 있으므로 여기서 직접 검사한다 (research R7).
 * 실패 메시지에는 변수 이름과 사유만 넣고 값은 절대 넣지 않는다 (FR-016).
 */
public class SecretsValidator implements EnvironmentPostProcessor, Ordered {

	static final List<String> REQUIRED = List.of(
			"DAMSO_DB_URL", "DAMSO_DB_USERNAME", "DAMSO_DB_PASSWORD", "DAMSO_OPENAI_API_KEY",
			"DAMSO_MAIL_HOST", "DAMSO_MAIL_FROM", "DAMSO_REDIS_HOST",
			// F3: 외부 AI 모델 이름. 코드에 두지 않고 배포 환경에서 정한다 (specs/003 research R5)
			"DAMSO_OPENAI_MODEL");

	static final List<String> REQUIRED_KEYS = List.of(
			"DAMSO_COLUMN_ENCRYPTION_KEY", "DAMSO_JWT_SECRET", "DAMSO_CODE_HASH_KEY");

	static final List<String> REQUIRED_PORTS = List.of("DAMSO_MAIL_PORT", "DAMSO_REDIS_PORT");

	static final String FCM_CREDENTIALS = "DAMSO_FCM_CREDENTIALS";

	static final String SUPER_ADMIN_EMAIL = "DAMSO_SUPER_ADMIN_EMAIL";

	static final String SUPER_ADMIN_PASSWORD = "DAMSO_SUPER_ADMIN_PASSWORD";

	static final int MIN_KEY_BYTES = 32;

	@Override
	public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
		List<String> problems = findProblems(environment);
		if (!problems.isEmpty()) {
			throw new IllegalStateException("필수 시크릿 검증 실패: " + String.join(", ", problems));
		}
	}

	static List<String> findProblems(PropertyResolver env) {
		List<String> problems = new ArrayList<>();
		for (String name : REQUIRED) {
			if (!StringUtils.hasText(env.getProperty(name))) {
				problems.add(name + "(누락)");
			}
		}
		for (String name : REQUIRED_KEYS) {
			String value = env.getProperty(name);
			if (!StringUtils.hasText(value)) {
				problems.add(name + "(누락)");
				continue;
			}
			byte[] decoded;
			try {
				decoded = Base64.getDecoder().decode(value.trim());
			}
			catch (IllegalArgumentException ex) {
				problems.add(name + "(형식 오류)");
				continue;
			}
			if (decoded.length < MIN_KEY_BYTES) {
				problems.add(name + "(키 길이 부족)");
			}
		}
		for (String name : REQUIRED_PORTS) {
			String value = env.getProperty(name);
			if (!StringUtils.hasText(value)) {
				problems.add(name + "(누락)");
			}
			else if (!isValidPort(value.trim())) {
				problems.add(name + "(형식 오류)");
			}
		}
		checkFcmCredentials(env.getProperty(FCM_CREDENTIALS), problems);
		// 슈퍼 관리자는 둘 다 있거나 둘 다 없어야 한다. 값은 출력하지 않는다.
		boolean hasEmail = StringUtils.hasText(env.getProperty(SUPER_ADMIN_EMAIL));
		boolean hasPassword = StringUtils.hasText(env.getProperty(SUPER_ADMIN_PASSWORD));
		if (hasEmail != hasPassword) {
			problems.add((hasEmail ? SUPER_ADMIN_PASSWORD : SUPER_ADMIN_EMAIL) + "(쌍 누락)");
		}
		return problems;
	}

	/** Base64로 디코딩한 JSON에 "type":"service_account"가 있어야 한다. 값은 메시지에 넣지 않는다. */
	private static void checkFcmCredentials(String value, List<String> problems) {
		if (!StringUtils.hasText(value)) {
			problems.add(FCM_CREDENTIALS + "(누락)");
			return;
		}
		String json;
		try {
			json = new String(Base64.getDecoder().decode(value.trim()), StandardCharsets.UTF_8);
		}
		catch (IllegalArgumentException ex) {
			problems.add(FCM_CREDENTIALS + "(형식 오류)");
			return;
		}
		try {
			JsonNode type = new ObjectMapper().readTree(json).get("type");
			if (type == null || !"service_account".equals(type.asString())) {
				problems.add(FCM_CREDENTIALS + "(형식 오류)");
			}
		}
		catch (JacksonException ex) {
			problems.add(FCM_CREDENTIALS + "(형식 오류)");
		}
	}

	private static boolean isValidPort(String value) {
		try {
			int port = Integer.parseInt(value);
			return port >= 1 && port <= 65535;
		}
		catch (NumberFormatException ex) {
			return false;
		}
	}

	/** application*.yml 로드 이후에 실행해 모든 프로퍼티 소스를 본다. */
	@Override
	public int getOrder() {
		return Ordered.LOWEST_PRECEDENCE;
	}

}
