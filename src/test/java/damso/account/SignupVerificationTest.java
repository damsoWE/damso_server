package damso.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.time.Duration;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

import damso.account.domain.Role;
import damso.school.domain.Classroom;
import damso.support.IntegrationTestSupport;

/** US2 시나리오 4, FR-017·032, Edge Case: 이메일 인증 코드의 만료·횟수 제한·재발송과 이미 가입된 이메일. */
class SignupVerificationTest extends IntegrationTestSupport {

	private String teacherCode() {
		return issueTeacherCode(newClassroom());
	}

	private MockHttpServletResponse resend(String pendingSignupId) throws Exception {
		return send(json(post("/api/v1/auth/signup/resend"), "{\"pendingSignupId\":\"" + pendingSignupId + "\"}"),
				null);
	}

	@Test
	void alreadyRegisteredEmailGetsTheSameResponseAndOnlyANoticeMail() throws Exception {
		String existing = uniqueEmail();
		createAccount(Role.PARENT, existing);

		MockHttpServletResponse forExisting = startSignup(teacherCode(), "김선생", existing);
		MockHttpServletResponse forNew = startSignup(issueTeacherCode(newClassroom(1, 2)), "김선생", uniqueEmail());

		assertThat(forExisting.getStatus()).isEqualTo(202);
		assertThat(forNew.getStatus()).isEqualTo(202);
		assertThat((Integer) read(forExisting, "$.codeExpiresInSeconds"))
				.isEqualTo(read(forNew, "$.codeExpiresInSeconds"));
		assertThat(mail.alreadyRegisteredSent(existing)).isTrue();
		assertThat(mail.lastSignupCode(existing)).isNull();

		// 안내를 받은 쪽은 인증을 이어갈 수 없다
		MockHttpServletResponse verified = verifySignup(read(forExisting, "$.pendingSignupId"), "123456");
		assertThat(verified.getStatus()).isEqualTo(400);
		assertThat(verified.getContentAsString()).contains("INVALID_VERIFICATION_CODE");
		assertThat(jdbc.queryForObject("select count(*) from user_account", Integer.class)).isEqualTo(1);
	}

	@Test
	void sixthWrongVerificationCodeIsLockedEvenWithTheRightCode() throws Exception {
		String email = uniqueEmail();
		MockHttpServletResponse started = startSignup(teacherCode(), "김선생", email);
		String pendingId = read(started, "$.pendingSignupId");
		String right = mail.lastSignupCode(email);
		String wrong = right.equals("000000") ? "000001" : "000000";

		for (int i = 0; i < 5; i++) {
			MockHttpServletResponse failed = verifySignup(pendingId, wrong);
			assertThat(failed.getStatus()).isEqualTo(400);
			assertThat(failed.getContentAsString()).contains("INVALID_VERIFICATION_CODE");
		}

		MockHttpServletResponse locked = verifySignup(pendingId, right);
		assertThat(locked.getStatus()).isEqualTo(429);
		assertThat(Long.parseLong(locked.getHeader("Retry-After"))).isBetween(1L, 600L);

		clock.advance(Duration.ofMinutes(10).plusSeconds(1));
		// 잠금이 풀려도 코드 자체는 10분이 지나 만료됐다
		assertThat(verifySignup(pendingId, right).getStatus()).isEqualTo(400);
	}

	@Test
	void verificationCodeExpiresAfterTenMinutes() throws Exception {
		String email = uniqueEmail();
		MockHttpServletResponse started = startSignup(teacherCode(), "김선생", email);
		String code = mail.lastSignupCode(email);

		clock.advance(Duration.ofMinutes(10).plusSeconds(1));

		MockHttpServletResponse verified = verifySignup(read(started, "$.pendingSignupId"), code);
		assertThat(verified.getStatus()).isEqualTo(400);
		assertThat(verified.getContentAsString()).contains("INVALID_VERIFICATION_CODE");
		assertThat(jdbc.queryForObject("select count(*) from user_account", Integer.class)).isZero();
	}

	@Test
	void resendIssuesANewCodeAndInvalidatesThePreviousOne() throws Exception {
		String email = uniqueEmail();
		MockHttpServletResponse started = startSignup(teacherCode(), "김선생", email);
		String pendingId = read(started, "$.pendingSignupId");
		String oldCode = mail.lastSignupCode(email);

		clock.advance(Duration.ofSeconds(61));
		assertThat(resend(pendingId).getStatus()).isEqualTo(202);
		String newCode = mail.lastSignupCode(email);

		if (!newCode.equals(oldCode)) {
			assertThat(verifySignup(pendingId, oldCode).getStatus()).isEqualTo(400);
		}
		assertThat(verifySignup(pendingId, newCode).getStatus()).isEqualTo(201);
	}

	@Test
	void resendWithinSixtySecondsIsRateLimited() throws Exception {
		String email = uniqueEmail();
		MockHttpServletResponse started = startSignup(teacherCode(), "김선생", email);

		MockHttpServletResponse tooSoon = resend(read(started, "$.pendingSignupId"));

		assertThat(tooSoon.getStatus()).isEqualTo(429);
		assertThat(Long.parseLong(tooSoon.getHeader("Retry-After"))).isBetween(1L, 60L);
		assertThat(mail.sendCount(email)).isEqualTo(1);
	}

	@Test
	void resendForAnUnknownPendingSignupDoesNotRevealAnything() throws Exception {
		assertThat(resend(UUID.randomUUID().toString()).getStatus()).isEqualTo(202);
	}

	@Test
	void verifyWithAnUnknownPendingSignupIsAnInvalidVerificationCode() throws Exception {
		MockHttpServletResponse response = verifySignup(UUID.randomUUID().toString(), "123456");
		assertThat(response.getStatus()).isEqualTo(400);
		assertThat(response.getContentAsString()).contains("INVALID_VERIFICATION_CODE");
	}

	@Test
	void startingOverReplacesTheEarlierUnfinishedAttempt() throws Exception {
		Classroom classroom = newClassroom();
		String code = issueTeacherCode(classroom);
		String email = uniqueEmail();
		MockHttpServletResponse first = startSignup(code, "김선생", email);
		String firstCode = mail.lastSignupCode(email);

		clock.advance(Duration.ofSeconds(61));
		MockHttpServletResponse second = startSignup(code, "김선생", email);

		assertThat(jdbc.queryForObject("select count(*) from pending_signup where email = ?", Integer.class, email))
				.isEqualTo(1);
		assertThat(verifySignup(read(first, "$.pendingSignupId"), firstCode).getStatus()).isEqualTo(400);
		assertThat(verifySignup(read(second, "$.pendingSignupId"), mail.lastSignupCode(email)).getStatus())
				.isEqualTo(201);
	}

}
