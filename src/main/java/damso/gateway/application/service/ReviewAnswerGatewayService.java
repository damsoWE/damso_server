package damso.gateway.application.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Service;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import damso.gateway.application.port.in.ReviewAnswerGateway;
import damso.gateway.application.port.out.ExternalAiPort.AiTask;
import damso.gateway.domain.AnswerReviewFindings;
import damso.gateway.domain.AnswerReviewFindings.Masked;
import damso.gateway.domain.GatewayFailure;
import damso.gateway.domain.Roster;

/**
 * 가명화 게이트웨이 — 교사 답변 점검. 답변 초안과 맥락(정리 결과)을 공통 흐름({@link MaskedAiCall})으로 한 번에
 * 가리고, 가린 글들을 JSON({"inquiry": {...}, "answer": ...})으로 묶어 보낸다. 맥락이 없으면 inquiry를 넣지 않는다.
 * 응답은 형식을 다시 확인하고 {@link AnswerReviewFindings}로 검증·복원한다. 하나라도 어긋나면 전체를 버린다.
 * 대응표는 이 메서드 안에서만 쓰고 버린다.
 */
@Service
public class ReviewAnswerGatewayService implements ReviewAnswerGateway {

	private static final ObjectMapper JSON = new ObjectMapper();

	private static final Set<String> HIGHLIGHT_FIELDS = Set.of("excerpt", "reason", "suggestion");

	private final MaskedAiCall pipeline;

	ReviewAnswerGatewayService(MaskedAiCall pipeline) {
		this.pipeline = pipeline;
	}

	@Override
	public ReviewOutcome review(String draft, Context context, Roster roster) {
		if (draft == null) {
			return ReviewOutcome.failed(GatewayFailure.MASKING_FAILED);
		}
		List<String> segments = context == null ? List.of(draft)
				: List.of(draft, nullToEmpty(context.facts()), nullToEmpty(context.requests()));
		MaskedAiCall.Outcome called = pipeline.call(AiTask.REVIEW_ANSWER, segments, roster,
				masked -> compose(masked, context));
		if (!called.succeeded()) {
			return ReviewOutcome.failed(called.failure());
		}
		return parse(called.reply().json())
				.flatMap(findings -> AnswerReviewFindings.validate(findings, called.maskedSegments().get(0), draft,
						called.map()))
				.map(valid -> ReviewOutcome.success(valid.stream()
						.map(finding -> new Finding(finding.excerpt(), finding.reason(), finding.suggestion()))
						.toList()))
				.orElse(ReviewOutcome.failed(GatewayFailure.INVALID_OUTPUT));
	}

	/** 가린 글만으로 외부에 보낼 JSON을 만든다. 정서강도·라벨은 허용값 코드라 가리지 않는다. */
	private static String compose(List<String> masked, Context context) {
		Map<String, Object> payload = new LinkedHashMap<>();
		if (context != null) {
			Map<String, Object> inquiry = new LinkedHashMap<>();
			inquiry.put("facts", masked.get(1));
			inquiry.put("requests", masked.get(2));
			inquiry.put("emotionLevel", nullToEmpty(context.emotionLevel()));
			inquiry.put("emotionLabels", context.emotionLabels());
			payload.put("inquiry", inquiry);
		}
		payload.put("answer", masked.get(0));
		return JSON.writeValueAsString(payload);
	}

	/** {"highlights": [{excerpt, reason, suggestion} 세 문자열만]}일 때만 꺼낸다. 어긋나면 비어 있다. */
	static Optional<List<Masked>> parse(String json) {
		JsonNode root;
		try {
			root = JSON.readTree(json);
		}
		catch (JacksonException ex) {
			return Optional.empty();
		}
		if (root == null || !root.isObject() || !Set.copyOf(root.propertyNames()).equals(Set.of("highlights"))) {
			return Optional.empty();
		}
		JsonNode highlights = root.get("highlights");
		if (!highlights.isArray()) {
			return Optional.empty();
		}
		List<Masked> findings = new ArrayList<>();
		for (JsonNode item : highlights) {
			if (!item.isObject() || !Set.copyOf(item.propertyNames()).equals(HIGHLIGHT_FIELDS)
					|| !item.get("excerpt").isString() || !item.get("reason").isString()
					|| !item.get("suggestion").isString()) {
				return Optional.empty();
			}
			findings.add(new Masked(item.get("excerpt").asString(), item.get("reason").asString(),
					item.get("suggestion").asString()));
		}
		return Optional.of(findings);
	}

	private static String nullToEmpty(String text) {
		return text == null ? "" : text;
	}

}
