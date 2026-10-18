package damso.account.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import damso.common.error.InvalidInputException;

/** FR-028, 체크리스트 보안기능 9: 8~64자, 영문 1자 이상 + 숫자 1자 이상. */
class PasswordPolicyTest {

	@ParameterizedTest
	@ValueSource(strings = { "abcd123", "12345678", "abcdefgh", "가나다라마바사아1" })
	void rejectsTooShortOrMissingLetterOrDigit(String password) {
		assertThat(PasswordPolicy.isValid(password)).isFalse();
	}

	@Test
	void rejectsSixtyFiveCharactersAndAcceptsSixtyFour() {
		assertThat(PasswordPolicy.isValid("a1".repeat(32) + "a")).isFalse();
		assertThat(PasswordPolicy.isValid("a1".repeat(32))).isTrue();
	}

	@Test
	void acceptsLettersAndDigits() {
		assertThat(PasswordPolicy.isValid("abcd1234")).isTrue();
		assertThat(PasswordPolicy.isValid("Passw0rd!")).isTrue();
	}

	@Test
	void rejectsNull() {
		assertThat(PasswordPolicy.isValid(null)).isFalse();
	}

	@Test
	void validateThrowsFixedReasonWithoutEchoingTheInput() {
		assertThatThrownBy(() -> PasswordPolicy.validate("short1", "password"))
				.isInstanceOfSatisfying(InvalidInputException.class, ex -> {
					assertThat(ex.field()).isEqualTo("password");
					assertThat(ex.reason()).doesNotContain("short1");
				});
	}

}
