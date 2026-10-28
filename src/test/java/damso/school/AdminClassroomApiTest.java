package damso.school;

import static damso.support.ErrorResponseAssertions.assertSafeErrorResponse;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.mock.web.MockHttpServletResponse;

import damso.account.domain.Role;
import damso.support.IntegrationTestSupport;

/** 관리자 학급·학생 명단 API와 역할 검사. */
class AdminClassroomApiTest extends IntegrationTestSupport {

	private String admin;

	@BeforeEach
	void loginAsAdmin() {
		admin = tokenFor(createAccount(Role.SUPER_ADMIN));
	}

	private MockHttpServletResponse createClassroom(int grade, int classNumber) throws Exception {
		return send(json(post("/api/v1/admin/classrooms"),
				"{\"grade\":" + grade + ",\"classNumber\":" + classNumber + "}"), admin);
	}

	private String classroomId(int grade, int classNumber) throws Exception {
		MockHttpServletResponse response = createClassroom(grade, classNumber);
		assertThat(response.getStatus()).isEqualTo(201);
		return read(response, "$.classroomId");
	}

	@Test
	void createsClassroomAndListsIt() throws Exception {
		MockHttpServletResponse created = createClassroom(3, 2);

		assertThat(created.getStatus()).isEqualTo(201);
		assertThat((Integer) read(created, "$.grade")).isEqualTo(3);
		assertThat((Integer) read(created, "$.classNumber")).isEqualTo(2);
		assertThat((Boolean) read(created, "$.homeroomAssigned")).isFalse();
		assertThat(UUID.fromString(read(created, "$.classroomId"))).isNotNull();

		MockHttpServletResponse listed = send(get("/api/v1/admin/classrooms"), admin);
		assertThat(listed.getStatus()).isEqualTo(200);
		assertThat((Integer) read(listed, "$.length()")).isEqualTo(1);
		assertThat((Integer) read(listed, "$[0].studentCount")).isZero();
	}

	@ParameterizedTest
	@org.junit.jupiter.params.provider.CsvSource({ "0,1", "7,1", "1,0", "1,21" })
	void rejectsGradeOrClassNumberOutOfRange(int grade, int classNumber) throws Exception {
		MockHttpServletResponse response = createClassroom(grade, classNumber);
		assertThat(response.getStatus()).isEqualTo(400);
		assertSafeErrorResponse(response.getContentAsString(), "INVALID_INPUT");
	}

	@Test
	void rejectsUnknownFieldsInTheRequestBody() throws Exception {
		MockHttpServletResponse response = send(
				json(post("/api/v1/admin/classrooms"), "{\"grade\":1,\"classNumber\":1,\"phone\":\"010\"}"), admin);
		assertThat(response.getStatus()).isEqualTo(400);
		assertSafeErrorResponse(response.getContentAsString(), "INVALID_INPUT");
	}

	@Test
	void sameGradeAndClassNumberIsAConflict() throws Exception {
		classroomId(1, 1);
		MockHttpServletResponse duplicate = createClassroom(1, 1);
		assertThat(duplicate.getStatus()).isEqualTo(409);
		assertSafeErrorResponse(duplicate.getContentAsString(), "CONFLICT");
	}

	@Test
	void registersStudentsAndListsThemWithoutGuardian() throws Exception {
		String classroomId = classroomId(2, 3);

		MockHttpServletResponse registered = send(json(post("/api/v1/admin/classrooms/" + classroomId + "/students"),
				"{\"names\":[\"김하나\",\"이둘\",\"박셋\"]}"), admin);
		assertThat(registered.getStatus()).isEqualTo(201);
		assertThat((Integer) read(registered, "$.length()")).isEqualTo(3);
		assertThat((Boolean) read(registered, "$[0].guardianLinked")).isFalse();

		MockHttpServletResponse listed = send(get("/api/v1/admin/classrooms/" + classroomId + "/students"), admin);
		assertThat(listed.getStatus()).isEqualTo(200);
		List<String> names = read(listed, "$[*].name");
		assertThat(names).containsExactlyInAnyOrder("김하나", "이둘", "박셋");

		MockHttpServletResponse classrooms = send(get("/api/v1/admin/classrooms"), admin);
		assertThat((Integer) read(classrooms, "$[0].studentCount")).isEqualTo(3);
	}

	@Test
	void studentNamesAreStoredEncrypted() throws Exception {
		String classroomId = classroomId(2, 4);
		send(json(post("/api/v1/admin/classrooms/" + classroomId + "/students"), "{\"names\":[\"비밀이름\"]}"), admin);

		List<String> stored = jdbc.queryForList("select name from student", String.class);
		assertThat(stored).hasSize(1);
		assertThat(stored.get(0)).doesNotContain("비밀이름");
	}

	@Test
	void rejectsEmptyTooManyOrTooLongNames() throws Exception {
		String classroomId = classroomId(4, 1);
		String path = "/api/v1/admin/classrooms/" + classroomId + "/students";

		assertThat(send(json(post(path), "{\"names\":[]}"), admin).getStatus()).isEqualTo(400);

		String forty = IntStream.range(0, 40).mapToObj(i -> "\"학생" + i + "\"")
				.reduce((a, b) -> a + "," + b).orElseThrow();
		assertThat(send(json(post(path), "{\"names\":[" + forty + "]}"), admin).getStatus()).isEqualTo(201);

		String fortyOne = forty + ",\"학생40\"";
		MockHttpServletResponse tooMany = send(json(post(path), "{\"names\":[" + fortyOne + "]}"), admin);
		assertThat(tooMany.getStatus()).isEqualTo(400);
		assertSafeErrorResponse(tooMany.getContentAsString(), "INVALID_INPUT");

		String tooLong = "가".repeat(21);
		assertThat(send(json(post(path), "{\"names\":[\"" + tooLong + "\"]}"), admin).getStatus()).isEqualTo(400);
		assertThat(send(json(post(path), "{\"names\":[\"   \"]}"), admin).getStatus()).isEqualTo(400);
	}

	@Test
	void unknownClassroomIsNotFound() throws Exception {
		String unknown = UUID.randomUUID().toString();
		MockHttpServletResponse listed = send(get("/api/v1/admin/classrooms/" + unknown + "/students"), admin);
		assertThat(listed.getStatus()).isEqualTo(404);
		assertSafeErrorResponse(listed.getContentAsString(), "NOT_FOUND");

		MockHttpServletResponse registered = send(
				json(post("/api/v1/admin/classrooms/" + unknown + "/students"), "{\"names\":[\"가나\"]}"), admin);
		assertThat(registered.getStatus()).isEqualTo(404);
	}

	@Test
	void malformedClassroomIdIsInvalidInput() throws Exception {
		MockHttpServletResponse response = send(get("/api/v1/admin/classrooms/1/students"), admin);
		assertThat(response.getStatus()).isEqualTo(400);
		assertSafeErrorResponse(response.getContentAsString(), "INVALID_INPUT");
	}

	@ParameterizedTest
	@EnumSource(value = Role.class, names = { "TEACHER", "PARENT" })
	void otherRolesCannotUseAdminApis(Role role) throws Exception {
		String token = tokenFor(createAccount(role));
		String unknown = UUID.randomUUID().toString();

		for (MockHttpServletResponse response : List.of(
				send(get("/api/v1/admin/classrooms"), token),
				send(json(post("/api/v1/admin/classrooms"), "{\"grade\":1,\"classNumber\":1}"), token),
				send(get("/api/v1/admin/classrooms/" + unknown + "/students"), token),
				send(post("/api/v1/admin/classrooms/" + unknown + "/teacher-invite"), token),
				send(post("/api/v1/admin/students/" + unknown + "/parent-invite"), token),
				send(get("/api/v1/admin/invite-codes").param("classroomId", unknown), token))) {
			assertThat(response.getStatus()).isEqualTo(403);
			assertSafeErrorResponse(response.getContentAsString(), "FORBIDDEN");
		}
	}

	@Test
	void adminApisRequireAuthentication() throws Exception {
		MockHttpServletResponse response = send(get("/api/v1/admin/classrooms"), null);
		assertThat(response.getStatus()).isEqualTo(401);
		assertSafeErrorResponse(response.getContentAsString(), "UNAUTHORIZED");
	}

}
