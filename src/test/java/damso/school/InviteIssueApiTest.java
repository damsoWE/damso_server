package damso.school;

import static damso.support.ErrorResponseAssertions.assertSafeErrorResponse;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletResponse;

import damso.account.domain.Role;
import damso.school.application.port.out.ClassroomPort;
import damso.school.application.port.out.StudentPort;
import damso.school.domain.Classroom;
import damso.school.domain.Student;
import damso.support.IntegrationTestSupport;

/** 초대 코드 발행·재발행·현황. */
class InviteIssueApiTest extends IntegrationTestSupport {

	private static final String CODE_PATTERN = "^[A-HJKMNP-Z2-9]{10}$";

	@Autowired
	private ClassroomPort classrooms;

	@Autowired
	private StudentPort students;

	private String admin;

	private Classroom classroom;

	private Student student;

	@BeforeEach
	void setUp() {
		admin = tokenFor(createAccount(Role.SUPER_ADMIN));
		classroom = classrooms.saveClassroom(new Classroom(1, 1));
		student = students.saveStudent(new Student(classroom.getId(), "김하나"));
	}

	private MockHttpServletResponse issueTeacher() throws Exception {
		return send(post("/api/v1/admin/classrooms/" + classroom.getId() + "/teacher-invite"), admin);
	}

	private MockHttpServletResponse issueParent() throws Exception {
		return send(post("/api/v1/admin/students/" + student.getId() + "/parent-invite"), admin);
	}

	private MockHttpServletResponse invites() throws Exception {
		return send(get("/api/v1/admin/invite-codes").param("classroomId", classroom.getId().toString()), admin);
	}

	private Map<String, Object> dbRow(String inviteCodeId) {
		return jdbc.queryForMap("select code_hash, status, active_target_key from invite_code where id = ?",
				UUID.fromString(inviteCodeId));
	}

	@Test
	void teacherInviteShowsTheCodeOnlyInTheIssueResponse() throws Exception {
		Instant before = clock.instant();
		MockHttpServletResponse issued = issueTeacher();

		assertThat(issued.getStatus()).isEqualTo(201);
		String code = read(issued, "$.code");
		assertThat(code).matches(CODE_PATTERN);
		Instant expiresAt = Instant.parse(read(issued, "$.expiresAt"));
		assertThat(Duration.between(before, expiresAt)).isBetween(Duration.ofHours(24).minusSeconds(5),
				Duration.ofHours(24).plusSeconds(5));

		String listed = invites().getContentAsString();
		assertThat(listed).doesNotContain(code).doesNotContain("\"code\"");

		Map<String, Object> row = dbRow(read(issued, "$.inviteCodeId"));
		assertThat((String) row.get("code_hash")).hasSize(64).isNotEqualTo(code).doesNotContain(code);
		assertThat(row.get("status")).isEqualTo("ISSUED");
		assertThat(row.get("active_target_key")).isEqualTo("TEACHER:" + classroom.getId());
	}

	@Test
	void parentInviteIsIssuedPerStudent() throws Exception {
		MockHttpServletResponse issued = issueParent();

		assertThat(issued.getStatus()).isEqualTo(201);
		assertThat((String) read(issued, "$.code")).matches(CODE_PATTERN);

		MockHttpServletResponse listed = invites();
		assertThat((Integer) read(listed, "$.length()")).isEqualTo(1);
		assertThat((String) read(listed, "$[0].kind")).isEqualTo("PARENT");
		assertThat((String) read(listed, "$[0].studentId")).isEqualTo(student.getId().toString());
		assertThat((String) read(listed, "$[0].studentName")).isEqualTo("김하나");
		assertThat((String) read(listed, "$[0].status")).isEqualTo("UNUSED");
	}

	@Test
	void classroomWithTeacherCannotGetATeacherInvite() throws Exception {
		classroom.assignTeacher(UUID.randomUUID());
		classrooms.saveClassroom(classroom);

		MockHttpServletResponse response = issueTeacher();

		assertThat(response.getStatus()).isEqualTo(409);
		assertSafeErrorResponse(response.getContentAsString(), "CLASSROOM_HAS_TEACHER");
	}

	@Test
	void studentWithGuardianCannotGetAParentInvite() throws Exception {
		student.assignGuardian(UUID.randomUUID());
		students.saveStudent(student);

		MockHttpServletResponse response = issueParent();

		assertThat(response.getStatus()).isEqualTo(409);
		assertSafeErrorResponse(response.getContentAsString(), "STUDENT_HAS_GUARDIAN");
	}

	@Test
	void reissuingRevokesThePreviousCodeImmediately() throws Exception {
		MockHttpServletResponse first = issueParent();
		MockHttpServletResponse second = issueParent();

		assertThat(second.getStatus()).isEqualTo(201);
		assertThat((String) read(second, "$.code")).isNotEqualTo(read(first, "$.code"));

		Map<String, Object> oldRow = dbRow(read(first, "$.inviteCodeId"));
		assertThat(oldRow.get("status")).isEqualTo("REVOKED");
		assertThat(oldRow.get("active_target_key")).isNull();

		Map<String, Object> newRow = dbRow(read(second, "$.inviteCodeId"));
		assertThat(newRow.get("status")).isEqualTo("ISSUED");
		assertThat(newRow.get("active_target_key")).isEqualTo("PARENT:" + student.getId());

		MockHttpServletResponse listed = invites();
		List<String> statuses = read(listed, "$[*].status");
		assertThat(statuses).containsExactlyInAnyOrder("REVOKED", "UNUSED");
	}

	@Test
	void reissuingManyTimesKeepsOnlyOneActiveCodePerTarget() throws Exception {
		for (int i = 0; i < 4; i++) {
			assertThat(issueParent().getStatus()).isEqualTo(201);
		}
		Integer active = jdbc.queryForObject("select count(*) from invite_code where status = 'ISSUED'",
				Integer.class);
		assertThat(active).isEqualTo(1);
	}

	@Test
	void codeShowsAsExpiredAfter24Hours() throws Exception {
		issueTeacher();

		clock.advance(Duration.ofHours(24).plusSeconds(1));

		MockHttpServletResponse listed = invites();
		assertThat((String) read(listed, "$[0].status")).isEqualTo("EXPIRED");
	}

	@Test
	void unknownTargetsAreNotFound() throws Exception {
		MockHttpServletResponse teacher = send(
				post("/api/v1/admin/classrooms/" + UUID.randomUUID() + "/teacher-invite"), admin);
		assertThat(teacher.getStatus()).isEqualTo(404);
		MockHttpServletResponse parent = send(
				post("/api/v1/admin/students/" + UUID.randomUUID() + "/parent-invite"), admin);
		assertThat(parent.getStatus()).isEqualTo(404);
		MockHttpServletResponse listed = send(
				get("/api/v1/admin/invite-codes").param("classroomId", UUID.randomUUID().toString()), admin);
		assertThat(listed.getStatus()).isEqualTo(404);
	}

	@Test
	void missingClassroomIdParameterIsInvalidInput() throws Exception {
		MockHttpServletResponse response = send(get("/api/v1/admin/invite-codes"), admin);
		assertThat(response.getStatus()).isEqualTo(400);
		assertSafeErrorResponse(response.getContentAsString(), "INVALID_INPUT");
	}

}
