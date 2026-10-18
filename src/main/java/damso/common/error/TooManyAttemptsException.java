package damso.common.error;

/**
 * 반복 실패로 잠겼거나 대기 중일 때 던진다. 429 응답에 Retry-After(초)가 붙는다.
 * 대상 이메일·계정 값은 담지 않는다 — 메시지는 에러 코드 이름만 쓴다.
 */
public class TooManyAttemptsException extends BusinessException {

	static final long MIN_SECONDS = 1;

	static final long MAX_SECONDS = 600;

	private final long retryAfterSeconds;

	public TooManyAttemptsException(long retryAfterSeconds) {
		super(ErrorCode.TOO_MANY_ATTEMPTS);
		this.retryAfterSeconds = Math.max(MIN_SECONDS, Math.min(MAX_SECONDS, retryAfterSeconds));
	}

	public long retryAfterSeconds() {
		return retryAfterSeconds;
	}

}
