package damso.common;

import static damso.support.ErrorResponseAssertions.assertSafeErrorResponse;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.multipart.MultipartResolver;

import damso.account.domain.Role;
import damso.account.domain.UserAccount;
import damso.support.IntegrationTestSupport;

/**
 * API는 JSON만 주고받는다. 의존성(firebase-admin)이 XML 변환기를 끌고 와도 XML 본문을
 * 받거나 XML로 응답하지 않고, 파일 업로드(multipart)를 파싱하지 않는다.
 */
class JsonOnlyApiTest extends IntegrationTestSupport {

	@Autowired
	private ApplicationContext context;

	@Test
	void xmlRequestBodyIsUnsupported() throws Exception {
		MockHttpServletResponse response = mockMvc.perform(post("/api/v1/auth/login")
				.contentType(MediaType.APPLICATION_XML)
				.content("<a><email>parent@damso.test</email><password>Passw0rd9x</password></a>"))
				.andReturn().getResponse();

		assertThat(response.getStatus()).isEqualTo(415);
		assertSafeErrorResponse(response.getContentAsString(), "UNSUPPORTED_MEDIA_TYPE");
	}

	/** Accept로 XML을 요청해도 정상 응답과 오류 응답 모두 JSON이다. */
	@Test
	void responsesAreJsonWhateverTheAcceptHeaderSays() throws Exception {
		UserAccount parent = createAccount(Role.PARENT);
		MockHttpServletResponse ok = mockMvc.perform(get("/api/v1/me")
				.header(HttpHeaders.AUTHORIZATION, bearer(tokenFor(parent)))
				.accept(MediaType.APPLICATION_XML))
				.andReturn().getResponse();
		MockHttpServletResponse error = mockMvc.perform(get("/api/v1/me")
				.header(HttpHeaders.AUTHORIZATION, bearer(validToken("PARENT")))
				.accept(MediaType.APPLICATION_XML))
				.andReturn().getResponse();

		assertThat(ok.getStatus()).isEqualTo(200);
		assertThat(ok.getContentType()).startsWith(MediaType.APPLICATION_JSON_VALUE);
		assertThat(error.getStatus()).isEqualTo(401);
		assertThat(error.getContentType()).startsWith(MediaType.APPLICATION_JSON_VALUE);
		assertSafeErrorResponse(error.getContentAsString(), "UNAUTHORIZED");
	}

	@Test
	void multipartParsingIsOff() {
		assertThat(context.getBeansOfType(MultipartResolver.class)).isEmpty();
	}

}
