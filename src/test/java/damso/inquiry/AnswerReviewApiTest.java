package damso.inquiry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import damso.account.domain.Role;
import damso.gateway.application.port.out.ExternalAiPort.AiTask;
import damso.support.FakeExternalAi;
import damso.support.IntegrationTestSupport;

/**
 * F4 US2 시나리오 1·3~9 / FR-008·008a·008b·008c·009·011·013, SC-002·010: 답변 점검은 실제 어댑터가 가명화 게이트웨이를 거쳐
 * 가짜 외부 AI로 점검한다. 맥락은 답하려는 문의 하나의 정리 결과뿐이고, 외부로 나간 글에는 이름·원문이 없다. 결과는 저장하지 않는다.
 */
class AnswerReviewApiTest extends IntegrationTestSupport {

	private static final ObjectMapper JSON = new ObjectMapper();

	/** generalInquiryNaming의 원문에만 있는 문장. 정리 결과(facts)에는 다른 문장을 쓴다. */
	private static final String ORIGINAL_ONLY = "숙제를 자꾸 잊어버려서";

	/** 루트 문의의 정리 결과 사실관계(가린 형태). [STUDENT_1]은 원문의 "김하나"다. */
	private static final String ROOT_FACTS = "[STUDENT_1]가 숙제를 자주 빠뜨린다고 전했다.";

	private static final String DRAFT = "최선생입니다. 하나 숙제는 제가 매일 확인하고 있으니 걱정하지 않으셔도 됩니다.";

	private MockHttpServletResponse requestReview(String token, UUID inquiryId, String content) throws Exception {
		String body = JSON.writeValueAsString(Map.of("content", content));
		return send(json(post("/api/v1/teacher/inquiries/" + inquiryId + "/answer-reviews"), body), token);
	}

	private MockHttpServletResponse sendAnswer(String token, UUID inquiryId, String content) throws Exception {
		String body = "{\"content\":" + JSON.writeValueAsString(content) + ",\"reviewed\":true,\"lastSeenFollowUpId\":"
				+ lastSeenFollowUpJson(inquiryId) + "}";
		return send(json(post("/api/v1/teacher/inquiries/" + inquiryId + "/answers"), body), token);
	}

	/** F5 T010: 점검 요청 감사 기록의 (actor_id, target_id) 목록. */
	private List<String> reviewRecords() {
		return jdbc.queryForList("select actor_id || ':' || target_id from audit_log "
				+ "where action = 'ANSWER_REVIEW_REQUESTED'", String.class);
	}

	/** 일반 문의를 접수하고 가짜 외부 AI가 정해 둔 사실관계로 정리를 끝낸다. */
	private UUID refinedGeneralInquiry(Scenario s, String maskedFacts) throws Exception {
		fakeAi.replyNext(FakeExternalAi.refinementJson("숙제 문의", maskedFacts, "숙제 확인 방법 안내", "MEDIUM",
				List.of("WORRY")));
		UUID id = UUID.fromString(read(submitInquiry(s.parent().accessToken(), s.student().getId(),
				generalInquiryNaming(s)), "$.inquiryId"));
		runRefinement();
		assertThat(jdbc.queryForObject("select count(*) from inquiry_refinement where inquiry_id = ?", Integer.class,
				id)).isEqualTo(1);
		return id;
	}

	private String reviewCallText() {
		List<FakeExternalAi.Call> calls = fakeAi.callsOf(AiTask.REVIEW_ANSWER);
		assertThat(calls).hasSize(1);
		return calls.get(0).userText();
	}

	@Test
	void reviewReturnsHighlightsReasonsAndSuggestionsWithContext() throws Exception {
		Scenario s = teacherWithClassroom();
		UUID inquiryId = refinedGeneralInquiry(s, ROOT_FACTS);
		fakeAi.replyNext(FakeExternalAi.reviewJson(new String[] { "걱정하지 않으셔도 됩니다",
				"숙제를 어떻게 확인하는지 근거 없이 단정합니다.", "알림장으로 매일 숙제를 확인해 [STUDENT_1] 어머님께 알려 드릴게요." }));

		MockHttpServletResponse response = requestReview(s.teacher().accessToken(), inquiryId, DRAFT);

		assertThat(response.getStatus()).isEqualTo(200);
		assertThat((Boolean) read(response, "$.available")).isTrue();
		assertThat((Boolean) read(response, "$.contextUsed")).isTrue();
		List<Map<String, String>> highlights = read(response, "$.highlights");
		assertThat(highlights).containsExactly(Map.of("excerpt", "걱정하지 않으셔도 됩니다", "reason",
				"숙제를 어떻게 확인하는지 근거 없이 단정합니다.", "suggestion", "알림장으로 매일 숙제를 확인해 하나 어머님께 알려 드릴게요."));
		assertThat(reviewRecords()).containsExactly(s.teacher().userId() + ":" + inquiryId);
	}

	@Test
	void onlyPseudonymizedDraftAndRefinementLeaveForTheExternalAi() throws Exception {
		Scenario s = teacherWithClassroom();
		UUID inquiryId = refinedGeneralInquiry(s, ROOT_FACTS);
		fakeAi.replyNext(FakeExternalAi.reviewJson());

		requestReview(s.teacher().accessToken(), inquiryId, DRAFT + " 010-9876-5432로 전화 주세요.");

		String sent = reviewCallText();
		assertThat(sent).doesNotContain(STUDENT_NAME).doesNotContain("하나").doesNotContain(TEACHER_NAME)
				.doesNotContain(PARENT_NAME).doesNotContain("한양초").doesNotContain("1학년").doesNotContain("1반")
				.doesNotContain("010-9876-5432").doesNotContain("010-1234-5678");
		// 가명화 전 원문(학부모가 쓴 글)은 쓰지 않는다 — 맥락은 정리 결과뿐이다
		assertThat(sent).doesNotContain(ORIGINAL_ONLY);
		JsonNode payload = JSON.readTree(sent);
		assertThat(payload.get("inquiry").get("facts").asString()).contains("숙제를 자주 빠뜨린다고 전했다");
		assertThat(payload.get("answer").asString()).contains("[STUDENT_").contains("[TEACHER_").contains("[PHONE_1]");
	}

	@Test
	void urgentInquiryWithoutRefinementIsReviewedWithoutContext() throws Exception {
		ConfirmedInquiry confirmed = urgentInquiryConfirmedByTeacher();
		fakeAi.replyNext(FakeExternalAi.reviewJson(new String[] { "괜찮을 거예요", "상황을 가볍게 여기는 표현입니다.",
				"바로 확인하고 연락드리겠습니다." }));

		MockHttpServletResponse response = requestReview(confirmed.scenario().teacher().accessToken(),
				confirmed.inquiryId(), "괜찮을 거예요. 걱정 마세요.");

		assertThat(response.getStatus()).isEqualTo(200);
		assertThat((Boolean) read(response, "$.available")).isTrue();
		assertThat((Boolean) read(response, "$.contextUsed")).isFalse();
		assertThat((List<?>) read(response, "$.highlights")).hasSize(1);
		JsonNode payload = JSON.readTree(reviewCallText());
		assertThat(payload.has("inquiry")).isFalse();
		assertThat(reviewCallText()).doesNotContain(URGENT_SAMPLE);
	}

	@Test
	void inquiryWhoseRefinementFailedIsReviewedWithoutContext() throws Exception {
		Scenario s = teacherWithClassroom();
		fakeAi.noResponseNext();
		fakeAi.noResponseNext();
		UUID inquiryId = UUID.fromString(read(submitInquiry(s.parent().accessToken(), s.student().getId(),
				generalInquiryNaming(s)), "$.inquiryId"));
		runRefinement();
		fakeAi.replyNext(FakeExternalAi.reviewJson());

		MockHttpServletResponse response = requestReview(s.teacher().accessToken(), inquiryId, DRAFT);

		assertThat((Boolean) read(response, "$.available")).isTrue();
		assertThat((Boolean) read(response, "$.contextUsed")).isFalse();
		assertThat(JSON.readTree(reviewCallText()).has("inquiry")).isFalse();
		assertThat(reviewCallText()).doesNotContain(ORIGINAL_ONLY);
	}

	@Test
	void contextIsTheLatestFollowUpOnlyWithoutEarlierAnswersOrTheRoot() throws Exception {
		Scenario s = teacherWithClassroom();
		UUID rootId = refinedGeneralInquiry(s, ROOT_FACTS);
		String earlierAnswer = "지난번 답변 문장입니다";
		assertThat(sendAnswer(s.teacher().accessToken(), rootId, earlierAnswer).getStatus()).isEqualTo(201);
		clock.advance(Duration.ofSeconds(61));
		fakeAi.replyNext(FakeExternalAi.refinementJson("준비물 문의", "[STUDENT_1]가 준비물을 챙기지 못했다고 전했다.", "",
				"LOW", List.of()));
		assertThat(submitFollowUp(s.parent().accessToken(), rootId, STUDENT_NAME + " 준비물을 못 챙겼어요. 확인 부탁드려요.")
				.getStatus()).isEqualTo(201);
		runRefinement();
		fakeAi.replyNext(FakeExternalAi.reviewJson());

		MockHttpServletResponse response = requestReview(s.teacher().accessToken(), rootId, "준비물은 제가 챙겨 둘게요.");

		assertThat((Boolean) read(response, "$.contextUsed")).isTrue();
		String sent = reviewCallText();
		assertThat(JSON.readTree(sent).get("inquiry").get("facts").asString()).contains("준비물을 챙기지 못했다고 전했다");
		assertThat(sent).doesNotContain("숙제를 자주 빠뜨린다고").doesNotContain(earlierAnswer)
				.doesNotContain("준비물을 못 챙겼어요");
	}

	@Test
	void blankOrTooLongDraftIsRejected() throws Exception {
		ConfirmedInquiry confirmed = urgentInquiryConfirmedByTeacher();
		String teacherToken = confirmed.scenario().teacher().accessToken();

		assertThat(requestReview(teacherToken, confirmed.inquiryId(), "   ").getStatus()).isEqualTo(400);
		assertThat(requestReview(teacherToken, confirmed.inquiryId(), "가".repeat(2001)).getStatus()).isEqualTo(400);
		assertThat(reviewRecords()).isEmpty();
		assertThat(fakeAi.callsOf(AiTask.REVIEW_ANSWER)).isEmpty();
	}

	@Test
	void teacherFromAnotherClassroomGetsNotFoundAndNothingLeaves() throws Exception {
		Scenario s = teacherWithClassroom();
		UUID inquiryId = refinedGeneralInquiry(s, ROOT_FACTS);
		Scenario otherClassroom = teacherWithClassroom(2, 1);

		MockHttpServletResponse response = requestReview(otherClassroom.teacher().accessToken(), inquiryId, DRAFT);

		assertThat(response.getStatus()).isEqualTo(404);
		assertThat(reviewRecords()).isEmpty();
		assertThat(fakeAi.callsOf(AiTask.REVIEW_ANSWER)).isEmpty();
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

}
