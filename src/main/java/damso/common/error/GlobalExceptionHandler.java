package damso.common.error;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import jakarta.persistence.OptimisticLockException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import damso.common.config.PayloadTooLargeException;

/**
 * MVC 계층의 모든 예외를 공통 ErrorResponse로 바꾼다.
 * 응답에는 고정 문구만 담고, 원인은 서버 로그에만 남긴다.
 * 서버 로그에도 요청 본문·쿼리스트링은 남기지 않는다.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	/** 잠금·대기 중. 남은 시간은 Retry-After 헤더(초)로 알린다. 본문은 공통 형식 그대로. */
	@ExceptionHandler(TooManyAttemptsException.class)
	ResponseEntity<ErrorResponse> handleTooManyAttempts(TooManyAttemptsException ex) {
		return ResponseEntity.status(ErrorCode.TOO_MANY_ATTEMPTS.status())
				.header(HttpHeaders.RETRY_AFTER, String.valueOf(ex.retryAfterSeconds()))
				.body(ErrorResponse.of(ErrorCode.TOO_MANY_ATTEMPTS));
	}

	@ExceptionHandler(InvalidInputException.class)
	ResponseEntity<ErrorResponse> handleInvalidInput(InvalidInputException ex) {
		return invalid(List.of(new ErrorResponse.FieldError(ex.field(), ex.reason())));
	}

	@ExceptionHandler(BusinessException.class)
	ResponseEntity<ErrorResponse> handleBusiness(BusinessException ex, HttpServletRequest request) {
		if (ex.errorCode() == ErrorCode.INTERNAL_ERROR) {
			// 서버 쪽 실패(Redis 장애 등)는 원인 없이 500만 남지 않게 기록한다. 메시지에는 값이 섞일 수 있어 종류만 남긴다
			Throwable cause = ex.getCause();
			log.error("Internal error on {} {} (cause={})", request.getMethod(), request.getRequestURI(),
					cause == null ? "none" : cause.getClass().getSimpleName());
		}
		return respond(ex.errorCode());
	}

	/** 컨트롤러·서비스 안에서 발생한 인가 실패 (메서드 보안·건별 소유권 검증 등). */
	@ExceptionHandler(AccessDeniedException.class)
	ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex) {
		return respond(ErrorCode.FORBIDDEN);
	}

	/**
	 * 낙관적 락 실패 — 같은 버전을 기준으로 한 두 번째 수정.
	 * 유니크 제약 위반도 같은 충돌로 본다(동시에 같은 학급을 만들거나 같은 대상의 코드를 발행한 경우).
	 * 행 잠금 대기 상한을 넘긴 경우(다른 요청이 같은 행을 쥐고 있음)도 같은 충돌로 본다.
	 */
	@ExceptionHandler({ OptimisticLockingFailureException.class, OptimisticLockException.class,
			DataIntegrityViolationException.class, PessimisticLockingFailureException.class })
	ResponseEntity<ErrorResponse> handleConflict(Exception ex) {
		return respond(ErrorCode.CONFLICT);
	}

	@ExceptionHandler({ NoResourceFoundException.class, NoHandlerFoundException.class })
	ResponseEntity<ErrorResponse> handleNotFound(Exception ex) {
		return respond(ErrorCode.NOT_FOUND);
	}

	@ExceptionHandler(HttpRequestMethodNotSupportedException.class)
	ResponseEntity<ErrorResponse> handleMethodNotAllowed(HttpRequestMethodNotSupportedException ex) {
		return respond(ErrorCode.METHOD_NOT_ALLOWED);
	}

	@ExceptionHandler(HttpMediaTypeNotSupportedException.class)
	ResponseEntity<ErrorResponse> handleUnsupportedMediaType(HttpMediaTypeNotSupportedException ex) {
		return respond(ErrorCode.UNSUPPORTED_MEDIA_TYPE);
	}

	/**
	 * 본문 파싱 실패·타입 불일치·필수 파라미터 누락. 파서 내부 메시지는 노출하지 않는다.
	 * 본문을 읽다가 크기 제한을 넘은 경우(chunked)는 413으로 응답한다.
	 */
	@ExceptionHandler({ HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class,
			MissingServletRequestParameterException.class })
	ResponseEntity<ErrorResponse> handleUnreadable(Exception ex) {
		for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
			if (cause instanceof PayloadTooLargeException) {
				return respond(ErrorCode.PAYLOAD_TOO_LARGE);
			}
		}
		return respond(ErrorCode.INVALID_INPUT);
	}

	@ExceptionHandler(PayloadTooLargeException.class)
	ResponseEntity<ErrorResponse> handlePayloadTooLarge(PayloadTooLargeException ex) {
		return respond(ErrorCode.PAYLOAD_TOO_LARGE);
	}

	/** @Valid 요청 본문 검증 실패. */
	@ExceptionHandler(MethodArgumentNotValidException.class)
	ResponseEntity<ErrorResponse> handleInvalidBody(MethodArgumentNotValidException ex) {
		List<ErrorResponse.FieldError> errors = new ArrayList<>();
		for (FieldError error : ex.getBindingResult().getFieldErrors()) {
			errors.add(new ErrorResponse.FieldError(error.getField(), reason(error)));
		}
		return invalid(errors);
	}

	/** 메서드 파라미터(@PathVariable·@RequestParam 등) 검증 실패. */
	@ExceptionHandler(HandlerMethodValidationException.class)
	ResponseEntity<ErrorResponse> handleInvalidParameters(HandlerMethodValidationException ex) {
		List<ErrorResponse.FieldError> errors = new ArrayList<>();
		ex.getParameterValidationResults().forEach(result -> result.getResolvableErrors().forEach(error -> {
			String field = (error instanceof FieldError fieldError) ? fieldError.getField()
					: result.getMethodParameter().getParameterName();
			String reason;
			try {
				reason = ValidationReasons.of(result.unwrap(error, ConstraintViolation.class));
			}
			catch (IllegalArgumentException notAViolation) {
				reason = ValidationReasons.INVALID;
			}
			errors.add(new ErrorResponse.FieldError(field, reason));
		}));
		return invalid(errors);
	}

	/** 서비스 계층 @Validated 검증 실패. */
	@ExceptionHandler(ConstraintViolationException.class)
	ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException ex) {
		List<ErrorResponse.FieldError> errors = new ArrayList<>();
		for (ConstraintViolation<?> violation : ex.getConstraintViolations()) {
			errors.add(new ErrorResponse.FieldError(lastNode(violation.getPropertyPath()),
					ValidationReasons.of(violation)));
		}
		return invalid(errors);
	}

	@ExceptionHandler(Exception.class)
	ResponseEntity<ErrorResponse> handleUnexpected(Exception ex, HttpServletRequest request) {
		// getRequestURI()는 쿼리스트링을 포함하지 않는다
		log.error("Unhandled exception on {} {}", request.getMethod(), request.getRequestURI(), ex);
		return respond(ErrorCode.INTERNAL_ERROR);
	}

	static ResponseEntity<ErrorResponse> respond(ErrorCode code) {
		return ResponseEntity.status(code.status()).body(ErrorResponse.of(code));
	}

	private static ResponseEntity<ErrorResponse> invalid(List<ErrorResponse.FieldError> errors) {
		return ResponseEntity.status(ErrorCode.INVALID_INPUT.status())
				.body(ErrorResponse.of(ErrorCode.INVALID_INPUT, errors));
	}

	private static String reason(FieldError error) {
		if (error.contains(ConstraintViolation.class)) {
			return ValidationReasons.of(error.unwrap(ConstraintViolation.class));
		}
		return ValidationReasons.of(error.getCode(), Map.of());
	}

	private static String lastNode(Path path) {
		String name = null;
		for (Path.Node node : path) {
			name = node.getName();
		}
		return name == null ? "" : name;
	}

}
