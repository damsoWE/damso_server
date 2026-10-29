package damso.gateway.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import damso.gateway.application.port.in.ReviewAnswerGateway.Context;
import damso.gateway.application.port.in.ReviewAnswerGateway.Finding;
import damso.gateway.application.port.in.ReviewAnswerGateway.ReviewOutcome;
import damso.gateway.application.port.out.ExternalAiPort.AiTask;
import damso.gateway.domain.GatewayFailure;
import damso.gateway.domain.Roster;
import damso.support.FakeExternalAi;

/**
 * F4 T070 / 원칙 II, FR-008a·008b·008c·012a, research R16·R17: 답변 점검 입구는 초안과 맥락을 한 사전으로 한 번에 가리고,
 * 외부 AI 결과를 검증한 뒤에만 복원한다. 지적 하나라도 어긋나면 결과 전체를 버린다.
 */
class ReviewAnswerGatewayServiceTest {

	private static final ObjectMapper JSON = new ObjectMapper();

	private static final Roster ROSTER = Roster.of(List.of("김민준"), List.of("박지영"), List.of("최유진"), 3, 2);

	/** 가리면 "[STUDENT_1]이는 원래 그래요. [TEACHER_1] 드림, [PHONE_1]"이 된다. */
	private static final String DRAFT = "민준이는 원래 그래요. 최유진 드림, 010-2222-3333";

	private static final Context CONTEXT = new Context("민준이 숙제를 자주 빠뜨린다고 박지영 님이 전했다.", "숙제 확인 방법 안내",
			"MEDIUM", List.of("WORRY"));

	private FakeExternalAi ai;

	private ReviewAnswerGatewayService gateway;

	@BeforeEach
	void setUp() {
		ai = new FakeExternalAi();
		gateway = new ReviewAnswerGatewayService(
				new MaskedAiCall(ai, task -> "고정 지시", () -> List.of("한양초등학교", "한양초")));
	}

	private JsonNode sent() {
		assertThat(ai.callsOf(AiTask.REVIEW_ANSWER)).hasSize(1);
		return JSON.readTree(ai.callsOf(AiTask.REVIEW_ANSWER).get(0).userText());
	}

	private static String[] highlight(String excerpt, String reason, String suggestion) {
		return new String[] { excerpt, reason, suggestion };
	}

	@Test
	void draftAndContextAreMaskedWithOneDictionaryBeforeCalling() {
		gateway.review(DRAFT, CONTEXT, ROSTER);

		String userText = ai.callsOf(AiTask.REVIEW_ANSWER).get(0).userText();
		assertThat(userText).doesNotContain("민준").doesNotContain("최유진").doesNotContain("박지영")
				.doesNotContain("010-2222-3333");
		JsonNode sent = sent();
		assertThat(sent.get("answer").asString()).isEqualTo("[STUDENT_1]이는 원래 그래요. [TEACHER_1] 드림, [PHONE_1]");
		// 같은 이름은 초안과 맥락에서 같은 토큰이다 — 외부 AI가 같은 사람으로 읽는다
		assertThat(sent.get("inquiry").get("facts").asString()).isEqualTo("[STUDENT_1]이 숙제를 자주 빠뜨린다고 [GUARDIAN_1] 님이 전했다.");
		assertThat(sent.get("inquiry").get("requests").asString()).isEqualTo("숙제 확인 방법 안내");
		assertThat(sent.get("inquiry").get("emotionLevel").asString()).isEqualTo("MEDIUM");
		// 시스템 지시와 본문은 따로 간다
		assertThat(ai.callsOf(AiTask.REVIEW_ANSWER).get(0).systemPrompt()).isEqualTo("고정 지시");
	}

	@Test
	void withoutContextOnlyTheAnswerLeaves() {
		gateway.review(DRAFT, null, ROSTER);

		JsonNode sent = sent();
		assertThat(sent.has("inquiry")).isFalse();
		assertThat(sent.get("answer").asString()).isEqualTo("[STUDENT_1]이는 원래 그래요. [TEACHER_1] 드림, [PHONE_1]");
	}

	@Test
	void validFindingsAreRestoredFromTheMap() {
		ai.replyNext(FakeExternalAi.reviewJson(highlight("[STUDENT_1]이는 원래 그래요", "[GUARDIAN_1] 님의 걱정을 가볍게 여기는 단정입니다.",
				"[STUDENT_1]이가 숙제를 챙길 수 있게 함께 살펴보겠습니다.")));

		ReviewOutcome outcome = gateway.review(DRAFT, CONTEXT, ROSTER);

		assertThat(outcome.succeeded()).isTrue();
		assertThat(outcome.findings()).containsExactly(new Finding("민준이는 원래 그래요", "박지영 님의 걱정을 가볍게 여기는 단정입니다.",
				"민준이가 숙제를 챙길 수 있게 함께 살펴보겠습니다."));
		assertThat(outcome.toString()).doesNotContain("민준").doesNotContain("단정");
	}

	@Test
	void noFindingsIsASuccessWithAnEmptyList() {
		ai.replyNext(FakeExternalAi.reviewJson());

		ReviewOutcome outcome = gateway.review(DRAFT, CONTEXT, ROSTER);

		assertThat(outcome.succeeded()).isTrue();
		assertThat(outcome.findings()).isEmpty();
	}

	@Test
	void anyInvalidFindingDiscardsTheWholeResult() {
		String ok = "{\"excerpt\":\"원래 그래요\",\"reason\":\"이유\",\"suggestion\":\"제안\"}";
		List<String> invalid = List.of(
				"이건 JSON이 아니다",
				"[]",
				"{\"highlights\":[" + ok + "],\"extra\":1}",
				"{\"highlights\":{}}",
				"{\"highlights\":[" + ok + ",{\"excerpt\":\"원래\",\"reason\":\"이유\"}]}",
				"{\"highlights\":[{\"excerpt\":\"원래\",\"reason\":\"이유\",\"suggestion\":\"제안\",\"category\":\"X\"}]}",
				"{\"highlights\":[{\"excerpt\":1,\"reason\":\"이유\",\"suggestion\":\"제안\"}]}",
				"{\"highlights\":[{\"excerpt\":\"원래\",\"reason\":\"   \",\"suggestion\":\"제안\"}]}",
				FakeExternalAi.reviewJson(highlight("원래", "이유", "제".repeat(301))),
				FakeExternalAi.reviewJson(highlight("원래", "이".repeat(301), "제안")),
				FakeExternalAi.reviewJson(highlight("원래", "이유", "제안"), highlight("그래요", "이유", "제안"),
						highlight("드림", "이유", "제안"), highlight("원래", "이유", "제안"), highlight("그래요", "이유", "제안"),
						highlight("드림", "이유", "제안")),
				// 대응표에 없는 토큰
				FakeExternalAi.reviewJson(highlight("원래 그래요", "[STUDENT_9]에게 상처가 됩니다.", "제안")),
				// 초안에 없는 표현(지어낸 문장)
				FakeExternalAi.reviewJson(highlight("원래 그래요", "이유", "제안"), highlight("절대 안 됩니다", "이유", "제안")),
				// 맥락에만 있는 표현
				FakeExternalAi.reviewJson(highlight("숙제를 자주 빠뜨린다고", "이유", "제안")));
		for (String reply : invalid) {
			ai.replyNext(reply);
			ReviewOutcome outcome = gateway.review(DRAFT, CONTEXT, ROSTER);

			assertThat(outcome.failure()).as(reply).isEqualTo(GatewayFailure.INVALID_OUTPUT);
			assertThat(outcome.findings()).isEmpty();
		}
	}

	@Test
	void excerptLongerThanTheLimitIsInvalid() {
		String longDraft = "가".repeat(201);
		ai.replyNext(FakeExternalAi.reviewJson(highlight(longDraft, "이유", "제안")));

		assertThat(gateway.review(longDraft, null, ROSTER).failure()).isEqualTo(GatewayFailure.INVALID_OUTPUT);
	}

	@Test
	void externalFailuresBecomeCodesAndMaskingErrorsNeverCall() {
		ai.noResponseNext();
		ai.throwNext();

		assertThat(gateway.review(DRAFT, CONTEXT, ROSTER).failure()).isEqualTo(GatewayFailure.NO_RESPONSE);
		assertThat(gateway.review(DRAFT, CONTEXT, ROSTER).failure()).isEqualTo(GatewayFailure.NO_RESPONSE);
		ai.clear();
		assertThat(gateway.review(null, CONTEXT, ROSTER).failure()).isEqualTo(GatewayFailure.MASKING_FAILED);
		assertThat(ai.calls()).isEmpty();
	}

	@Test
	void separatorCharactersInTheInputCannotShiftSegments() {
		String draft = "민준이는원래 그래요.";
		ai.replyNext(FakeExternalAi.reviewJson());

		ReviewOutcome outcome = gateway.review(draft, CONTEXT, ROSTER);

		assertThat(outcome.succeeded()).isTrue();
		JsonNode sent = sent();
		assertThat(sent.get("answer").asString()).isEqualTo("[STUDENT_1]이는 원래 그래요.");
		assertThat(sent.get("inquiry").get("facts").asString()).startsWith("[STUDENT_1]이 숙제를");
	}

}
