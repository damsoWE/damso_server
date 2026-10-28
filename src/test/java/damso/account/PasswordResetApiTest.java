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

	/** 요청 위치(IP)를 지정한 재설정 요청. 운영에서는 Caddy의 X-Forwarded-For가 remoteAddr로 반영된다. */
	private MockHttpServletResponse requestFrom(String email, String clientIp) throws Exception {
		return send(json(post("/api/v1/auth/password-reset"), "{\"email\":\"" + email + "\"}").with(request -> {
			request.setRemoteAddr(clientIp);
			return request;
		}), null);
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

	/**
	 * 이슈 #22: 발송 대기 중이면 조용히 202로 끝내지 않고 429 + Retry-After로 알린다. 대기는 계정 조회 전에 이메일+요청 위치
	 * 기준으로 판단하므로 가입된 이메일과 가입 안 된 이메일의 응답이 같다(가입 여부 비노출).
	 */
	@Test
	void requestingAgainWithinAMinuteIsTooManyAttemptsForRegisteredAndUnregisteredAlike() throws Exception {
		UserAccount parent = createAccount(Role.PARENT);
		String registered = parent.getEmail();
		String unregistered = uniqueEmail();
		assertThat(request(registered).getStatus()).isEqualTo(202);
		assertThat(request(unregistered).getStatus()).isEqualTo(202);

		MockHttpServletResponse registeredAgain = request(registered);
		MockHttpServletResponse unregisteredAgain = request(unregistered);

		for (MockHttpServletResponse again : new MockHttpServletResponse[] { registeredAgain, unregisteredAgain }) {
			assertThat(again.getStatus()).isEqualTo(429);
			assertSafeErrorResponse(again.getContentAsString(), "TOO_MANY_ATTEMPTS");
			assertThat(Long.parseLong(again.getHeader("Retry-After"))).isBetween(1L, 60L);
		}
		assertThat(unregisteredAgain.getContentAsString()).isEqualTo(registeredAgain.getContentAsString());
		assertThat(mail.sendCount(registered)).isEqualTo(1);
		assertThat(mail.sendCount(unregistered)).isZero();

		clock.advance(Duration.ofSeconds(61));
		assertThat(request(registered).getStatus()).isEqualTo(202);
		assertThat(mail.sendCount(registered)).isEqualTo(2);
	}

	/** 이슈 #22: 시간당 5회 제한에 걸리면 Retry-After가 600초에서 잘리지 않고 실제 남은 시간(최대 1시간)을 알린다. */
	@Test
	void sixthRequestWithinAnHourReportsTheRealHourlyWait() throws Exception {
		String email = createAccount(Role.PARENT).getEmail();
		for (int i = 0; i < 5; i++) {
			assertThat(request(email).getStatus()).isEqualTo(202);
			clock.advance(Duration.ofSeconds(61));
		}

		MockHttpServletResponse sixth = request(email);

		assertThat(sixth.getStatus()).isEqualTo(429);
		assertThat(Long.parseLong(sixth.getHeader("Retry-After"))).isBetween(601L, 3600L);
		assertThat(mail.sendCount(email)).isEqualTo(5);
	}

	/**
	 * 이슈 #22 재현 상황: 이미 가입된 이메일로 가입을 시도해 안내 메일이 나간 직후 재설정을 요청한다. 재설정 메일은 가입 인증
	 * 메일과 따로 세므로 막히지 않고 바로 발송된다.
	 */
	@Test
	void resetRightAfterASignupMailIsNotBlockedBySignupMailWait() throws Exception {
		UserAccount parent = createAccount(Role.PARENT);
		startSignup(issueParentCode(newStudent(newClassroom(), "김하나")), "박부모", parent.getEmail());

		MockHttpServletResponse reset = request(parent.getEmail());

		assertThat(reset.getStatus()).isEqualTo(202);
		assertThat(mail.lastResetCode(parent.getEmail())).matches("[0-9]{6}");
	}

	/** 이슈 #22 리뷰: 남이 내 이메일로 재설정을 요청해도 그 사람의 요청 위치 칸만 차고, 내 요청 위치에서는 바로 받을 수 있다. */
	@Test
	void anotherClientCannotBlockMyReset() throws Exception {
		String victim = createAccount(Role.PARENT).getEmail();
		assertThat(requestFrom(victim, "198.51.100.66").getStatus()).isEqualTo(202);
		assertThat(requestFrom(victim, "198.51.100.66").getStatus()).isEqualTo(429);

		MockHttpServletResponse mine = requestFrom(victim, "203.0.113.7");

		assertThat(mine.getStatus()).isEqualTo(202);
		assertThat(mail.sendCount(victim)).isEqualTo(2);
	}

	/** 이슈 #22 리뷰: 여러 요청 위치를 써도 이메일 하나에는 1시간 10통까지만 — 메일 폭탄 상한. */
	@Test
	void oneEmailGetsAtMostTenResetMailsAnHourFromAnyClients() throws Exception {
		String email = createAccount(Role.PARENT).getEmail();
		for (int i = 1; i <= 10; i++) {
			assertThat(requestFrom(email, "198.51.100." + i).getStatus()).isEqualTo(202);
		}

		MockHttpServletResponse eleventh = requestFrom(email, "198.51.100.11");

		assertThat(eleventh.getStatus()).isEqualTo(429);
		assertSafeErrorResponse(eleventh.getContentAsString(), "TOO_MANY_ATTEMPTS");
		assertThat(Long.parseLong(eleventh.getHeader("Retry-After"))).isBetween(1L, 3600L);
		assertThat(mail.sendCount(email)).isEqualTo(10);
	}

	/**
	 * 이슈 #26: 거부된 요청은 어느 칸도 소모하지 않는다. 이메일 상한에 걸린 동안 내 요청 위치에서 다시 시도해도
	 * 이메일+요청 위치 칸(1시간 5회)이 차지 않아, 이메일 칸이 풀리면 바로 받는다.
	 */
	@Test
	void rejectedRequestsDoNotUseUpTheOtherLimits() throws Exception {
		String email = createAccount(Role.PARENT).getEmail();
		for (int i = 1; i <= 10; i++) {
			assertThat(requestFrom(email, "198.51.100." + i).getStatus()).isEqualTo(202);
		}
		clock.advance(Duration.ofMinutes(30));
		for (int i = 0; i < 5; i++) {
			assertThat(requestFrom(email, "203.0.113.7").getStatus()).isEqualTo(429);
			clock.advance(Duration.ofSeconds(61));
		}

		clock.advance(Duration.ofMinutes(30));
		MockHttpServletResponse afterEmailLimit = requestFrom(email, "203.0.113.7");

		assertThat(afterEmailLimit.getStatus()).isEqualTo(202);
		assertThat(mail.sendCount(email)).isEqualTo(11);
	}

	/** 이슈 #22 리뷰: 요청 위치 하나는 여러 이메일을 합쳐 1시간 20회까지만 — 대량 요청 상한. */
	@Test
	void oneClientCanRequestAtMostTwentyResetsAnHour() throws Exception {
		for (int i = 0; i < 20; i++) {
			assertThat(requestFrom(uniqueEmail(), "198.51.100.200").getStatus()).isEqualTo(202);
		}

		MockHttpServletResponse twentyFirst = requestFrom(uniqueEmail(), "198.51.100.200");

		assertThat(twentyFirst.getStatus()).isEqualTo(429);
		assertThat(requestFrom(uniqueEmail(), "198.51.100.201").getStatus()).isEqualTo(202);
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
