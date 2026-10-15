package damso.common.config;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.PropertyResolver;
import org.springframework.util.StringUtils;

/**
 * 필수 시크릿을 프로퍼티 바인딩 이전에 검사하고, 문제가 있으면 기동을 거부한다 (constitution 원칙 VIII).
 * 바인딩 단계에서 검증하면 Boot 실패 리포트가 거부된 값을 출력할 수 있으므로 여기서 직접 검사한다 (research R7).
 * 실패 메시지에는 변수 이름과 사유만 넣고 값은 절대 넣지 않는다 (FR-016).
 */
public class SecretsValidator implements EnvironmentPostProcessor, Ordered {

	static final List<String> REQUIRED = List.of(
			"DAMSO_DB_URL", "DAMSO_DB_USERNAME", "DAMSO_DB_PASSWORD", "DAMSO_OPENAI_API_KEY");

	static final List<String> REQUIRED_KEYS = List.of("DAMSO_COLUMN_ENCRYPTION_KEY", "DAMSO_JWT_SECRET");

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
		return problems;
	}

	/** application*.yml 로드 이후에 실행해 모든 프로퍼티 소스를 본다. */
	@Override
	public int getOrder() {
		return Ordered.LOWEST_PRECEDENCE;
	}

}
