package damso.inquiry;

import static damso.support.ErrorResponseAssertions.assertSafeErrorResponse;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;
import org.springframework.mock.web.MockHttpServletResponse;

import damso.account.domain.Role;
import damso.school.domain.Classroom;
import damso.school.domain.Student;
import damso.support.IntegrationTestSupport;

/**
 * 문의 제출(자녀 선택·본문 규칙·대상·권한).
 * F3 이후 일반 문의는 커밋 직후 정리되므로, 이 클래스는 정리 확정 전 상태(접수 단계)를 보도록 즉시 실행을 끈다.
 * 정리 후 상태는 TeacherRefinementApiTest 등이 확인한다.
 */
@TestPropertySource(properties = "damso.refinement.dispatch-on-commit=false")
class SubmitInquiryApiTest extends IntegrationTestSupport {

	private MockHttpServletResponse postRaw(String token, String body) throws Exception {
		return send(json(post("/api/v1/parent/inquiries"), body).header("Idempotency-Key", UUID.randomUUID().toString()),
				token);
	}

	@Test
	void submitsAGeneralInquiryAndStoresTheContentEncrypted() throws Exception {
		Scenario s = teacherWithClassroom();

		MockHttpServletResponse response = submitInquiry(s.parent().accessToken(), s.student().getId(), NORMAL_SAMPLE);

		assertThat(response.getStatus()).isEqualTo(201);
		assertThat((Boolean) read(response, "$.urgent")).isFalse();
		assertThat((String) read(response, "$.stage")).isEqualTo("RECEIVED");
		List<String> notices = read(response, "$.notices");
		assertThat(notices).containsExactly("RECEIVED");
		List<String> hotlines = read(response, "$.hotlines");
		assertThat(hotlines).isEmpty();
		assertThat((String) read(response, "$.inquiryId")).isNotBlank();
		assertThat((String) read(response, "$.submittedAt")).isNotBlank();

		String stored = jdbc.queryForObject("select content from inquiry", String.class);
		assertThat(stored).isNotBlank().isNotEqualTo(NORMAL_SAMPLE).doesNotContain("준비물");
		List<Map<String, Object>> history = jdbc.queryForList("select stage from inquiry_stage_history");
		assertThat(history).hasSize(1);
		assertThat(history.get(0).get("stage")).isEqualTo("RECEIVED");
	}

	@Test
	void rejectsBlankAndTooLongContentButCountsCodePoints() throws Exception {
		Scenario s = teacherWithClassroom();
		String token = s.parent().accessToken();
		UUID child = s.student().getId();

		for (String bad : List.of("", "   ", " \n\t ", "가".repeat(2001))) {
			MockHttpServletResponse response = submitInquiry(token, child, bad);
			assertThat(response.getStatus()).as("[%d chars]", bad.length()).isEqualTo(400);
			assertSafeErrorResponse(response.getContentAsString(), "INVALID_INPUT");
		}
		assertThat(jdbc.queryForObject("select count(*) from inquiry", Integer.class)).isZero();

		// 이모지는 UTF-16으로 4,000칸이지만 코드 포인트로는 2,000자라 접수된다
		String emojis = "😀".repeat(2000);
		assertThat(emojis.length()).isEqualTo(4000);
		assertThat(submitInquiry(token, child, emojis).getStatus()).isEqualTo(201);
		// 2,001 코드 포인트는 거부
		clock.advance(Duration.ofSeconds(61));
		assertThat(submitInquiry(token, child, "😀".repeat(2001)).getStatus()).isEqualTo(400);
	}

	@Test
	void rejectsUnknownFields() throws Exception {
		Scenario s = teacherWithClassroom();
		String token = s.parent().accessToken();
		String id = s.student().getId().toString();

		for (String extra : List.of("\"title\":\"제목\"", "\"phone\":\"010-0000-0000\"", "\"urgent\":true")) {
			MockHttpServletResponse response = postRaw(token,
					"{\"studentId\":\"" + id + "\",\"content\":\"" + NORMAL_SAMPLE + "\"," + extra + "}");
			assertThat(response.getStatus()).as(extra).isEqualTo(400);
		}
		assertThat(jdbc.queryForObject("select count(*) from inquiry", Integer.class)).isZero();
	}

	@Test
	void otherParentsChildAndUnknownStudentAreNotFound() throws Exception {
		Scenario s = teacherWithClassroom();
		Student others = newStudent(s.classroom(), "이둘");
		signUpWith(issueParentCode(others), "정부모");

		MockHttpServletResponse notMine = submitInquiry(s.parent().accessToken(), others.getId(), NORMAL_SAMPLE);
		MockHttpServletResponse unknown = submitInquiry(s.parent().accessToken(), UUID.randomUUID(), NORMAL_SAMPLE);

		assertThat(notMine.getStatus()).isEqualTo(404);
		assertSafeErrorResponse(notMine.getContentAsString(), "NOT_FOUND");
		assertThat(unknown.getStatus()).isEqualTo(404);
		// 존재 여부를 드러내지 않도록 두 응답이 같다
		assertThat(notMine.getContentAsString()).isEqualTo(unknown.getContentAsString());
		assertThat(jdbc.queryForObject("select count(*) from inquiry", Integer.class)).isZero();
	}

	@Test
	void classroomWithoutHomeroomTeacherIsRejectedWithoutStoring() throws Exception {
		Classroom classroom = newClassroom();
		Student student = newStudent(classroom, "김하나");
		SignedUp parent = signUpWith(issueParentCode(student), "박부모");

		MockHttpServletResponse response = submitInquiry(parent.accessToken(), student.getId(), NORMAL_SAMPLE);

		assertThat(response.getStatus()).isEqualTo(409);
		assertSafeErrorResponse(response.getContentAsString(), "NO_HOMEROOM_TEACHER");
		assertThat(jdbc.queryForObject("select count(*) from inquiry", Integer.class)).isZero();
		assertThat(jdbc.queryForObject("select count(*) from inquiry_stage_history", Integer.class)).isZero();
	}

	@Test
	void inquiryGoesToTheHomeroomTeacherOfTheChosenChild() throws Exception {
		Scenario first = teacherWithClassroom(1, 1);
		Classroom secondClassroom = newClassroom(1, 2);
		Student secondChild = newStudent(secondClassroom, "김둘");
		SignedUp secondTeacher = signUpWith(issueTeacherCode(secondClassroom), "강선생");
		MockHttpServletResponse linked = send(json(post("/api/v1/parent/children"),
				"{\"inviteCode\":\"" + issueParentCode(secondChild) + "\"}"), first.parent().accessToken());
		assertThat(linked.getStatus()).isEqualTo(201);

		MockHttpServletResponse response = submitInquiry(first.parent().accessToken(), secondChild.getId(),
				NORMAL_SAMPLE);

		assertThat(response.getStatus()).isEqualTo(201);
		Map<String, Object> row = jdbc.queryForMap("select teacher_id, classroom_id, student_id from inquiry");
		assertThat(row.get("teacher_id")).isEqualTo(secondTeacher.userId());
		assertThat(row.get("classroom_id")).isEqualTo(secondClassroom.getId());
		assertThat(row.get("student_id")).isEqualTo(secondChild.getId());
	}

	@Test
	void teacherAndClassroomAreFixedAtSubmissionEvenIfTheTeacherLeavesLater() throws Exception {
		Scenario s = teacherWithClassroom();
		assertThat(submitInquiry(s.parent().accessToken(), s.student().getId(), NORMAL_SAMPLE).getStatus())
				.isEqualTo(201);

		Classroom stored = classroomPort.findClassroomById(s.classroom().getId()).orElseThrow();
		stored.releaseTeacher();
		classroomPort.saveClassroom(stored);

		Map<String, Object> row = jdbc.queryForMap("select teacher_id, classroom_id from inquiry");
		assertThat(row.get("teacher_id")).isEqualTo(s.teacher().userId());
		assertThat(row.get("classroom_id")).isEqualTo(s.classroom().getId());
	}

	@Test
	void onlyParentsCanSubmit() throws Exception {
		Scenario s = teacherWithClassroom();
		UUID child = s.student().getId();

		MockHttpServletResponse byTeacher = submitInquiry(s.teacher().accessToken(), child, NORMAL_SAMPLE);
		MockHttpServletResponse byAdmin = submitInquiry(tokenFor(createAccount(Role.SUPER_ADMIN)), child, NORMAL_SAMPLE);
		MockHttpServletResponse anonymous = submitInquiry(null, child, NORMAL_SAMPLE);

		assertThat(byTeacher.getStatus()).isEqualTo(403);
		assertThat(byAdmin.getStatus()).isEqualTo(403);
		assertThat(anonymous.getStatus()).isEqualTo(401);
		assertThat(jdbc.queryForObject("select count(*) from inquiry", Integer.class)).isZero();
	}

	@Test
	void thereIsNoWayToEditOrDeleteAnInquiry() throws Exception {
		Scenario s = teacherWithClassroom();
		MockHttpServletResponse created = submitInquiry(s.parent().accessToken(), s.student().getId(), NORMAL_SAMPLE);
		String id = read(created, "$.inquiryId");
		String body = "{\"content\":\"바꾼 내용\"}";

		List<MockHttpServletResponse> attempts = List.of(
				send(json(put("/api/v1/parent/inquiries/" + id), body), s.parent().accessToken()),
				send(json(patch("/api/v1/parent/inquiries/" + id), body), s.parent().accessToken()),
				send(delete("/api/v1/parent/inquiries/" + id), s.parent().accessToken()),
				send(json(put("/api/v1/parent/inquiries"), body), s.parent().accessToken()),
				send(delete("/api/v1/parent/inquiries"), s.parent().accessToken()));

		for (MockHttpServletResponse response : attempts) {
			assertThat(response.getStatus()).isIn(404, 405);
		}
		assertThat(jdbc.queryForObject("select count(*) from inquiry", Integer.class)).isEqualTo(1);
		String stored = jdbc.queryForObject("select content from inquiry", String.class);
		assertThat(stored).isNotBlank().doesNotContain("바꾼 내용");
	}

}
