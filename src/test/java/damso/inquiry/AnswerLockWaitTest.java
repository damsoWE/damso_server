package damso.inquiry;

import static damso.support.ErrorResponseAssertions.assertSafeErrorResponse;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.transaction.support.TransactionTemplate;

import damso.support.IntegrationTestSupport;

/**
 * 이슈 #26(체크리스트 시간 및 상태 2): 답변 전송은 루트 문의 행을 쓰기 잠금으로 읽는다. 다른 트랜잭션이 그 행을 오래 쥐고 있어도
 * 커넥션을 쥔 채 무한정 기다리지 않고, 잠금 대기 상한(3초) 뒤 409 CONFLICT로 끝난다.
 */
class AnswerLockWaitTest extends IntegrationTestSupport {

	@Autowired
	private TransactionTemplate transaction;

	@Test
	void sendingAnAnswerStopsWaitingForALockedThreadWithConflict() throws Exception {
		Scenario s = teacherWithClassroom();
		UUID inquiryId = UUID.fromString(
				read(submitInquiry(s.parent().accessToken(), s.student().getId(), NORMAL_SAMPLE), "$.inquiryId"));
		CountDownLatch locked = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		ExecutorService holder = Executors.newSingleThreadExecutor();
		try {
			holder.submit(() -> transaction.executeWithoutResult(status -> {
				jdbc.queryForList("select id from inquiry where id = ? for update", inquiryId);
				locked.countDown();
				try {
					release.await(30, TimeUnit.SECONDS);
				}
				catch (InterruptedException ex) {
					Thread.currentThread().interrupt();
				}
			}));
			assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();

			long started = System.nanoTime();
			MockHttpServletResponse response = send(json(post("/api/v1/teacher/inquiries/" + inquiryId + "/answers"),
					"{\"content\":\"확인했습니다.\",\"reviewed\":false,\"lastSeenFollowUpId\":" + lastSeenFollowUpJson(inquiryId) + "}"), s.teacher().accessToken());
			Duration waited = Duration.ofNanos(System.nanoTime() - started);

			assertThat(response.getStatus()).isEqualTo(409);
			assertSafeErrorResponse(response.getContentAsString(), "CONFLICT");
			assertThat(waited).isLessThan(Duration.ofSeconds(10));
		}
		finally {
			release.countDown();
			holder.shutdown();
			holder.awaitTermination(30, TimeUnit.SECONDS);
		}
	}

}
