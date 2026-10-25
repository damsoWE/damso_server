package damso.gateway.application.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Service;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import damso.gateway.application.port.in.RefineInquiryGateway;
import damso.gateway.application.port.out.ExternalAiPort.AiTask;
import damso.gateway.domain.EmotionLabel;
import damso.gateway.domain.GatewayFailure;
import damso.gateway.domain.PseudonymMap;
import damso.gateway.domain.RefinementResult;
import damso.gateway.domain.RefinementResult.Valid;
import damso.gateway.domain.Roster;

/**
 * 가명화 게이트웨이 — 일반 문의 정리 (원칙 II, research R1·R6). 가명화·자기 점검·외부 호출은 공통 흐름({@link MaskedAiCall})이 하고,
 * 여기서는 응답의 형식·허용값을 다시 검증한 뒤 메모리에서 복원한다. 대응표는 이 메서드 안에서만 쓰고 버린다.
 */
@Service
public class RefineInquiryGatewayService implements RefineInquiryGateway {

	private static final ObjectMapper JSON = new ObjectMapper();

	private static final Set<String> FIELDS = Set.of("title", "facts", "requests", "emotionLevel", "emotionLabels");

	private final MaskedAiCall pipeline;

	RefineInquiryGatewayService(MaskedAiCall pipeline) {
		this.pipeline = pipeline;
	}

	@Override
	public RefineOutcome refine(String content, Roster roster) {
		MaskedAiCall.Outcome called = pipeline.call(AiTask.REFINE, content, roster);
		if (!called.succeeded()) {
			return RefineOutcome.failed(called.failure());
		}
		return parse(called.reply().json(), called.map())
				.map(valid -> RefineOutcome.success(new Refined(valid.title(), valid.facts(), valid.requests(),
						valid.level().name(), valid.labels().stream().map(EmotionLabel::name).toList())))
				.orElse(RefineOutcome.failed(GatewayFailure.INVALID_OUTPUT));
	}

	/** 정해진 다섯 필드만 있고 타입이 맞을 때만 값을 꺼내 검증한다. 어긋나면 비어 있다. */
	static Optional<Valid> parse(String json, PseudonymMap map) {
		JsonNode root;
		try {
			root = JSON.readTree(json);
		}
		catch (JacksonException ex) {
			return Optional.empty();
		}
		if (root == null || !root.isObject() || !Set.copyOf(root.propertyNames()).equals(FIELDS)) {
			return Optional.empty();
		}
		JsonNode labels = root.get("emotionLabels");
		if (!root.get("title").isString() || !root.get("facts").isString() || !root.get("requests").isString()
				|| !root.get("emotionLevel").isString() || !labels.isArray()) {
			return Optional.empty();
		}
		List<String> labelValues = new ArrayList<>();
		for (JsonNode label : labels) {
			if (!label.isString()) {
				return Optional.empty();
			}
			labelValues.add(label.asString());
		}
		return RefinementResult.validate(root.get("title").asString(), root.get("facts").asString(),
				root.get("requests").asString(), root.get("emotionLevel").asString(), labelValues, map);
	}

}
