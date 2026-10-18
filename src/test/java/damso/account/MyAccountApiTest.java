package damso.account;

import static damso.support.ErrorResponseAssertions.assertSafeErrorResponse;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

import damso.school.domain.Classroom;
import damso.school.domain.Student;
import damso.support.IntegrationTestSupport;

/** US7 시나리오 1, FR-037: 마이페이지의 내 정보와 연결된 학급. */
class MyAccountApiTest extends IntegrationTestSupport {

	private MockHttpServletResponse rename(String token, String body) throws Exception {
		return send(json(patch("/api/v1/me"), body), token);
	}

	@Test
	void showsOnlyMyOwnInformation() throws Exception {
		Classroom classroom = newClassroom();
		Student student = newStudent(classroom, "김하나");
		SignedUp teacher = signUpWith(issueTeacherCode(classroom), "최선생");
		SignedUp parent = signUpWith(issueParentCode(student), "박부모");

		MockHttpServletResponse mine = send(get("/api/v1/me"), parent.accessToken());

		assertThat(mine.getStatus()).isEqualTo(200);
		assertThat((String) read(mine, "$.userId")).isEqualTo(parent.userId().toString());
		assertThat((String) read(mine, "$.name")).isEqualTo("박부모");
		assertThat((String) read(mine, "$.email")).isEqualTo(parent.email());
		assertThat((String) read(mine, "$.role")).isEqualTo("PARENT");
		assertThat(mine.getContentAsString()).doesNotContain(teacher.email()).doesNotContain("최선생");

		MockHttpServletResponse teacherView = send(get("/api/v1/me"), teacher.accessToken());
		assertThat((String) read(teacherView, "$.role")).isEqualTo("TEACHER");
		assertThat((String) read(teacherView, "$.email")).isEqualTo(teacher.email());
	}

	@Test
	void connectedClassroomsComeFromTheRoleSpecificApisAndShowOnlyMyOwn() throws Exception {
		Classroom mine = newClassroom(1, 1);
		Classroom other = newClassroom(1, 2);
		Student myChild = newStudent(mine, "김하나");
		Student otherChild = newStudent(other, "이둘");
		SignedUp teacher = signUpWith(issueTeacherCode(mine), "최선생");
		SignedUp parent = signUpWith(issueParentCode(myChild), "박부모");
		signUpWith(issueParentCode(otherChild), "정부모");

		MockHttpServletResponse parentView = send(get("/api/v1/parent/children"), parent.accessToken());
		assertThat((Integer) read(parentView, "$.length()")).isEqualTo(1);
		assertThat((String) read(parentView, "$[0].studentName")).isEqualTo("김하나");

		MockHttpServletResponse teacherView = send(get("/api/v1/teacher/classroom"), teacher.accessToken());
		assertThat((Integer) read(teacherView, "$.classroom.classNumber")).isEqualTo(1);
		assertThat((Integer) read(teacherView, "$.students.length()")).isEqualTo(1);
	}

	@Test
	void renameChangesTheNameOnly() throws Exception {
		SignedUp parent = signUpWith(issueParentCode(newStudent(newClassroom(), "김하나")), "박부모");

		MockHttpServletResponse renamed = rename(parent.accessToken(), "{\"name\":\"박새이름\"}");

		assertThat(renamed.getStatus()).isEqualTo(200);
		assertThat((String) read(renamed, "$.name")).isEqualTo("박새이름");
		assertThat((String) read(renamed, "$.email")).isEqualTo(parent.email());
		assertThat(accountPort.findById(parent.userId()).orElseThrow().getName().reveal()).isEqualTo("박새이름");
	}

	@Test
	void nameMustBeOneToTwentyCharactersAndOtherFieldsAreRejected() throws Exception {
		SignedUp parent = signUpWith(issueParentCode(newStudent(newClassroom(), "김하나")), "박부모");

		assertThat(rename(parent.accessToken(), "{\"name\":\"" + "가".repeat(20) + "\"}").getStatus()).isEqualTo(200);
		MockHttpServletResponse tooLong = rename(parent.accessToken(), "{\"name\":\"" + "가".repeat(21) + "\"}");
		assertThat(tooLong.getStatus()).isEqualTo(400);
		assertSafeErrorResponse(tooLong.getContentAsString(), "INVALID_INPUT");
		assertThat(rename(parent.accessToken(), "{\"name\":\"   \"}").getStatus()).isEqualTo(400);
		assertThat(rename(parent.accessToken(), "{\"name\":\"가나\",\"email\":\"new@damso.test\"}").getStatus())
				.isEqualTo(400);
		assertThat(accountPort.findById(parent.userId()).orElseThrow().getEmail()).isEqualTo(parent.email());
	}

	@Test
	void requiresAuthentication() throws Exception {
		assertThat(send(get("/api/v1/me"), null).getStatus()).isEqualTo(401);
		assertThat(rename(null, "{\"name\":\"가나\"}").getStatus()).isEqualTo(401);
	}

}
