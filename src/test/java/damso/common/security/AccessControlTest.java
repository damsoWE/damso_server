package damso.common.security;

import static damso.support.ErrorResponseAssertions.assertSafeErrorResponse;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import damso.support.IntegrationTestSupport;
import damso.support.TestJwtIssuer;

/** US3 / SC-003 / FR-007: 기본 거부, 헤더 토큰만, 세션 쿠키 없음, 진단 정보 비노출. */
class AccessControlTest extends IntegrationTestSupport {

	@ParameterizedTest
	@ValueSource(strings = { "/test/auth/any", "/anything", "/actuator/env" })
	void unauthenticatedRequestIsRejected(String path) throws Exception {
		expectError(call(get(path), null), 401, "UNAUTHORIZED");
	}

	@Test
	void healthIsPublicAndShowsStatusOnly() throws Exception {
		MockHttpServletResponse response = call(get("/actuator/health"), null);
		assertThat(response.getStatus()).isEqualTo(200);
		assertThat(response.getContentAsString()).isEqualTo("{\"status\":\"UP\"}");
		assertNoSessionCookie(response);
	}

	@Test
	void publicPathIgnoresInvalidToken() throws Exception {
		MockHttpServletResponse response = call(get("/actuator/health"), TestJwtIssuer.forged("TEACHER"));
		assertThat(response.getStatus()).isEqualTo(200);
	}

	@Test
	void validTokenIsAccepted() throws Exception {
		MockHttpServletResponse response = call(get("/test/auth/any"), validToken("PARENT"));
		assertThat(response.getStatus()).isEqualTo(200);
		assertNoSessionCookie(response);
	}

	@Test
	void forgedTokenIsRejected() throws Exception {
		expectError(call(get("/test/auth/any"), TestJwtIssuer.forged("TEACHER")), 401, "UNAUTHORIZED");
	}

	@Test
	void expiredTokenIsRejected() throws Exception {
		expectError(call(get("/test/auth/any"), TestJwtIssuer.expired("TEACHER")), 401, "UNAUTHORIZED");
	}

	@Test
	void unsignedAlgNoneTokenIsRejected() throws Exception {
		expectError(call(get("/test/auth/any"), TestJwtIssuer.unsigned("TEACHER")), 401, "UNAUTHORIZED");
	}

	@Test
	void tokenWithoutExpIsRejected() throws Exception {
		expectError(call(get("/test/auth/any"), TestJwtIssuer.withoutExp("TEACHER")), 401, "UNAUTHORIZED");
	}

	@Test
	void wrongRoleIsForbidden() throws Exception {
		expectError(call(get("/test/auth/teacher-only"), validToken("PARENT")), 403, "FORBIDDEN");
	}

	@Test
	void unknownRoleGetsNoAuthority() throws Exception {
		expectError(call(get("/test/auth/teacher-only"), validToken("ADMIN")), 403, "FORBIDDEN");
	}

	@Test
	void rightRoleIsAllowed() throws Exception {
		assertThat(call(get("/test/auth/teacher-only"), validToken("TEACHER")).getStatus()).isEqualTo(200);
	}

	@ParameterizedTest
	@ValueSource(strings = { "/actuator/env", "/actuator/beans", "/actuator/configprops" })
	void diagnosticEndpointsAreNotExposedEvenWhenAuthenticated(String path) throws Exception {
		expectError(call(get(path), validToken("SUPER_ADMIN")), 404, "NOT_FOUND");
	}

	private MockHttpServletResponse call(MockHttpServletRequestBuilder request, String token) throws Exception {
		if (token != null) {
			request.header(HttpHeaders.AUTHORIZATION, bearer(token));
		}
		return mockMvc.perform(request).andReturn().getResponse();
	}

	private static void expectError(MockHttpServletResponse response, int status, String code) throws Exception {
		assertThat(response.getStatus()).isEqualTo(status);
		assertSafeErrorResponse(response.getContentAsString(), code);
		assertNoSessionCookie(response);
	}

	private static void assertNoSessionCookie(MockHttpServletResponse response) {
		assertThat(response.getHeaders(HttpHeaders.SET_COOKIE)).isEmpty();
		assertThat(response.getCookies()).isEmpty();
	}

}
