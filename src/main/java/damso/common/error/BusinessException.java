package damso.common.error;

/**
 * 기능 코드가 공통 에러 코드로 실패를 알릴 때 던지는 예외.
 * 예외 메시지에 입력값·민감 값을 넣지 않는다 — 메시지는 에러 코드 이름만 쓴다.
 */
public class BusinessException extends RuntimeException {

	private final ErrorCode errorCode;

	public BusinessException(ErrorCode errorCode) {
		super(errorCode.name());
		this.errorCode = errorCode;
	}

	public ErrorCode errorCode() {
		return errorCode;
	}

}
