package damso.school;

import static damso.support.ErrorResponseAssertions.assertSafeErrorResponse;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

import damso.account.domain.Role;
import damso.school.domain.Classroom;
import damso.school.domain.Student;
import damso.support.IntegrationTestSupport;

/** 교사 프로필과 담당 학급 연결 현황. */
class TeacherApiTest extends IntegrationTestSupport {

	private MockHttpServletResponse putProfile(String token, String displayName, String introduction)
			throws Exception {
		return send(json(put("/api/v1/teacher/profile"),
				"{\"displayName\":\"" + displayName + "\",\"introduction\":\"" + introduction + "\"}"), token);
	}

	@Test
	void teacherUpdatesTheProfileAndGuardiansSeeIt() throws Exception {
		Classroom classroom = newClassroom();
		Student student = newStudent(classroom, "김하나");
		SignedUp teacher = signUpWith(issueTeacherCode(classroom), "최선생");
		SignedUp parent = signUpWith(issueParentCode(student), "박부모");

		MockHttpServletResponse initial = send(get("/api/v1/teacher/profile"), teacher.accessToken());
		assertThat(initial.getStatus()).isEqualTo(200);
		assertThat((String) read(initial, "$.displayName")).isEqualTo("최선생");
		assertThat((String) read(initial, "$.introduction")).isEmpty();

		MockHttpServletResponse updated = putProfile(teacher.accessToken(), "최은지 선생님", "함께 천천히 가요");
		assertThat(updated.getStatus()).isEqualTo(200);
		assertThat((String) read(updated, "$.displayName")).isEqualTo("최은지 선생님");

		MockHttpServletResponse seenByParent = send(get("/api/v1/parent/children"), parent.accessToken());
		assertThat((String) read(seenByParent, "$[0].teacher.displayName")).isEqualTo("최은지 선생님");
		assertThat((String) read(seenByParent, "$[0].teacher.introduction")).isEqualTo("함께 천천히 가요");
	}

	@Test
	void introductionIsLimitedToSixtyCharacters() throws Exception {
		SignedUp teacher = signUpWith(issueTeacherCode(newClassroom()), "최선생");

		assertThat(putProfile(teacher.accessToken(), "최선생", "가".repeat(60)).getStatus()).isEqualTo(200);
		MockHttpServletResponse tooLong = putProfile(teacher.accessToken(), "최선생", "가".repeat(61));
		assertThat(tooLong.getStatus()).isEqualTo(400);
		assertSafeErrorResponse(tooLong.getContentAsString(), "INVALID_INPUT");
		assertThat(putProfile(teacher.accessToken(), "최선생", "").getStatus()).isEqualTo(200);
	}

	@Test
	void displayNameMustBeOneToTwentyCharacters() throws Exception {
		SignedUp teacher = signUpWith(issueTeacherCode(newClassroom()), "최선생");

		assertThat(putProfile(teacher.accessToken(), "가".repeat(20), "소개").getStatus()).isEqualTo(200);
		assertThat(putProfile(teacher.accessToken(), "가".repeat(21), "소개").getStatus()).isEqualTo(400);
		assertThat(putProfile(teacher.accessToken(), "   ", "소개").getStatus()).isEqualTo(400);
		assertThat(send(json(put("/api/v1/teacher/profile"), "{\"displayName\":\"최선생\"}"), teacher.accessToken())
				.getStatus()).isEqualTo(400);
	}

	@Test
	void profileRejectsUnknownFieldsLikeAnEmailOrPhoto() throws Exception {
		SignedUp teacher = signUpWith(issueTeacherCode(newClassroom()), "최선생");

		MockHttpServletResponse response = send(json(put("/api/v1/teacher/profile"),
				"{\"displayName\":\"최선생\",\"introduction\":\"\",\"photoUrl\":\"http://x\"}"), teacher.accessToken());

		assertThat(response.getStatus()).isEqualTo(400);
	}

	@Test
	void classroomShowsOnlyOwnClassWithGuardianLinkedFlagsAndNoParentIdentity() throws Exception {
		Classroom mine = newClassroom(3, 2);
		Classroom theirs = newClassroom(3, 3);
		Student linked = newStudent(mine, "김하나");
		newStudent(mine, "이둘");
		newStudent(theirs, "박셋");
		SignedUp teacher = signUpWith(issueTeacherCode(mine), "최선생");
		SignedUp parent = signUpWith(issueParentCode(linked), "정학부모");

		MockHttpServletResponse response = send(get("/api/v1/teacher/classroom"), teacher.accessToken());

		assertThat(response.getStatus()).isEqualTo(200);
		assertThat((Integer) read(response, "$.classroom.grade")).isEqualTo(3);
		assertThat((Integer) read(response, "$.classroom.classNumber")).isEqualTo(2);
		List<String> names = read(response, "$.students[*].name");
		assertThat(names).containsExactlyInAnyOrder("김하나", "이둘");
		List<Boolean> flags = read(response, "$.students[?(@.name=='김하나')].guardianLinked");
		assertThat(flags).containsExactly(true);
		List<Boolean> notLinked = read(response, "$.students[?(@.name=='이둘')].guardianLinked");
		assertThat(notLinked).containsExactly(false);

		// 학부모의 이메일·이름은 어디에도 없다
		String body = response.getContentAsString();
		assertThat(body).doesNotContain("@").doesNotContain(parent.email()).doesNotContain("정학부모")
				.doesNotContain("박셋");
	}

	@Test
	void teacherWhoseClassroomIsEmptyStillSeesItAndOtherTeachersCannotReachIt() throws Exception {
		Classroom first = newClassroom(1, 1);
		Classroom second = newClassroom(1, 2);
		newStudent(first, "김하나");
		newStudent(second, "이둘");
		SignedUp firstTeacher = signUpWith(issueTeacherCode(first), "최선생");
		SignedUp secondTeacher = signUpWith(issueTeacherCode(second), "한선생");

		List<String> firstNames = read(send(get("/api/v1/teacher/classroom"), firstTeacher.accessToken()),
				"$.students[*].name");
		List<String> secondNames = read(send(get("/api/v1/teacher/classroom"), secondTeacher.accessToken()),
				"$.students[*].name");

		assertThat(firstNames).containsExactly("김하나");
		assertThat(secondNames).containsExactly("이둘");
	}

	@Test
	void otherRolesCannotUseTeacherApis() throws Exception {
		String parent = tokenFor(createAccount(Role.PARENT));
		String admin = tokenFor(createAccount(Role.SUPER_ADMIN));

		for (String token : List.of(parent, admin)) {
			for (MockHttpServletResponse response : List.of(
					send(get("/api/v1/teacher/profile"), token),
					putProfile(token, "가나", "소개"),
					send(get("/api/v1/teacher/classroom"), token))) {
				assertThat(response.getStatus()).isEqualTo(403);
				assertSafeErrorResponse(response.getContentAsString(), "FORBIDDEN");
			}
		}
		assertThat(send(get("/api/v1/teacher/classroom"), null).getStatus()).isEqualTo(401);
	}

}
