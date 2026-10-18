package damso.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

import damso.account.domain.Role;
import damso.account.domain.UserAccount;
import damso.support.IntegrationTestSupport;

/** US4 시나리오 1~5, FR-022~024, SC-003·004: 로그인과 단계별 대기. */
class LoginApiTest extends IntegrationTestSupport {

	private MockHttpServletResponse login(String email, String password) throws Exception {
		return send(json(post("/api/v1/auth/login"),
				"{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"), null);
	}

	private static final String WRONG = "Wrong" + "Pass1";

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

}
