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

	/** 받은 목록을 복사해 둔다(불변) — 만든 쪽이 나중에 목록을 바꿔도 이 값은 바뀌지 않는다. */
	public ErrorResponse {
		fieldErrors = fieldErrors == null ? null : List.copyOf(fieldErrors);
	}

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
