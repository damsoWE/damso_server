package damso.gateway.domain;

import java.util.Objects;

/** 식별정보를 토큰으로 가린 본문. 가렸어도 문의 내용이므로 로그에 남기지 않는다 — toString은 길이만 보여 준다. */
public final class MaskedText {

	private final String value;

	public MaskedText(String value) {
		this.value = Objects.requireNonNull(value);
	}

	/** 외부 AI에 보낼 때만 꺼낸다. */
	public String value() {
		return value;
	}

	@Override
	public String toString() {
		return "MaskedText[length=" + value.length() + "]";
	}

}
