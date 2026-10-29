package damso.inquiry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.time.Duration;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.TestPropertySource;

import damso.support.IntegrationTestSupport;

/**
 * 이슈 #28: 답변 전송은 교사가 본 마지막 재문의(lastSeenFollowUpId)가 스레드의 최신 재문의와 같을 때만 저장된다.
 * 답변을 쓰는 동안 온 재문의가 그 답변에 묻혀 "답변 완료"로 넘어가지 않게 한다. 키는 필수, 값은 null 가능.
 */
@TestPropertySource(properties = "damso.refinement.dispatch-on-commit=false")
class SendAnswerFollowUpCheckTest extends IntegrationTestSupport {

	private MockHttpServletResponse sendAnswer(String token, UUID inquiryId, String lastSeenJson) throws Exception {
		String body = "{\"content\":\"확인했습니다.\",\"reviewed\":false"
				+ (lastSeenJson == null ? "" : ",\"lastSeenFollowUpId\":" + lastSeenJson) + "}";
		return send(json(post("/api/v1/teacher/inquiries/" + inquiryId + "/answers"), body), token);
	}

	private UUID followUp(String token, UUID inquiryId) throws Exception {
		clock.advance(Duration.ofSeconds(61));
		MockHttpServletResponse response = submitFollowUp(token, inquiryId, NORMAL_SAMPLE);
		assertThat(response.getStatus()).isEqualTo(201);
		return UUID.fromString(read(response, "$.inquiryId"));
	}

	private int answerCount(UUID rootId) {
		return jdbc.queryForObject("select count(*) from answer where thread_root_id = ?", Integer.class, rootId);
	}

	private static String quoted(UUID id) {
		return "\"" + id + "\"";
	}

	private void assertRejectedAsNewFollowUp(MockHttpServletResponse response) throws Exception {
		assertThat(response.getStatus()).isEqualTo(409);
		assertThat((String) read(response, "$.code")).isEqualTo("NEW_FOLLOW_UP");
	}

	@Test
	void threadWithoutFollowUpAcceptsNull() throws Exception {
		ConfirmedInquiry confirmed = urgentInquiryConfirmedByTeacher();

		MockHttpServletResponse response = sendAnswer(confirmed.scenario().teacher().accessToken(),
				confirmed.inquiryId(), "null");

		assertThat(response.getStatus()).isEqualTo(201);
	}

	@Test
	void missingKeyIsRejectedAsInvalidInput() throws Exception {
		ConfirmedInquiry confirmed = urgentInquiryConfirmedByTeacher();

		MockHttpServletResponse response = sendAnswer(confirmed.scenario().teacher().accessToken(),
				confirmed.inquiryId(), null);

		assertThat(response.getStatus()).isEqualTo(400);
		assertThat((String) read(response, "$.code")).isEqualTo("INVALID_INPUT");
		assertThat(answerCount(confirmed.inquiryId())).isZero();
	}

	@Test
	void followUpThatArrivedWhileWritingBlocksTheAnswer() throws Exception {
		ConfirmedInquiry confirmed = urgentInquiryConfirmedByTeacher();
		Scenario s = confirmed.scenario();
		String teacher = s.teacher().accessToken();
		// 교사가 재문의 없는 스레드를 열고 답변을 쓰는 동안 재문의가 온다
		followUp(s.parent().accessToken(), confirmed.inquiryId());

		assertRejectedAsNewFollowUp(sendAnswer(teacher, confirmed.inquiryId(), "null"));
		assertThat(answerCount(confirmed.inquiryId())).isZero();
		MockHttpServletResponse waiting = send(get("/api/v1/teacher/inquiries?answerStatus=WAITING"), teacher);
		assertThat((String) read(waiting, "$.items[0].inquiryId")).isEqualTo(confirmed.inquiryId().toString());
	}

	@Test
	void olderFollowUpIdIsRejectedAndTheLatestIsAccepted() throws Exception {
		ConfirmedInquiry confirmed = urgentInquiryConfirmedByTeacher();
		Scenario s = confirmed.scenario();
		String teacher = s.teacher().accessToken();
		UUID first = followUp(s.parent().accessToken(), confirmed.inquiryId());
		UUID second = followUp(s.parent().accessToken(), confirmed.inquiryId());

		assertRejectedAsNewFollowUp(sendAnswer(teacher, confirmed.inquiryId(), quoted(first)));
		assertThat(answerCount(confirmed.inquiryId())).isZero();

		assertThat(sendAnswer(teacher, confirmed.inquiryId(), quoted(second)).getStatus()).isEqualTo(201);
		assertThat(answerCount(confirmed.inquiryId())).isEqualTo(1);
	}

	@Test
	void followUpIdThatIsNotInTheThreadIsRejected() throws Exception {
		ConfirmedInquiry confirmed = urgentInquiryConfirmedByTeacher();

		MockHttpServletResponse response = sendAnswer(confirmed.scenario().teacher().accessToken(),
				confirmed.inquiryId(), quoted(UUID.randomUUID()));

		assertRejectedAsNewFollowUp(response);
		assertThat(answerCount(confirmed.inquiryId())).isZero();
	}

}
