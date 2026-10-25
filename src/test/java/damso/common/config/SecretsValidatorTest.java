package damso.common.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Base64;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import damso.support.TestSecrets;

/** US2 / SC-002: 필수 시크릿 검증 (foundation 6개 + F1 추가분). 실패 메시지에 이름은 있고 값은 없다. */
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
			"DAMSO_COLUMN_ENCRYPTION_KEY", "DAMSO_JWT_SECRET", "DAMSO_OPENAI_API_KEY",
			"DAMSO_CODE_HASH_KEY", "DAMSO_MAIL_HOST", "DAMSO_MAIL_PORT", "DAMSO_MAIL_FROM",
			"DAMSO_REDIS_HOST", "DAMSO_REDIS_PORT", "DAMSO_FCM_CREDENTIALS" })
	void refusesWhenAnySecretIsMissing(String name) {
		MockEnvironment env = complete();
		env.setProperty(name, "");
		assertThatThrownBy(() -> validator.postProcessEnvironment(env, null))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining(name + "(누락)");
	}

	@ParameterizedTest
	@ValueSource(strings = { "DAMSO_COLUMN_ENCRYPTION_KEY", "DAMSO_JWT_SECRET", "DAMSO_CODE_HASH_KEY" })
	void refusesShortKeyWithoutPrintingIt(String name) {
		MockEnvironment env = complete();
		env.setProperty(name, KEY_31);
		assertThatThrownBy(() -> validator.postProcessEnvironment(env, null))
				.hasMessageContaining(name + "(키 길이 부족)")
				.message().doesNotContain(KEY_31);
	}

	@ParameterizedTest
	@ValueSource(strings = { "DAMSO_COLUMN_ENCRYPTION_KEY", "DAMSO_JWT_SECRET", "DAMSO_CODE_HASH_KEY" })
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

	@ParameterizedTest
	@ValueSource(strings = { "0", "70000", "abc", "-1" })
	void refusesInvalidPortWithoutPrintingIt(String port) {
		MockEnvironment env = complete();
		env.setProperty("DAMSO_MAIL_PORT", port);
		assertThatThrownBy(() -> validator.postProcessEnvironment(env, null))
				.hasMessageContaining("DAMSO_MAIL_PORT(형식 오류)");
	}

	@Test
	void refusesFcmCredentialsThatAreNotBase64WithoutPrintingThem() {
		MockEnvironment env = complete();
		env.setProperty("DAMSO_FCM_CREDENTIALS", "not base64 !!! fcm-secret-xyz");
		assertThatThrownBy(() -> validator.postProcessEnvironment(env, null))
				.hasMessageContaining("DAMSO_FCM_CREDENTIALS(형식 오류)")
				.message().doesNotContain("fcm-secret-xyz");
	}

	@Test
	void refusesFcmCredentialsThatAreNotJson() {
		MockEnvironment env = complete();
		env.setProperty("DAMSO_FCM_CREDENTIALS", base64("this is not json"));
		assertThatThrownBy(() -> validator.postProcessEnvironment(env, null))
				.hasMessageContaining("DAMSO_FCM_CREDENTIALS(형식 오류)");
	}

	@Test
	void refusesFcmCredentialsWhoseTypeIsNotServiceAccount() {
		MockEnvironment env = complete();
		env.setProperty("DAMSO_FCM_CREDENTIALS", base64("{\"type\":\"authorized_user\",\"private_key\":\"pk-secret-xyz\"}"));
		assertThatThrownBy(() -> validator.postProcessEnvironment(env, null))
				.hasMessageContaining("DAMSO_FCM_CREDENTIALS(형식 오류)")
				.message().doesNotContain("pk-secret-xyz");
	}

	@Test
	void superAdminVariablesMustComeAsAPair() {
		MockEnvironment onlyEmail = complete();
		onlyEmail.setProperty("DAMSO_SUPER_ADMIN_EMAIL", "admin@school.example");
		assertThatThrownBy(() -> validator.postProcessEnvironment(onlyEmail, null))
				.hasMessageContaining("DAMSO_SUPER_ADMIN_PASSWORD(쌍 누락)")
				.message().doesNotContain("admin@school.example");

		MockEnvironment onlyPassword = complete();
		onlyPassword.setProperty("DAMSO_SUPER_ADMIN_PASSWORD", "Passw0rd-secret-xyz");
		assertThatThrownBy(() -> validator.postProcessEnvironment(onlyPassword, null))
				.hasMessageContaining("DAMSO_SUPER_ADMIN_EMAIL(쌍 누락)")
				.message().doesNotContain("Passw0rd-secret-xyz");
	}

	@Test
	void superAdminVariablesAreOptionalAsAPair() {
		assertThat(SecretsValidator.findProblems(complete())).isEmpty();
		MockEnvironment both = complete();
		both.setProperty("DAMSO_SUPER_ADMIN_EMAIL", "admin@school.example");
		both.setProperty("DAMSO_SUPER_ADMIN_PASSWORD", "Passw0rd-secret-xyz");
		assertThat(SecretsValidator.findProblems(both)).isEmpty();
	}

	private static String base64(String text) {
		return Base64.getEncoder().encodeToString(text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
	}

	private static MockEnvironment complete() {
		return new MockEnvironment()
				.withProperty("DAMSO_DB_URL", "jdbc:postgresql://db/damso")
				.withProperty("DAMSO_DB_USERNAME", "damso")
				.withProperty("DAMSO_DB_PASSWORD", "db-password-value")
				.withProperty("DAMSO_COLUMN_ENCRYPTION_KEY", KEY_32)
				.withProperty("DAMSO_JWT_SECRET", KEY_32)
				.withProperty("DAMSO_OPENAI_API_KEY", "openai-key-value")
				.withProperty("DAMSO_CODE_HASH_KEY", KEY_32)
				.withProperty("DAMSO_MAIL_HOST", "smtp.example")
				.withProperty("DAMSO_MAIL_PORT", "587")
				.withProperty("DAMSO_MAIL_FROM", "no-reply@damso.example")
				.withProperty("DAMSO_REDIS_HOST", "redis")
				.withProperty("DAMSO_REDIS_PORT", "6379")
				.withProperty("DAMSO_FCM_CREDENTIALS", base64("{\"type\":\"service_account\"}"));
	}

}
