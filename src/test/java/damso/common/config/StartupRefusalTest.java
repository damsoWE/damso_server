package damso.common.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Base64;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

import damso.DamsoApplication;
import damso.support.TestSecrets;

/** US2 / SC-002: 실제 기동 경로에서 시크릿이 빠지면 기동이 실패하고, 출력에 값이 없다. */
@ExtendWith(OutputCaptureExtension.class)
class StartupRefusalTest {

	@Test
	void applicationRefusesToStartWithoutSecrets(CapturedOutput output) {
		String shortKey = Base64.getEncoder().encodeToString(TestSecrets.randomBytes(16));
		StandardEnvironment env = isolatedEnvironment(Map.of(
				"DAMSO_DB_URL", "jdbc:postgresql://db/damso",
				"DAMSO_DB_USERNAME", "damso",
				"DAMSO_DB_PASSWORD", "very-secret-db-password",
				"DAMSO_JWT_SECRET", shortKey));

		SpringApplication app = new SpringApplication(DamsoApplication.class);
		app.setWebApplicationType(WebApplicationType.NONE);
		app.setEnvironment(env);

		assertThatThrownBy(app::run)
				.hasStackTraceContaining("DAMSO_COLUMN_ENCRYPTION_KEY(누락)")
				.hasStackTraceContaining("DAMSO_JWT_SECRET(키 길이 부족)")
				.hasStackTraceContaining("DAMSO_OPENAI_API_KEY(누락)")
				.hasStackTraceContaining("DAMSO_CODE_HASH_KEY(누락)")
				.hasStackTraceContaining("DAMSO_REDIS_HOST(누락)")
				.hasStackTraceContaining("DAMSO_FCM_CREDENTIALS(누락)");
		assertThat(output.getAll()).doesNotContain("very-secret-db-password").doesNotContain(shortKey);
	}

	/** 다른 테스트가 넣은 시스템 프로퍼티(TestSecrets)의 영향을 받지 않는 환경. */
	private static StandardEnvironment isolatedEnvironment(Map<String, Object> values) {
		StandardEnvironment env = new StandardEnvironment();
		env.getPropertySources().remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
		env.getPropertySources().remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
		env.getPropertySources().addFirst(new MapPropertySource("test-secrets", values));
		return env;
	}

}
