package damso.inquiry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

import tools.jackson.databind.ObjectMapper;

import damso.support.IntegrationTestSupport;

/**
 * 학부모는 정제·교사 확인 단계를 시각과 함께 보지만, 정리 결과·정서·실패
 * 사유는 보지 않는다. 일반 문의에는 "교사에게 전달됨" 단계가 없다.
 */
class ParentRefinementTimelineTest extends IntegrationTestSupport {

	private static final ObjectMapper JSON = new ObjectMapper();

	/** F2 계약의 학부모 상세·목록 키. F3는 학부모 응답에 키를 더하지 않는다(followUps·answers는 F4가 더한 것). */
	private static final Set<String> DETAIL_KEYS = Set.of("inquiryId", "studentId", "studentName", "content", "urgent",
			"stage", "submittedAt", "timeline", "notices", "hotlines", "followUps", "answers");

	private UUID submit(Scenario s, String content) throws Exception {
		return UUID.fromString(read(submitInquiry(s.parent().accessToken(), s.student().getId(), content),
				"$.inquiryId"));
	}

	private MockHttpServletResponse detail(Scenario s, UUID id) throws Exception {
		return send(get("/api/v1/parent/inquiries/" + id), s.parent().accessToken());
	}

	private List<String> stages(MockHttpServletResponse response) throws Exception {
		return read(response, "$.timeline[*].stage");
	}

	@Test
	void timelineShowsRefiningThenTeacherConfirmedWithTimes() throws Exception {
		Scenario s = teacherWithClassroom();
		UUID id = submit(s, generalInquiryNaming(s));

		MockHttpServletResponse afterRefinement = detail(s, id);
		send(get("/api/v1/teacher/inquiries/" + id + "/refinement"), s.teacher().accessToken());
		MockHttpServletResponse afterView = detail(s, id);

		assertThat(stages(afterRefinement)).containsExactly("RECEIVED", "REFINING");
		assertThat(stages(afterView)).containsExactly("RECEIVED", "REFINING", "TEACHER_CONFIRMED");
		assertThat(IntegrationTestSupport.<List<String>>read(afterView, "$.timeline[*].occurredAt"))
				.allSatisfy(at -> assertThat(at).isNotBlank());
		assertThat((String) read(afterView, "$.stage")).isEqualTo("TEACHER_CONFIRMED");
	}

	@Test
	void parentResponsesCarryNoRefinementResultEmotionOrFailure() throws Exception {
		Scenario s = teacherWithClassroom();
		fakeAi.replyNext(damso.support.FakeExternalAi.refinementJson("숙제 문의", "숙제를 자주 잊는다", "확인 부탁",
				"HIGH", List.of("ANGER")));
		UUID refined = submit(s, NORMAL_SAMPLE);
		clock.advance(java.time.Duration.ofSeconds(61));
		fakeAi.noResponseNext();
		fakeAi.noResponseNext();
		UUID failed = submit(s, NORMAL_SAMPLE + " 다시");

		for (UUID id : List.of(refined, failed)) {
			MockHttpServletResponse response = detail(s, id);
			assertThat(Set.copyOf(JSON.readTree(response.getContentAsString()).propertyNames())).isEqualTo(DETAIL_KEYS);
			assertThat(response.getContentAsString()).doesNotContain("숙제를 자주 잊는다").doesNotContain("숙제 문의")
					.doesNotContain("HIGH").doesNotContain("ANGER").doesNotContain("NEEDS_ORIGINAL")
					.doesNotContain("refinement").doesNotContain("emotion");
		}
		MockHttpServletResponse list = send(get("/api/v1/parent/inquiries"), s.parent().accessToken());
		assertThat(list.getContentAsString()).doesNotContain("숙제 문의").doesNotContain("refinement")
				.doesNotContain("emotion").doesNotContain("title");
		// 원문 확인 필요는 정제 완료가 아니다 — 학부모 타임라인은 접수에 머문다
		assertThat(stages(detail(s, failed))).containsExactly("RECEIVED");
	}

	@Test
	void generalInquiriesNeverShowDelivered() throws Exception {
		Scenario s = teacherWithClassroom();
		UUID refined = submit(s, NORMAL_SAMPLE);
		send(get("/api/v1/teacher/inquiries/" + refined + "/refinement"), s.teacher().accessToken());
		clock.advance(java.time.Duration.ofSeconds(61));
		fakeAi.noResponseNext();
		fakeAi.noResponseNext();
		UUID failed = submit(s, NORMAL_SAMPLE + " 다시");
		send(get("/api/v1/teacher/inquiries/" + failed + "/content"), s.teacher().accessToken());

		assertThat(stages(detail(s, refined))).doesNotContain("DELIVERED");
		assertThat(stages(detail(s, failed))).containsExactly("RECEIVED", "TEACHER_CONFIRMED");
		assertThat(jdbc.queryForObject("select count(*) from inquiry_stage_history h join inquiry i on i.id = h.inquiry_id "
				+ "where i.urgent = false and h.stage = 'DELIVERED'", Integer.class)).isZero();
	}

	@Test
	void anotherParentCannotSeeTheTimeline() throws Exception {
		Scenario s = teacherWithClassroom(1, 1);
		Scenario other = teacherWithClassroom(2, 1);
		UUID id = submit(s, NORMAL_SAMPLE);

		MockHttpServletResponse response = send(get("/api/v1/parent/inquiries/" + id), other.parent().accessToken());

		assertThat(response.getStatus()).isEqualTo(404);
	}

}
