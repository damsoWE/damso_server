package damso.common.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Base64;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import damso.support.TestSecrets;

/** US2 / SC-002: 필수 시크릿 6개 검증. 실패 메시지에 이름은 있고 값은 없다. */
class SecretsValidatorTest {

	private static final String KEY_32 = Base64.getEncoder().encodeToString(TestSecrets.randomBytes(32));
	private static final String KEY_31 = Base64.getEncoder().encodeToString(TestSecrets.randomBytes(31));

	private final SecretsValidator validator = new SecretsValidator();

	@Test
	void passesWhenAllSecretsAreValid() {
		assertThat(SecretsValidator.findProblems(complete())).isEmpty();
	}

	@ParameterizedTest
	@ValueSource(strings = { "DAMSO_DB_URL", "DAMSO_DB_USERNAME", "DAMSO_DB_PASSWORD",
			"DAMSO_COLUMN_ENCRYPTION_KEY", "DAMSO_JWT_SECRET", "DAMSO_OPENAI_API_KEY" })
	void refusesWhenAnySecretIsMissing(String name) {
		MockEnvironment env = complete();
		env.setProperty(name, "");
		assertThatThrownBy(() -> validator.postProcessEnvironment(env, null))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining(name + "(누락)");
	}

	@ParameterizedTest
	@ValueSource(strings = { "DAMSO_COLUMN_ENCRYPTION_KEY", "DAMSO_JWT_SECRET" })
	void refusesShortKeyWithoutPrintingIt(String name) {
		MockEnvironment env = complete();
		env.setProperty(name, KEY_31);
		assertThatThrownBy(() -> validator.postProcessEnvironment(env, null))
				.hasMessageContaining(name + "(키 길이 부족)")
				.message().doesNotContain(KEY_31);
	}

	@ParameterizedTest
	@ValueSource(strings = { "DAMSO_COLUMN_ENCRYPTION_KEY", "DAMSO_JWT_SECRET" })
	void refusesNonBase64KeyWithoutPrintingIt(String name) {
		MockEnvironment env = complete();
		env.setProperty(name, "not base64 !!! secret-value-xyz");
		assertThatThrownBy(() -> validator.postProcessEnvironment(env, null))
				.hasMessageContaining(name + "(형식 오류)")
				.message().doesNotContain("secret-value-xyz");
	}

	@Test
	void reportsAllProblemsAtOnceWithNamesOnly() {
		MockEnvironment env = complete();
		env.setProperty("DAMSO_DB_PASSWORD", "");
		env.setProperty("DAMSO_JWT_SECRET", KEY_31);
		assertThatThrownBy(() -> validator.postProcessEnvironment(env, null))
				.hasMessageContaining("DAMSO_DB_PASSWORD(누락)")
				.hasMessageContaining("DAMSO_JWT_SECRET(키 길이 부족)")
				.message().doesNotContain(KEY_31).doesNotContain(KEY_32);
	}

	private static MockEnvironment complete() {
		return new MockEnvironment()
				.withProperty("DAMSO_DB_URL", "jdbc:postgresql://db/damso")
				.withProperty("DAMSO_DB_USERNAME", "damso")
				.withProperty("DAMSO_DB_PASSWORD", "db-password-value")
				.withProperty("DAMSO_COLUMN_ENCRYPTION_KEY", KEY_32)
				.withProperty("DAMSO_JWT_SECRET", KEY_32)
				.withProperty("DAMSO_OPENAI_API_KEY", "openai-key-value");
	}

}
