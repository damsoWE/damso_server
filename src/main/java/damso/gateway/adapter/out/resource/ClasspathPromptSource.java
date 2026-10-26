package damso.gateway.adapter.out.resource;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import damso.gateway.application.port.out.ExternalAiPort.AiTask;
import damso.gateway.application.port.out.PromptSource;

/** 서버 리소스(gateway/prompts/*.txt)에서 고정 시스템 지시를 읽는다. 하나라도 비었거나 못 읽으면 기동을 거부한다 (원칙 VIII). */
@Component
class ClasspathPromptSource implements PromptSource {

	private final String refine;

	private final String classifyCrisis;

	ClasspathPromptSource(@Value("${damso.gateway.prompts.refine:classpath:gateway/prompts/refine.txt}") Resource refine,
			@Value("${damso.gateway.prompts.classify-crisis:classpath:gateway/prompts/classify-crisis.txt}")
			Resource classifyCrisis) {
		this.refine = ResourceLines.read(refine, "정리 지시 파일").strip();
		this.classifyCrisis = ResourceLines.read(classifyCrisis, "위기 분류 지시 파일").strip();
	}

	@Override
	public String systemPrompt(AiTask task) {
		return switch (task) {
			case REFINE -> refine;
			case CLASSIFY_CRISIS -> classifyCrisis;
		};
	}

}
