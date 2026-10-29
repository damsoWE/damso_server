package damso.inquiry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import tools.jackson.databind.ObjectMapper;

import damso.support.FakeExternalAi;
import damso.support.IntegrationTestSupport;

/**
 * 점검이 성공하든 검증에 실패하든 외부 AI가 장애를 내든, 로그 어디에도 답변 초안·점검 결과·
 * 맥락으로 쓰인 정리 결과·문의 원문이 남지 않는다.
 */
@ExtendWith(OutputCaptureExtension.class)
class AnswerReviewLoggingTest extends IntegrationTestSupport {

	private static final ObjectMapper JSON = new ObjectMapper();

	private static final String DRAFT = "로그점검초안문장QWX 걱정하지 않으셔도 됩니다.";

	private static final String FACTS = "로그점검정리결과QWX 숙제를 자주 빠뜨린다고 전했다.";

	private static final String REASON = "로그점검이유QWX";

	private static final String SUGGESTION = "로그점검제안QWX";

	@Test
	void neitherDraftNorFindingsNorContextReachTheLogs(CapturedOutput output) throws Exception {
		Scenario s = teacherWithClassroom();
		fakeAi.replyNext(FakeExternalAi.refinementJson("숙제 문의", FACTS, "", "MEDIUM", List.of("WORRY")));
		UUID inquiryId = UUID.fromString(read(submitInquiry(s.parent().accessToken(), s.student().getId(),
				generalInquiryNaming(s)), "$.inquiryId"));
		runRefinement();
		String body = JSON.writeValueAsString(Map.of("content", DRAFT));
		String path = "/api/v1/teacher/inquiries/" + inquiryId + "/answer-reviews";

		fakeAi.replyNext(FakeExternalAi.reviewJson(new String[] { "걱정하지 않으셔도 됩니다", REASON, SUGGESTION }));
		assertThat((Boolean) read(send(json(post(path), body), s.teacher().accessToken()), "$.available")).isTrue();
		fakeAi.replyNext(FakeExternalAi.reviewJson(new String[] { "초안에 없는 말", REASON, SUGGESTION }));
		assertThat((Boolean) read(send(json(post(path), body), s.teacher().accessToken()), "$.available")).isFalse();
		fakeAi.throwNext();
		assertThat((Boolean) read(send(json(post(path), body), s.teacher().accessToken()), "$.available")).isFalse();

		String logs = output.getAll();
		assertThat(logs).contains("inquiry=" + inquiryId);
		assertThat(logs).doesNotContain("로그점검초안문장QWX").doesNotContain("로그점검정리결과QWX")
				.doesNotContain(REASON).doesNotContain(SUGGESTION).doesNotContain("숙제를 자꾸 잊어버려서")
				.doesNotContain("초안에 없는 말");
	}

}
