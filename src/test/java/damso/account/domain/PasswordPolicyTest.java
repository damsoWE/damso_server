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

	/** BCrypt는 72바이트까지만 받는다. 64자 안이라도 한글처럼 여러 바이트인 글자로 72바이트를 넘으면 정책에서 거부한다. */
	@Test
	void rejectsPasswordsOverSeventyTwoBytesEvenWithinSixtyFourCharacters() {
		String seventyFourBytes = "가".repeat(24) + "a1";
		assertThat(seventyFourBytes.length()).isLessThanOrEqualTo(PasswordPolicy.MAX_LENGTH);

		assertThat(PasswordPolicy.isValid(seventyFourBytes)).isFalse();
		assertThat(PasswordPolicy.isValid("가".repeat(23) + "a1")).isTrue();
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
