package damso.inquiry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.TestPropertySource;

import damso.inquiry.application.port.out.AnswerReviewPort;
import damso.inquiry.domain.AnswerReviewResult;
import damso.inquiry.domain.AnswerReviewResult.Highlight;
import damso.notification.application.service.NotificationDispatcher;
import damso.support.IntegrationTestSupport;

/**
 * 답변 전송·점검(성공·실패 둘 다)·재문의·종료·알림 발송 전체에서 답변 본문·점검
 * 하이라이트(이유·대체 표현 포함)·재문의 본문·FCM 토큰이 로그에 남지 않는다.
 * 운영에서는 종료 경로가 꺼져 있다. 종료 경로를 다시 열 때를 대비해 서버 검증을 계속 확인한다(PR #19 리뷰 P0).
 */
@TestPropertySource(properties = "damso.inquiry.close-enabled=true")
@ExtendWith(OutputCaptureExtension.class)
class AnswerSensitiveLoggingTest extends IntegrationTestSupport {

	private static final String ANSWER_BODY = "로그검사답변본문ZXQ" + "7401";

	private static final String REVIEW_DRAFT = "로그검사점검초안ZXQ" + "7402";

	private static final String HIGHLIGHT_EXCERPT = "로그검사하이라이트구절ZXQ" + "7403";

	private static final String HIGHLIGHT_REASON = "로그검사하이라이트이유ZXQ" + "7404";

	private static final String HIGHLIGHT_SUGGESTION = "로그검사하이라이트제안ZXQ" + "7405";

	private static final String FOLLOWUP_BODY = "로그검사재문의본문ZXQ" + "7406";

	private static final String FCM_TOKEN = "fcm-token-SECRET-" + "ZXQ7407";

	/** 하이라이트를 돌려주거나(성공) 예외를 던지는(실패) 가짜 점검 포트. 이 테스트에서만 쓴다. */
	@TestConfiguration
	static class ToggleableReviewConfig {

		static volatile boolean failing;

		@Bean
		@Primary
		AnswerReviewPort toggleableAnswerReviewPort() {
			return (draftContent, context, roster) -> {
				if (failing) {
					throw new IllegalStateException("점검 서비스 장애 흉내");
				}
				return Optional
						.of(new AnswerReviewResult(true, false,
								List.of(new Highlight(HIGHLIGHT_EXCERPT, HIGHLIGHT_REASON, HIGHLIGHT_SUGGESTION))));
			};
		}

	}

	@Autowired
	private NotificationDispatcher dispatcher;

	private MockHttpServletResponse requestReview(String token, UUID inquiryId, String content) throws Exception {
		return send(json(post("/api/v1/teacher/inquiries/" + inquiryId + "/answer-reviews"),
				"{\"content\":\"" + content + "\"}"), token);
	}

	private MockHttpServletResponse sendAnswer(String token, UUID inquiryId, String content) throws Exception {
		return send(json(post("/api/v1/teacher/inquiries/" + inquiryId + "/answers"),
				"{\"content\":\"" + content + "\",\"reviewed\":true,\"lastSeenFollowUpId\":" + lastSeenFollowUpJson(inquiryId) + "}"), token);
	}

	@Test
	void answerReviewFollowUpAndCloseLeaveNoSensitiveStringsInTheLogs(CapturedOutput output) throws Exception {
		List<String> secrets = new ArrayList<>(List.of(ANSWER_BODY, REVIEW_DRAFT, HIGHLIGHT_EXCERPT, HIGHLIGHT_REASON,
				HIGHLIGHT_SUGGESTION, FOLLOWUP_BODY, FCM_TOKEN));

		ConfirmedInquiry confirmed = urgentInquiryConfirmedByTeacher();
		String teacherToken = confirmed.scenario().teacher().accessToken();
		String parentToken = confirmed.scenario().parent().accessToken();
		UUID inquiryId = confirmed.inquiryId();
		registerDevice(confirmed.scenario().parent().userId(), sessionIdOf(parentToken), FCM_TOKEN);

		// 점검 성공(하이라이트 포함) · 점검 실패(예외) · 입력 검증 실패
		ToggleableReviewConfig.failing = false;
		requestReview(teacherToken, inquiryId, REVIEW_DRAFT);
		ToggleableReviewConfig.failing = true;
		requestReview(teacherToken, inquiryId, REVIEW_DRAFT + "2");
		requestReview(teacherToken, inquiryId, "   ");

		// 답변 전송(성공), 재문의(일반·실패 입력), 추가 답변
		sendAnswer(teacherToken, inquiryId, ANSWER_BODY);
		submitFollowUp(parentToken, inquiryId, FOLLOWUP_BODY);
		submitFollowUp(parentToken, inquiryId, "가".repeat(2001));
		sendAnswer(teacherToken, inquiryId, ANSWER_BODY + "2");

		// 알림 발송(ANSWER kind, FCM 토큰이 데이터로 나가는 경로)
		dispatcher.dispatchDue();

		// 종료, 종료 후 거부되는 경로(실패)
		send(post("/api/v1/teacher/inquiries/" + inquiryId + "/close"), teacherToken);
		sendAnswer(teacherToken, inquiryId, ANSWER_BODY + "3");
		submitFollowUp(parentToken, inquiryId, FOLLOWUP_BODY + "2");

		String logs = output.getAll();
		for (String secret : secrets) {
			assertThat(logs).as("secret of length %d", secret.length()).doesNotContain(secret);
		}
	}

}
