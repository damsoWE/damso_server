package damso.common.crypto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

/** 영문 대문자+숫자 10자리, 헷갈리는 문자 제외. */
class SecureCodesTest {

	@Test
	void inviteCodeUsesOnlyAllowedCharactersAndLengthTen() {
		Set<String> seen = new HashSet<>();
		for (int i = 0; i < 1000; i++) {
			String code = SecureCodes.inviteCode();
			assertThat(code).hasSize(10).matches("[A-HJKMNP-Z2-9]{10}");
			assertThat(code).doesNotContain("0", "O", "1", "I", "L");
			seen.add(code);
		}
		assertThat(seen).hasSize(1000);
	}

	@Test
	void sixDigitsAreZeroPaddedNumbers() {
		for (int i = 0; i < 1000; i++) {
			assertThat(SecureCodes.sixDigits()).matches("[0-9]{6}");
		}
	}

	@Test
	void refreshTokensAreUniqueUrlSafeValues() {
		Set<String> seen = new HashSet<>();
		for (int i = 0; i < 200; i++) {
			String token = SecureCodes.refreshToken();
			assertThat(token).matches("[A-Za-z0-9_-]{43}");
			seen.add(token);
		}
		assertThat(seen).hasSize(200);
	}

}
