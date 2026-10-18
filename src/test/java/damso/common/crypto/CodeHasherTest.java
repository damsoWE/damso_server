package damso.common.crypto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Base64;

import org.junit.jupiter.api.Test;

import damso.support.TestSecrets;

class CodeHasherTest {

	private static String key() {
		return Base64.getEncoder().encodeToString(TestSecrets.codeHashKey());
	}

	@Test
	void sameInputGivesSameHashOfLength64() {
		CodeHasher hasher = new CodeHasher(key());
		String first = hasher.hash("K7M3QX9PRT");
		assertThat(first).hasSize(64).matches("[0-9a-f]{64}");
		assertThat(hasher.hash("K7M3QX9PRT")).isEqualTo(first);
	}

	@Test
	void differentKeyGivesDifferentHash() {
		String other = Base64.getEncoder().encodeToString(TestSecrets.randomBytes(32));
		assertThat(new CodeHasher(other).hash("123456")).isNotEqualTo(new CodeHasher(key()).hash("123456"));
	}

	@Test
	void differentInputGivesDifferentHash() {
		CodeHasher hasher = new CodeHasher(key());
		assertThat(hasher.hash("123456")).isNotEqualTo(hasher.hash("123457"));
	}

}
