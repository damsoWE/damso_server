package damso.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.time.Duration;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

import damso.common.crypto.SecureCodes;
import damso.school.domain.Classroom;
import damso.school.domain.Student;
import damso.support.IntegrationTestSupport;

/**
 * US2·US3 가입 전 코드 확인, FR-011a: 역할만 알려 주고 코드를 소모하지 않는다. 실패 응답과 횟수는 가입 1단계와 같다.
 */
class InviteCodeCheckApiTest extends IntegrationTestSupport {

	private MockHttpServletResponse check(String inviteCode, String email) throws Exception {
		return send(json(post("/api/v1/auth/invite-codes/verify"),
				"{\"inviteCode\":\"" + inviteCode + "\",\"email\":\"" + email + "\"}"), null);
	}

	@Test
	void teacherAndParentCodesAnswerOnlyTheirRole() throws Exception {
		Classroom classroom = newClassroom();
		Student student = newStudent(classroom, "김민준");

		MockHttpServletResponse teacher = check(issueTeacherCode(classroom), uniqueEmail());
		MockHttpServletResponse parent = check(issueParentCode(student).toLowerCase(), uniqueEmail());

		assertThat(teacher.getStatus()).isEqualTo(200);
		assertThat(teacher.getContentAsString()).isEqualTo("{\"role\":\"TEACHER\"}");
		assertThat(parent.getStatus()).isEqualTo(200);
		assertThat(parent.getContentAsString()).isEqualTo("{\"role\":\"PARENT\"}");
	}

	@Test
	void checkingDoesNotConsumeTheCodeAndSignupStillWorks() throws Exception {
		Classroom classroom = newClassroom();
		String code = issueTeacherCode(classroom);

		assertThat(check(code, uniqueEmail()).getStatus()).isEqualTo(200);
		assertThat(check(code, uniqueEmail()).getStatus()).isEqualTo(200);

		signUpWith(code, "김선생");
		assertThat(classroomPort.findClassroomById(classroom.getId()).orElseThrow().hasTeacher()).isTrue();
	}

	@Test
	void unusableCodesGetTheSameResponseAsSignup() throws Exception {
		String unknown = check(SecureCodes.inviteCode(), uniqueEmail()).getContentAsString();

		Classroom expiredRoom = newClassroom(1, 1);
		String expiredCode = issueTeacherCode(expiredRoom);
		clock.advance(Duration.ofHours(24).plusSeconds(1));
		MockHttpServletResponse expired = check(expiredCode, uniqueEmail());

		Classroom usedRoom = newClassroom(1, 2);
		String usedCode = issueTeacherCode(usedRoom);
		signUpWith(usedCode, "김선생");
		MockHttpServletResponse used = check(usedCode, uniqueEmail());

		Classroom revokedRoom = newClassroom(1, 3);
		String revokedCode = issueTeacherCode(revokedRoom);
		issueTeacherCode(revokedRoom);
		MockHttpServletResponse revoked = check(revokedCode, uniqueEmail());

		Classroom takenRoom = newClassroom(1, 4);
		String takenCode = issueTeacherCode(takenRoom);
		takenRoom.assignTeacher(UUID.randomUUID());
		classroomPort.saveClassroom(takenRoom);
		MockHttpServletResponse taken = check(takenCode, uniqueEmail());

		MockHttpServletResponse signupWithUnknown = startSignup(SecureCodes.inviteCode(), "김선생", uniqueEmail());

		assertThat(unknown).contains("INVALID_INVITE_CODE");
		assertThat(expired.getStatus()).isEqualTo(400);
		assertThat(expired.getContentAsString()).isEqualTo(unknown);
		assertThat(used.getContentAsString()).isEqualTo(unknown);
		assertThat(revoked.getContentAsString()).isEqualTo(unknown);
		assertThat(taken.getContentAsString()).isEqualTo(unknown);
		assertThat(signupWithUnknown.getContentAsString()).isEqualTo(unknown);
	}

	@Test
	void failuresAreCountedPerEmailTogetherWithSignup() throws Exception {
		Classroom classroom = newClassroom();
		String validCode = issueTeacherCode(classroom);
		String email = uniqueEmail();

		// 확인 단계 3회 + 가입 1단계 2회 = 같은 이메일로 5회 실패
		for (int i = 0; i < 3; i++) {
			assertThat(check(SecureCodes.inviteCode(), email).getStatus()).isEqualTo(400);
		}
		for (int i = 0; i < 2; i++) {
			assertThat(startSignup(SecureCodes.inviteCode(), "김선생", email).getStatus()).isEqualTo(400);
		}

		MockHttpServletResponse lockedCheck = check(validCode, email);
		MockHttpServletResponse lockedSignup = startSignup(validCode, "김선생", email);
		assertThat(lockedCheck.getStatus()).isEqualTo(429);
		assertThat(lockedCheck.getContentAsString()).contains("TOO_MANY_ATTEMPTS");
		assertThat(Long.parseLong(lockedCheck.getHeader("Retry-After"))).isBetween(1L, 600L);
		assertThat(lockedSignup.getStatus()).isEqualTo(429);

		// 다른 이메일은 잠기지 않는다
		assertThat(check(validCode, uniqueEmail()).getStatus()).isEqualTo(200);

		clock.advance(Duration.ofMinutes(10).plusSeconds(1));
		assertThat(check(validCode, email.toUpperCase()).getStatus()).isEqualTo(200);
	}

	@Test
	void malformedInputIsRejectedWithoutCountingAFailure() throws Exception {
		String email = uniqueEmail();

		MockHttpServletResponse noEmail = send(json(post("/api/v1/auth/invite-codes/verify"),
				"{\"inviteCode\":\"" + SecureCodes.inviteCode() + "\"}"), null);
		MockHttpServletResponse badCode = check("SHORT", email);
		MockHttpServletResponse extraField = send(json(post("/api/v1/auth/invite-codes/verify"),
				"{\"inviteCode\":\"" + SecureCodes.inviteCode() + "\",\"email\":\"" + email + "\",\"role\":\"TEACHER\"}"),
				null);

		assertThat(noEmail.getStatus()).isEqualTo(400);
		assertThat(noEmail.getContentAsString()).contains("INVALID_INPUT");
		assertThat(badCode.getStatus()).isEqualTo(400);
		assertThat(badCode.getContentAsString()).contains("INVALID_INPUT");
		assertThat(extraField.getStatus()).isEqualTo(400);
		for (int i = 0; i < 4; i++) {
			check(SecureCodes.inviteCode(), email);
		}
		// 형식 오류는 세지 않았으므로 아직 4회 — 5번째 실패까지는 400
		assertThat(check(SecureCodes.inviteCode(), email).getStatus()).isEqualTo(400);
	}

}
