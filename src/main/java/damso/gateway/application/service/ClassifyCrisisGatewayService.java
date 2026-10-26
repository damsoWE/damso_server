package damso.gateway.application.service;

import java.util.Set;

import org.springframework.stereotype.Service;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import damso.gateway.application.port.in.ClassifyCrisisGateway;
import damso.gateway.application.port.out.ExternalAiPort.AiTask;
import damso.gateway.domain.GatewayFailure;
import damso.gateway.domain.Roster;

/**
 * 가명화 게이트웨이 — 긴급 문의 위기 종류 분류 (F3 research R11). 가명화·자기 점검·외부 호출은 공통 흐름({@link MaskedAiCall})이 하고,
 * 여기서는 결과가 crisisType 하나뿐이고 허용값(SELF_HARM·ABUSE·OTHER_SAFETY)인지만 본다. 아니면 버린다.
 */
@Service
public class ClassifyCrisisGatewayService implements ClassifyCrisisGateway {

	private static final ObjectMapper JSON = new ObjectMapper();

	private static final Set<String> ALLOWED = Set.of("SELF_HARM", "ABUSE", "OTHER_SAFETY");

	private final MaskedAiCall pipeline;

	ClassifyCrisisGatewayService(MaskedAiCall pipeline) {
		this.pipeline = pipeline;
	}

	@Override
	public CrisisOutcome classify(String content, Roster roster) {
		MaskedAiCall.Outcome called = pipeline.call(AiTask.CLASSIFY_CRISIS, content, roster);
		if (!called.succeeded()) {
			return CrisisOutcome.failed(called.failure());
		}
		String type = parse(called.reply().json());
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
