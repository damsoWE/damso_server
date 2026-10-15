package damso.common.config;

import static damso.support.ErrorResponseAssertions.assertSafeErrorResponse;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

/** FR-025 / research R9: Content-Length가 없는(chunked) 요청도 읽은 바이트 기준으로 끊는다. */
class RequestSizeLimitFilterTest {

	private final RequestSizeLimitFilter filter = new RequestSizeLimitFilter(JsonMapper.builder().build());

	@Test
	void declaredLengthOverLimitIsRejectedWith413() throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest("POST", "/any");
		request.setContent(new byte[RequestSizeLimitFilter.MAX_BYTES + 1]);
		MockHttpServletResponse response = new MockHttpServletResponse();

		filter.doFilter(request, response, new MockFilterChain());

		assertThat(response.getStatus()).isEqualTo(413);
		assertSafeErrorResponse(response.getContentAsString(), "PAYLOAD_TOO_LARGE");
	}

	@Test
	void chunkedBodyOverLimitStopsWhileReading() {
		MockHttpServletRequest request = new UnknownLengthRequest(new byte[RequestSizeLimitFilter.MAX_BYTES + 1]);

		assertThatThrownBy(() -> filter.doFilter(request, new MockHttpServletResponse(),
				new MockFilterChain(new ReadAllServlet())))
				.isInstanceOf(PayloadTooLargeException.class);
	}

	@Test
	void chunkedBodyWithinLimitPasses() throws Exception {
		MockHttpServletRequest request = new UnknownLengthRequest(new byte[RequestSizeLimitFilter.MAX_BYTES]);
		MockHttpServletResponse response = new MockHttpServletResponse();

		filter.doFilter(request, response, new MockFilterChain(new ReadAllServlet()));

		assertThat(response.getStatus()).isEqualTo(200);
	}

	/** Content-Length 헤더 없이 전송된 요청을 흉내 낸다. */
	private static final class UnknownLengthRequest extends MockHttpServletRequest {

		UnknownLengthRequest(byte[] body) {
			super("POST", "/any");
			setContent(body);
		}

		@Override
		public int getContentLength() {
			return -1;
		}

		@Override
		public long getContentLengthLong() {
			return -1;
		}

	}

	private static final class ReadAllServlet extends HttpServlet {

		@Override
		protected void service(HttpServletRequest req, HttpServletResponse resp) throws java.io.IOException {
			req.getInputStream().readAllBytes();
			resp.setStatus(200);
		}

	}

}
