package damso.common.security;

import java.io.IOException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import damso.common.error.ErrorCode;
import damso.common.error.ErrorResponse;
import tools.jackson.databind.ObjectMapper;

/**
 * 인증 실패(토큰 없음·위조·만료)를 401 ErrorResponse로 응답한다.
 * 기본 Bearer 진입점과 달리 WWW-Authenticate 헤더에 토큰 오류 상세를 넣지 않는다.
 */
@Component
public class JsonAuthenticationEntryPoint implements AuthenticationEntryPoint {

	private final ObjectMapper objectMapper;

	public JsonAuthenticationEntryPoint(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
	}

	@Override
	public void commence(HttpServletRequest request, HttpServletResponse response,
			AuthenticationException authException) throws IOException {
		write(response, ErrorCode.UNAUTHORIZED, objectMapper);
	}

	static void write(HttpServletResponse response, ErrorCode code, ObjectMapper objectMapper) throws IOException {
		response.setStatus(code.status().value());
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.setCharacterEncoding("UTF-8");
		objectMapper.writeValue(response.getOutputStream(), ErrorResponse.of(code));
	}

}
