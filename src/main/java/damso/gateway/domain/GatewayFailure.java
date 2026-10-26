package damso.gateway.domain;

/** 게이트웨이가 결과를 내지 못한 이유. 내용 없는 코드뿐이다 — 어떤 글도 담지 않는다. */
public enum GatewayFailure {

	/** 가린 뒤에도 원래 값이 남았거나 가명화 중 오류가 났다. 외부 AI를 호출하지 않았다 (FR-005). */
	MASKING_FAILED,
	/** 외부 AI가 시간 안에 답하지 않았거나 오류를 돌려줬다 (FR-013a·014). */
	NO_RESPONSE,
	/** 외부 AI의 결과가 형식·허용값 검증을 통과하지 못했다. 결과는 폐기했다 (FR-008). */
	INVALID_OUTPUT

}
