package damso.account;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

import damso.common.crypto.SecureCodes;
import damso.school.domain.Classroom;
import damso.support.IntegrationTestSupport;

/** 코드가 쓸 수 없는 이유를 구분하지 않고, 5회 실패 시 잠긴다. */
class SignupInviteCodeFailureTest extends IntegrationTestSupport {

	private String bodyFor(String code) throws Exception {
		MockHttpServletResponse response = startSignup(code, "김선생", uniqueEmail());
		assertThat(response.getStatus()).isEqualTo(400);
		return response.getContentAsString();
	}

	@Test
	void unknownExpiredUsedRevokedAndTakenCodesGetTheSameResponse() throws Exception {
		// 없는 코드
		String unknown = bodyFor(SecureCodes.inviteCode());

		// 만료된 코드
		Classroom expiredRoom = newClassroom(1, 1);
		String expiredCode = issueTeacherCode(expiredRoom);
		clock.advance(Duration.ofHours(24).plusSeconds(1));
		String expired = bodyFor(expiredCode);

		// 사용된 코드
		Classroom usedRoom = newClassroom(1, 2);
		String usedCode = issueTeacherCode(usedRoom);
		signUpWith(usedCode, "김선생");
		String used = bodyFor(usedCode);

		// 재발행으로 무효가 된 코드
		Classroom revokedRoom = newClassroom(1, 3);
		String revokedCode = issueTeacherCode(revokedRoom);
		issueTeacherCode(revokedRoom);
		String revoked = bodyFor(revokedCode);

		// 대상이 이미 찬 코드(담임이 다른 경로로 확정됨)
		Classroom takenRoom = newClassroom(1, 4);
		String takenCode = issueTeacherCode(takenRoom);
		takenRoom.assignTeacher(UUID.randomUUID());
		classroomPort.saveClassroom(takenRoom);
		String taken = bodyFor(takenCode);

		assertThat(unknown).contains("INVALID_INVITE_CODE");
		assertThat(expired).isEqualTo(unknown);
		assertThat(used).isEqualTo(unknown);
		assertThat(revoked).isEqualTo(unknown);
		assertThat(taken).isEqualTo(unknown);
	}

	@Test
	void sixthAttemptWithTheSameEmailIsLockedEvenWithAValidCode() throws Exception {
		Classroom classroom = newClassroom();
		String validCode = issueTeacherCode(classroom);
		String email = uniqueEmail();

		for (int i = 0; i < 5; i++) {
			MockHttpServletResponse failed = startSignup(SecureCodes.inviteCode(), "김선생", email);
			assertThat(failed.getStatus()).isEqualTo(400);
		}

		MockHttpServletResponse locked = startSignup(validCode, "김선생", email);
		assertThat(locked.getStatus()).isEqualTo(429);
		assertThat(locked.getContentAsString()).contains("TOO_MANY_ATTEMPTS");
		long retryAfter = Long.parseLong(locked.getHeader("Retry-After"));
		assertThat(retryAfter).isBetween(1L, 600L);

		clock.advance(Duration.ofMinutes(10).plusSeconds(1));
		MockHttpServletResponse allowed = startSignup(validCode, "김선생", email);
		assertThat(allowed.getStatus()).isEqualTo(202);
	}

	@Test
	void otherEmailsAreNotLockedBySomeoneElsesFailures() throws Exception {
		Classroom classroom = newClassroom();
		String validCode = issueTeacherCode(classroom);
		String victim = uniqueEmail();
		for (int i = 0; i < 5; i++) {
			startSignup(SecureCodes.inviteCode(), "김선생", victim);
		}

		MockHttpServletResponse other = startSignup(validCode, "김선생", uniqueEmail());
		assertThat(other.getStatus()).isEqualTo(202);
	}

	@Test
	void reissuingBetweenTheTwoStepsInvalidatesTheSignup() throws Exception {
		Classroom classroom = newClassroom();
		String firstCode = issueTeacherCode(classroom);
		String email = uniqueEmail();
		MockHttpServletResponse started = startSignup(firstCode, "김선생", email);
		assertThat(started.getStatus()).isEqualTo(202);

		issueTeacherCode(classroom); // 이전 코드는 즉시 무효

		MockHttpServletResponse verified = verifySignup(read(started, "$.pendingSignupId"),
				mail.lastSignupCode(email));
		assertThat(verified.getStatus()).isEqualTo(400);
		assertThat(verified.getContentAsString()).contains("INVALID_INVITE_CODE");
		assertThat(jdbc.queryForObject("select count(*) from user_account", Integer.class)).isZero();
		assertThat(classroomPort.findClassroomById(classroom.getId()).orElseThrow().hasTeacher()).isFalse();
	}

}
