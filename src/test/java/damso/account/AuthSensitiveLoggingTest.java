package damso.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mock.web.MockHttpServletResponse;

import damso.account.domain.Role;
import damso.school.domain.Classroom;
import damso.school.domain.Student;
import damso.support.IntegrationTestSupport;

/**
 * SC-006, FR-041, 원칙 VI: 가입·로그인·갱신·재설정·탈퇴·코드 발행 흐름 전체에서
 * 초대 코드 원문, 인증·재설정 코드, 비밀번호, 토큰, 이메일이 로그에 남지 않는다. 실패 경로도 포함한다.
 */
@ExtendWith(OutputCaptureExtension.class)
class AuthSensitiveLoggingTest extends IntegrationTestSupport {

	private static final String NEW_PASSWORD = "NewPass" + "55z";

	private static final String WRONG = "Wrong" + "Pass3";

	@Test
	void secretsNeverAppearInTheLogs(CapturedOutput output) throws Exception {
		List<String> secrets = new ArrayList<>();
		List<String> numericCodes = new ArrayList<>();
		secrets.add(TEST_PASSWORD);
		secrets.add(NEW_PASSWORD);
		secrets.add(WRONG);

		Classroom classroom = newClassroom();
		Student student = newStudent(classroom, "로그확인학생");
		String admin = tokenFor(createAccount(Role.SUPER_ADMIN));

		// 관리자가 코드를 발행한다(원문은 응답에만 있다)
		MockHttpServletResponse teacherInvite = send(
				post("/api/v1/admin/classrooms/" + classroom.getId() + "/teacher-invite"), admin);
		MockHttpServletResponse parentInvite = send(
				post("/api/v1/admin/students/" + student.getId() + "/parent-invite"), admin);
		String teacherCode = read(teacherInvite, "$.code");
		String parentCode = read(parentInvite, "$.code");
		secrets.add(teacherCode);
		secrets.add(parentCode);

		// 가입 — 성공 경로와 실패 경로
		String teacherEmail = uniqueEmail();
		String parentEmail = uniqueEmail();
		secrets.add(teacherEmail);
		secrets.add(parentEmail);
		startSignup("ZZZZZZZZZZ", "로그확인", teacherEmail); // 없는 코드
		MockHttpServletResponse teacherStart = startSignup(teacherCode, "로그확인", teacherEmail);
		numericCodes.add(mail.lastSignupCode(teacherEmail));
		verifySignup(read(teacherStart, "$.pendingSignupId"), otherThan(mail.lastSignupCode(teacherEmail))); // 틀린 코드
		MockHttpServletResponse teacherVerified = verifySignup(read(teacherStart, "$.pendingSignupId"),
				mail.lastSignupCode(teacherEmail));
		MockHttpServletResponse parentStart = startSignup(parentCode, "로그확인", parentEmail);
		numericCodes.add(mail.lastSignupCode(parentEmail));
		MockHttpServletResponse parentVerified = verifySignup(read(parentStart, "$.pendingSignupId"),
				mail.lastSignupCode(parentEmail));
		secrets.add(read(teacherVerified, "$.accessToken"));
		secrets.add(read(teacherVerified, "$.refreshToken"));
		secrets.add(read(parentVerified, "$.accessToken"));
		secrets.add(read(parentVerified, "$.refreshToken"));

		// 로그인(성공·실패)과 갱신, 재사용
		send(json(post("/api/v1/auth/login"), loginBody(parentEmail, WRONG)), null);
		MockHttpServletResponse login = send(json(post("/api/v1/auth/login"), loginBody(parentEmail, TEST_PASSWORD)),
				null);
		String oldRefresh = read(login, "$.refreshToken");
		secrets.add(read(login, "$.accessToken"));
		secrets.add(oldRefresh);
		MockHttpServletResponse refreshed = send(json(post("/api/v1/auth/token/refresh"),
				"{\"refreshToken\":\"" + oldRefresh + "\"}"), null);
		secrets.add(read(refreshed, "$.refreshToken"));
		send(json(post("/api/v1/auth/token/refresh"), "{\"refreshToken\":\"" + oldRefresh + "\"}"), null); // 재사용

		// 비밀번호 재설정 (같은 이메일로 인증 메일을 다시 받으려면 60초 간격이 필요하다)
		clock.advance(java.time.Duration.ofSeconds(61));
		send(json(post("/api/v1/auth/password-reset"), "{\"email\":\"" + parentEmail + "\"}"), null);
		String resetCode = mail.lastResetCode(parentEmail);
		numericCodes.add(resetCode);
		send(json(post("/api/v1/auth/password-reset/confirm"), "{\"email\":\"" + parentEmail + "\",\"code\":\""
				+ otherThan(resetCode) + "\",\"newPassword\":\"" + NEW_PASSWORD + "\"}"), null);
		send(json(post("/api/v1/auth/password-reset/confirm"), "{\"email\":\"" + parentEmail + "\",\"code\":\""
				+ resetCode + "\",\"newPassword\":\"" + NEW_PASSWORD + "\"}"), null);

		// 탈퇴(틀린 비밀번호 → 성공)
		MockHttpServletResponse relogin = send(json(post("/api/v1/auth/login"), loginBody(parentEmail, NEW_PASSWORD)),
				null);
		String parentAccess = read(relogin, "$.accessToken");
		secrets.add(parentAccess);
		secrets.add(read(relogin, "$.refreshToken"));
		send(json(post("/api/v1/me/withdrawal"), "{\"password\":\"" + WRONG + "\"}"), parentAccess);
		send(json(post("/api/v1/me/withdrawal"), "{\"password\":\"" + NEW_PASSWORD + "\"}"), parentAccess);

		String logs = output.getAll();
		for (String secret : secrets) {
			assertThat(logs).as("secret of length %d", secret.length()).doesNotContain(secret);
		}
		for (String code : numericCodes) {
			assertThat(Pattern.compile("(?<![0-9])" + code + "(?![0-9])").matcher(logs).find())
					.as("six digit code").isFalse();
		}
	}

	private static String loginBody(String email, String password) {
		return "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}";
	}

	private static String otherThan(String code) {
		return code.equals("000000") ? "000001" : "000000";
	}

}
