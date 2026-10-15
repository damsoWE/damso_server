package damso.common.security;

import java.io.IOException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import damso.common.error.ErrorCode;
import tools.jackson.databind.ObjectMapper;

/** 필터 계층의 인가 실패를 403 ErrorResponse로 응답한다. */
@Component
public class JsonAccessDeniedHandler implements AccessDeniedHandler {

	private final ObjectMapper objectMapper;

	public JsonAccessDeniedHandler(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
	}

	@Override
	public void handle(HttpServletRequest request, HttpServletResponse response,
			AccessDeniedException accessDeniedException) throws IOException {
		JsonAuthenticationEntryPoint.write(response, ErrorCode.FORBIDDEN, objectMapper);
	}

}
