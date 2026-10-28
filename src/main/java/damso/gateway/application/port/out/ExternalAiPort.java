package damso.gateway.application.port.out;

import damso.gateway.domain.MaskedText;

/**
 * 외부 AI 호출의 유일한 출력 포트. 이 포트는 gateway.application.service 안에서만 주입한다 —
 * 그래서 외부로 나가는 글은 반드시 가명화를 먼저 거친다(GatewayStructureTest가 검사한다).
 * 입력은 가린 본문뿐이고, 시스템 지시와 본문은 역할을 나눠 보낸다.
 */
public interface ExternalAiPort {

	/** 무엇을 시키는지. 작업마다 출력 형식(JSON 스키마)이 다르다. */
	enum AiTask {
		/** 일반 문의 3요소 정리: title·facts·requests·emotionLevel·emotionLabels */
		REFINE,
		/** 긴급 문의 위기 종류: crisisType */
		CLASSIFY_CRISIS
	}

	/**
	 * 외부 AI의 응답. 정해진 형식을 강제해 받은 JSON 문자열이거나, 시간 초과·오류로 답이 없음이다.
	 * JSON은 아직 검증 전이므로 로그에 남기지 않는다 — toString은 응답 여부만 보여 준다.
	 */
	record AiReply(String json) {

		public static AiReply noResponse() {
			return new AiReply(null);
		}

		public boolean responded() {
			return json != null;
		}

		@Override
		public String toString() {
			return "AiReply[responded=" + responded() + "]";
		}

	}

	/** 시간 초과·연결 실패·오류 응답은 예외 대신 {@link AiReply#noResponse()}로 돌려준다. */
	AiReply call(AiTask task, String systemPrompt, MaskedText userText);

}
