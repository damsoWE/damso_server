package damso.common.error;

import static damso.support.ErrorResponseAssertions.assertSafeErrorResponse;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;

import damso.support.IntegrationTestSupport;
import tools.jackson.databind.JsonNode;

/** US7 / FR-023~025: 진입 지점 검증, 필드별 고정 사유, 입력값 비반환, 64KB 초과 거부. */
class InputValidationTest extends IntegrationTestSupport {

	private static final String MARKER = "ECHO-MARKER-";

	@Test
	void tooLongFieldReportsFieldAndFixedReasonWithoutEchoingInput() throws Exception {
		String content = MARKER.repeat(200).substring(0, 2001);
		MockHttpServletResponse response = postJson("{\"content\":\"" + content + "\",\"count\":1}");

		assertThat(response.getStatus()).isEqualTo(400);
		assertThat(response.getContentAsString()).doesNotContain(MARKER);
		JsonNode body = assertSafeErrorResponse(response.getContentAsString(), "INVALID_INPUT");
		assertThat(body.get("fieldErrors").get(0).get("field").asString()).isEqualTo("content");
		assertThat(body.get("fieldErrors").get(0).get("reason").asString()).isEqualTo("최대 2000자입니다");
	}

	@Test
	void blankRequiredFieldReportsRequired() throws Exception {
		JsonNode body = assertInvalid(postJson("{\"content\":\"\",\"count\":1}"));
		assertThat(body.get("fieldErrors").get(0).get("reason").asString()).isEqualTo("필수 값입니다");
	}

	@Test
	void outOfRangeNumberReportsRange() throws Exception {
		JsonNode body = assertInvalid(postJson("{\"content\":\"ok\",\"count\":0}"));
		assertThat(body.get("fieldErrors").get(0).get("field").asString()).isEqualTo("count");
		assertThat(body.get("fieldErrors").get(0).get("reason").asString()).isEqualTo("허용 범위를 벗어났습니다");
	}

	@Test
	void brokenJsonDoesNotExposeParserMessage() throws Exception {
		MockHttpServletResponse response = postJson("{\"content\": " + MARKER + " }");
		assertThat(response.getContentAsString()).doesNotContain(MARKER).doesNotContain("Unrecognized");
		assertInvalid(response);
	}

	@Test
	void bodyOver64KbIsRejectedBeforeProcessing() throws Exception {
		String big = "{\"content\":\"" + "a".repeat(65_537) + "\"}";
		MockHttpServletResponse response = postJson(big);
		assertThat(response.getStatus()).isEqualTo(413);
		assertSafeErrorResponse(response.getContentAsString(), "PAYLOAD_TOO_LARGE");
	}

	private MockHttpServletResponse postJson(String json) throws Exception {
		return mockMvc.perform(post("/test/validation")
				.header(HttpHeaders.AUTHORIZATION, bearer(validToken("PARENT")))
				.contentType(MediaType.APPLICATION_JSON)
				.content(json)).andReturn().getResponse();
	}

	private static JsonNode assertInvalid(MockHttpServletResponse response) throws Exception {
		assertThat(response.getStatus()).isEqualTo(400);
		return assertSafeErrorResponse(response.getContentAsString(), "INVALID_INPUT");
	}

}
