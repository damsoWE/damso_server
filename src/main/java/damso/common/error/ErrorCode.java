package damso.common.error;

import org.springframework.http.HttpStatus;

/**
 * 시스템 전체 공통 에러 코드.
 * 메시지는 고정 문구이며 예외 메시지·입력값을 담지 않는다 (constitution 원칙 VI).
 * 기능별 코드는 이 목록에 추가하되, 기존 코드의 의미는 바꾸지 않는다.
 */
public enum ErrorCode {

	INVALID_INPUT(HttpStatus.BAD_REQUEST, "입력값이 올바르지 않습니다."),
	UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "인증이 필요합니다."),
	FORBIDDEN(HttpStatus.FORBIDDEN, "권한이 없습니다."),
	NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 대상을 찾을 수 없습니다."),
	METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "허용되지 않은 요청 방식입니다."),
	CONFLICT(HttpStatus.CONFLICT, "다른 요청과 충돌했습니다. 다시 시도해 주세요."),
	PAYLOAD_TOO_LARGE(HttpStatus.CONTENT_TOO_LARGE, "요청 크기가 너무 큽니다."),
	UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "지원하지 않는 형식입니다."),
	INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "일시적인 오류가 발생했습니다."),

	// F1 (specs/001-auth-signup). 기존 코드 뒤에 둔다 — fromStatus는 선언 순서대로 찾으므로
	// 400·401·409는 계속 위의 공통 코드로 대응된다.
	INVALID_INVITE_CODE(HttpStatus.BAD_REQUEST, "코드를 확인해 주세요."),
	INVALID_VERIFICATION_CODE(HttpStatus.BAD_REQUEST, "인증 코드가 올바르지 않거나 만료되었습니다."),
	INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않습니다."),
	TOO_MANY_ATTEMPTS(HttpStatus.TOO_MANY_REQUESTS, "잠시 후 다시 시도해 주세요."),
	CLASSROOM_HAS_TEACHER(HttpStatus.CONFLICT, "이미 담임이 연결된 학급입니다."),
	STUDENT_HAS_GUARDIAN(HttpStatus.CONFLICT, "이미 보호자가 연결된 학생입니다.");

	private final HttpStatus status;
	private final String message;

	ErrorCode(HttpStatus status, String message) {
		this.status = status;
		this.message = message;
	}

	public HttpStatus status() {
		return status;
	}

	public String message() {
		return message;
	}

	/** HTTP 상태 코드에 대응하는 공통 코드. 대응이 없으면 INTERNAL_ERROR. */
	public static ErrorCode fromStatus(int status) {
		for (ErrorCode code : values()) {
			if (code.status.value() == status) {
				return code;
			}
		}
		return INTERNAL_ERROR;
	}

}
