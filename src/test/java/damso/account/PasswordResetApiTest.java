package damso.account;

import static damso.support.ErrorResponseAssertions.assertSafeErrorResponse;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

import damso.account.domain.Role;
import damso.account.domain.UserAccount;
import damso.support.IntegrationTestSupport;

/** US5 시나리오 1~5, FR-029~031, SC-003: 비밀번호 재설정 코드 요청·확인·변경. */
class PasswordResetApiTest extends IntegrationTestSupport {

	private static final String NEW_PASSWORD = "NewPass" + "77x";

	private MockHttpServletResponse request(String email) throws Exception {
		return send(json(post("/api/v1/auth/password-reset"), "{\"email\":\"" + email + "\"}"), null);
	}

	private MockHttpServletResponse verify(String email, String code) throws Exception {
		return send(json(post("/api/v1/auth/password-reset/verify"),
				"{\"email\":\"" + email + "\",\"code\":\"" + code + "\"}"), null);
	}

	private MockHttpServletResponse confirm(String email, String code, String newPassword) throws Exception {
		return send(json(post("/api/v1/auth/password-reset/confirm"),
				"{\"email\":\"" + email + "\",\"code\":\"" + code + "\",\"newPassword\":\"" + newPassword + "\"}"),
				null);
	}

	private MockHttpServletResponse login(String email, String password) throws Exception {
		return send(json(post("/api/v1/auth/login"),
				"{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"), null);
	}

	private static String otherThan(String code) {
		return code.equals("000000") ? "000001" : "000000";
	}

	@Test
	void requestGetsTheSameResponseForRegisteredAndUnregisteredEmails() throws Exception {
		UserAccount parent = createAccount(Role.PARENT);
		String unknown = uniqueEmail();

		MockHttpServletResponse registered = request(parent.getEmail());
		MockHttpServletResponse unregistered = request(unknown);

		assertThat(registered.getStatus()).isEqualTo(202);
		assertThat(unregistered.getStatus()).isEqualTo(202);
		assertThat(unregistered.getContentAsString()).isEqualTo(registered.getContentAsString());
		assertThat(mail.lastResetCode(parent.getEmail())).matches("[0-9]{6}");
		assertThat(mail.lastResetCode(unknown)).isNull();
		assertThat(mail.sendCount(unknown)).isZero();
	}

	@Test
	void withdrawnAccountsGetNoMail() throws Exception {
		UserAccount parent = createAccount(Role.PARENT);
		String email = parent.getEmail();
		parent.withdraw(clock.instant());
		accountPort.save(parent);

		assertThat(request(email).getStatus()).isEqualTo(202);
		assertThat(mail.totalSent()).isZero();
	}

	@Test
	void verifyDoesNotConsumeTheCodeAndConfirmChangesThePassword() throws Exception {
		UserAccount parent = createAccount(Role.PARENT);
		String email = parent.getEmail();
		request(email);
		String code = mail.lastResetCode(email);

		assertThat(verify(email, code).getStatus()).isEqualTo(204);
		assertThat(verify(email, code).getStatus()).isEqualTo(204);

		assertThat(confirm(email, code, NEW_PASSWORD).getStatus()).isEqualTo(204);

		MockHttpServletResponse oldPassword = login(email, TEST_PASSWORD);
		assertThat(oldPassword.getStatus()).isEqualTo(401);
		assertThat(oldPassword.getContentAsString()).contains("INVALID_CREDENTIALS");
		assertThat(login(email, NEW_PASSWORD).getStatus()).isEqualTo(200);
		assertThat(accountPort.findById(parent.getId()).orElseThrow().getPasswordHash())
				.doesNotContain(NEW_PASSWORD);
	}

	@Test
	void confirmEndsEverySessionOfTheAccount() throws Exception {
		UserAccount parent = createAccount(Role.PARENT);
		String email = parent.getEmail();
		MockHttpServletResponse deviceA = login(email, TEST_PASSWORD);
		MockHttpServletResponse deviceB = login(email, TEST_PASSWORD);
		String accessA = read(deviceA, "$.accessToken");
		String accessB = read(deviceB, "$.accessToken");
		String refreshA = read(deviceA, "$.refreshToken");
		assertThat(send(get("/test/auth/any"), accessA).getStatus()).isEqualTo(200);

		request(email);
		assertThat(confirm(email, mail.lastResetCode(email), NEW_PASSWORD).getStatus()).isEqualTo(204);

		assertThat(send(get("/test/auth/any"), accessA).getStatus()).isEqualTo(401);
		assertThat(send(get("/test/auth/any"), accessB).getStatus()).isEqualTo(401);
		assertThat(send(json(post("/api/v1/auth/token/refresh"), "{\"refreshToken\":\"" + refreshA + "\"}"), null)
				.getStatus()).isEqualTo(401);
		assertThat(jdbc.queryForObject("select count(*) from auth_session where revoke_reason = 'PASSWORD_RESET'",
				Integer.class)).isEqualTo(2);
	}

	@Test
	void theCodeWorksOnlyOnce() throws Exception {
		UserAccount parent = createAccount(Role.PARENT);
		String email = parent.getEmail();
		request(email);
		String code = mail.lastResetCode(email);
		assertThat(confirm(email, code, NEW_PASSWORD).getStatus()).isEqualTo(204);

		MockHttpServletResponse second = confirm(email, code, "Another" + "Pass88");

		assertThat(second.getStatus()).isEqualTo(400);
		assertThat(second.getContentAsString()).contains("INVALID_VERIFICATION_CODE");
		assertThat(login(email, NEW_PASSWORD).getStatus()).isEqualTo(200);
	}

	@Test
	void sixthWrongCodeIsLockedAndANewRequestAfterTheLockWorks() throws Exception {
		UserAccount parent = createAccount(Role.PARENT);
		String email = parent.getEmail();
		request(email);
		String code = mail.lastResetCode(email);

		for (int i = 0; i < 5; i++) {
			MockHttpServletResponse failed = verify(email, otherThan(code));
			assertThat(failed.getStatus()).isEqualTo(400);
			assertThat(failed.getContentAsString()).contains("INVALID_VERIFICATION_CODE");
		}
		MockHttpServletResponse locked = verify(email, code);
		assertThat(locked.getStatus()).isEqualTo(429);
		assertThat(Long.parseLong(locked.getHeader("Retry-After"))).isBetween(1L, 600L);
		assertThat(confirm(email, code, NEW_PASSWORD).getStatus()).isEqualTo(429);

		clock.advance(Duration.ofMinutes(10).plusSeconds(1));
		request(email); // 잠금이 풀린 뒤 새 코드를 받는다
		assertThat(confirm(email, mail.lastResetCode(email), NEW_PASSWORD).getStatus()).isEqualTo(204);
	}

	@Test
	void codeExpiresAfterTenMinutes() throws Exception {
		UserAccount parent = createAccount(Role.PARENT);
		String email = parent.getEmail();
		request(email);
		String code = mail.lastResetCode(email);

		clock.advance(Duration.ofMinutes(10).plusSeconds(1));

		MockHttpServletResponse expired = confirm(email, code, NEW_PASSWORD);
		assertThat(expired.getStatus()).isEqualTo(400);
		assertThat(expired.getContentAsString()).contains("INVALID_VERIFICATION_CODE");
		assertThat(login(email, TEST_PASSWORD).getStatus()).isEqualTo(200);
	}

	@Test
	void requestingAgainInvalidatesThePreviousCode() throws Exception {
		UserAccount parent = createAccount(Role.PARENT);
		String email = parent.getEmail();
		request(email);
		String oldCode = mail.lastResetCode(email);

		clock.advance(Duration.ofSeconds(61));
		request(email);
		String newCode = mail.lastResetCode(email);

		if (!newCode.equals(oldCode)) {
			assertThat(verify(email, oldCode).getStatus()).isEqualTo(400);
		}
		assertThat(verify(email, newCode).getStatus()).isEqualTo(204);
	}

	@Test
	void requestingAgainWithinAMinuteSendsNoSecondMailButStillLooksTheSame() throws Exception {
		UserAccount parent = createAccount(Role.PARENT);
		String email = parent.getEmail();
		MockHttpServletResponse first = request(email);
		MockHttpServletResponse second = request(email);

		assertThat(second.getStatus()).isEqualTo(202);
		assertThat(second.getContentAsString()).isEqualTo(first.getContentAsString());
		assertThat(mail.sendCount(email)).isEqualTo(1);
	}

	@Test
	void weakNewPasswordIsRejectedAndTheCodeStaysUsable() throws Exception {
		UserAccount parent = createAccount(Role.PARENT);
		String email = parent.getEmail();
		request(email);
		String code = mail.lastResetCode(email);

		MockHttpServletResponse weak = confirm(email, code, "abcdefghij");

		assertThat(weak.getStatus()).isEqualTo(400);
		assertSafeErrorResponse(weak.getContentAsString(), "INVALID_INPUT");
		assertThat(weak.getContentAsString()).doesNotContain("abcdefghij");
		assertThat(confirm(email, code, "short1").getStatus()).isEqualTo(400);
		assertThat(confirm(email, code, NEW_PASSWORD).getStatus()).isEqualTo(204);
	}

	@Test
	void unknownEmailGetsTheSameVerifyFailureAsAWrongCode() throws Exception {
		UserAccount parent = createAccount(Role.PARENT);
		request(parent.getEmail());

		MockHttpServletResponse wrongCode = verify(parent.getEmail(), otherThan(mail.lastResetCode(parent.getEmail())));
		MockHttpServletResponse unknownEmail = verify(uniqueEmail(), "123456");

		assertThat(unknownEmail.getStatus()).isEqualTo(400);
		assertThat(unknownEmail.getContentAsString()).isEqualTo(wrongCode.getContentAsString());
	}

	@Test
	void emailIsCaseInsensitive() throws Exception {
		UserAccount parent = createAccount(Role.PARENT);
		String email = parent.getEmail();
		request(email.toUpperCase());
		assertThat(mail.lastResetCode(email)).isNotNull();
		assertThat(confirm(email.toUpperCase(), mail.lastResetCode(email), NEW_PASSWORD).getStatus()).isEqualTo(204);
	}

	@Test
	void resetCodeIsStoredOnlyAsAHash() throws Exception {
		UserAccount parent = createAccount(Role.PARENT);
		request(parent.getEmail());
		String code = mail.lastResetCode(parent.getEmail());

		String stored = jdbc.queryForObject("select code_hash from password_reset", String.class);

		assertThat(stored).hasSize(64).isNotEqualTo(code);
	}

}
