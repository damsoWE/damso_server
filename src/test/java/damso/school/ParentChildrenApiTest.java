package damso.school;

import static damso.support.ErrorResponseAssertions.assertSafeErrorResponse;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

import damso.account.domain.Role;
import damso.common.crypto.SecureCodes;
import damso.school.domain.Classroom;
import damso.school.domain.Student;
import damso.support.IntegrationTestSupport;

/** 학부모 가입·자녀 추가·자녀 목록. */
class ParentChildrenApiTest extends IntegrationTestSupport {

	private MockHttpServletResponse link(String accessToken, String inviteCode) throws Exception {
		return send(json(post("/api/v1/parent/children"), "{\"inviteCode\":\"" + inviteCode + "\"}"), accessToken);
	}

	private MockHttpServletResponse children(String accessToken) throws Exception {
		return send(get("/api/v1/parent/children"), accessToken);
	}

	@Test
	void parentSignsUpAndBecomesTheGuardian() throws Exception {
		Classroom classroom = newClassroom();
		Student student = newStudent(classroom, "김하나");

		SignedUp parent = signUpWith(issueParentCode(student), "박부모");

		assertThat(studentPort.findStudentById(student.getId()).orElseThrow().getGuardianId())
				.isEqualTo(parent.userId());
		assertThat(accountPort.findById(parent.userId()).orElseThrow().getRole()).isEqualTo(Role.PARENT);
		List<Map<String, Object>> history = jdbc.queryForList(
				"select target_type, target_id, event from link_history where account_id = ?", parent.userId());
		assertThat(history).hasSize(1);
		assertThat(history.get(0).get("target_type")).isEqualTo("STUDENT");
		assertThat(history.get(0).get("target_id")).isEqualTo(student.getId());
		assertThat(history.get(0).get("event")).isEqualTo("LINKED");
		assertThat(jdbc.queryForObject("select status from invite_code", String.class)).isEqualTo("USED");
	}

	@Test
	void listShowsOnlyOwnChildrenWithHomeroomProfileAndNoEmail() throws Exception {
		Classroom classroom = newClassroom(3, 2);
		Student student = newStudent(classroom, "김하나");
		Student otherStudent = newStudent(classroom, "이둘");
		SignedUp teacher = signUpWith(issueTeacherCode(classroom), "최선생");
		SignedUp parent = signUpWith(issueParentCode(student), "박부모");
		SignedUp otherParent = signUpWith(issueParentCode(otherStudent), "정부모");

		MockHttpServletResponse listed = children(parent.accessToken());

		assertThat(listed.getStatus()).isEqualTo(200);
		assertThat((Integer) read(listed, "$.length()")).isEqualTo(1);
		assertThat((String) read(listed, "$[0].studentId")).isEqualTo(student.getId().toString());
		assertThat((String) read(listed, "$[0].studentName")).isEqualTo("김하나");
		assertThat((Integer) read(listed, "$[0].classroom.grade")).isEqualTo(3);
		assertThat((Integer) read(listed, "$[0].classroom.classNumber")).isEqualTo(2);
		assertThat((String) read(listed, "$[0].teacher.displayName")).isEqualTo("최선생");
		assertThat((String) read(listed, "$[0].teacher.introduction")).isEmpty();

		// 담임·다른 학부모의 이메일이나 이름이 응답에 없다
		String body = listed.getContentAsString();
		assertThat(body).doesNotContain("@").doesNotContain(teacher.email()).doesNotContain(otherParent.email())
				.doesNotContain("이둘").doesNotContain("정부모").doesNotContain("email");

		// 다른 학부모는 자기 자녀만 본다
		MockHttpServletResponse other = children(otherParent.accessToken());
		assertThat((String) read(other, "$[0].studentName")).isEqualTo("이둘");
		assertThat((Integer) read(other, "$.length()")).isEqualTo(1);
	}

	@Test
	void teacherIsNullUntilTheHomeroomTeacherJoins() throws Exception {
		Classroom classroom = newClassroom();
		Student student = newStudent(classroom, "김하나");
		SignedUp parent = signUpWith(issueParentCode(student), "박부모");

		MockHttpServletResponse listed = children(parent.accessToken());

		assertThat(listed.getContentAsString()).contains("\"teacher\":null");
	}

	@Test
	void secondChildIsLinkedByEnteringAnotherCodeEvenInAnotherClassroom() throws Exception {
		Classroom first = newClassroom(1, 1);
		Classroom second = newClassroom(1, 2);
		Student firstChild = newStudent(first, "김하나");
		Student secondChild = newStudent(second, "김둘");
		SignedUp parent = signUpWith(issueParentCode(firstChild), "박부모");

		MockHttpServletResponse linked = link(parent.accessToken(), issueParentCode(secondChild));

		assertThat(linked.getStatus()).isEqualTo(201);
		assertThat((String) read(linked, "$.studentId")).isEqualTo(secondChild.getId().toString());
		MockHttpServletResponse listed = children(parent.accessToken());
		assertThat((Integer) read(listed, "$.length()")).isEqualTo(2);
		List<Integer> classNumbers = read(listed, "$[*].classroom.classNumber");
		assertThat(classNumbers).containsExactlyInAnyOrder(1, 2);
		assertThat(jdbc.queryForObject("select count(*) from link_history where account_id = ?", Integer.class,
				parent.userId())).isEqualTo(2);
	}

	@Test
	void unusableCodesGetTheSameResponseAsAnUnknownOne() throws Exception {
		Classroom classroom = newClassroom();
		Student mine = newStudent(classroom, "김하나");
		SignedUp parent = signUpWith(issueParentCode(mine), "박부모");

		String unknown = link(parent.accessToken(), SecureCodes.inviteCode()).getContentAsString();
		assertThat(unknown).contains("INVALID_INVITE_CODE");

		// 교사 코드
		String teacherCode = issueTeacherCode(classroom);
		MockHttpServletResponse withTeacherCode = link(parent.accessToken(), teacherCode);
		assertThat(withTeacherCode.getStatus()).isEqualTo(400);
		assertThat(withTeacherCode.getContentAsString()).isEqualTo(unknown);

		// 이미 다른 학부모가 쓴 코드
		Student taken = newStudent(classroom, "이둘");
		String usedCode = issueParentCode(taken);
		signUpWith(usedCode, "정부모");
		assertThat(link(parent.accessToken(), usedCode).getContentAsString()).isEqualTo(unknown);

		// 이미 보호자가 있는 학생의 코드
		Student guarded = newStudent(classroom, "박셋");
		String guardedCode = issueParentCode(guarded);
		guarded.assignGuardian(UUID.randomUUID());
		studentPort.saveStudent(guarded);
		assertThat(link(parent.accessToken(), guardedCode).getContentAsString()).isEqualTo(unknown);

		// 만료된 코드
		Student late = newStudent(classroom, "최넷");
		String expiredCode = issueParentCode(late);
		clock.advance(Duration.ofHours(24).plusSeconds(1));
		assertThat(link(parent.accessToken(), expiredCode).getContentAsString()).isEqualTo(unknown);

		// 연결은 늘지 않았다
		assertThat((Integer) read(children(parent.accessToken()), "$.length()")).isEqualTo(1);
	}

	@Test
	void sixthFailureForTheSameAccountIsLockedAndOtherAccountsAreNot() throws Exception {
		Classroom classroom = newClassroom();
		SignedUp parent = signUpWith(issueParentCode(newStudent(classroom, "김하나")), "박부모");
		SignedUp other = signUpWith(issueParentCode(newStudent(classroom, "이둘")), "정부모");
		String validCode = issueParentCode(newStudent(classroom, "박셋"));

		for (int i = 0; i < 5; i++) {
			assertThat(link(parent.accessToken(), SecureCodes.inviteCode()).getStatus()).isEqualTo(400);
		}

		MockHttpServletResponse locked = link(parent.accessToken(), validCode);
		assertThat(locked.getStatus()).isEqualTo(429);
		assertThat(Long.parseLong(locked.getHeader("Retry-After"))).isBetween(1L, 600L);

		// 다른 계정은 같은 코드를 쓸 수 있다
		assertThat(link(other.accessToken(), validCode).getStatus()).isEqualTo(201);

		clock.advance(Duration.ofMinutes(10).plusSeconds(1));
		String anotherCode = issueParentCode(newStudent(classroom, "최넷"));
		assertThat(link(parent.accessToken(), anotherCode).getStatus()).isEqualTo(201);
	}

	@Test
	void malformedCodeIsInvalidInputAndDoesNotCount() throws Exception {
		Classroom classroom = newClassroom();
		SignedUp parent = signUpWith(issueParentCode(newStudent(classroom, "김하나")), "박부모");

		for (int i = 0; i < 6; i++) {
			MockHttpServletResponse response = link(parent.accessToken(), "0OIL1");
			assertThat(response.getStatus()).isEqualTo(400);
			assertSafeErrorResponse(response.getContentAsString(), "INVALID_INPUT");
		}
		String validCode = issueParentCode(newStudent(classroom, "이둘"));
		assertThat(link(parent.accessToken(), validCode).getStatus()).isEqualTo(201);
	}

	@Test
	void otherRolesCannotUseParentApis() throws Exception {
		String teacher = tokenFor(createAccount(Role.TEACHER));
		String admin = tokenFor(createAccount(Role.SUPER_ADMIN));

		for (String token : List.of(teacher, admin)) {
			MockHttpServletResponse listed = children(token);
			assertThat(listed.getStatus()).isEqualTo(403);
			assertSafeErrorResponse(listed.getContentAsString(), "FORBIDDEN");
			assertThat(link(token, SecureCodes.inviteCode()).getStatus()).isEqualTo(403);
		}
		assertThat(children(null).getStatus()).isEqualTo(401);
	}

}
