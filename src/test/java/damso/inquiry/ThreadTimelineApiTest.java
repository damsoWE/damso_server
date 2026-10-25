package damso.inquiry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

import damso.support.IntegrationTestSupport;

/**
 * F4 US4 시나리오 5 / FR-016·017, research R1: 스레드 상세의 재문의·답변 타임라인(시간순),
 * 재문의 자신의 id로는 같은 스레드 상세로 취급하지 않음(별도 엔드포인트 없음).
 */
class ThreadTimelineApiTest extends IntegrationTestSupport {

	private MockHttpServletResponse sendAnswer(String teacherToken, UUID inquiryId, String content) throws Exception {
		String body = "{\"content\":\"" + content + "\",\"reviewed\":false}";
		return send(json(post("/api/v1/teacher/inquiries/" + inquiryId + "/answers"), body), teacherToken);
	}

	@Test
	void parentAndTeacherDetailShowFollowUpsAndAnswersInOrder() throws Exception {
		ConfirmedInquiry confirmed = urgentInquiryConfirmedByTeacher();
		String parentToken = confirmed.scenario().parent().accessToken();
		String teacherToken = confirmed.scenario().teacher().accessToken();
		UUID rootId = confirmed.inquiryId();

		MockHttpServletResponse answer = sendAnswer(teacherToken, rootId, "우선 안내드립니다.");
		assertThat(answer.getStatus()).isEqualTo(201);
		MockHttpServletResponse followUp1 = submitFollowUp(parentToken, rootId, "추가로 궁금한 게 있어요 1");
		assertThat(followUp1.getStatus()).isEqualTo(201);
		clock.advance(Duration.ofSeconds(61));
		MockHttpServletResponse followUp2 = submitFollowUp(parentToken, rootId, "추가로 궁금한 게 있어요 2");
		assertThat(followUp2.getStatus()).isEqualTo(201);
		String followUpId1 = read(followUp1, "$.inquiryId");
		String followUpId2 = read(followUp2, "$.inquiryId");

		MockHttpServletResponse parentDetail = send(get("/api/v1/parent/inquiries/" + rootId), parentToken);
		MockHttpServletResponse teacherDetail = send(get("/api/v1/teacher/inquiries/" + rootId), teacherToken);

		assertThat(parentDetail.getStatus()).isEqualTo(200);
		List<String> parentFollowUpIds = read(parentDetail, "$.followUps[*].inquiryId");
		assertThat(parentFollowUpIds).containsExactly(followUpId1, followUpId2);
		assertThat((String) read(parentDetail, "$.followUps[0].content")).isEqualTo("추가로 궁금한 게 있어요 1");
		List<String> parentAnswerIds = read(parentDetail, "$.answers[*].answerId");
		assertThat(parentAnswerIds).hasSize(1);
		assertThat((String) read(parentDetail, "$.answers[0].content")).isEqualTo("우선 안내드립니다.");

		assertThat(teacherDetail.getStatus()).isEqualTo(200);
		List<String> teacherFollowUpIds = read(teacherDetail, "$.followUps[*].inquiryId");
		assertThat(teacherFollowUpIds).containsExactly(followUpId1, followUpId2);
		assertThat(teacherDetail.getContentAsString()).doesNotContain("추가로 궁금한 게 있어요");
		List<String> teacherAnswerIds = read(teacherDetail, "$.answers[*].answerId");
		assertThat(teacherAnswerIds).hasSize(1);
		assertThat((String) read(teacherDetail, "$.answers[0].content")).isEqualTo("우선 안내드립니다.");
	}

	@Test
	void callingTeacherDetailWithAFollowUpsOwnIdDoesNotActAsTheThreadDetail() throws Exception {
		ConfirmedInquiry confirmed = urgentInquiryConfirmedByTeacher();
		String parentToken = confirmed.scenario().parent().accessToken();
		String teacherToken = confirmed.scenario().teacher().accessToken();
		UUID rootId = confirmed.inquiryId();
		sendAnswer(teacherToken, rootId, "답변입니다.");
		MockHttpServletResponse followUp = submitFollowUp(parentToken, rootId, "재문의입니다.");
		String followUpId = read(followUp, "$.inquiryId");

		MockHttpServletResponse ownDetail = send(get("/api/v1/teacher/inquiries/" + followUpId), teacherToken);

		assertThat(ownDetail.getStatus()).isEqualTo(200);
		assertThat((List<?>) read(ownDetail, "$.followUps")).isEmpty();
		assertThat((List<?>) read(ownDetail, "$.answers")).isEmpty();
		assertThat((String) read(ownDetail, "$.inquiryId")).isEqualTo(followUpId).isNotEqualTo(rootId.toString());
	}

}
