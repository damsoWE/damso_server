package damso.common.error;

/**
 * 서비스 계층에서 검증한 입력 오류(비밀번호 정책 등). 400 INVALID_INPUT + fieldErrors로 응답한다.
 * reason은 고정 문구여야 하며 사용자가 보낸 입력값을 담지 않는다 (FR-024).
 */
public class InvalidInputException extends BusinessException {

	private final String field;

	private final String reason;

	public InvalidInputException(String field, String reason) {
		super(ErrorCode.INVALID_INPUT);
		this.field = field;
		this.reason = reason;
	}

	public String field() {
		return field;
	}

	public String reason() {
		return reason;
	}

}
