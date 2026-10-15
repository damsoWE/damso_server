package damso.common.error;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * MVC 밖(서블릿 컨테이너·필터)에서 난 오류가 도달하는 /error 경로.
 * Boot 기본 BasicErrorController를 대체해 공통 ErrorResponse로만 응답한다 (FR-002).
 */
@RestController
public class JsonErrorController implements ErrorController {

	/** 응답 생성 중 다시 실패했을 때 쓰는 최종 대비 본문. */
	private static final String FALLBACK_BODY = "{\"code\":\"INTERNAL_ERROR\",\"message\":\""
			+ ErrorCode.INTERNAL_ERROR.message() + "\"}";

	@RequestMapping("/error")
	ResponseEntity<ErrorResponse> error(HttpServletRequest request, HttpServletResponse response) throws IOException {
		try {
			Object status = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
			ErrorCode code = (status instanceof Integer value) ? ErrorCode.fromStatus(value) : ErrorCode.INTERNAL_ERROR;
			return GlobalExceptionHandler.respond(code);
		}
		catch (RuntimeException ex) {
			response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
			response.setContentType(MediaType.APPLICATION_JSON_VALUE);
			response.getOutputStream().write(FALLBACK_BODY.getBytes(StandardCharsets.UTF_8));
			return null;
		}
	}

}
