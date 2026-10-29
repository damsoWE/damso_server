package damso.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.TestPropertySource;

import damso.school.domain.Classroom;
import damso.school.domain.Student;
import damso.support.IntegrationTestSupport;
import damso.support.OperatorQueries;

/**
 * F5 T012 / US1, FR-004·008·009·011, SC-003·004: 운영자 조회문(contracts/operator-queries.md)을 문서에서 그대로 읽어
 * 실행해, 스레드 하나의 전 과정이 순서대로 나오고 본문·이름·이메일이 없는지 확인한다.
 * 시각은 MutableClock으로 고정한다. 한 요청 안의 기록은 같은 시각이 되고(research R5의 seq가 순서를 정한다),
 * 서로 다른 요청 사이에는 1초씩 진행한다.
 */
@TestPropertySource(properties = "damso.inquiry.close-enabled=true")
class OperatorQueriesTest extends IntegrationTestSupport {

	private static final String ANSWER_BODY = "조회문검사답변본문ZXQ" + "9101";

	private static final String FOLLOW_UP_BODY = "조회문검사재문의본문ZXQ" + "9102 숙제는 어떻게 하나요?";

	private void tick() {
		clock.advance(Duration.ofSeconds(1));
	}

	private UUID submitGeneral(Scenario s) throws Exception {
		MockHttpServletResponse response = submitInquiry(s.parent().accessToken(), s.student().getId(),
				generalInquiryNaming(s));
		assertThat(response.getStatus()).isEqualTo(201);
		return UUID.fromString(read(response, "$.inquiryId"));
	}

	private MockHttpServletResponse view(Scenario s, UUID id, String what) throws Exception {
		MockHttpServletResponse response = send(get("/api/v1/teacher/inquiries/" + id + "/" + what),
				s.teacher().accessToken());
		assertThat(response.getStatus()).as(what).isEqualTo(200);
		return response;
	}

	private void review(Scenario s, UUID id) throws Exception {
		assertThat(send(json(post("/api/v1/teacher/inquiries/" + id + "/answer-reviews"),
				"{\"content\":\"" + ANSWER_BODY + "\"}"), s.teacher().accessToken()).getStatus()).isEqualTo(200);
	}

	private void answer(Scenario s, UUID id) throws Exception {
		assertThat(send(json(post("/api/v1/teacher/inquiries/" + id + "/answers"),
				"{\"content\":\"" + ANSWER_BODY + "\",\"reviewed\":true,\"lastSeenFollowUpId\":" + lastSeenFollowUpJson(id) + "}"), s.teacher().accessToken()).getStatus())
				.isEqualTo(201);
	}

	@Test
	void theWholeGeneralThreadComesOutInOrderWithoutContent() throws Exception {
		Scenario s = teacherWithClassroom();
		UUID root = submitGeneral(s);
		tick();
		view(s, root, "refinement");
		tick();
		view(s, root, "content");
		tick();
		review(s, root);
		tick();
		answer(s, root);
		clock.advance(Duration.ofSeconds(61));
		MockHttpServletResponse followUp = submitFollowUp(s.parent().accessToken(), root, FOLLOW_UP_BODY);
		assertThat(followUp.getStatus()).isEqualTo(201);
		UUID followUpId = UUID.fromString(read(followUp, "$.inquiryId"));
		tick();
		answer(s, root);

		List<Map<String, Object>> rows = OperatorQueries.thread(jdbc, root);

		assertThat(rows.stream().map(row -> (String) row.get("event")).toList()).containsExactly(
				"INQUIRY_SUBMITTED", "STAGE_RECEIVED", "REFINE_ATTEMPT", "STAGE_REFINING",
				"INQUIRY_REFINEMENT_VIEW_ATTEMPTED", "STAGE_TEACHER_CONFIRMED", "INQUIRY_REFINEMENT_VIEWED",
				"INQUIRY_CONTENT_VIEW_ATTEMPTED", "INQUIRY_CONTENT_VIEWED",
				"ANSWER_REVIEW_REQUESTED",
				"ANSWER_SENT",
				"FOLLOW_UP_SUBMITTED", "STAGE_RECEIVED", "REFINE_ATTEMPT", "STAGE_REFINING",
				"ANSWER_SENT");
		assertThat(rows.get(12).get("message_id")).isEqualTo(followUpId);
		assertThat(rows.get(2).get("detail")).isEqualTo("AUTO:SUCCESS");
		assertThat(rows.get(4).get("actor_id")).isEqualTo(s.teacher().userId());
		assertThat(rows.get(1).get("actor_kind")).isEqualTo("SYSTEM");
		assertThat(rows.get(0).get("actor_id")).isEqualTo(s.parent().userId());
		// 결과 컬럼은 식별자·종류·시각뿐이고 어떤 값에도 본문·이름·이메일이 없다(SC-003)
		assertThat(rows.get(0).keySet()).containsExactly("occurred_at", "seq", "actor_kind", "actor_id", "event",
				"message_id", "detail");
		String everything = rows.toString() + OperatorQueries.account(jdbc, s.teacher().userId())
				+ OperatorQueries.account(jdbc, s.parent().userId());
		assertThat(everything).doesNotContain(STUDENT_NAME).doesNotContain(TEACHER_NAME).doesNotContain(PARENT_NAME)
				.doesNotContain("숙제").doesNotContain(ANSWER_BODY).doesNotContain("ZXQ").doesNotContain("@");
		// Q2: 교사가 한 행위만 나온다
		assertThat(OperatorQueries.account(jdbc, s.teacher().userId()).stream().map(row -> (String) row.get("event")))
				.containsExactly("INQUIRY_REFINEMENT_VIEW_ATTEMPTED", "INQUIRY_REFINEMENT_VIEWED",
						"INQUIRY_CONTENT_VIEW_ATTEMPTED", "INQUIRY_CONTENT_VIEWED", "ANSWER_REVIEW_REQUESTED",
						"ANSWER_SENT", "ANSWER_SENT");
	}

	@Test
	void urgentDeliveryAndTheFirstOriginalViewKeepTheirOrderAtTheSameInstant() throws Exception {
		Scenario s = teacherWithClassroom();
		MockHttpServletResponse created = submitInquiry(s.parent().accessToken(), s.student().getId(), URGENT_SAMPLE);
		UUID root = UUID.fromString(read(created, "$.inquiryId"));
		tick();
		view(s, root, "content");

		List<String> events = OperatorQueries.threadEvents(jdbc, root);

		assertThat(events).containsSubsequence("INQUIRY_SUBMITTED", "STAGE_RECEIVED", "STAGE_DELIVERED",
				"INQUIRY_CONTENT_VIEW_ATTEMPTED", "STAGE_TEACHER_CONFIRMED", "INQUIRY_CONTENT_VIEWED");
		assertThat(events.subList(0, 3)).containsExactly("INQUIRY_SUBMITTED", "STAGE_RECEIVED", "STAGE_DELIVERED");
		assertThat(events.subList(events.size() - 3, events.size())).containsExactly(
				"INQUIRY_CONTENT_VIEW_ATTEMPTED", "STAGE_TEACHER_CONFIRMED", "INQUIRY_CONTENT_VIEWED");
	}

	@Test
	void aFailedRefinementAndTheTeacherRetryAreAllThere() throws Exception {
		// spec US1-3: 자동 정리 실패 → 교사 재시도 → 재시도 결과
		Scenario s = teacherWithClassroom();
		fakeAi.noResponseNext();
		fakeAi.noResponseNext();
		UUID root = submitGeneral(s);
		tick();
		assertThat(send(post("/api/v1/teacher/inquiries/" + root + "/refinement/retry"), s.teacher().accessToken())
				.getStatus()).isEqualTo(202);

		List<Map<String, Object>> rows = OperatorQueries.thread(jdbc, root);

		assertThat(rows.stream().map(row -> row.get("event") + (row.get("detail") == null ? "" : "/" + row.get("detail"))))
				.containsExactly("INQUIRY_SUBMITTED", "STAGE_RECEIVED", "REFINE_ATTEMPT/AUTO:NO_RESPONSE",
						"REFINE_ATTEMPT/AUTO_RETRY:NO_RESPONSE", "REFINEMENT_RETRY_REQUESTED",
						"REFINE_ATTEMPT/TEACHER:SUCCESS", "STAGE_REFINING");
		assertThat(rows.get(4).get("actor_id")).isEqualTo(s.teacher().userId());
		assertThat(OperatorQueries.account(jdbc, s.teacher().userId()).stream().map(row -> (String) row.get("event")))
				.containsExactly("REFINEMENT_RETRY_REQUESTED");
	}

	@Test
	void pastRecordsStayAfterTheHomeroomChangesAndTheStudentIsUnlinked() throws Exception {
		// FR-008, Edge Cases: 담임 교체·연결 해제 뒤에도 이전 담임의 기록은 그대로이고 새 담임은 행위자로 나오지 않는다
		Scenario s = teacherWithClassroom();
		MockHttpServletResponse created = submitInquiry(s.parent().accessToken(), s.student().getId(), URGENT_SAMPLE);
		UUID root = UUID.fromString(read(created, "$.inquiryId"));
		tick();
		view(s, root, "content");
		List<Map<String, Object>> before = OperatorQueries.thread(jdbc, root);

		Classroom classroom = classroomPort.findClassroomById(s.classroom().getId()).orElseThrow();
		classroom.releaseTeacher();
		classroomPort.saveClassroom(classroom);
		SignedUp newTeacher = signUpWith(issueTeacherCode(classroom), "새담임");
		Student student = studentPort.findStudentById(s.student().getId()).orElseThrow();
		student.releaseGuardian();
		studentPort.saveStudent(student);

		List<Map<String, Object>> after = OperatorQueries.thread(jdbc, root);
		assertThat(after).isEqualTo(before);
		assertThat(after).anyMatch(row -> s.teacher().userId().equals(row.get("actor_id")))
				.noneMatch(row -> newTeacher.userId().equals(row.get("actor_id")));
		assertThat(OperatorQueries.account(jdbc, s.teacher().userId())).hasSize(2);
		assertThat(OperatorQueries.account(jdbc, newTeacher.userId())).isEmpty();
	}

	@Test
	void aClosedThreadEndsWithTheAnsweredStageThenTheCloseRecord() throws Exception {
		// T033 / US1-5, FR-009: 종료 API는 같은 시각에 단계 이력(ANSWERED) 뒤 감사 기록(INQUIRY_CLOSED)을 남긴다
		Scenario s = teacherWithClassroom();
		MockHttpServletResponse created = submitInquiry(s.parent().accessToken(), s.student().getId(), URGENT_SAMPLE);
		UUID root = UUID.fromString(read(created, "$.inquiryId"));
		tick();
		view(s, root, "content");
		tick();
		answer(s, root);
		tick();
		assertThat(send(post("/api/v1/teacher/inquiries/" + root + "/close"), s.teacher().accessToken()).getStatus())
				.isEqualTo(200);

		List<Map<String, Object>> rows = OperatorQueries.thread(jdbc, root);

		List<String> events = rows.stream().map(row -> (String) row.get("event")).toList();
		assertThat(events.subList(events.size() - 3, events.size())).containsExactly("ANSWER_SENT", "STAGE_ANSWERED",
				"INQUIRY_CLOSED");
		Map<String, Object> close = rows.get(rows.size() - 1);
		assertThat(close).containsEntry("actor_kind", "ACCOUNT").containsEntry("actor_id", s.teacher().userId())
				.containsEntry("message_id", root);
		assertThat(rows.get(rows.size() - 2)).containsEntry("actor_kind", "SYSTEM");
	}

	@Test
	void aWithdrawnTeacherKeepsTheirRecordsUnderTheSameIdentifier() throws Exception {
		// T034 / US1-8, FR-008: 교사가 탈퇴해도 그 교사의 열람·답변 기록은 같은 계정 UUID로 남는다
		Scenario s = teacherWithClassroom();
		MockHttpServletResponse created = submitInquiry(s.parent().accessToken(), s.student().getId(), URGENT_SAMPLE);
		UUID root = UUID.fromString(read(created, "$.inquiryId"));
		tick();
		view(s, root, "content");
		tick();
		answer(s, root);
		List<Map<String, Object>> threadBefore = OperatorQueries.thread(jdbc, root);
		List<Map<String, Object>> accountBefore = OperatorQueries.account(jdbc, s.teacher().userId());

		MockHttpServletResponse withdrawn = send(
				json(post("/api/v1/me/withdrawal"), "{\"password\":\"" + TEST_PASSWORD + "\"}"),
				s.teacher().accessToken());
		assertThat(withdrawn.getStatus()).isIn(200, 204);

		assertThat(OperatorQueries.thread(jdbc, root)).isEqualTo(threadBefore);
		List<Map<String, Object>> accountAfter = OperatorQueries.account(jdbc, s.teacher().userId());
		assertThat(accountAfter).isEqualTo(accountBefore);
		assertThat(accountAfter.stream().map(row -> (String) row.get("event"))).containsExactly(
				"INQUIRY_CONTENT_VIEW_ATTEMPTED", "INQUIRY_CONTENT_VIEWED", "ANSWER_SENT");
	}

	@Test
	void aWithdrawnParentStillHasTheSubmissionUnderTheSameIdentifier() throws Exception {
		Scenario s = teacherWithClassroom();
		UUID root = submitGeneral(s);

		MockHttpServletResponse withdrawn = send(
				json(post("/api/v1/me/withdrawal"), "{\"password\":\"" + TEST_PASSWORD + "\"}"),
				s.parent().accessToken());
		assertThat(withdrawn.getStatus()).isIn(200, 204);

		List<Map<String, Object>> rows = OperatorQueries.account(jdbc, s.parent().userId());
		assertThat(rows).hasSize(1);
		assertThat(rows.get(0)).containsEntry("event", "INQUIRY_SUBMITTED").containsEntry("message_id", root);
	}

}
