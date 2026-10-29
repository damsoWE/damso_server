package damso.inquiry;

import static damso.support.ErrorResponseAssertions.assertSafeErrorResponse;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.TestPropertySource;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import damso.account.domain.Role;
import damso.school.domain.Classroom;
import damso.support.IntegrationTestSupport;

/**
 * 슈퍼 관리자는 학급 × 처리 상태(waiting·confirmed·answered)
 * 건수만 본다. 긴급 여부·학생 단위 정보·본문·제목·연락처는 없고, 슈퍼 관리자가 아닌 계정은 403이다.
 */
@TestPropertySource(properties = "damso.inquiry.close-enabled=true")
class AdminInquiryStatusApiTest extends IntegrationTestSupport {

	private static final ObjectMapper MAPPER = new ObjectMapper();

	private static final String PATH = "/api/v1/admin/inquiry-status";

	private static final String REFINED_TITLE = "현황검사제목ZXQ" + "9301";

	private static final String ANSWER_BODY = "현황검사답변ZXQ" + "9302";

	private UUID submitGeneral(Scenario s) throws Exception {
		clock.advance(Duration.ofSeconds(61));
		MockHttpServletResponse response = submitInquiry(s.parent().accessToken(), s.student().getId(),
				generalInquiryNaming(s));
		assertThat(response.getStatus()).isEqualTo(201);
		return UUID.fromString(read(response, "$.inquiryId"));
	}

	private UUID submitUrgent(Scenario s) throws Exception {
		MockHttpServletResponse response = submitInquiry(s.parent().accessToken(), s.student().getId(), URGENT_SAMPLE);
		assertThat(response.getStatus()).isEqualTo(201);
		return UUID.fromString(read(response, "$.inquiryId"));
	}

	private void teacherGet(Scenario s, UUID id, String what) throws Exception {
		assertThat(send(get("/api/v1/teacher/inquiries/" + id + "/" + what), s.teacher().accessToken()).getStatus())
				.isEqualTo(200);
	}

	private void answer(Scenario s, UUID id) throws Exception {
		assertThat(send(json(post("/api/v1/teacher/inquiries/" + id + "/answers"),
				"{\"content\":\"" + ANSWER_BODY + "\",\"reviewed\":false,\"lastSeenFollowUpId\":" + lastSeenFollowUpJson(id) + "}"), s.teacher().accessToken()).getStatus())
				.isEqualTo(201);
	}

	private String stage(UUID id) {
		return jdbc.queryForObject("select stage from inquiry where id = ?", String.class, id);
	}

	private MockHttpServletResponse status(String token) throws Exception {
		return send(get(PATH), token);
	}

	private static Map<String, Object> counts(UUID classroomId, int waiting, int confirmed, int answered) {
		return Map.of("classroomId", classroomId.toString(), "waiting", waiting, "confirmed", confirmed, "answered",
				answered);
	}

	@Test
	void countsEachThreadOnceInExactlyOneOfThreeBuckets() throws Exception {
		Scenario a = teacherWithClassroom(1, 1);
		Classroom empty = newClassroom(1, 2);
		Scenario c = teacherWithClassroom(2, 1);

		// waiting: 정리 완료(REFINING), 긴급 전달(DELIVERED), 원문 확인 필요(RECEIVED)
		UUID refining = submitGeneral(a);
		UUID delivered = submitUrgent(a);
		fakeAi.noResponseNext();
		fakeAi.noResponseNext();
		UUID needsOriginal = submitGeneral(a);
		// confirmed: 번역본 첫 열람으로 TEACHER_CONFIRMED, 답변 없음
		UUID confirmed = submitGeneral(a);
		teacherGet(a, confirmed, "refinement");
		// answered: 확인 뒤 답변 / 교사 확인 전에 답변(먼저 판정) / 종료 경로로 ANSWERED
		UUID answeredAfterConfirm = submitUrgent(a);
		teacherGet(a, answeredAfterConfirm, "content");
		answer(a, answeredAfterConfirm);
		UUID answeredBeforeConfirm = submitGeneral(a);
		answer(a, answeredBeforeConfirm);
		UUID closed = submitUrgent(a);
		teacherGet(a, closed, "content");
		assertThat(send(post("/api/v1/teacher/inquiries/" + closed + "/close"), a.teacher().accessToken())
				.getStatus()).isEqualTo(200);
		// 재문의는 스레드 단위로 세므로 따로 세지 않는다
		clock.advance(Duration.ofSeconds(61));
		assertThat(submitFollowUp(a.parent().accessToken(), confirmed, URGENT_SAMPLE).getStatus()).isEqualTo(201);
		// 다른 학급의 긴급 문의
		submitUrgent(c);

		assertThat(List.of(stage(refining), stage(delivered), stage(needsOriginal), stage(confirmed),
				stage(answeredBeforeConfirm), stage(closed)))
				.containsExactly("REFINING", "DELIVERED", "RECEIVED", "TEACHER_CONFIRMED", "REFINING", "ANSWERED");

		MockHttpServletResponse response = status(tokenFor(createAccount(Role.SUPER_ADMIN)));

		assertThat(response.getStatus()).isEqualTo(200);
		// 문의가 없는 학급도 0으로 나오고, 순서는 학년·반 순이다
		List<Map<String, Object>> items = read(response, "$.items");
		assertThat(items).containsExactly(counts(a.classroom().getId(), 3, 1, 3), counts(empty.getId(), 0, 0, 0),
				counts(c.classroom().getId(), 1, 0, 0));
	}

	@Test
	void theResponseCarriesOnlyClassroomIdsAndThreeCounts() throws Exception {
		Scenario s = teacherWithClassroom();
		fakeAi.replyNext(damso.support.FakeExternalAi.refinementJson(REFINED_TITLE, "사실관계", "요청사항", "LOW",
				List.of()));
		UUID general = submitGeneral(s);
		UUID urgent = submitUrgent(s);
		teacherGet(s, urgent, "content");
		answer(s, urgent);

		MockHttpServletResponse response = status(tokenFor(createAccount(Role.SUPER_ADMIN)));

		JsonNode body = MAPPER.readTree(response.getContentAsString());
		assertThat(Set.copyOf(body.propertyNames())).containsExactly("items");
		for (JsonNode item : body.get("items")) {
			assertThat(Set.copyOf(item.propertyNames())).containsExactlyInAnyOrder("classroomId", "waiting",
					"confirmed", "answered");
		}
		// 본문·제목·답변·학생·학부모·교사 정보, 연락처, 긴급 여부, 문의 식별자가 어디에도 없다
		String raw = response.getContentAsString();
		assertThat(raw).doesNotContain(general.toString()).doesNotContain(urgent.toString())
				.doesNotContain(s.student().getId().toString()).doesNotContain(STUDENT_NAME)
				.doesNotContain(TEACHER_NAME).doesNotContain(PARENT_NAME).doesNotContain(REFINED_TITLE)
				.doesNotContain(ANSWER_BODY).doesNotContain(URGENT_SAMPLE).doesNotContain("010-")
				.doesNotContain("@");
		for (String field : List.of("content", "title", "facts", "requests", "answer", "student", "parent",
				"teacher", "phone", "contact", "email", "urgent", "stage", "inquiryId")) {
			assertThat(raw).as(field).doesNotContain("\"" + field + "\":");
		}
	}

	@Test
	void onlyTheSuperAdminCanSeeTheStatus() throws Exception {
		Scenario s = teacherWithClassroom();
		submitUrgent(s);

		for (String token : List.of(s.teacher().accessToken(), s.parent().accessToken())) {
			MockHttpServletResponse response = status(token);
			assertThat(response.getStatus()).isEqualTo(403);
			assertSafeErrorResponse(response.getContentAsString(), "FORBIDDEN");
			assertThat(response.getContentAsString()).doesNotContain(s.classroom().getId().toString());
		}
		MockHttpServletResponse anonymous = status(null);
		assertThat(anonymous.getStatus()).isEqualTo(401);
		assertSafeErrorResponse(anonymous.getContentAsString(), "UNAUTHORIZED");
	}

	@Test
	void viewingTheStatusLeavesNoAuditRecord() throws Exception {
		Scenario s = teacherWithClassroom();
		UUID urgent = submitUrgent(s);
		teacherGet(s, urgent, "content");
		int before = jdbc.queryForObject("select count(*) from audit_log", Integer.class);

		assertThat(status(tokenFor(createAccount(Role.SUPER_ADMIN))).getStatus()).isEqualTo(200);

		assertThat(jdbc.queryForObject("select count(*) from audit_log", Integer.class)).isEqualTo(before);
	}

}
