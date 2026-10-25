package damso.gateway.application.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import damso.gateway.application.port.in.RefineInquiryGateway;
import damso.gateway.application.port.out.ExternalAiPort;
import damso.gateway.application.port.out.ExternalAiPort.AiReply;
import damso.gateway.application.port.out.ExternalAiPort.AiTask;
import damso.gateway.application.port.out.PromptSource;
import damso.gateway.application.port.out.SchoolTermsSource;
import damso.gateway.domain.EmotionLabel;
import damso.gateway.domain.GatewayFailure;
import damso.gateway.domain.MaskingVerifier;
import damso.gateway.domain.PseudonymMap;
import damso.gateway.domain.Pseudonymizer;
import damso.gateway.domain.Pseudonymizer.Masked;
import damso.gateway.domain.RefinementResult;
import damso.gateway.domain.RefinementResult.Valid;
import damso.gateway.domain.Roster;

/**
 * 가명화 게이트웨이 — 일반 문의 정리 (원칙 II, research R1·R4·R6).
 * 순서: 학교 용어 합치기 → 가명화 → 가린 뒤 자기 점검(못 가렸으면 외부 호출 없음) → 외부 AI → 형식·허용값 재검증 → 복원.
 * 대응표(PseudonymMap)는 이 메서드의 지역 변수로만 있고 필드·캐시·로그에 남기지 않는다. 로그에는 실패 코드만 남긴다.
 */
@Service
public class RefineInquiryGatewayService implements RefineInquiryGateway {

	private static final Logger log = LoggerFactory.getLogger(RefineInquiryGatewayService.class);

	private static final ObjectMapper JSON = new ObjectMapper();

	private static final Set<String> FIELDS = Set.of("title", "facts", "requests", "emotionLevel", "emotionLabels");

	private final ExternalAiPort ai;

	private final PromptSource prompts;

	private final SchoolTermsSource schoolTerms;

	public RefineInquiryGatewayService(ExternalAiPort ai, PromptSource prompts, SchoolTermsSource schoolTerms) {
		this.ai = ai;
		this.prompts = prompts;
		this.schoolTerms = schoolTerms;
	}

	@Override
	public RefineOutcome refine(String content, Roster roster) {
		Roster full = roster.withSchoolTerms(schoolTerms.terms());
		Masked masked;
		try {
			masked = Pseudonymizer.mask(content, full);
		}
		catch (RuntimeException ex) {
			log.warn("가명화 실패 (type={})", ex.getClass().getSimpleName());
			return RefineOutcome.failed(GatewayFailure.MASKING_FAILED);
		}
		if (MaskingVerifier.verify(masked.text(), full).isPresent()) {
			return RefineOutcome.failed(GatewayFailure.MASKING_FAILED);
		}
		AiReply reply;
		try {
			reply = ai.call(AiTask.REFINE, prompts.systemPrompt(AiTask.REFINE), masked.text());
		}
		catch (RuntimeException ex) {
			log.warn("외부 AI 호출 오류 (type={})", ex.getClass().getSimpleName());
			return RefineOutcome.failed(GatewayFailure.NO_RESPONSE);
		}
		if (!reply.responded()) {
			return RefineOutcome.failed(GatewayFailure.NO_RESPONSE);
		}
		return parse(reply.json(), masked.map())
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
