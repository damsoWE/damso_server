package damso.common.crypto;

import java.util.Objects;

/**
 * 민감 텍스트(문의·답변 본문, 정제 결과 등)를 담는 값 타입.
 * - 엔티티 필드 타입으로 쓰면 SensitiveTextConverter가 자동으로 AES-256-GCM 암호화해 저장한다.
 * - toString()은 항상 [MASKED] — 로그·예외 메시지에 원문이 섞이지 않는다 (constitution 원칙 VI).
 * - 원문은 reveal()로만 꺼낸다. 게터 이름이 아니므로 JSON으로 자동 직렬화되지 않는다.
 */
public final class SensitiveText {

	static final String MASK = "[MASKED]";

	private final String value;

	private SensitiveText(String value) {
		this.value = value;
	}

	public static SensitiveText of(String value) {
		return new SensitiveText(Objects.requireNonNull(value, "value"));
	}

	/** 원문. 응답 DTO로 옮길 때만 쓰고, 로그·예외 메시지에 넘기지 않는다. */
	public String reveal() {
		return value;
	}

	public boolean isEmpty() {
		return value.isEmpty();
	}

	@Override
	public boolean equals(Object o) {
		return this == o || (o instanceof SensitiveText other && value.equals(other.value));
	}

	@Override
	public int hashCode() {
		return value.hashCode();
	}

	@Override
	public String toString() {
		return MASK;
	}

}
