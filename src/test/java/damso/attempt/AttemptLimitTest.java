package damso.attempt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.Base64;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import damso.attempt.adapter.out.redis.RedisAttemptLimitAdapter;
import damso.attempt.application.port.in.AttemptLimitUseCase;
import damso.attempt.domain.AttemptKind;
import damso.attempt.domain.AttemptPolicy;
import damso.attempt.domain.AttemptState;
import damso.common.crypto.CodeHasher;
import damso.common.error.BusinessException;
import damso.common.error.ErrorCode;
import damso.common.error.TooManyAttemptsException;
import damso.support.IntegrationTestSupport;
import damso.support.TestSecrets;

/** SC-004, FR-011·024: Redis 기반 대기·잠금. 시간은 MutableClock으로 옮긴다. */
class AttemptLimitTest extends IntegrationTestSupport {

	@Autowired
	private AttemptLimitUseCase attempts;

	@Autowired
	private RedisAttemptLimitAdapter adapter;

	private static String subject() {
		return "user-" + UUID.randomUUID() + "@damso.test";
	}

	private long retryAfter(AttemptKind kind, String subject) {
		try {
			attempts.ensureAllowed(kind, subject);
		}
		catch (TooManyAttemptsException ex) {
			return ex.retryAfterSeconds();
		}
		throw new AssertionError("expected to be blocked");
	}

	@Test
	void loginAllowsTwoFailuresThenWaitsThirtySeconds() {
		String subject = subject();
		attempts.recordFailure(AttemptKind.LOGIN, subject);
		attempts.recordFailure(AttemptKind.LOGIN, subject);
		attempts.ensureAllowed(AttemptKind.LOGIN, subject);

		attempts.recordFailure(AttemptKind.LOGIN, subject);
		assertThat(retryAfter(AttemptKind.LOGIN, subject)).isBetween(29L, 30L);

		clock.advance(Duration.ofSeconds(31));
		attempts.ensureAllowed(AttemptKind.LOGIN, subject);
	}

	@Test
	void loginWaitGrowsAndKeepsTheCountAfterTheWaitEnds() {
		String subject = subject();
		for (int i = 0; i < 3; i++) {
			attempts.recordFailure(AttemptKind.LOGIN, subject);
		}
		clock.advance(Duration.ofSeconds(31));
		attempts.recordFailure(AttemptKind.LOGIN, subject);
		assertThat(retryAfter(AttemptKind.LOGIN, subject)).isBetween(59L, 60L);

		clock.advance(Duration.ofSeconds(61));
		attempts.recordFailure(AttemptKind.LOGIN, subject);
		assertThat(retryAfter(AttemptKind.LOGIN, subject)).isBetween(119L, 120L);
	}

	@Test
	void loginWaitIsCappedAtTenMinutes() {
		String subject = subject();
		for (int i = 0; i < 12; i++) {
			clock.advance(Duration.ofMinutes(11));
			attempts.recordFailure(AttemptKind.LOGIN, subject);
		}
		assertThat(retryAfter(AttemptKind.LOGIN, subject)).isLessThanOrEqualTo(600L);
	}

	@Test
	void attemptsDuringTheWaitDoNotRaiseTheCount() {
		String subject = subject();
		for (int i = 0; i < 3; i++) {
			attempts.recordFailure(AttemptKind.LOGIN, subject);
		}
		for (int i = 0; i < 10; i++) {
			attempts.recordFailure(AttemptKind.LOGIN, subject);
		}
		clock.advance(Duration.ofSeconds(31));
		attempts.recordFailure(AttemptKind.LOGIN, subject);
		assertThat(retryAfter(AttemptKind.LOGIN, subject)).isBetween(59L, 60L);
	}

	@Test
	void successResetsTheCount() {
		String subject = subject();
		for (int i = 0; i < 3; i++) {
			attempts.recordFailure(AttemptKind.LOGIN, subject);
		}
		attempts.reset(AttemptKind.LOGIN, subject);
		attempts.ensureAllowed(AttemptKind.LOGIN, subject);
		attempts.recordFailure(AttemptKind.LOGIN, subject);
		attempts.recordFailure(AttemptKind.LOGIN, subject);
		attempts.ensureAllowed(AttemptKind.LOGIN, subject);
	}

	@Test
	void codeInputLocksTenMinutesOnTheFifthFailureAndRestartsFromZero() {
		String subject = subject();
		for (int i = 0; i < 4; i++) {
			attempts.recordFailure(AttemptKind.INVITE_CODE, subject);
			attempts.ensureAllowed(AttemptKind.INVITE_CODE, subject);
		}
		attempts.recordFailure(AttemptKind.INVITE_CODE, subject);
		assertThat(retryAfter(AttemptKind.INVITE_CODE, subject)).isBetween(599L, 600L);

		clock.advance(Duration.ofMinutes(10).plusSeconds(1));
		attempts.ensureAllowed(AttemptKind.INVITE_CODE, subject);
		for (int i = 0; i < 4; i++) {
			attempts.recordFailure(AttemptKind.INVITE_CODE, subject);
		}
		attempts.ensureAllowed(AttemptKind.INVITE_CODE, subject);
	}

	@Test
	void kindsAndSubjectsAreCountedSeparately() {
		String first = subject();
		String second = subject();
		for (int i = 0; i < 5; i++) {
			attempts.recordFailure(AttemptKind.SIGNUP_CODE, first);
		}
		assertThat(retryAfter(AttemptKind.SIGNUP_CODE, first)).isPositive();
		attempts.ensureAllowed(AttemptKind.SIGNUP_CODE, second);
		attempts.ensureAllowed(AttemptKind.RESET_CODE, first);
	}

	@Test
	void redisKeysDoNotContainThePlainSubject() {
		String subject = "plain-" + UUID.randomUUID() + "@damso.test";
		attempts.recordFailure(AttemptKind.LOGIN, subject);
		var keys = redis.keys("damso:attempt:*");
		assertThat(keys).isNotEmpty();
		assertThat(keys).noneMatch(key -> key.contains(subject) || key.contains("@"));
	}

	@Test
	void mailSendIsLimitedToOnePerMinuteAndFivePerHour() {
		String subject = subject();
		assertThat(attempts.reserveMailSend(subject)).isZero();
		assertThat(attempts.reserveMailSend(subject)).isBetween(59L, 60L);
		assertThatThrownBy(() -> attempts.ensureMailSendAllowed(subject)).isInstanceOf(TooManyAttemptsException.class);

		for (int i = 0; i < 4; i++) {
			clock.advance(Duration.ofSeconds(61));
			assertThat(attempts.reserveMailSend(subject)).isZero();
		}
		clock.advance(Duration.ofSeconds(61));
		assertThat(attempts.reserveMailSend(subject)).isPositive();
		clock.advance(Duration.ofHours(1));
		assertThat(attempts.reserveMailSend(subject)).isZero();
	}

	@Test
	void unreachableRedisRejectsTheRequestInsteadOfSkippingTheLimit() {
		LettuceConnectionFactory factory = new LettuceConnectionFactory(new RedisStandaloneConfiguration("localhost", 1));
		factory.afterPropertiesSet();
		try {
			RedisAttemptLimitAdapter broken = new RedisAttemptLimitAdapter(new StringRedisTemplate(factory));
			assertThatThrownBy(() -> broken.get(AttemptKind.LOGIN, "hash"))
					.isInstanceOfSatisfying(BusinessException.class,
							ex -> assertThat(ex.errorCode()).isEqualTo(ErrorCode.INTERNAL_ERROR));
			assertThatThrownBy(() -> broken.recordFailure(AttemptKind.LOGIN, "hash", clock.instant(),
					AttemptPolicy.ruleFor(AttemptKind.LOGIN))).isInstanceOf(BusinessException.class);
			assertThatThrownBy(() -> broken.reserveMailSend("hash", clock.instant()))
					.isInstanceOf(BusinessException.class);
		}
		finally {
			factory.destroy();
		}
	}

	@Test
	void stateReflectsStoredValues() {
		String subject = subject();
		for (int i = 0; i < 3; i++) {
			attempts.recordFailure(AttemptKind.LOGIN, subject);
		}
		String hash = new CodeHasher(Base64.getEncoder().encodeToString(TestSecrets.codeHashKey())).hash(subject);
		AttemptState state = adapter.get(AttemptKind.LOGIN, hash);
		assertThat(state.failedCount()).isEqualTo(3);
		assertThat(state.nextAllowedAt()).isNotNull();
	}

}
