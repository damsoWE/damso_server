package damso.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.mock.web.MockHttpServletResponse;

import damso.account.domain.Role;
import damso.account.domain.UserAccount;
import damso.attempt.adapter.out.redis.RedisAttemptLimitAdapter;
import damso.attempt.application.port.out.AttemptLimitPort;
import damso.attempt.domain.AttemptKind;
import damso.attempt.domain.AttemptRule;
import damso.attempt.domain.AttemptState;
import damso.common.error.BusinessException;
import damso.common.error.ErrorCode;
import damso.support.IntegrationTestSupport;

/** US4 시나리오 1~5, FR-022~024a, SC-003·004: 로그인과 단계별 대기, 요청 위치 단위 상한. */
class LoginApiTest extends IntegrationTestSupport {

	/** 요청 위치 단위 로그인 제한(LOGIN_CLIENT) 저장소만 장애로 만들 수 있다. 이 테스트 클래스에서만 쓴다. */
	@TestConfiguration
	static class FailableClientLimitConfig {

		static volatile boolean down;

		@Bean
		@Primary
		AttemptLimitPort failableClientLimitPort(RedisAttemptLimitAdapter real) {
			return new AttemptLimitPort() {

				@Override
				public AttemptState get(AttemptKind kind, String subjectHash) {
					return real.get(kind, subjectHash);
				}

				@Override
				public AttemptState recordFailure(AttemptKind kind, String subjectHash, Instant now, AttemptRule rule) {
					return real.recordFailure(kind, subjectHash, now, rule);
				}

				@Override
				public void reset(AttemptKind kind, String subjectHash) {
					real.reset(kind, subjectHash);
				}

				@Override
				public long reserveRate(AttemptKind kind, String subjectHash, Instant now, Duration minGap,
						Duration window, int max) {
					if (down && kind == AttemptKind.LOGIN_CLIENT) {
						throw new BusinessException(ErrorCode.INTERNAL_ERROR);
					}
					return real.reserveRate(kind, subjectHash, now, minGap, window, max);
				}

				@Override
				public long reserveRates(Instant now, List<RateLimit> limits) {
					return real.reserveRates(now, limits);
				}

			};
		}

	}

	private MockHttpServletResponse login(String email, String password) throws Exception {
		return send(json(post("/api/v1/auth/login"),
				"{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"), null);
	}

	/** 요청 위치(IP)를 지정한 로그인. 운영에서는 Caddy의 X-Forwarded-For가 remoteAddr로 반영된다. */
	private MockHttpServletResponse loginFrom(String email, String password, String clientIp) throws Exception {
		return send(json(post("/api/v1/auth/login"),
				"{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}").with(request -> {
					request.setRemoteAddr(clientIp);
					return request;
				}), null);
	}

	/** 서로 다른 이메일로 요청 위치 하나의 상한을 모두 쓴다. */
	private void useUpClientLimit(String clientIp) throws Exception {
		for (int i = 0; i < CLIENT_LIMIT; i++) {
			assertThat(loginFrom(uniqueEmail(), WRONG, clientIp).getStatus()).isEqualTo(401);
		}
	}

	private static final String WRONG = "Wrong" + "Pass1";

	private static final int CLIENT_LIMIT = 60;

	private static final String CLIENT = "203.0.113.10";

	private static final String OTHER_CLIENT = "203.0.113.20";

	@Test
	void successReturnsTokensAndTheRole() throws Exception {
		UserAccount teacher = createAccount(Role.TEACHER);

		MockHttpServletResponse response = login(teacher.getEmail(), TEST_PASSWORD);

		assertThat(response.getStatus()).isEqualTo(200);
		assertThat((String) read(response, "$.role")).isEqualTo("TEACHER");
		assertThat((Integer) read(response, "$.accessTokenExpiresInSeconds")).isEqualTo(900);
		assertThat((Integer) read(response, "$.refreshTokenExpiresInSeconds")).isEqualTo(14 * 24 * 3600);
		assertThat(response.getHeader("Set-Cookie")).isNull();
		assertThat(send(get("/test/auth/any"), read(response, "$.accessToken")).getStatus()).isEqualTo(200);
	}

	@Test
	void emailIsCaseInsensitive() throws Exception {
		UserAccount parent = createAccount(Role.PARENT);
		assertThat(login(parent.getEmail().toUpperCase(), TEST_PASSWORD).getStatus()).isEqualTo(200);
	}

	@Test
	void unknownEmailWrongPasswordAndWithdrawnAccountGetTheSameResponse() throws Exception {
		UserAccount parent = createAccount(Role.PARENT);
		UserAccount withdrawn = createAccount(Role.PARENT);
		String withdrawnEmail = withdrawn.getEmail();
		withdrawn.withdraw(clock.instant());
		accountPort.save(withdrawn);

		MockHttpServletResponse unknown = login(uniqueEmail(), TEST_PASSWORD);
		MockHttpServletResponse wrong = login(parent.getEmail(), WRONG);
		MockHttpServletResponse gone = login(withdrawnEmail, TEST_PASSWORD);

		assertThat(unknown.getStatus()).isEqualTo(401);
		assertThat(unknown.getContentAsString()).contains("INVALID_CREDENTIALS");
		assertThat(wrong.getStatus()).isEqualTo(401);
		assertThat(wrong.getContentAsString()).isEqualTo(unknown.getContentAsString());
		assertThat(gone.getStatus()).isEqualTo(401);
		assertThat(gone.getContentAsString()).isEqualTo(unknown.getContentAsString());
	}

	@Test
	void twoFailuresAreFreeAndTheThirdStartsAThirtySecondWait() throws Exception {
		UserAccount parent = createAccount(Role.PARENT);
		String email = parent.getEmail();

		assertThat(login(email, WRONG).getStatus()).isEqualTo(401);
		assertThat(login(email, WRONG).getStatus()).isEqualTo(401);
		assertThat(login(email, TEST_PASSWORD).getStatus()).isEqualTo(200); // 2회까지는 대기 없음, 성공하면 초기화

		for (int i = 0; i < 3; i++) {
			assertThat(login(email, WRONG).getStatus()).isEqualTo(401);
		}
		MockHttpServletResponse waiting = login(email, TEST_PASSWORD); // 맞는 비밀번호여도 대기 중
		assertThat(waiting.getStatus()).isEqualTo(429);
		assertThat(waiting.getContentAsString()).contains("TOO_MANY_ATTEMPTS");
		assertThat(Long.parseLong(waiting.getHeader("Retry-After"))).isBetween(1L, 30L);

		clock.advance(Duration.ofSeconds(31));
		assertThat(login(email, TEST_PASSWORD).getStatus()).isEqualTo(200);
	}

	@Test
	void successResetsTheFailureCount() throws Exception {
		UserAccount parent = createAccount(Role.PARENT);
		String email = parent.getEmail();
		for (int i = 0; i < 3; i++) {
			login(email, WRONG);
		}
		clock.advance(Duration.ofSeconds(31));
		assertThat(login(email, TEST_PASSWORD).getStatus()).isEqualTo(200);

		// 초기화됐으므로 다시 두 번은 대기 없이 틀릴 수 있다
		assertThat(login(email, WRONG).getStatus()).isEqualTo(401);
		assertThat(login(email, WRONG).getStatus()).isEqualTo(401);
		assertThat(login(email, TEST_PASSWORD).getStatus()).isEqualTo(200);
	}

	@Test
	void waitNeverExceedsTenMinutesEvenAfterManyFailures() throws Exception {
		UserAccount parent = createAccount(Role.PARENT);
		String email = parent.getEmail();
		for (int i = 0; i < 12; i++) {
			clock.advance(Duration.ofMinutes(11));
			login(email, WRONG);
		}

		MockHttpServletResponse waiting = login(email, TEST_PASSWORD);

		assertThat(waiting.getStatus()).isEqualTo(429);
		assertThat(Long.parseLong(waiting.getHeader("Retry-After"))).isBetween(1L, 600L);
	}

	@Test
	void attemptsDuringTheWaitDoNotRaiseTheCount() throws Exception {
		UserAccount parent = createAccount(Role.PARENT);
		String email = parent.getEmail();
		for (int i = 0; i < 3; i++) {
			login(email, WRONG);
		}
		for (int i = 0; i < 10; i++) {
			assertThat(login(email, WRONG).getStatus()).isEqualTo(429); // 대기 중 시도
		}

		clock.advance(Duration.ofSeconds(31));
		assertThat(login(email, WRONG).getStatus()).isEqualTo(401); // 4회째
		MockHttpServletResponse waiting = login(email, TEST_PASSWORD);
		assertThat(Long.parseLong(waiting.getHeader("Retry-After"))).isBetween(31L, 60L); // 1분 대기
	}

	@Test
	void unknownEmailsGetTheSameWaitRule() throws Exception {
		String email = uniqueEmail();
		for (int i = 0; i < 3; i++) {
			assertThat(login(email, WRONG).getStatus()).isEqualTo(401);
		}

		MockHttpServletResponse waiting = login(email, TEST_PASSWORD);

		assertThat(waiting.getStatus()).isEqualTo(429);
		assertThat(Long.parseLong(waiting.getHeader("Retry-After"))).isBetween(1L, 30L);
	}

	@Test
	void otherAccountsAreNotAffectedByOneAccountsWait() throws Exception {
		UserAccount victim = createAccount(Role.PARENT);
		UserAccount other = createAccount(Role.PARENT);
		for (int i = 0; i < 4; i++) {
			login(victim.getEmail(), WRONG);
		}

		assertThat(login(other.getEmail(), TEST_PASSWORD).getStatus()).isEqualTo(200);
	}

	@Test
	void malformedRequestsAreInvalidInputNotCredentialErrors() throws Exception {
		assertThat(login("not-an-email", TEST_PASSWORD).getStatus()).isEqualTo(400);
		assertThat(send(json(post("/api/v1/auth/login"), "{\"email\":\"a@b.co\"}"), null).getStatus())
				.isEqualTo(400);
	}

	@Test
	void oneClientChangingEmailsIsStoppedWithTheSameResponseAsAnEmailWait() throws Exception {
		useUpClientLimit(CLIENT);

		MockHttpServletResponse limited = loginFrom(uniqueEmail(), WRONG, CLIENT);

		assertThat(limited.getStatus()).isEqualTo(429);
		assertThat(Long.parseLong(limited.getHeader("Retry-After"))).isBetween(1L, 600L);

		String waitingEmail = uniqueEmail();
		for (int i = 0; i < 3; i++) {
			loginFrom(waitingEmail, WRONG, OTHER_CLIENT);
		}
		MockHttpServletResponse emailWait = loginFrom(waitingEmail, TEST_PASSWORD, OTHER_CLIENT);
		assertThat(emailWait.getStatus()).isEqualTo(429);
		assertThat(limited.getContentAsString()).isEqualTo(emailWait.getContentAsString());

		Set<String> keys = redis.keys("damso:attempt:LOGIN_CLIENT:*");
		assertThat(keys).hasSize(2).noneMatch(key -> key.contains(CLIENT) || key.contains(OTHER_CLIENT));
	}

	@Test
	void theClientLimitAppliesToCorrectPasswordsOnlyFromThatClientAndEndsWithTheWindow() throws Exception {
		UserAccount parent = createAccount(Role.PARENT);
		useUpClientLimit(CLIENT);

		assertThat(loginFrom(parent.getEmail(), TEST_PASSWORD, CLIENT).getStatus()).isEqualTo(429);
		assertThat(loginFrom(parent.getEmail(), TEST_PASSWORD, OTHER_CLIENT).getStatus()).isEqualTo(200);

		clock.advance(Duration.ofMinutes(10));
		assertThat(loginFrom(parent.getEmail(), TEST_PASSWORD, CLIENT).getStatus()).isEqualTo(200);
	}

	@Test
	void requestsRejectedByTheClientLimitDoNotCountAsEmailFailures() throws Exception {
		UserAccount parent = createAccount(Role.PARENT);
		useUpClientLimit(CLIENT);

		for (int i = 0; i < 5; i++) {
			assertThat(loginFrom(parent.getEmail(), WRONG, CLIENT).getStatus()).isEqualTo(429);
		}

		assertThat(loginFrom(parent.getEmail(), TEST_PASSWORD, OTHER_CLIENT).getStatus()).isEqualTo(200);
	}

	@Test
	void requestsRejectedByAnEmailWaitDoNotUseUpTheClientLimit() throws Exception {
		String email = uniqueEmail();
		for (int i = 0; i < 3; i++) {
			assertThat(loginFrom(email, WRONG, CLIENT).getStatus()).isEqualTo(401);
		}
		for (int i = 0; i < CLIENT_LIMIT; i++) {
			assertThat(loginFrom(email, WRONG, CLIENT).getStatus()).isEqualTo(429);
		}

		assertThat(loginFrom(uniqueEmail(), WRONG, CLIENT).getStatus()).isEqualTo(401);
	}

	@Test
	void aCorrectPasswordIsRefusedWhenTheClientLimitStoreIsDown() throws Exception {
		UserAccount parent = createAccount(Role.PARENT);
		FailableClientLimitConfig.down = true;
		try {
			MockHttpServletResponse response = loginFrom(parent.getEmail(), TEST_PASSWORD, CLIENT);

			assertThat(response.getStatus()).isEqualTo(500);
			assertThat(response.getContentAsString()).contains("INTERNAL_ERROR").doesNotContain("accessToken");
		}
		finally {
			FailableClientLimitConfig.down = false;
		}
	}

}
