package damso.common.security;

import static damso.support.ErrorResponseAssertions.assertSafeErrorResponse;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;

import tools.jackson.databind.json.JsonMapper;

/** 필터 계층의 인증·인가 실패도 공통 형식으로 응답한다. */
class JsonAccessDeniedHandlerTest {

	private final JsonMapper mapper = JsonMapper.builder().build();

	@Test
	void accessDeniedWritesForbiddenJson() throws Exception {
		MockHttpServletResponse response = new MockHttpServletResponse();
		new JsonAccessDeniedHandler(mapper).handle(new MockHttpServletRequest(), response,
				new AccessDeniedException("denied by rule at damso.SomeClass.java"));
		assertThat(response.getStatus()).isEqualTo(403);
		assertSafeErrorResponse(response.getContentAsString(), "FORBIDDEN");
	}

	@Test
	void authenticationFailureWritesUnauthorizedJsonWithoutDetailHeader() throws Exception {
		MockHttpServletResponse response = new MockHttpServletResponse();
		new JsonAuthenticationEntryPoint(mapper).commence(new MockHttpServletRequest(), response,
				new BadCredentialsException("token signature invalid"));
		assertThat(response.getStatus()).isEqualTo(401);
		assertThat(response.getHeader("WWW-Authenticate")).isNull();
		assertSafeErrorResponse(response.getContentAsString(), "UNAUTHORIZED");
	}

}
