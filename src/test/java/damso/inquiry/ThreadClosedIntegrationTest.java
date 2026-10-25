package damso.inquiry;

import static damso.support.ErrorResponseAssertions.assertSafeErrorResponse;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

import damso.support.IntegrationTestSupport;

/**
 * F4 US5 시나리오 3~4 / FR-021, 원칙 백엔드 요건 동시성: 종료된 스레드는 재문의·답변을 모두 거부하고,
 * 동시 종료 요청은 하나만 성공한다(@Version).
 */
class ThreadClosedIntegrationTest extends IntegrationTestSupport {

	private MockHttpServletResponse close(String token, UUID inquiryId) throws Exception {
		return send(post("/api/v1/teacher/inquiries/" + inquiryId + "/close"), token);
	}

	private MockHttpServletResponse sendAnswer(String token, UUID inquiryId, String content) throws Exception {
		String body = "{\"content\":\"" + content + "\",\"reviewed\":false}";
		return send(json(post("/api/v1/teacher/inquiries/" + inquiryId + "/answers"), body), token);
	}

	@Test
	void closedThreadRejectsFollowUps() throws Exception {
		ConfirmedInquiry confirmed = urgentInquiryConfirmedByTeacher();
		assertThat(close(confirmed.scenario().teacher().accessToken(), confirmed.inquiryId()).getStatus())
				.isEqualTo(200);

		MockHttpServletResponse response = submitFollowUp(confirmed.scenario().parent().accessToken(),
				confirmed.inquiryId(), "그 뒤로도 궁금해요");

		assertThat(response.getStatus()).isEqualTo(409);
		assertSafeErrorResponse(response.getContentAsString(), "THREAD_CLOSED");
		assertThat(jdbc.queryForObject("select count(*) from inquiry where thread_root_id is not null", Integer.class))
				.isZero();
	}

	@Test
	void closedThreadRejectsAnswers() throws Exception {
		ConfirmedInquiry confirmed = urgentInquiryConfirmedByTeacher();
		String teacherToken = confirmed.scenario().teacher().accessToken();
		assertThat(close(teacherToken, confirmed.inquiryId()).getStatus()).isEqualTo(200);

		MockHttpServletResponse response = sendAnswer(teacherToken, confirmed.inquiryId(), "그래도 답변을 보냅니다");

		assertThat(response.getStatus()).isEqualTo(409);
		assertSafeErrorResponse(response.getContentAsString(), "THREAD_CLOSED");
		assertThat(jdbc.queryForObject("select count(*) from answer", Integer.class)).isZero();
	}

	@Test
	void concurrentCloseRequestsOnlyOneSucceeds() throws Exception {
		ConfirmedInquiry confirmed = urgentInquiryConfirmedByTeacher();
		String teacherToken = confirmed.scenario().teacher().accessToken();
		UUID inquiryId = confirmed.inquiryId();
		int callers = 2;
		ExecutorService pool = Executors.newFixedThreadPool(callers);
		CountDownLatch start = new CountDownLatch(1);
		try {
			List<Future<MockHttpServletResponse>> futures = new ArrayList<>();
			for (int i = 0; i < callers; i++) {
				Callable<MockHttpServletResponse> call = () -> {
					start.await();
					return close(teacherToken, inquiryId);
				};
				futures.add(pool.submit(call));
			}
			start.countDown();

			int succeeded = 0;
			int conflicted = 0;
			for (Future<MockHttpServletResponse> future : futures) {
				int status = future.get().getStatus();
				if (status == 200) {
					succeeded++;
				}
				else if (status == 409) {
					conflicted++;
				}
			}
			assertThat(succeeded).isEqualTo(1);
			assertThat(conflicted).isEqualTo(1);
			assertThat(jdbc.queryForObject("select stage from inquiry where id = ?", String.class, inquiryId))
					.isEqualTo("ANSWERED");
			assertThat(jdbc.queryForObject(
					"select count(*) from inquiry_stage_history where inquiry_id = ? and stage = 'ANSWERED'",
					Integer.class, inquiryId)).isEqualTo(1);
		}
		finally {
			pool.shutdownNow();
		}
	}

}
