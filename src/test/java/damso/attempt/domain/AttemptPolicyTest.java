package damso.attempt.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** FR-011·024·030·032: 대기·잠금 규칙의 경계값. */
class AttemptPolicyTest {

	@ParameterizedTest
	@CsvSource({ "1,0", "2,0", "3,30", "4,60", "5,120", "6,300", "7,600", "10,600", "100,600" })
	void loginWaitGrowsWithConsecutiveFailuresAndCapsAtTenMinutes(int failures, long seconds) {
		assertThat(AttemptPolicy.waitAfterFailure(AttemptKind.LOGIN, failures)).isEqualTo(Duration.ofSeconds(seconds));
	}

	@ParameterizedTest
	@CsvSource({ "INVITE_CODE", "SIGNUP_CODE", "RESET_CODE" })
	void codeInputLocksTenMinutesOnTheFifthFailure(AttemptKind kind) {
		for (int failures = 1; failures <= 4; failures++) {
			assertThat(AttemptPolicy.waitAfterFailure(kind, failures)).isZero();
		}
		assertThat(AttemptPolicy.waitAfterFailure(kind, 5)).isEqualTo(Duration.ofMinutes(10));
		assertThat(AttemptPolicy.ruleFor(kind).restartAfterLock()).isTrue();
	}

	@Test
	void loginKeepsItsCountAfterTheWaitEnds() {
		assertThat(AttemptPolicy.ruleFor(AttemptKind.LOGIN).restartAfterLock()).isFalse();
	}

	@Test
	void mailSendHasItsOwnLimit() {
		assertThatThrownBy(() -> AttemptPolicy.ruleFor(AttemptKind.MAIL_SEND))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void stateBlocksOnlyBeforeNextAllowedTime() {
		Instant now = Instant.parse("2026-10-18T00:00:00Z");
		AttemptState state = new AttemptState(5, now.plusSeconds(30));
		assertThat(state.isBlocked(now)).isTrue();
		assertThat(state.remaining(now)).isEqualTo(Duration.ofSeconds(30));
		assertThat(state.isBlocked(now.plusSeconds(30))).isFalse();
		assertThat(state.remaining(now.plusSeconds(31))).isZero();
		assertThat(AttemptState.NONE.isBlocked(now)).isFalse();
	}

}
