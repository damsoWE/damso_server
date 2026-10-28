package damso.common.config;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import damso.common.error.ErrorCode;
import damso.common.error.ErrorResponse;
import tools.jackson.databind.ObjectMapper;

/**
 * 요청 본문 최대 64KB. 보안 필터보다 먼저 실행된다.
 * - Content-Length가 있으면 바로 비교해 413으로 거부
 * - 없으면(chunked) 읽은 바이트를 세다가 넘는 순간 PayloadTooLargeException → 공통 핸들러가 413으로 응답
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestSizeLimitFilter extends OncePerRequestFilter {

	static final int MAX_BYTES = 64 * 1024;

	private final ObjectMapper objectMapper;

	public RequestSizeLimitFilter(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		long declared = request.getContentLengthLong();
		if (declared > MAX_BYTES) {
			response.setStatus(ErrorCode.PAYLOAD_TOO_LARGE.status().value());
			response.setContentType(MediaType.APPLICATION_JSON_VALUE);
			response.setCharacterEncoding("UTF-8");
			objectMapper.writeValue(response.getOutputStream(), ErrorResponse.of(ErrorCode.PAYLOAD_TOO_LARGE));
			return;
		}
		chain.doFilter(declared < 0 ? new LimitedRequest(request) : request, response);
	}

	private static final class LimitedRequest extends HttpServletRequestWrapper {

		private ServletInputStream limited;

		LimitedRequest(HttpServletRequest request) {
			super(request);
		}

		@Override
		public ServletInputStream getInputStream() throws IOException {
			if (limited == null) {
				limited = new LimitedInputStream(super.getInputStream());
			}
			return limited;
		}

	}

	private static final class LimitedInputStream extends ServletInputStream {

		private final ServletInputStream delegate;
		private long count;

		LimitedInputStream(ServletInputStream delegate) {
			this.delegate = delegate;
		}

		@Override
		public int read() throws IOException {
			int b = delegate.read();
			if (b != -1) {
				count(1);
			}
			return b;
		}

		@Override
		public int read(byte[] buffer, int offset, int length) throws IOException {
			int n = delegate.read(buffer, offset, length);
			if (n > 0) {
				count(n);
			}
			return n;
		}

		private void count(int n) throws PayloadTooLargeException {
			count += n;
			if (count > MAX_BYTES) {
				throw new PayloadTooLargeException();
			}
		}

		@Override
		public boolean isFinished() {
			return delegate.isFinished();
		}

		@Override
		public boolean isReady() {
			return delegate.isReady();
		}

		@Override
		public void setReadListener(ReadListener listener) {
			delegate.setReadListener(listener);
		}

	}

}
