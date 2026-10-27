package damso.inquiry;

import static damso.support.ErrorResponseAssertions.assertSafeErrorResponse;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

import damso.account.domain.Role;
import damso.support.IntegrationTestSupport;

/**
 * F4 US5 시나리오 1~4 / FR-020·022: 교사가 "답변 완료"로 문의를 종료한다 — 명시적 동작만, 자동 전이 없음.
 * 앱은 이 경로를 쓰지 않는다(docs/decisions.md 2026-10-27). 호출되면 누가 닫았는지 감사 기록을 남긴다(F5 T011).
 */
class CloseInquiryApiTest extends IntegrationTestSupport {

	private MockHttpServletResponse close(String token, UUID inquiryId) throws Exception {
		return send(post("/api/v1/teacher/inquiries/" + inquiryId + "/close"), token);
	}

	private MockHttpServletResponse sendAnswer(String token, UUID inquiryId, String content) throws Exception {
		String body = "{\"content\":\"" + content + "\",\"reviewed\":false}";
		return send(json(post("/api/v1/teacher/inquiries/" + inquiryId + "/answers"), body), token);
	}

	private List<String> closeRecords() {
		return jdbc.queryForList("select actor_id || ':' || target_id from audit_log where action = 'INQUIRY_CLOSED'",
				String.class);
	}

	private String stageOf(UUID inquiryId) {
		return jdbc.queryForObject("select stage from inquiry where id = ?", String.class, inquiryId);
	}

	@Test
	void teacherClosesAConfirmedThread() throws Exception {
		ConfirmedInquiry confirmed = urgentInquiryConfirmedByTeacher();

		MockHttpServletResponse response = close(confirmed.scenario().teacher().accessToken(), confirmed.inquiryId());

		assertThat(response.getStatus()).isEqualTo(200);
		assertThat((String) read(response, "$.inquiryId")).isEqualTo(confirmed.inquiryId().toString());
		assertThat((String) read(response, "$.closedAt")).isNotBlank();
		assertThat(stageOf(confirmed.inquiryId())).isEqualTo("ANSWERED");
		List<Map<String, Object>> history = jdbc.queryForList(
				"select stage from inquiry_stage_history where inquiry_id = ? and stage = 'ANSWERED'",
				confirmed.inquiryId());
		assertThat(history).hasSize(1);
		assertThat(closeRecords())
				.containsExactly(confirmed.scenario().teacher().userId() + ":" + confirmed.inquiryId());
	}

	@Test
	void sendingAnAnswerDoesNotAutomaticallyCloseTheThread() throws Exception {
		ConfirmedInquiry confirmed = urgentInquiryConfirmedByTeacher();
		String teacherToken = confirmed.scenario().teacher().accessToken();

		assertThat(sendAnswer(teacherToken, confirmed.inquiryId(), "답변을 보냅니다.").getStatus()).isEqualTo(201);

		assertThat(stageOf(confirmed.inquiryId())).isEqualTo("TEACHER_CONFIRMED");
	}

	@Test
	void aGeneralInquiryNotYetConfirmedCannotBeClosed() throws Exception {
		Scenario s = teacherWithClassroom();
		MockHttpServletResponse submitted = submitInquiry(s.parent().accessToken(), s.student().getId(),
				NORMAL_SAMPLE);
		UUID inquiryId = UUID.fromString(read(submitted, "$.inquiryId"));
		// F3가 머지된 뒤로는 테스트 환경에서 정리 작업이 동기로 끝나 RECEIVED→REFINING까지 바로 갈 수 있다.
		// 이 테스트가 보려는 건 "아직 TEACHER_CONFIRMED가 아니면 종료할 수 없다"이므로 그 전제만 확인한다.
		assertThat(stageOf(inquiryId)).isNotEqualTo("TEACHER_CONFIRMED").isNotEqualTo("ANSWERED");
		String stageBeforeClose = stageOf(inquiryId);

		MockHttpServletResponse response = close(s.teacher().accessToken(), inquiryId);

		assertThat(response.getStatus()).isEqualTo(409);
		assertSafeErrorResponse(response.getContentAsString(), "INQUIRY_NOT_READY_TO_CLOSE");
		assertThat(stageOf(inquiryId)).isEqualTo(stageBeforeClose);
		assertThat(closeRecords()).isEmpty();
	}

	@Test
	void otherClassroomTeacherNotFoundAndParentOrAdminForbidden() throws Exception {
		ConfirmedInquiry confirmed = urgentInquiryConfirmedByTeacher();
		Scenario otherClassroom = teacherWithClassroom(2, 1);

		MockHttpServletResponse byOtherTeacher = close(otherClassroom.teacher().accessToken(), confirmed.inquiryId());
		MockHttpServletResponse byParent = close(confirmed.scenario().parent().accessToken(), confirmed.inquiryId());
		MockHttpServletResponse byAdmin = close(tokenFor(createAccount(Role.SUPER_ADMIN)), confirmed.inquiryId());

		assertThat(byOtherTeacher.getStatus()).isEqualTo(404);
		assertThat(byParent.getStatus()).isEqualTo(403);
		assertThat(byAdmin.getStatus()).isEqualTo(403);
		assertThat(stageOf(confirmed.inquiryId())).isEqualTo("TEACHER_CONFIRMED");
		assertThat(closeRecords()).isEmpty();
	}

	@Test
	void closingAnAlreadyClosedThreadIsRejected() throws Exception {
		ConfirmedInquiry confirmed = urgentInquiryConfirmedByTeacher();
		String teacherToken = confirmed.scenario().teacher().accessToken();
		assertThat(close(teacherToken, confirmed.inquiryId()).getStatus()).isEqualTo(200);

		MockHttpServletResponse again = close(teacherToken, confirmed.inquiryId());

		assertThat(again.getStatus()).isEqualTo(409);
		assertSafeErrorResponse(again.getContentAsString(), "THREAD_CLOSED");
		List<Map<String, Object>> history = jdbc.queryForList(
				"select stage from inquiry_stage_history where inquiry_id = ? and stage = 'ANSWERED'",
				confirmed.inquiryId());
		assertThat(history).hasSize(1);
		assertThat(closeRecords()).hasSize(1);
	}

}
