package damso.gateway.application.service;

import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import damso.gateway.application.port.in.ClassifyCrisisGateway;
import damso.gateway.application.port.out.ExternalAiPort;
import damso.gateway.application.port.out.ExternalAiPort.AiReply;
import damso.gateway.application.port.out.ExternalAiPort.AiTask;
import damso.gateway.application.port.out.PromptSource;
import damso.gateway.application.port.out.SchoolTermsSource;
import damso.gateway.domain.GatewayFailure;
import damso.gateway.domain.MaskingVerifier;
import damso.gateway.domain.Pseudonymizer;
import damso.gateway.domain.Pseudonymizer.Masked;
import damso.gateway.domain.Roster;

/**
 * 가명화 게이트웨이 — 긴급 문의 위기 종류 분류 (F3 research R11). 일반 정리와 같은 순서로 가린 글만 외부로 보낸다.
 * 결과는 crisisType 하나만 있어야 하고 허용값(SELF_HARM·ABUSE·OTHER_SAFETY)이 아니면 버린다.
 */
@Service
public class ClassifyCrisisGatewayService implements ClassifyCrisisGateway {

	private static final Logger log = LoggerFactory.getLogger(ClassifyCrisisGatewayService.class);

	private static final ObjectMapper JSON = new ObjectMapper();

	private static final Set<String> ALLOWED = Set.of("SELF_HARM", "ABUSE", "OTHER_SAFETY");

	private final ExternalAiPort ai;

	private final PromptSource prompts;

	private final SchoolTermsSource schoolTerms;

	public ClassifyCrisisGatewayService(ExternalAiPort ai, PromptSource prompts, SchoolTermsSource schoolTerms) {
		this.ai = ai;
		this.prompts = prompts;
		this.schoolTerms = schoolTerms;
	}

	@Override
	public CrisisOutcome classify(String content, Roster roster) {
		Roster full = roster.withSchoolTerms(schoolTerms.terms());
		Masked masked;
		try {
			masked = Pseudonymizer.mask(content, full);
		}
		catch (RuntimeException ex) {
			log.warn("가명화 실패 (type={})", ex.getClass().getSimpleName());
			return CrisisOutcome.failed(GatewayFailure.MASKING_FAILED);
		}
		if (MaskingVerifier.verify(masked.text(), full).isPresent()) {
			return CrisisOutcome.failed(GatewayFailure.MASKING_FAILED);
		}
		AiReply reply;
		try {
			reply = ai.call(AiTask.CLASSIFY_CRISIS, prompts.systemPrompt(AiTask.CLASSIFY_CRISIS), masked.text());
		}
		catch (RuntimeException ex) {
			log.warn("외부 AI 호출 오류 (type={})", ex.getClass().getSimpleName());
			return CrisisOutcome.failed(GatewayFailure.NO_RESPONSE);
		}
		if (!reply.responded()) {
			return CrisisOutcome.failed(GatewayFailure.NO_RESPONSE);
		}
		String type = parse(reply.json());
		return type == null ? CrisisOutcome.failed(GatewayFailure.INVALID_OUTPUT) : CrisisOutcome.success(type);
	}

	/** {"crisisType": 허용값} 하나만 있을 때 그 값, 아니면 null. */
	static String parse(String json) {
		JsonNode root;
		try {
			root = JSON.readTree(json);
		}
		catch (JacksonException ex) {
			return null;
		}
		if (root == null || !root.isObject() || !Set.copyOf(root.propertyNames()).equals(Set.of("crisisType"))) {
			return null;
		}
		JsonNode type = root.get("crisisType");
		return type.isString() && ALLOWED.contains(type.asString()) ? type.asString() : null;
	}

}
