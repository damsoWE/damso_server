package damso.inquiry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

import damso.support.FakeExternalAi;
import damso.support.IntegrationTestSupport;

/**
 * 재문의도 F3의 정제·위기 분류 파이프라인을 타야 한다(PR #10 리뷰 6번). SubmitFollowUpService가 SubmitInquiryService처럼
 * RefinementJob을 만들지 않으면: 일반 재문의는 제목이 "정리 중"에 영원히 머물고, 긴급 재문의는 위기 분류가 되지 않아
 * 상담 번호가 항상 둘 다(112·109) 보인다.
 */
class FollowUpRefinementPipelineTest extends IntegrationTestSupport {

	private UUID submitRoot(Scenario s) throws Exception {
		return UUID.fromString(read(submitInquiry(s.parent().accessToken(), s.student().getId(), NORMAL_SAMPLE),
				"$.inquiryId"));
	}

	private MockHttpServletResponse teacherDetail(Scenario s, UUID rootId) throws Exception {
		return send(get("/api/v1/teacher/inquiries/" + rootId), s.teacher().accessToken());
	}

	private MockHttpServletResponse parentDetail(Scenario s, UUID rootId) throws Exception {
		return send(get("/api/v1/parent/inquiries/" + rootId), s.parent().accessToken());
	}

	@Test
	void generalFollowUpIsRefinedAndTeacherSeesTheTranslatedTitle() throws Exception {
		Scenario s = teacherWithClassroom();
		UUID rootId = submitRoot(s);
		clock.advance(Duration.ofSeconds(61));
		fakeAi.replyNext(FakeExternalAi.refinementJson("준비물 문의", "준비물을 다시 챙겨 달라는 요청", "", "LOW", List.of()));

		UUID followUpId = UUID.fromString(
				read(submitFollowUp(s.parent().accessToken(), rootId, "준비물을 다시 챙겨 주실 수 있는지 여쭤봅니다"), "$.inquiryId"));

		MockHttpServletResponse detail = teacherDetail(s, rootId);
		assertThat((String) read(detail, "$.followUps[0].inquiryId")).isEqualTo(followUpId.toString());
		assertThat((String) read(detail, "$.followUps[0].title")).isEqualTo("준비물 문의");
		assertThat(detail.getContentAsString()).doesNotContain("다시 챙겨 주실 수 있는지 여쭤봅니다");
	}

	@Test
	void urgentFollowUpIsCrisisClassifiedAndNarrowsTheHotlinesForTheParent() throws Exception {
		Scenario s = teacherWithClassroom();
		UUID rootId = submitRoot(s);
		clock.advance(Duration.ofSeconds(61));
		fakeAi.replyNext(FakeExternalAi.crisisJson("SELF_HARM"));

		submitFollowUp(s.parent().accessToken(), rootId, URGENT_SAMPLE);

		MockHttpServletResponse detail = parentDetail(s, rootId);
		assertThat(IntegrationTestSupport.<List<String>>read(detail, "$.followUps[0].hotlines"))
				.containsExactly("SUICIDE_PREVENTION_109");
	}

	@Test
	void whenRefinementFailsTheFollowUpNeedsOriginalAndNeverLeaksRawContentToTheTeacher() throws Exception {
		Scenario s = teacherWithClassroom();
		UUID rootId = submitRoot(s);
		clock.advance(Duration.ofSeconds(61));
		fakeAi.noResponseNext();
		fakeAi.noResponseNext();
		String content = "숙제를 챙겨 주시지 않아서 다시 여쭤봅니다 안내 부탁드려요";

		submitFollowUp(s.parent().accessToken(), rootId, content);

		MockHttpServletResponse detail = teacherDetail(s, rootId);
		assertThat((String) read(detail, "$.followUps[0].title")).isEqualTo("원문 확인 필요");
		assertThat(detail.getContentAsString()).doesNotContain(content);
	}

}
