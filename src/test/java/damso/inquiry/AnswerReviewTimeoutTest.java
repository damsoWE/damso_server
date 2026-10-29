package damso.inquiry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.time.Duration;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.TestPropertySource;

import damso.support.FakeExternalAi;
import damso.support.IntegrationTestSupport;

/**
 * F4 T073 / FR-012b, SC-002: 외부 점검이 상한 안에 끝나지 않으면 기다리지 않고 available=false로 응답하고, 전송은 그대로 된다.
 * 실제 상한(8초)을 기다리지 않도록 이 테스트만 상한을 짧게 둔다.
 */
@TestPropertySource(properties = "damso.answer-review.timeout=300ms")
class AnswerReviewTimeoutTest extends IntegrationTestSupport {

	@Test
	void slowReviewIsCutAtTheLimitAndSendingStillWorks() throws Exception {
		ConfirmedInquiry confirmed = urgentInquiryConfirmedByTeacher();
		String token = confirmed.scenario().teacher().accessToken();
		fakeAi.replyAfter(Duration.ofSeconds(5), FakeExternalAi.reviewJson());

		long start = System.nanoTime();
		MockHttpServletResponse review = send(json(post("/api/v1/teacher/inquiries/" + confirmed.inquiryId()
				+ "/answer-reviews"), "{\"content\":\"곧 확인해 볼게요.\"}"), token);
		long elapsedMillis = Duration.ofNanos(System.nanoTime() - start).toMillis();

		assertThat(review.getStatus()).isEqualTo(200);
		assertThat((Boolean) read(review, "$.available")).isFalse();
		assertThat((Boolean) read(review, "$.contextUsed")).isFalse();
		assertThat(elapsedMillis).as("상한(300ms)에서 끊고 외부 응답(5초)을 기다리지 않는다").isLessThan(3_000);

		UUID inquiryId = confirmed.inquiryId();
		MockHttpServletResponse sent = send(json(post("/api/v1/teacher/inquiries/" + inquiryId + "/answers"),
				"{\"content\":\"곧 확인해 볼게요.\",\"reviewed\":true,\"lastSeenFollowUpId\":" + lastSeenFollowUpJson(inquiryId)
						+ "}"), token);
		assertThat(sent.getStatus()).isEqualTo(201);
	}

}
