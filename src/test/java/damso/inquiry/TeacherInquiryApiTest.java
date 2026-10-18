package damso.inquiry;

import static damso.support.ErrorResponseAssertions.assertSafeErrorResponse;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletResponse;

import damso.account.domain.Role;
import damso.notification.application.service.NotificationDispatcher;
import damso.notification.domain.NotificationKind;
import damso.school.domain.Classroom;
import damso.school.domain.Student;
import damso.support.IntegrationTestSupport;

/** F2 US3 시나리오 1~4 / FR-024~026, SC-005·010·013: 교사 목록(긴급 우선)·상세(본문 없음)·긴급 원문 열람. */
class TeacherInquiryApiTest extends IntegrationTestSupport {

	@Autowired
	private NotificationDispatcher dispatcher;

	private MockHttpServletResponse list(String token, String query) throws Exception {
		return send(get("/api/v1/teacher/inquiries" + query), token);
	}

	private UUID submit(Scenario s, String content) throws Exception {
		clock.advance(Duration.ofSeconds(61));
		MockHttpServletResponse response = submitInquiry(s.parent().accessToken(), s.student().getId(), content);
		assertThat(response.getStatus()).isEqualTo(201);
		return UUID.fromString(read(response, "$.inquiryId"));
	}

	@Test
	void listPutsUrgentFirstThenNewestAndHasNoContentOrParentInfo() throws Exception {
		Scenario s = teacherWithClassroom();
		UUID first = submit(s, NORMAL_SAMPLE + " 첫째");
		UUID second = submit(s, NORMAL_SAMPLE + " 둘째");
		UUID urgent = submit(s, URGENT_SAMPLE);
		UUID fourth = submit(s, NORMAL_SAMPLE + " 넷째");

		MockHttpServletResponse response = list(s.teacher().accessToken(), "");

		assertThat(response.getStatus()).isEqualTo(200);
		List<String> ids = read(response, "$.items[*].inquiryId");
		assertThat(ids).containsExactly(urgent.toString(), fourth.toString(), second.toString(), first.toString());
		assertThat((Integer) read(response, "$.page")).isZero();
		assertThat((Integer) read(response, "$.size")).isEqualTo(20);
		assertThat(((Number) read(response, "$.totalElements")).longValue()).isEqualTo(4);
		List<String> titles = read(response, "$.items[*].title");
		assertThat(titles).containsExactly("긴급 문의", "정리 중", "정리 중", "정리 중");
		assertThat((String) read(response, "$.items[0].studentName")).isEqualTo("김하나");
		assertThat((String) read(response, "$.items[0].stage")).isEqualTo("DELIVERED");
		assertThat((String) read(response, "$.items[1].stage")).isEqualTo("RECEIVED");

		String body = response.getContentAsString();
		assertThat(body).doesNotContain("content").doesNotContain("죽고").doesNotContain("준비물")
				.doesNotContain(s.parent().email()).doesNotContain("박부모").doesNotContain("email");
	}

	@Test
	void listIsPagedWithDefaultTwentyAndMaxFifty() throws Exception {
		Scenario s = teacherWithClassroom();
		for (int i = 0; i < 3; i++) {
			submit(s, NORMAL_SAMPLE + " " + i);
		}

		MockHttpServletResponse firstPage = list(s.teacher().accessToken(), "?page=0&size=2");
		MockHttpServletResponse secondPage = list(s.teacher().accessToken(), "?page=1&size=2");

		assertThat((List<?>) read(firstPage, "$.items")).hasSize(2);
		assertThat((List<?>) read(secondPage, "$.items")).hasSize(1);
		assertThat(((Number) read(secondPage, "$.totalElements")).longValue()).isEqualTo(3);
		assertThat(list(s.teacher().accessToken(), "?size=50").getStatus()).isEqualTo(200);
		for (String bad : List.of("?size=51", "?size=0", "?page=-1", "?size=abc", "?page=x")) {
			MockHttpServletResponse response = list(s.teacher().accessToken(), bad);
			assertThat(response.getStatus()).as(bad).isEqualTo(400);
			assertSafeErrorResponse(response.getContentAsString(), "INVALID_INPUT");
		}
	}

	@Test
	void teacherOfAnotherClassroomSeesNothingOfIt() throws Exception {
		Scenario s = teacherWithClassroom(1, 1);
		Scenario other = teacherWithClassroom(1, 2);
		UUID mine = submit(s, NORMAL_SAMPLE);

		MockHttpServletResponse otherList = list(other.teacher().accessToken(), "");
		assertThat((List<?>) read(otherList, "$.items")).isEmpty();
		assertThat(((Number) read(otherList, "$.totalElements")).longValue()).isZero();

		MockHttpServletResponse detail = send(get("/api/v1/teacher/inquiries/" + mine), other.teacher().accessToken());
		MockHttpServletResponse unknown = send(get("/api/v1/teacher/inquiries/" + UUID.randomUUID()),
				other.teacher().accessToken());
		assertThat(detail.getStatus()).isEqualTo(404);
		assertSafeErrorResponse(detail.getContentAsString(), "NOT_FOUND");
		// 존재 여부를 드러내지 않도록 없는 문의와 같은 응답이다
		assertThat(detail.getContentAsString()).isEqualTo(unknown.getContentAsString());
		MockHttpServletResponse content = send(get("/api/v1/teacher/inquiries/" + mine + "/content"),
				other.teacher().accessToken());
		assertThat(content.getStatus()).isEqualTo(404);
	}

	@Test
	void formerHomeroomTeacherLosesAccessAfterRelease() throws Exception {
		Scenario s = teacherWithClassroom();
		UUID id = submit(s, URGENT_SAMPLE);
		Classroom stored = classroomPort.findClassroomById(s.classroom().getId()).orElseThrow();
		stored.releaseTeacher();
		classroomPort.saveClassroom(stored);

		MockHttpServletResponse listed = list(s.teacher().accessToken(), "");
		assertThat((List<?>) read(listed, "$.items")).isEmpty();
		assertThat(send(get("/api/v1/teacher/inquiries/" + id), s.teacher().accessToken()).getStatus()).isEqualTo(404);
		assertThat(send(get("/api/v1/teacher/inquiries/" + id + "/content"), s.teacher().accessToken()).getStatus())
				.isEqualTo(404);
		assertThat(jdbc.queryForObject("select count(*) from audit_log", Integer.class)).isZero();
	}

	@Test
	void detailHasTheTimelineButNoContent() throws Exception {
		Scenario s = teacherWithClassroom();
		UUID urgent = submit(s, URGENT_SAMPLE);
		UUID general = submit(s, NORMAL_SAMPLE);

		MockHttpServletResponse urgentDetail = send(get("/api/v1/teacher/inquiries/" + urgent),
				s.teacher().accessToken());
		MockHttpServletResponse generalDetail = send(get("/api/v1/teacher/inquiries/" + general),
				s.teacher().accessToken());

		assertThat(urgentDetail.getStatus()).isEqualTo(200);
		assertThat((String) read(urgentDetail, "$.title")).isEqualTo("긴급 문의");
		assertThat((Boolean) read(urgentDetail, "$.urgent")).isTrue();
		List<String> urgentStages = read(urgentDetail, "$.timeline[*].stage");
		assertThat(urgentStages).containsExactly("RECEIVED", "DELIVERED");
		assertThat((String) read(generalDetail, "$.title")).isEqualTo("정리 중");
		List<String> generalStages = read(generalDetail, "$.timeline[*].stage");
		assertThat(generalStages).containsExactly("RECEIVED");
		assertThat(urgentDetail.getContentAsString()).doesNotContain("content").doesNotContain("죽고");
		assertThat(generalDetail.getContentAsString()).doesNotContain("content").doesNotContain("준비물");
	}

	@Test
	void urgentContentIsReturnedAndEveryViewLeavesAnAuditRecord() throws Exception {
		Scenario s = teacherWithClassroom();
		UUID id = submit(s, URGENT_SAMPLE);

		MockHttpServletResponse first = send(get("/api/v1/teacher/inquiries/" + id + "/content"),
				s.teacher().accessToken());

		assertThat(first.getStatus()).isEqualTo(200);
		assertThat((String) read(first, "$.content")).isEqualTo(URGENT_SAMPLE);
		assertThat((String) read(first, "$.inquiryId")).isEqualTo(id.toString());
		assertThat(jdbc.queryForObject("select stage from inquiry where id = ?", String.class, id))
				.isEqualTo("TEACHER_CONFIRMED");
		assertThat(jdbc.queryForObject("select count(*) from inquiry_stage_history where inquiry_id = ?",
				Integer.class, id)).isEqualTo(3);
		List<Map<String, Object>> audit = jdbc.queryForList("select actor_id, action, target_type, target_id from audit_log");
		assertThat(audit).hasSize(1);
		assertThat(audit.get(0)).containsEntry("actor_id", s.teacher().userId())
				.containsEntry("action", "INQUIRY_CONTENT_VIEWED").containsEntry("target_type", "INQUIRY")
				.containsEntry("target_id", id);

		// 두 번째 열람은 기록만 늘고 단계 이력은 그대로다
		MockHttpServletResponse second = send(get("/api/v1/teacher/inquiries/" + id + "/content"),
				s.teacher().accessToken());
		assertThat(second.getStatus()).isEqualTo(200);
		assertThat(jdbc.queryForObject("select count(*) from audit_log", Integer.class)).isEqualTo(2);
		assertThat(jdbc.queryForObject("select count(*) from inquiry_stage_history where inquiry_id = ?",
				Integer.class, id)).isEqualTo(3);
	}

	@Test
	void generalInquiryContentIsNotAvailableYet() throws Exception {
		Scenario s = teacherWithClassroom();
		UUID id = submit(s, NORMAL_SAMPLE);

		MockHttpServletResponse response = send(get("/api/v1/teacher/inquiries/" + id + "/content"),
				s.teacher().accessToken());

		assertThat(response.getStatus()).isEqualTo(409);
		assertSafeErrorResponse(response.getContentAsString(), "INQUIRY_CONTENT_UNAVAILABLE");
		assertThat(response.getContentAsString()).doesNotContain("준비물");
		assertThat(jdbc.queryForObject("select count(*) from audit_log", Integer.class)).isZero();
		assertThat(jdbc.queryForObject("select stage from inquiry where id = ?", String.class, id))
				.isEqualTo("RECEIVED");
	}

	@Test
	void onlyTeachersCanUseTheTeacherApi() throws Exception {
		Scenario s = teacherWithClassroom();
		UUID id = submit(s, URGENT_SAMPLE);
		String admin = tokenFor(createAccount(Role.SUPER_ADMIN));

		for (String path : List.of("/api/v1/teacher/inquiries", "/api/v1/teacher/inquiries/" + id,
				"/api/v1/teacher/inquiries/" + id + "/content")) {
			assertThat(send(get(path), s.parent().accessToken()).getStatus()).as(path).isEqualTo(403);
			assertThat(send(get(path), admin).getStatus()).as(path).isEqualTo(403);
			assertThat(send(get(path), null).getStatus()).as(path).isEqualTo(401);
		}
		assertThat(jdbc.queryForObject("select count(*) from audit_log", Integer.class)).isZero();
	}

	@Test
	void viewingStopsTheReminders() throws Exception {
		Scenario s = teacherWithClassroom();
		registerDevice(s.teacher().userId(), sessionIdOf(s.teacher().accessToken()), "teacher-token");
		UUID id = submit(s, URGENT_SAMPLE);
		assertThat(pushes.sentOfKind(NotificationKind.URGENT)).hasSize(1);

		send(get("/api/v1/teacher/inquiries/" + id + "/content"), s.teacher().accessToken());
		clock.advance(Duration.ofHours(1).plusSeconds(1));
		dispatcher.dispatchDue();

		assertThat(pushes.sentOfKind(NotificationKind.URGENT_REMINDER)).isEmpty();
		assertThat(jdbc.queryForObject(
				"select count(*) from notification_request where kind = 'URGENT_REMINDER' and status = 'CANCELLED'",
				Integer.class)).isEqualTo(1);
	}

	@Test
	void studentNamesComeFromTheInquiryStudent() throws Exception {
		Scenario s = teacherWithClassroom();
		Student sibling = newStudent(s.classroom(), "김둘");
		MockHttpServletResponse linked = send(
				org.springframework.test.web.servlet.request.MockMvcRequestBuilders
						.post("/api/v1/parent/children")
						.contentType(org.springframework.http.MediaType.APPLICATION_JSON)
						.content("{\"inviteCode\":\"" + issueParentCode(sibling) + "\"}"),
				s.parent().accessToken());
		assertThat(linked.getStatus()).isEqualTo(201);
		clock.advance(Duration.ofSeconds(61));
		submitInquiry(s.parent().accessToken(), sibling.getId(), NORMAL_SAMPLE);

		MockHttpServletResponse response = list(s.teacher().accessToken(), "");

		assertThat((String) read(response, "$.items[0].studentName")).isEqualTo("김둘");
		assertThat((String) read(response, "$.items[0].studentId")).isEqualTo(sibling.getId().toString());
	}

}
