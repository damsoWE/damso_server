package damso.gateway.application.port.out;

import damso.gateway.application.port.out.ExternalAiPort.AiTask;

/** 작업별 고정 시스템 지시(research R5). 서버 리소스에 있고 PR 리뷰로만 바뀐다. 본문을 이어 붙이지 않는다. */
public interface PromptSource {

	String systemPrompt(AiTask task);

}
