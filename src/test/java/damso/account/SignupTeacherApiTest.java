package damso.account;

import static damso.support.ErrorResponseAssertions.assertSafeErrorResponse;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

import damso.account.domain.Role;
import damso.account.domain.UserAccount;
import damso.school.domain.Classroom;
import damso.support.IntegrationTestSupport;

/** US2 시나리오 1·5, FR-014·016·018·019: 교사가 초대 코드로 가입해 담임으로 확정된다. */
class SignupTeacherApiTest extends IntegrationTestSupport {

	private Classroom classroom;

	private String code;

	private void prepare() {
		classroom = newClassroom(3, 2);
		code = issueTeacherCode(classroom);
	}

	private int accountCount() {
		return jdbc.queryForObject("select count(*) from user_account", Integer.class);
	}

	@Test
	void teacherSignsUpAndBecomesTheHomeroomTeacher() throws Exception {
		prepare();
		String email = uniqueEmail();

		MockHttpServletResponse started = startSignup(code, "김선생", email);
		assertThat(started.getStatus()).isEqualTo(202);
		assertThat((Integer) read(started, "$.codeExpiresInSeconds")).isEqualTo(600);
		String pendingId = read(started, "$.pendingSignupId");
		assertThat(UUID.fromString(pendingId)).isNotNull();
		assertThat(started.getContentAsString()).doesNotContain("token").doesNotContain(code);

		// 인증 전에는 계정도 연결도 코드 소모도 없다 (FR-016)
		assertThat(accountCount()).isZero();
		assertThat(jdbc.queryForObject("select status from invite_code", String.class)).isEqualTo("ISSUED");
		assertThat(classroomPort.findClassroomById(classroom.getId()).orElseThrow().hasTeacher()).isFalse();

		MockHttpServletResponse verified = verifySignup(pendingId, mail.lastSignupCode(email));
		assertThat(verified.getStatus()).isEqualTo(201);
		assertThat((String) read(verified, "$.role")).isEqualTo("TEACHER");
		assertThat((Integer) read(verified, "$.accessTokenExpiresInSeconds")).isEqualTo(900);
		assertThat((Integer) read(verified, "$.refreshTokenExpiresInSeconds")).isEqualTo(14 * 24 * 3600);

		UserAccount account = accountPort.findByEmail(email).orElseThrow();
		assertThat(account.getRole()).isEqualTo(Role.TEACHER);
		assertThat(account.getTermsAgreedAt()).isNotNull();
		assertThat(account.getName().reveal()).isEqualTo("김선생");
		assertThat(account.getPasswordHash()).doesNotContain(TEST_PASSWORD);

		assertThat(classroomPort.findClassroomById(classroom.getId()).orElseThrow().getHomeroomTeacherId())
				.isEqualTo(account.getId());
		Map<String, Object> invite = jdbc.queryForMap("select status, used_by, active_target_key from invite_code");
		assertThat(invite.get("status")).isEqualTo("USED");
		assertThat(invite.get("used_by")).isEqualTo(account.getId());
		assertThat(invite.get("active_target_key")).isNull();
		assertThat(jdbc.queryForObject("select display_name from teacher_profile where teacher_id = ?",
				String.class, account.getId())).isEqualTo("김선생");

		List<Map<String, Object>> history = jdbc.queryForList(
				"select target_type, target_id, event from link_history where account_id = ?", account.getId());
		assertThat(history).hasSize(1);
		assertThat(history.get(0).get("target_type")).isEqualTo("CLASSROOM");
		assertThat(history.get(0).get("target_id")).isEqualTo(classroom.getId());
		assertThat(history.get(0).get("event")).isEqualTo("LINKED");

		// 발급된 접근 토큰으로 보호된 경로에 들어갈 수 있다
		String accessToken = read(verified, "$.accessToken");
		assertThat(send(get("/test/auth/any"), accessToken).getStatus()).isEqualTo(200);
	}

	@Test
	void emailIsNormalizedToLowercase() throws Exception {
		prepare();
		String email = "Teacher." + UUID.randomUUID() + "@Damso.Test";

		MockHttpServletResponse started = startSignup(code, "김선생", email);
		assertThat(started.getStatus()).isEqualTo(202);
		assertThat(mail.lastSignupCode(email.toLowerCase())).isNotNull();

		MockHttpServletResponse verified = verifySignup(read(started, "$.pendingSignupId"),
				mail.lastSignupCode(email.toLowerCase()));
		assertThat(verified.getStatus()).isEqualTo(201);
		assertThat(accountPort.findByEmail(email.toLowerCase())).isPresent();
	}

	@Test
	void signupWithoutAgreeingToTermsIsRejected() throws Exception {
		prepare();
		MockHttpServletResponse response = send(json(post("/api/v1/auth/signup"),
				signupBody(code, "김선생", uniqueEmail(), TEST_PASSWORD, false)), null);

		assertThat(response.getStatus()).isEqualTo(400);
		assertSafeErrorResponse(response.getContentAsString(), "INVALID_INPUT");
		assertThat(jdbc.queryForObject("select count(*) from pending_signup", Integer.class)).isZero();
	}

	@Test
	void weakPasswordIsRejectedWithoutEchoingIt() throws Exception {
		prepare();
		String weak = "abcdefgh";
		MockHttpServletResponse response = send(json(post("/api/v1/auth/signup"),
				signupBody(code, "김선생", uniqueEmail(), weak, true)), null);

		assertThat(response.getStatus()).isEqualTo(400);
		assertSafeErrorResponse(response.getContentAsString(), "INVALID_INPUT");
		assertThat(response.getContentAsString()).doesNotContain(weak);
	}

	@Test
	void unknownFieldsLikeContactInformationAreRejected() throws Exception {
		prepare();
		String body = "{\"inviteCode\":\"" + code + "\",\"name\":\"김선생\",\"email\":\"" + uniqueEmail()
				+ "\",\"password\":\"" + TEST_PASSWORD + "\",\"termsAgreed\":true,\"phone\":\"010-1234-5678\"}";

		MockHttpServletResponse response = send(json(post("/api/v1/auth/signup"), body), null);

		assertThat(response.getStatus()).isEqualTo(400);
		assertSafeErrorResponse(response.getContentAsString(), "INVALID_INPUT");
		assertThat(response.getContentAsString()).doesNotContain("010");
	}

	@Test
	void roleAndClassroomCannotBeChosenByTheRequest() throws Exception {
		prepare();
		String body = "{\"inviteCode\":\"" + code + "\",\"name\":\"김선생\",\"email\":\"" + uniqueEmail()
				+ "\",\"password\":\"" + TEST_PASSWORD + "\",\"termsAgreed\":true,\"role\":\"SUPER_ADMIN\"}";

		MockHttpServletResponse response = send(json(post("/api/v1/auth/signup"), body), null);

		assertThat(response.getStatus()).isEqualTo(400);
		assertThat(accountCount()).isZero();
	}

	@Test
	void malformedInviteCodeFormatIsInvalidInput() throws Exception {
		MockHttpServletResponse response = startSignup("0OIL1", "김선생", uniqueEmail());
		assertThat(response.getStatus()).isEqualTo(400);
		assertSafeErrorResponse(response.getContentAsString(), "INVALID_INPUT");
	}

	@Test
	void signupEndpointsArePublicButOtherAuthEndpointsAreNot() throws Exception {
		MockHttpServletResponse logout = send(post("/api/v1/auth/logout"), null);
		assertThat(logout.getStatus()).isEqualTo(401);
	}

	@Test
	void lowercaseInviteCodeIsAccepted() throws Exception {
		prepare();
		String email = uniqueEmail();
		MockHttpServletResponse started = startSignup(code.toLowerCase(), "김선생", email);
		assertThat(started.getStatus()).isEqualTo(202);
		assertThat(verifySignup(read(started, "$.pendingSignupId"), mail.lastSignupCode(email)).getStatus())
				.isEqualTo(201);
	}

}
