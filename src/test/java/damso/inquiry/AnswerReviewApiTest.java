package damso.inquiry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.mock.web.MockHttpServletResponse;

import damso.account.domain.Role;
import damso.inquiry.application.port.out.AnswerReviewPort;
import damso.inquiry.domain.AnswerReviewResult;
import damso.inquiry.domain.AnswerReviewResult.Highlight;
import damso.support.IntegrationTestSupport;

/**
 * 답변 점검은 F3 전까지 항상 실패를 반환하는
 * 스텁이고, 결과를 저장하지 않으며, 실제 어댑터가 들어왔을 때 가명화된 맥락만 받는다.
 */
class AnswerReviewApiTest extends IntegrationTestSupport {

	/**
	 * AnswerReviewPort를 기본은 스텁과 같은 결과(빈 값)를 돌려주되, 테스트가 nextResult를 바꾸면 그 결과를
	 * 돌려주는 가짜로 바꾼다. 전달된 인자도 기록한다.
	 */
	@TestConfiguration
	static class FakeReviewConfig {

		static volatile Optional<AnswerReviewResult> nextResult = Optional.empty();

		static final AtomicReference<String> capturedPseudonymizedContext = new AtomicReference<>();

		@Bean
		@Primary
		AnswerReviewPort fakeAnswerReviewPort() {
			return (draftContent, pseudonymizedContext) -> {
				capturedPseudonymizedContext.set(pseudonymizedContext);
				return nextResult;
			};
		}

	}

	@BeforeEach
	void resetFakeReview() {
		FakeReviewConfig.nextResult = Optional.empty();
		FakeReviewConfig.capturedPseudonymizedContext.set(null);
	}

	private MockHttpServletResponse requestReview(String token, UUID inquiryId, String content) throws Exception {
		String body = "{\"content\":\"" + content + "\"}";
		return send(json(post("/api/v1/teacher/inquiries/" + inquiryId + "/answer-reviews"), body), token);
	}

	/** 점검 요청 감사 기록의 (actor_id, target_id) 목록. */
	private List<String> reviewRecords() {
		return jdbc.queryForList("select actor_id || ':' || target_id from audit_log "
				+ "where action = 'ANSWER_REVIEW_REQUESTED'", String.class);
	}

	@Test
	void reviewAlwaysReturnsUnavailableBeforeF3() throws Exception {
		ConfirmedInquiry confirmed = urgentInquiryConfirmedByTeacher();

		MockHttpServletResponse response = requestReview(confirmed.scenario().teacher().accessToken(),
				confirmed.inquiryId(), "곧 다시 확인해서 답변드릴게요.");

		assertThat(response.getStatus()).isEqualTo(200);
		assertThat((Boolean) read(response, "$.available")).isFalse();
		List<Object> highlights = read(response, "$.highlights");
		assertThat(highlights).isEmpty();
		// 점검 요청마다 누가 어느 스레드에 요청했는지 남는다
		assertThat(reviewRecords())
				.containsExactly(confirmed.scenario().teacher().userId() + ":" + confirmed.inquiryId());
	}

	@Test
	void blankOrTooLongDraftIsRejected() throws Exception {
		ConfirmedInquiry confirmed = urgentInquiryConfirmedByTeacher();
		String teacherToken = confirmed.scenario().teacher().accessToken();

		assertThat(requestReview(teacherToken, confirmed.inquiryId(), "   ").getStatus()).isEqualTo(400);
		assertThat(requestReview(teacherToken, confirmed.inquiryId(), "가".repeat(2001)).getStatus()).isEqualTo(400);
		assertThat(reviewRecords()).isEmpty();
	}

	@Test
	void teacherFromAnotherClassroomCannotReview() throws Exception {
		ConfirmedInquiry confirmed = urgentInquiryConfirmedByTeacher();
		Scenario otherClassroom = teacherWithClassroom(2, 1);

		MockHttpServletResponse response = requestReview(otherClassroom.teacher().accessToken(),
				confirmed.inquiryId(), "안녕하세요.");

		assertThat(response.getStatus()).isEqualTo(404);
		assertThat(reviewRecords()).isEmpty();
	}

	@Test
	void parentAndAdminCannotReview() throws Exception {
		ConfirmedInquiry confirmed = urgentInquiryConfirmedByTeacher();

		assertThat(requestReview(confirmed.scenario().parent().accessToken(), confirmed.inquiryId(), "답변").getStatus())
				.isEqualTo(403);
		assertThat(requestReview(tokenFor(createAccount(Role.SUPER_ADMIN)), confirmed.inquiryId(), "답변").getStatus())
				.isEqualTo(403);
		assertThat(requestReview(null, confirmed.inquiryId(), "답변").getStatus()).isEqualTo(401);
	}

	@Test
	void reviewResultIsNeverPersisted() {
		List<String> tables = jdbc.queryForList(
				"select tablename from pg_tables where schemaname = 'public' and tablename ilike '%review%'",
				String.class);
		assertThat(tables).isEmpty();
	}

	@Test
	void whenRealGatewayArrivesItOnlyReceivesPseudonymizedContextNeverRawContent() throws Exception {
		ConfirmedInquiry confirmed = urgentInquiryConfirmedByTeacher();
		FakeReviewConfig.nextResult = Optional
				.of(new AnswerReviewResult(true, List.of(new Highlight("확인해보겠습니다", "약속처럼 들릴 수 있음", "검토 후 다시 안내드릴게요"))));

		MockHttpServletResponse response = requestReview(confirmed.scenario().teacher().accessToken(),
				confirmed.inquiryId(), "다시 확인해볼게요.");

		assertThat(response.getStatus()).isEqualTo(200);
		assertThat((Boolean) read(response, "$.available")).isTrue();
		List<Map<String, String>> highlights = read(response, "$.highlights");
		assertThat(highlights).hasSize(1);
		assertThat(highlights.get(0).get("suggestion")).isEqualTo("검토 후 다시 안내드릴게요");
		// F3 전까지 호출하는 쪽은 pseudonymizedContext에 항상 null을 넘긴다 — 가명화 전 원문이 아니다.
		assertThat(FakeReviewConfig.capturedPseudonymizedContext.get()).isNull();
	}

}
