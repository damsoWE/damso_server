package damso.inquiry;

import static damso.support.ErrorResponseAssertions.assertSafeErrorResponse;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.time.Duration;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

import damso.account.domain.Role;
import damso.support.IntegrationTestSupport;

/**
 * F4 US7 시나리오 1·2 / FR-031·032, SC-014: 앱에서 쓰지 않는 종료 경로는 운영 기본값(damso.inquiry.close-enabled=false)에서
 * 막혀 있다. 교사는 담당 여부와 무관하게 없는 경로와 같은 404를 받고, 스레드는 열린 채로 남는다.
 * 교사가 아닌 사용자는 경로 단위 역할 검사에서 먼저 403이다. 설정을 켰을 때의 동작은 CloseInquiryApiTest가 본다.
 */
class CloseDisabledApiTest extends IntegrationTestSupport {

	private MockHttpServletResponse close(String token, UUID inquiryId) throws Exception {
		return send(post("/api/v1/teacher/inquiries/" + inquiryId + "/close"), token);
	}

	private String stageOf(UUID inquiryId) {
		return jdbc.queryForObject("select stage from inquiry where id = ?", String.class, inquiryId);
	}

	private int answeredHistoryOf(UUID inquiryId) {
		return jdbc.queryForObject(
				"select count(*) from inquiry_stage_history where inquiry_id = ? and stage = 'ANSWERED'", Integer.class,
				inquiryId);
	}

	@Test
	void homeroomTeacherGetsNotFoundAndTheThreadStaysOpen() throws Exception {
		ConfirmedInquiry confirmed = urgentInquiryConfirmedByTeacher();
		Scenario s = confirmed.scenario();

		MockHttpServletResponse response = close(s.teacher().accessToken(), confirmed.inquiryId());

		assertThat(response.getStatus()).isEqualTo(404);
		assertSafeErrorResponse(response.getContentAsString(), "NOT_FOUND");
		assertThat(stageOf(confirmed.inquiryId())).isEqualTo("TEACHER_CONFIRMED");
		assertThat(answeredHistoryOf(confirmed.inquiryId())).isZero();

		clock.advance(Duration.ofSeconds(61));
		assertThat(submitFollowUp(s.parent().accessToken(), confirmed.inquiryId(), NORMAL_SAMPLE).getStatus())
			.isEqualTo(201);
		MockHttpServletResponse answered = send(json(post("/api/v1/teacher/inquiries/" + confirmed.inquiryId()
				+ "/answers"), "{\"content\":\"확인했습니다.\",\"reviewed\":false,\"lastSeenFollowUpId\":" + lastSeenFollowUpJson(confirmed.inquiryId()) + "}"), s.teacher().accessToken());
		assertThat(answered.getStatus()).isEqualTo(201);
	}

	@Test
	void otherTeacherGetsNotFoundAndParentOrAdminForbidden() throws Exception {
		ConfirmedInquiry confirmed = urgentInquiryConfirmedByTeacher();
		Scenario otherClassroom = teacherWithClassroom(2, 1);

		MockHttpServletResponse byOtherTeacher = close(otherClassroom.teacher().accessToken(), confirmed.inquiryId());
		MockHttpServletResponse byParent = close(confirmed.scenario().parent().accessToken(), confirmed.inquiryId());
		MockHttpServletResponse byAdmin = close(tokenFor(createAccount(Role.SUPER_ADMIN)), confirmed.inquiryId());

		assertThat(byOtherTeacher.getStatus()).isEqualTo(404);
		assertSafeErrorResponse(byOtherTeacher.getContentAsString(), "NOT_FOUND");
		assertThat(byParent.getStatus()).isEqualTo(403);
		assertThat(byAdmin.getStatus()).isEqualTo(403);
		assertThat(stageOf(confirmed.inquiryId())).isEqualTo("TEACHER_CONFIRMED");
		assertThat(answeredHistoryOf(confirmed.inquiryId())).isZero();
	}

	@Test
	void disabledPathLooksTheSameAsAMissingInquiry() throws Exception {
		ConfirmedInquiry confirmed = urgentInquiryConfirmedByTeacher();
		String teacherToken = confirmed.scenario().teacher().accessToken();

		MockHttpServletResponse disabled = close(teacherToken, confirmed.inquiryId());
		MockHttpServletResponse missing = close(teacherToken, UUID.randomUUID());

		assertThat(disabled.getStatus()).isEqualTo(missing.getStatus()).isEqualTo(404);
		assertThat(disabled.getContentAsString()).isEqualTo(missing.getContentAsString());
	}

	/** F5: 꺼진 경로의 404는 거부된 요청이므로 종료 감사 기록을 남기지 않는다. */
	@Test
	void disabledCloseLeavesNoAuditRecord() throws Exception {
		ConfirmedInquiry confirmed = urgentInquiryConfirmedByTeacher();

		MockHttpServletResponse response = close(confirmed.scenario().teacher().accessToken(), confirmed.inquiryId());

		assertThat(response.getStatus()).isEqualTo(404);
		assertThat(jdbc.queryForObject("select count(*) from audit_log where action = 'INQUIRY_CLOSED'", Integer.class))
			.isZero();
	}

}
