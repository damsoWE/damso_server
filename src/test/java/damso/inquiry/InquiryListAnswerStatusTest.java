package damso.inquiry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.TestPropertySource;

import damso.account.domain.Role;
import damso.account.domain.UserAccount;
import damso.school.domain.Classroom;
import damso.support.IntegrationTestSupport;

/**
 * F4 US6 시나리오 1~6 / FR-026~030, SC-012·013: 학부모·교사 문의 목록에서 답변한 문의를 구별한다.
 * 답변 여부는 스레드 전체(최초 문의 + 재문의) 기준이며 처리 단계(stage)와 무관하다. 목록에는 답변 본문이 없다.
 * 재문의가 커밋 직후 정리되지 않도록 즉시 실행을 끈다(ParentInquiryQueryApiTest와 같은 컨텍스트).
 */
@TestPropertySource(properties = "damso.refinement.dispatch-on-commit=false")
class InquiryListAnswerStatusTest extends IntegrationTestSupport {

	private static final String ANSWER = "내일 준비물은 색연필과 도화지입니다.";

	private void sendAnswer(String token, UUID inquiryId, String content) throws Exception {
		MockHttpServletResponse response = send(json(post("/api/v1/teacher/inquiries/" + inquiryId + "/answers"),
				"{\"content\":\"" + content + "\",\"reviewed\":false}"), token);
		assertThat(response.getStatus()).isEqualTo(201);
	}

	private void followUp(String token, UUID inquiryId) throws Exception {
		clock.advance(Duration.ofSeconds(61));
		assertThat(submitFollowUp(token, inquiryId, NORMAL_SAMPLE).getStatus()).isEqualTo(201);
	}

	private Map<String, Object> parentItem(String token, UUID inquiryId) throws Exception {
		List<Map<String, Object>> items = read(send(get("/api/v1/parent/inquiries"), token), "$");
		return itemOf(items, inquiryId);
	}

	private Map<String, Object> teacherItem(String token, UUID inquiryId) throws Exception {
		List<Map<String, Object>> items = read(send(get("/api/v1/teacher/inquiries"), token), "$.items");
		return itemOf(items, inquiryId);
	}

	private static Map<String, Object> itemOf(List<Map<String, Object>> items, UUID inquiryId) {
		return items.stream()
				.filter(item -> inquiryId.toString().equals(item.get("inquiryId")))
				.findFirst()
				.orElseThrow();
	}

	private Instant lastSentAt(UUID rootId) {
		return jdbc.queryForObject("select max(sent_at) from answer where thread_root_id = ?", Timestamp.class, rootId)
			.toInstant();
	}

	private static Instant lastAnsweredAt(Map<String, Object> item) {
		return Instant.parse((String) item.get("lastAnsweredAt"));
	}

	@Test
	void inquiryWithoutAnswerHasNoAnswerAndNullTimeOnBothLists() throws Exception {
		ConfirmedInquiry confirmed = urgentInquiryConfirmedByTeacher();
		Scenario s = confirmed.scenario();

		Map<String, Object> parent = parentItem(s.parent().accessToken(), confirmed.inquiryId());
		Map<String, Object> teacher = teacherItem(s.teacher().accessToken(), confirmed.inquiryId());

		for (Map<String, Object> item : List.of(parent, teacher)) {
			assertThat(item.get("hasAnswer")).isEqualTo(false);
			assertThat(item).containsKey("lastAnsweredAt");
			assertThat(item.get("lastAnsweredAt")).isNull();
		}
		assertThat(parent.keySet()).containsExactlyInAnyOrder("inquiryId", "studentId", "studentName", "urgent",
				"stage", "submittedAt", "hasAnswer", "lastAnsweredAt");
	}

	@Test
	void answeredThreadShowsAnswerAndLastSentTimeWithoutAnswerContent() throws Exception {
		ConfirmedInquiry confirmed = urgentInquiryConfirmedByTeacher();
		Scenario s = confirmed.scenario();

		sendAnswer(s.teacher().accessToken(), confirmed.inquiryId(), ANSWER);

		MockHttpServletResponse parentList = send(get("/api/v1/parent/inquiries"), s.parent().accessToken());
		MockHttpServletResponse teacherList = send(get("/api/v1/teacher/inquiries"), s.teacher().accessToken());
		Map<String, Object> parent = itemOf(read(parentList, "$"), confirmed.inquiryId());
		Map<String, Object> teacher = itemOf(read(teacherList, "$.items"), confirmed.inquiryId());
		for (Map<String, Object> item : List.of(parent, teacher)) {
			assertThat(item.get("hasAnswer")).isEqualTo(true);
			assertThat(lastAnsweredAt(item)).isEqualTo(lastSentAt(confirmed.inquiryId()));
		}
		assertThat(parentList.getContentAsString()).doesNotContain("색연필");
		assertThat(teacherList.getContentAsString()).doesNotContain("색연필");
	}

	@Test
	void followUpKeepsTheAnswerAndAnotherAnswerMovesTheTime() throws Exception {
		ConfirmedInquiry confirmed = urgentInquiryConfirmedByTeacher();
		Scenario s = confirmed.scenario();
		sendAnswer(s.teacher().accessToken(), confirmed.inquiryId(), ANSWER);
		Instant first = lastSentAt(confirmed.inquiryId());

		followUp(s.parent().accessToken(), confirmed.inquiryId());

		Map<String, Object> afterFollowUp = parentItem(s.parent().accessToken(), confirmed.inquiryId());
		assertThat(afterFollowUp.get("hasAnswer")).isEqualTo(true);
		assertThat(lastAnsweredAt(afterFollowUp)).isEqualTo(first);

		clock.advance(Duration.ofMinutes(5));
		sendAnswer(s.teacher().accessToken(), confirmed.inquiryId(), "추가로 확인해 보니 가위도 필요합니다.");

		Instant second = lastSentAt(confirmed.inquiryId());
		assertThat(second).isAfter(first);
		assertThat(lastAnsweredAt(parentItem(s.parent().accessToken(), confirmed.inquiryId()))).isEqualTo(second);
		assertThat(lastAnsweredAt(teacherItem(s.teacher().accessToken(), confirmed.inquiryId()))).isEqualTo(second);
	}

	@Test
	void answerSentAfterAFollowUpCountsForTheWholeThread() throws Exception {
		ConfirmedInquiry confirmed = urgentInquiryConfirmedByTeacher();
		Scenario s = confirmed.scenario();
		followUp(s.parent().accessToken(), confirmed.inquiryId());
		assertThat(parentItem(s.parent().accessToken(), confirmed.inquiryId()).get("hasAnswer")).isEqualTo(false);

		sendAnswer(s.teacher().accessToken(), confirmed.inquiryId(), ANSWER);

		assertThat(parentItem(s.parent().accessToken(), confirmed.inquiryId()).get("hasAnswer")).isEqualTo(true);
		assertThat(teacherItem(s.teacher().accessToken(), confirmed.inquiryId()).get("hasAnswer")).isEqualTo(true);
	}

	@Test
	void eachItemCarriesOnlyItsOwnThreadsAnswer() throws Exception {
		ConfirmedInquiry confirmed = urgentInquiryConfirmedByTeacher();
		Scenario s = confirmed.scenario();
		clock.advance(Duration.ofSeconds(61));
		UUID unanswered = UUID.fromString(
				read(submitInquiry(s.parent().accessToken(), s.student().getId(), NORMAL_SAMPLE), "$.inquiryId"));

		sendAnswer(s.teacher().accessToken(), confirmed.inquiryId(), ANSWER);

		assertThat(parentItem(s.parent().accessToken(), confirmed.inquiryId()).get("hasAnswer")).isEqualTo(true);
		Map<String, Object> parentOther = parentItem(s.parent().accessToken(), unanswered);
		Map<String, Object> teacherOther = teacherItem(s.teacher().accessToken(), unanswered);
		for (Map<String, Object> item : List.of(parentOther, teacherOther)) {
			assertThat(item.get("hasAnswer")).isEqualTo(false);
			assertThat(item.get("lastAnsweredAt")).isNull();
		}
	}

	@Test
	void newHomeroomTeacherSeesAnswersOfThePreviousTeacher() throws Exception {
		ConfirmedInquiry confirmed = urgentInquiryConfirmedByTeacher();
		Scenario s = confirmed.scenario();
		sendAnswer(s.teacher().accessToken(), confirmed.inquiryId(), ANSWER);

		UserAccount newTeacher = createAccount(Role.TEACHER);
		Classroom stored = classroomPort.findClassroomById(s.classroom().getId()).orElseThrow();
		stored.releaseTeacher();
		stored.assignTeacher(newTeacher.getId());
		classroomPort.saveClassroom(stored);

		Map<String, Object> item = teacherItem(tokenFor(newTeacher), confirmed.inquiryId());
		assertThat(item.get("hasAnswer")).isEqualTo(true);
		assertThat(lastAnsweredAt(item)).isEqualTo(lastSentAt(confirmed.inquiryId()));
	}

	@Test
	void answerStaysAfterTheAnsweringTeacherWithdraws() throws Exception {
		ConfirmedInquiry confirmed = urgentInquiryConfirmedByTeacher();
		Scenario s = confirmed.scenario();
		sendAnswer(s.teacher().accessToken(), confirmed.inquiryId(), ANSWER);
		Instant sent = lastSentAt(confirmed.inquiryId());

		MockHttpServletResponse withdrawn = send(
				json(post("/api/v1/me/withdrawal"), "{\"password\":\"" + TEST_PASSWORD + "\"}"),
				s.teacher().accessToken());
		assertThat(withdrawn.getStatus()).isEqualTo(204);

		Map<String, Object> item = parentItem(s.parent().accessToken(), confirmed.inquiryId());
		assertThat(item.get("hasAnswer")).isEqualTo(true);
		assertThat(lastAnsweredAt(item)).isEqualTo(sent);
	}

}
