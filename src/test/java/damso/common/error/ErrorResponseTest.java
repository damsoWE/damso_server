package damso.common.error;

import static damso.support.ErrorResponseAssertions.assertSafeErrorResponse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.RequestDispatcher;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import damso.support.IntegrationTestSupport;
import damso.support.TestJwtIssuer;

/** US1 / SC-001: 오류를 유발해도 공통 형식이고 내부 정보가 보이지 않는다. */
class ErrorResponseTest extends IntegrationTestSupport {

	private final String token = bearer(TestJwtIssuer.valid("TEACHER"));

	@Test
	void unexpectedExceptionIsInternalErrorAndNotRewrittenTo401() throws Exception {
		expect(perform(get("/test/errors/runtime")), 500, "INTERNAL_ERROR");
	}

	@Test
	void exceptionMessageWithPathAndSqlIsNotExposed() throws Exception {
		expect(perform(get("/test/errors/leaky")), 500, "INTERNAL_ERROR");
	}

	@Test
	void businessExceptionUsesItsCode() throws Exception {
		expect(perform(get("/test/errors/not-found")), 404, "NOT_FOUND");
	}

	@Test
	void unknownPathIsNotFound() throws Exception {
		expect(perform(get("/test/no-such-path")), 404, "NOT_FOUND");
	}

	@Test
	void wrongMethodIsMethodNotAllowed() throws Exception {
		expect(perform(post("/test/errors/get-only")), 405, "METHOD_NOT_ALLOWED");
	}

	@Test
	void brokenJsonIsInvalidInput() throws Exception {
		expect(perform(post("/test/errors/body")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"content\": ")), 400, "INVALID_INPUT");
	}

	@Test
	void wrongContentTypeIsUnsupportedMediaType() throws Exception {
		expect(perform(post("/test/errors/body")
				.contentType(MediaType.TEXT_PLAIN)
				.content("hello")), 415, "UNSUPPORTED_MEDIA_TYPE");
	}

	@Test
	void servletErrorPathUsesCommonFormat() throws Exception {
		expect(perform(get("/error").requestAttr(RequestDispatcher.ERROR_STATUS_CODE, 413)),
				413, "PAYLOAD_TOO_LARGE");
	}

	@Test
	void servletErrorPathWithUnknownStatusFallsBackToInternalError() throws Exception {
		expect(perform(get("/error").requestAttr(RequestDispatcher.ERROR_STATUS_CODE, 418)),
				500, "INTERNAL_ERROR");
	}

	private ResultActions perform(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request)
			throws Exception {
		return mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, token));
	}

	private static void expect(ResultActions result, int status, String code) throws Exception {
		result.andExpect(status().is(status));
		assertSafeErrorResponse(result.andReturn().getResponse().getContentAsString(), code);
	}

}
