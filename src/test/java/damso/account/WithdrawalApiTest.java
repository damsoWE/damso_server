package damso.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

import damso.account.domain.AccountStatus;
import damso.account.domain.Role;
import damso.account.domain.UserAccount;
import damso.school.domain.Classroom;
import damso.school.domain.Student;
import damso.support.IntegrationTestSupport;

/** US7 시나리오 2~7, FR-038·038a, research R11: 탈퇴는 비식별화·연결 해제·이력 보존이고 같은 이메일로 다시 가입할 수 있다. */
class WithdrawalApiTest extends IntegrationTestSupport {

	private static final String WRONG = "Wrong" + "Pass1";

	private MockHttpServletResponse withdraw(String token, String password) throws Exception {
		return send(json(post("/api/v1/me/withdrawal"), "{\"password\":\"" + password + "\"}"), token);
	}

	private int status(String token) throws Exception {
		return send(get("/test/auth/any"), token).getStatus();
	}

	@Test
	void wrongPasswordDoesNotWithdraw() throws Exception {
		SignedUp parent = signUpWith(issueParentCode(newStudent(newClassroom(), "김하나")), "박부모");

		MockHttpServletResponse response = withdraw(parent.accessToken(), WRONG);

		assertThat(response.getStatus()).isEqualTo(401);
		assertThat(response.getContentAsString()).contains("INVALID_CREDENTIALS");
		assertThat(accountPort.findById(parent.userId()).orElseThrow().getStatus()).isEqualTo(AccountStatus.ACTIVE);
		assertThat(status(parent.accessToken())).isEqualTo(200);
	}

	@Test
	void repeatedWrongPasswordsAreRateLimitedLikeLogin() throws Exception {
		SignedUp parent = signUpWith(issueParentCode(newStudent(newClassroom(), "김하나")), "박부모");
		for (int i = 0; i < 3; i++) {
			assertThat(withdraw(parent.accessToken(), WRONG).getStatus()).isEqualTo(401);
		}

		MockHttpServletResponse waiting = withdraw(parent.accessToken(), TEST_PASSWORD);

		assertThat(waiting.getStatus()).isEqualTo(429);
		assertThat(accountPort.findById(parent.userId()).orElseThrow().isActive()).isTrue();
		clock.advance(Duration.ofSeconds(31));
		assertThat(withdraw(parent.accessToken(), TEST_PASSWORD).getStatus()).isEqualTo(204);
	}

	@Test
	void parentWithdrawalAnonymizesReleasesChildrenAndKeepsAnIdOnlyHistory() throws Exception {
		Classroom classroom = newClassroom();
		Student first = newStudent(classroom, "김하나");
		Student second = newStudent(classroom, "김둘");
		SignedUp parent = signUpWith(issueParentCode(first), "박부모");
		MockHttpServletResponse linked = send(json(post("/api/v1/parent/children"),
				"{\"inviteCode\":\"" + issueParentCode(second) + "\"}"), parent.accessToken());
		assertThat(linked.getStatus()).isEqualTo(201);
		MockHttpServletResponse otherDevice = send(json(post("/api/v1/auth/login"),
				"{\"email\":\"" + parent.email() + "\",\"password\":\"" + TEST_PASSWORD + "\"}"), null);
		String otherAccess = read(otherDevice, "$.accessToken");
		String otherRefresh = read(otherDevice, "$.refreshToken");

		MockHttpServletResponse response = withdraw(parent.accessToken(), TEST_PASSWORD);

		assertThat(response.getStatus()).isEqualTo(204);
		// 모든 기기의 세션이 끊긴다
		assertThat(status(parent.accessToken())).isEqualTo(401);
		assertThat(status(otherAccess)).isEqualTo(401);
		assertThat(send(json(post("/api/v1/auth/token/refresh"), "{\"refreshToken\":\"" + otherRefresh + "\"}"),
				null).getStatus()).isEqualTo(401);
		// 이메일·이름 비식별화
		UserAccount gone = accountPort.findById(parent.userId()).orElseThrow();
		assertThat(gone.getStatus()).isEqualTo(AccountStatus.WITHDRAWN);
		assertThat(gone.getEmail()).isNull();
		assertThat(gone.getPasswordHash()).isNull();
		assertThat(gone.getName().reveal()).isEqualTo(UserAccount.WITHDRAWN_NAME);
		assertThat(gone.getWithdrawnAt()).isNotNull();
		assertThat(accountPort.findByEmail(parent.email())).isEmpty();
		assertThat(send(json(post("/api/v1/auth/login"),
				"{\"email\":\"" + parent.email() + "\",\"password\":\"" + TEST_PASSWORD + "\"}"), null).getStatus())
						.isEqualTo(401);
		// 연결 해제: 학생은 남고 보호자만 비워진다
		assertThat(studentPort.findStudentById(first.getId()).orElseThrow().hasGuardian()).isFalse();
		assertThat(studentPort.findStudentById(second.getId()).orElseThrow().hasGuardian()).isFalse();

		// 슈퍼 관리자는 해제된 학생에게 새 코드를 발행할 수 있다
		String admin = tokenFor(createAccount(Role.SUPER_ADMIN));
		MockHttpServletResponse students = send(get("/api/v1/admin/classrooms/" + classroom.getId() + "/students"),
				admin);
		List<Boolean> flags = read(students, "$[*].guardianLinked");
		assertThat(flags).containsExactly(false, false);
		assertThat(send(post("/api/v1/admin/students/" + first.getId() + "/parent-invite"), admin).getStatus())
				.isEqualTo(201);

		// 이력: 연결 2건 + 해제 2건 + 탈퇴 1건, 개인정보 없음
		List<Map<String, Object>> history = jdbc.queryForList(
				"select target_type, target_id, event from link_history where account_id = ? order by occurred_at",
				parent.userId());
		assertThat(history).hasSize(5);
		assertThat(history.stream().filter(row -> "UNLINKED".equals(row.get("event"))).map(row -> row.get("target_id")))
				.containsExactlyInAnyOrder(first.getId(), second.getId());
		assertThat(history.stream().filter(row -> "WITHDRAWN".equals(row.get("event"))).count()).isEqualTo(1);
		assertThat(history.stream().filter(row -> "LINKED".equals(row.get("event"))).count()).isEqualTo(2);
		List<String> columns = jdbc.queryForList(
				"select column_name from information_schema.columns where table_name = 'link_history'", String.class);
		assertThat(columns).doesNotContain("email", "name", "phone");
		// 초대 코드 사용 기록 등 다른 기록은 지워지지 않는다
		assertThat(jdbc.queryForObject("select count(*) from invite_code where status = 'USED'", Integer.class))
				.isEqualTo(2);
	}

	@Test
	void teacherWithdrawalReleasesTheClassroomAndDeletesTheProfile() throws Exception {
		Classroom classroom = newClassroom();
		SignedUp teacher = signUpWith(issueTeacherCode(classroom), "최선생");

		MockHttpServletResponse response = withdraw(teacher.accessToken(), TEST_PASSWORD);

		assertThat(response.getStatus()).isEqualTo(204);
		assertThat(classroomPort.findClassroomById(classroom.getId()).orElseThrow().hasTeacher()).isFalse();
		assertThat(jdbc.queryForObject("select count(*) from teacher_profile", Integer.class)).isZero();
		List<Map<String, Object>> history = jdbc.queryForList(
				"select target_type, event from link_history where account_id = ? order by occurred_at",
				teacher.userId());
		assertThat(history).extracting(row -> row.get("event") + ":" + row.get("target_type"))
				.containsExactly("LINKED:CLASSROOM", "UNLINKED:CLASSROOM", "WITHDRAWN:NONE");
		// 새 담임을 위한 교사 코드를 다시 발행할 수 있다
		String admin = tokenFor(createAccount(Role.SUPER_ADMIN));
		assertThat(send(post("/api/v1/admin/classrooms/" + classroom.getId() + "/teacher-invite"), admin).getStatus())
				.isEqualTo(201);
	}

	@Test
	void theSameEmailCanSignUpAgainWithANewInviteCode() throws Exception {
		Classroom classroom = newClassroom();
		Student student = newStudent(classroom, "김하나");
		SignedUp parent = signUpWith(issueParentCode(student), "박부모");
		String email = parent.email();
		assertThat(withdraw(parent.accessToken(), TEST_PASSWORD).getStatus()).isEqualTo(204);
		clock.advance(Duration.ofSeconds(61)); // 같은 이메일로 인증 메일을 다시 받으려면 60초 간격이 필요하다

		MockHttpServletResponse started = startSignup(issueParentCode(student), "박부모", email);
		assertThat(started.getStatus()).isEqualTo(202);
		MockHttpServletResponse verified = verifySignup(read(started, "$.pendingSignupId"), mail.lastSignupCode(email));

		assertThat(verified.getStatus()).isEqualTo(201);
		UserAccount fresh = accountPort.findByEmail(email).orElseThrow();
		assertThat(fresh.getId()).isNotEqualTo(parent.userId());
		assertThat(studentPort.findStudentById(student.getId()).orElseThrow().getGuardianId()).isEqualTo(fresh.getId());
		// 이전 계정의 이력은 그대로 남는다
		assertThat(jdbc.queryForObject("select count(*) from link_history where account_id = ?", Integer.class,
				parent.userId())).isEqualTo(3);
	}

	@Test
	void superAdminCannotWithdraw() throws Exception {
		String admin = tokenFor(createAccount(Role.SUPER_ADMIN));

		MockHttpServletResponse response = withdraw(admin, TEST_PASSWORD);

		assertThat(response.getStatus()).isEqualTo(403);
		assertThat(response.getContentAsString()).contains("FORBIDDEN");
	}

	@Test
	void requiresAuthenticationAndAPassword() throws Exception {
		assertThat(withdraw(null, TEST_PASSWORD).getStatus()).isEqualTo(401);
		SignedUp parent = signUpWith(issueParentCode(newStudent(newClassroom(), "김하나")), "박부모");
		assertThat(send(json(post("/api/v1/me/withdrawal"), "{}"), parent.accessToken()).getStatus()).isEqualTo(400);
		assertThat(UUID.fromString(parent.userId().toString())).isNotNull();
	}

}
