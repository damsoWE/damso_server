package damso.school;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

import damso.account.domain.Role;
import damso.school.domain.Classroom;
import damso.school.domain.Student;
import damso.support.IntegrationTestSupport;

/**
 * SC-005·006, FR-036, 원칙 I: 교사·학부모·관리자의 어떤 응답에도 다른 사용자의 이메일이나 연락처 필드가 없고,
 * 다른 학급의 정보를 얻을 경로가 없다. 본인 이메일은 마이페이지(/me)에만 나온다.
 */
class CrossAccountExposureTest extends IntegrationTestSupport {

	private static final Pattern CONTACT_FIELD = Pattern.compile("\"(phone|tel|mobile|address|email)\"",
			Pattern.CASE_INSENSITIVE);

	@Test
	void noResponseExposesAnotherUsersEmailOrContactFields() throws Exception {
		Classroom roomA = newClassroom(1, 1);
		Classroom roomB = newClassroom(1, 2);
		Student childA = newStudent(roomA, "에이학생");
		Student childB = newStudent(roomB, "비학생");
		SignedUp teacherA = signUpWith(issueTeacherCode(roomA), "에이선생");
		SignedUp teacherB = signUpWith(issueTeacherCode(roomB), "비선생");
		SignedUp parentA = signUpWith(issueParentCode(childA), "에이부모");
		SignedUp parentB = signUpWith(issueParentCode(childB), "비부모");
		String admin = tokenFor(createAccount(Role.SUPER_ADMIN));
		List<String> allEmails = List.of(teacherA.email(), teacherB.email(), parentA.email(), parentB.email());

		List<MockHttpServletResponse> responses = new ArrayList<>();
		for (SignedUp teacher : List.of(teacherA, teacherB)) {
			responses.add(send(get("/api/v1/teacher/profile"), teacher.accessToken()));
			responses.add(send(get("/api/v1/teacher/classroom"), teacher.accessToken()));
		}
		for (SignedUp parent : List.of(parentA, parentB)) {
			responses.add(send(get("/api/v1/parent/children"), parent.accessToken()));
		}
		for (Classroom room : List.of(roomA, roomB)) {
			responses.add(send(get("/api/v1/admin/classrooms/" + room.getId() + "/students"), admin));
			responses.add(send(get("/api/v1/admin/invite-codes").param("classroomId", room.getId().toString()),
					admin));
		}
		responses.add(send(get("/api/v1/admin/classrooms"), admin));

		for (MockHttpServletResponse response : responses) {
			assertThat(response.getStatus()).isEqualTo(200);
			String body = response.getContentAsString();
			assertThat(body).doesNotContain("@");
			for (String email : allEmails) {
				assertThat(body).doesNotContain(email);
			}
			assertThat(CONTACT_FIELD.matcher(body).find()).as("contact field in %s", body).isFalse();
		}
	}

	@Test
	void myPageShowsOnlyMyOwnEmail() throws Exception {
		Classroom room = newClassroom();
		SignedUp teacher = signUpWith(issueTeacherCode(room), "에이선생");
		SignedUp parent = signUpWith(issueParentCode(newStudent(room, "에이학생")), "에이부모");

		String teacherBody = send(get("/api/v1/me"), teacher.accessToken()).getContentAsString();
		String parentBody = send(get("/api/v1/me"), parent.accessToken()).getContentAsString();

		assertThat(teacherBody).contains(teacher.email()).doesNotContain(parent.email());
		assertThat(parentBody).contains(parent.email()).doesNotContain(teacher.email());
	}

	@Test
	void eachTeacherAndParentSeesOnlyTheirOwnClassroomAndChildren() throws Exception {
		Classroom roomA = newClassroom(1, 1);
		Classroom roomB = newClassroom(1, 2);
		Student childA = newStudent(roomA, "에이학생");
		Student childB = newStudent(roomB, "비학생");
		SignedUp teacherA = signUpWith(issueTeacherCode(roomA), "에이선생");
		SignedUp parentA = signUpWith(issueParentCode(childA), "에이부모");
		signUpWith(issueParentCode(childB), "비부모");

		String teacherView = send(get("/api/v1/teacher/classroom"), teacherA.accessToken()).getContentAsString();
		String parentView = send(get("/api/v1/parent/children"), parentA.accessToken()).getContentAsString();

		assertThat(teacherView).contains("에이학생").doesNotContain("비학생").doesNotContain(roomB.getId().toString());
		assertThat(parentView).contains("에이학생").doesNotContain("비학생").doesNotContain(roomB.getId().toString());
	}

}
