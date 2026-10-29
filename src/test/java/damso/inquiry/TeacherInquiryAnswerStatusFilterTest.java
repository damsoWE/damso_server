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

import damso.support.IntegrationTestSupport;

/**
 * 이슈 #28: 교사 문의 목록의 재문의 시각(lastFollowUpAt)·답변 필요 여부(needsAnswer)와 답변 여부 필터(answerStatus).
 * 답변 대기는 "답변이 없거나 마지막 답변 뒤에 재문의가 있는 스레드"이고, totalElements도 거른 결과 기준이다.
 * 재문의가 커밋 직후 정리되지 않도록 즉시 실행을 끈다(InquiryListAnswerStatusTest와 같은 컨텍스트).
 */
@TestPropertySource(properties = "damso.refinement.dispatch-on-commit=false")
class TeacherInquiryAnswerStatusFilterTest extends IntegrationTestSupport {

	private static final String ANSWER = "내일 준비물은 색연필과 도화지입니다.";

	private void sendAnswer(String token, UUID inquiryId) throws Exception {
		MockHttpServletResponse response = send(json(post("/api/v1/teacher/inquiries/" + inquiryId + "/answers"),
				"{\"content\":\"" + ANSWER + "\",\"reviewed\":false,\"lastSeenFollowUpId\":" + lastSeenFollowUpJson(inquiryId) + "}"), token);
		assertThat(response.getStatus()).isEqualTo(201);
	}

	private void followUp(String token, UUID inquiryId) throws Exception {
		clock.advance(Duration.ofSeconds(61));
		assertThat(submitFollowUp(token, inquiryId, NORMAL_SAMPLE).getStatus()).isEqualTo(201);
	}

	private MockHttpServletResponse list(String token, String answerStatus, int page, int size) throws Exception {
		var request = get("/api/v1/teacher/inquiries").param("page", String.valueOf(page)).param("size",
				String.valueOf(size));
		if (answerStatus != null) {
			request = request.param("answerStatus", answerStatus);
		}
		return send(request, token);
	}

	private List<Map<String, Object>> items(String token, String answerStatus) throws Exception {
		return read(list(token, answerStatus, 0, 50), "$.items");
	}

	private Map<String, Object> item(String token, UUID inquiryId) throws Exception {
		return items(token, null).stream()
				.filter(item -> inquiryId.toString().equals(item.get("inquiryId")))
				.findFirst()
				.orElseThrow();
	}

	private boolean listed(String token, String answerStatus, UUID inquiryId) throws Exception {
		return items(token, answerStatus).stream().anyMatch(item -> inquiryId.toString().equals(item.get("inquiryId")));
	}

	private Instant lastFollowUpSubmittedAt(UUID rootId) {
		return jdbc.queryForObject("select max(submitted_at) from inquiry where thread_root_id = ?", Timestamp.class, rootId)
			.toInstant();
	}

	@Test
	void threadWithoutAnswerIsWaitingWithNullFollowUpTime() throws Exception {
		ConfirmedInquiry confirmed = urgentInquiryConfirmedByTeacher();
		String teacher = confirmed.scenario().teacher().accessToken();

		Map<String, Object> item = item(teacher, confirmed.inquiryId());
		assertThat(item).containsKey("lastFollowUpAt");
		assertThat(item.get("lastFollowUpAt")).isNull();
		assertThat(item.get("needsAnswer")).isEqualTo(true);
		assertThat(listed(teacher, "WAITING", confirmed.inquiryId())).isTrue();
		assertThat(listed(teacher, "ANSWERED", confirmed.inquiryId())).isFalse();
	}

	@Test
	void answeredThreadIsAnsweredAndNotWaiting() throws Exception {
		ConfirmedInquiry confirmed = urgentInquiryConfirmedByTeacher();
		String teacher = confirmed.scenario().teacher().accessToken();

		sendAnswer(teacher, confirmed.inquiryId());

		Map<String, Object> item = item(teacher, confirmed.inquiryId());
		assertThat(item.get("lastFollowUpAt")).isNull();
		assertThat(item.get("needsAnswer")).isEqualTo(false);
		assertThat(listed(teacher, "ANSWERED", confirmed.inquiryId())).isTrue();
		assertThat(listed(teacher, "WAITING", confirmed.inquiryId())).isFalse();
	}

	@Test
	void followUpAfterTheAnswerMovesTheThreadBackToWaiting() throws Exception {
		ConfirmedInquiry confirmed = urgentInquiryConfirmedByTeacher();
		Scenario s = confirmed.scenario();
		String teacher = s.teacher().accessToken();
		sendAnswer(teacher, confirmed.inquiryId());

		followUp(s.parent().accessToken(), confirmed.inquiryId());

		Map<String, Object> item = item(teacher, confirmed.inquiryId());
		assertThat(Instant.parse((String) item.get("lastFollowUpAt")))
				.isEqualTo(lastFollowUpSubmittedAt(confirmed.inquiryId()));
		assertThat(item.get("hasAnswer")).isEqualTo(true);
		assertThat(item.get("needsAnswer")).isEqualTo(true);
		assertThat(listed(teacher, "WAITING", confirmed.inquiryId())).isTrue();
		assertThat(listed(teacher, "ANSWERED", confirmed.inquiryId())).isFalse();
	}

	@Test
	void answeringAgainAfterTheFollowUpMovesTheThreadToAnswered() throws Exception {
		ConfirmedInquiry confirmed = urgentInquiryConfirmedByTeacher();
		Scenario s = confirmed.scenario();
		String teacher = s.teacher().accessToken();
		sendAnswer(teacher, confirmed.inquiryId());
		followUp(s.parent().accessToken(), confirmed.inquiryId());

		clock.advance(Duration.ofMinutes(5));
		sendAnswer(teacher, confirmed.inquiryId());

		Map<String, Object> item = item(teacher, confirmed.inquiryId());
		assertThat(item.get("lastFollowUpAt")).isNotNull();
		assertThat(item.get("needsAnswer")).isEqualTo(false);
		assertThat(listed(teacher, "ANSWERED", confirmed.inquiryId())).isTrue();
		assertThat(listed(teacher, "WAITING", confirmed.inquiryId())).isFalse();
	}

	@Test
	void followUpBeforeTheFirstAnswerStaysWaitingUntilAnswered() throws Exception {
		ConfirmedInquiry confirmed = urgentInquiryConfirmedByTeacher();
		Scenario s = confirmed.scenario();
		String teacher = s.teacher().accessToken();
		followUp(s.parent().accessToken(), confirmed.inquiryId());

		assertThat(item(teacher, confirmed.inquiryId()).get("needsAnswer")).isEqualTo(true);
		assertThat(listed(teacher, "WAITING", confirmed.inquiryId())).isTrue();

		clock.advance(Duration.ofMinutes(5));
		sendAnswer(teacher, confirmed.inquiryId());

		assertThat(item(teacher, confirmed.inquiryId()).get("needsAnswer")).isEqualTo(false);
		assertThat(listed(teacher, "ANSWERED", confirmed.inquiryId())).isTrue();
	}

	@Test
	void totalElementsAndPagesFollowTheFilter() throws Exception {
		ConfirmedInquiry answered = urgentInquiryConfirmedByTeacher();
		Scenario s = answered.scenario();
		String teacher = s.teacher().accessToken();
		clock.advance(Duration.ofSeconds(61));
		UUID waiting = UUID.fromString(
				read(submitInquiry(s.parent().accessToken(), s.student().getId(), NORMAL_SAMPLE), "$.inquiryId"));
		sendAnswer(teacher, answered.inquiryId());

		MockHttpServletResponse all = list(teacher, null, 0, 1);
		MockHttpServletResponse onlyWaiting = list(teacher, "WAITING", 0, 1);
		MockHttpServletResponse onlyAnswered = list(teacher, "ANSWERED", 0, 1);

		int allTotal = read(all, "$.totalElements");
		int waitingTotal = read(onlyWaiting, "$.totalElements");
		int answeredTotal = read(onlyAnswered, "$.totalElements");
		assertThat(allTotal).isEqualTo(2);
		assertThat(waitingTotal).isEqualTo(1);
		assertThat(answeredTotal).isEqualTo(1);
		List<Map<String, Object>> waitingItems = read(onlyWaiting, "$.items");
		List<Map<String, Object>> answeredItems = read(onlyAnswered, "$.items");
		assertThat(waitingItems).extracting(item -> item.get("inquiryId")).containsExactly(waiting.toString());
		assertThat(answeredItems).extracting(item -> item.get("inquiryId"))
				.containsExactly(answered.inquiryId().toString());
		List<Map<String, Object>> secondPage = read(list(teacher, "WAITING", 1, 1), "$.items");
		assertThat(secondPage).isEmpty();
	}

	@Test
	void unknownAnswerStatusIsRejected() throws Exception {
		ConfirmedInquiry confirmed = urgentInquiryConfirmedByTeacher();
		String teacher = confirmed.scenario().teacher().accessToken();

		assertThat(list(teacher, "DONE", 0, 20).getStatus()).isEqualTo(400);
		assertThat(list(teacher, "waiting", 0, 20).getStatus()).isEqualTo(400);
	}

	/** 재문의 접수 시각과 마지막 답변 시각이 같으면 SQL 필터도 답변 완료로 본다 — needsAnswer()와 같은 기준. */
	@Test
	void followUpAtTheSameInstantAsTheAnswerCountsAsAnswered() throws Exception {
		ConfirmedInquiry confirmed = urgentInquiryConfirmedByTeacher();
		Scenario s = confirmed.scenario();
		String teacher = s.teacher().accessToken();
		followUp(s.parent().accessToken(), confirmed.inquiryId());
		sendAnswer(teacher, confirmed.inquiryId());
		Timestamp answeredAt = jdbc.queryForObject("select max(sent_at) from answer where thread_root_id = ?",
				Timestamp.class, confirmed.inquiryId());
		jdbc.update("update inquiry set submitted_at = ? where thread_root_id = ?", answeredAt, confirmed.inquiryId());

		Map<String, Object> item = item(teacher, confirmed.inquiryId());
		assertThat(Instant.parse((String) item.get("lastFollowUpAt"))).isEqualTo(answeredAt.toInstant());
		assertThat(Instant.parse((String) item.get("lastAnsweredAt"))).isEqualTo(answeredAt.toInstant());
		assertThat(item.get("needsAnswer")).isEqualTo(false);
		assertThat(listed(teacher, "ANSWERED", confirmed.inquiryId())).isTrue();
		assertThat(listed(teacher, "WAITING", confirmed.inquiryId())).isFalse();
	}

	@Test
	void emptyAnswerStatusIsTheSameAsOmitting() throws Exception {
		ConfirmedInquiry confirmed = urgentInquiryConfirmedByTeacher();
		String teacher = confirmed.scenario().teacher().accessToken();
		sendAnswer(teacher, confirmed.inquiryId());

		MockHttpServletResponse response = send(get("/api/v1/teacher/inquiries?answerStatus="), teacher);

		assertThat(response.getStatus()).isEqualTo(200);
		int total = read(response, "$.totalElements");
		assertThat(total).isEqualTo(1);
		// 앞뒤 공백은 잘라내고 받는다(Spring enum 변환 기본 동작, 명세에 적음)
		int padded = read(list(teacher, " ANSWERED ", 0, 20), "$.totalElements");
		assertThat(padded).isEqualTo(1);
	}

}
