package damso.inquiry;

import static damso.support.ErrorResponseAssertions.assertSafeErrorResponse;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

import damso.account.domain.Role;
import damso.support.IntegrationTestSupport;

/**
 * F4 US1 시나리오 1~5 / FR-001~007, SC-001: 교사가 답변을 작성해 점검 없이 바로 전송한다.
 * 전송은 문의 단계·점검 서비스 상태와 무관하게 동작한다.
 */
class SendAnswerApiTest extends IntegrationTestSupport {

	private MockHttpServletResponse sendAnswer(String token, UUID inquiryId, String content, Boolean reviewed)
			throws Exception {
		String body = "{\"content\":\"" + content + "\",\"reviewed\":" + reviewed + ",\"lastSeenFollowUpId\":" + lastSeenFollowUpJson(inquiryId) + "}";
		return send(json(post("/api/v1/teacher/inquiries/" + inquiryId + "/answers"), body), token);
	}

	@Test
	void teacherSendsAnswerWithoutReview() throws Exception {
		ConfirmedInquiry confirmed = urgentInquiryConfirmedByTeacher();

		MockHttpServletResponse response = sendAnswer(confirmed.scenario().teacher().accessToken(),
				confirmed.inquiryId(), "곧 다시 확인해서 답변드릴게요.", false);

		String answerId = read(response, "$.answerId");
		assertThat(response.getStatus()).isEqualTo(201);
		assertThat(answerId).isNotBlank();
		assertThat((String) read(response, "$.inquiryId")).isEqualTo(confirmed.inquiryId().toString());
		assertThat((String) read(response, "$.sentAt")).isNotBlank();

		String stored = jdbc.queryForObject("select content from answer where id = ?", String.class,
				UUID.fromString(answerId));
		assertThat(stored).isNotBlank().doesNotContain("곧 다시 확인해서 답변드릴게요");
	}

	@Test
	void blankOrTooLongContentIsRejected() throws Exception {
		ConfirmedInquiry confirmed = urgentInquiryConfirmedByTeacher();
		String teacherToken = confirmed.scenario().teacher().accessToken();

		MockHttpServletResponse blank = sendAnswer(teacherToken, confirmed.inquiryId(), "   ", false);
		MockHttpServletResponse tooLong = sendAnswer(teacherToken, confirmed.inquiryId(), "가".repeat(2001), false);

		assertThat(blank.getStatus()).isEqualTo(400);
		assertSafeErrorResponse(blank.getContentAsString(), "INVALID_INPUT");
		assertThat(tooLong.getStatus()).isEqualTo(400);
		assertThat(jdbc.queryForObject("select count(*) from answer", Integer.class)).isZero();
	}

	@Test
	void teacherFromAnotherClassroomCannotAnswer() throws Exception {
		ConfirmedInquiry confirmed = urgentInquiryConfirmedByTeacher();
		Scenario otherClassroom = teacherWithClassroom(2, 1);

		MockHttpServletResponse response = sendAnswer(otherClassroom.teacher().accessToken(), confirmed.inquiryId(),
				"안녕하세요.", false);

		assertThat(response.getStatus()).isEqualTo(404);
		assertThat(jdbc.queryForObject("select count(*) from answer", Integer.class)).isZero();
	}

	@Test
	void parentAndAdminCannotAnswer() throws Exception {
		ConfirmedInquiry confirmed = urgentInquiryConfirmedByTeacher();

		MockHttpServletResponse byParent = sendAnswer(confirmed.scenario().parent().accessToken(),
				confirmed.inquiryId(), "답변", false);
		MockHttpServletResponse byAdmin = sendAnswer(tokenFor(createAccount(Role.SUPER_ADMIN)), confirmed.inquiryId(),
				"답변", false);
		MockHttpServletResponse anonymous = sendAnswer(null, confirmed.inquiryId(), "답변", false);

		assertThat(byParent.getStatus()).isEqualTo(403);
		assertThat(byAdmin.getStatus()).isEqualTo(403);
		assertThat(anonymous.getStatus()).isEqualTo(401);
	}

	@Test
	void teacherCanAnswerAGeneralInquiryEvenBeforeTeacherConfirmed() throws Exception {
		Scenario s = teacherWithClassroom();
		MockHttpServletResponse submitted = submitInquiry(s.parent().accessToken(), s.student().getId(),
				NORMAL_SAMPLE);
		UUID inquiryId = UUID.fromString(read(submitted, "$.inquiryId"));
		assertThat((String) read(submitted, "$.stage")).isEqualTo("RECEIVED");

		MockHttpServletResponse response = sendAnswer(s.teacher().accessToken(), inquiryId, "확인했습니다.", false);

		assertThat(response.getStatus()).isEqualTo(201);
	}

	@Test
	void thereIsNoWayToEditOrDeleteAnAnswer() throws Exception {
		ConfirmedInquiry confirmed = urgentInquiryConfirmedByTeacher();
		String teacherToken = confirmed.scenario().teacher().accessToken();
		MockHttpServletResponse created = sendAnswer(teacherToken, confirmed.inquiryId(), "원래 답변", false);
		String answerId = read(created, "$.answerId");
		String body = "{\"content\":\"바꾼 답변\"}";

		List<MockHttpServletResponse> attempts = List.of(
				send(json(put("/api/v1/teacher/inquiries/" + confirmed.inquiryId() + "/answers/" + answerId), body),
						teacherToken),
				send(json(patch("/api/v1/teacher/inquiries/" + confirmed.inquiryId() + "/answers/" + answerId), body),
						teacherToken),
				send(delete("/api/v1/teacher/inquiries/" + confirmed.inquiryId() + "/answers/" + answerId),
						teacherToken),
				send(json(put("/api/v1/teacher/inquiries/" + confirmed.inquiryId() + "/answers"), body), teacherToken),
				send(delete("/api/v1/teacher/inquiries/" + confirmed.inquiryId() + "/answers"), teacherToken));

		for (MockHttpServletResponse response : attempts) {
			assertThat(response.getStatus()).isIn(404, 405);
		}
		assertThat(jdbc.queryForObject("select count(*) from answer", Integer.class)).isEqualTo(1);
		String stored = jdbc.queryForObject("select content from answer where id = ?", String.class,
				UUID.fromString(answerId));
		assertThat(stored).isNotBlank().doesNotContain("바꾼 답변");
	}

}
