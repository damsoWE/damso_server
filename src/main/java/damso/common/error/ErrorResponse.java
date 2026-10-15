package damso.common.error;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 모든 오류 응답의 공통 형식: { code, message, fieldErrors? }.
 * 스택트레이스·예외명·내부 경로·SQL은 담지 않는다 (FR-001, FR-002).
 */
public record ErrorResponse(
		ErrorCode code,
		String message,
		@JsonInclude(JsonInclude.Include.NON_EMPTY) List<FieldError> fieldErrors) {

	public static ErrorResponse of(ErrorCode code) {
		return new ErrorResponse(code, code.message(), List.of());
	}

	public static ErrorResponse of(ErrorCode code, List<FieldError> fieldErrors) {
		return new ErrorResponse(code, code.message(), List.copyOf(fieldErrors));
	}

	/** 검증 실패 필드. reason은 고정 문구이며 입력값을 담지 않는다 (FR-024). */
	public record FieldError(String field, String reason) {
	}

}
