package damso.inquiry;

import static damso.support.ErrorResponseAssertions.assertSafeErrorResponse;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import damso.support.IntegrationTestSupport;

/**
 * F4 US5 시나리오 3~4 / FR-021, 원칙 백엔드 요건 동시성: 종료된 스레드는 재문의·답변을 모두 거부하고,
 * 동시 종료 요청은 하나만 성공한다(@Version).
 * 운영에서는 종료 경로가 꺼져 있다(FR-031). 종료 경로를 다시 열 때를 대비해 FR-021·022 서버 검증을 계속 확인한다(PR #19 리뷰 P0).
 */
@TestPropertySource(properties = "damso.inquiry.close-enabled=true")
class ThreadClosedIntegrationTest extends IntegrationTestSupport {

	@Autowired
	private PlatformTransactionManager transactionManager;

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

	/**
	 * 다른 트랜잭션이 이미 루트 행에 쓰기 잠금(FOR UPDATE)을 쥔 채 "종료 중"이라고 흉내 낸다. 잠금을 쥔 뒤
	 * {@code holdMillis}만큼 커밋을 늦췄다가 stage를 ANSWERED로 바꾸고 커밋한다. 이 메서드가 리턴하기 전에는
	 * 그 행에 쓰려는 다른 트랜잭션(답변 전송·재문의 제출)이 블로킹돼야 한다 — 그게 이번에 추가한 잠금의 핵심이다.
	 */
	private Future<?> closeRootAfterDelay(UUID inquiryId, CountDownLatch lockAcquired, long holdMillis,
			ExecutorService pool) {
		TransactionTemplate closingTransaction = new TransactionTemplate(transactionManager);
		return pool.submit(() -> closingTransaction.executeWithoutResult(status -> {
			jdbc.queryForList("select id from inquiry where id = ? for update", inquiryId);
			lockAcquired.countDown();
			try {
				Thread.sleep(holdMillis);
			}
			catch (InterruptedException ex) {
				Thread.currentThread().interrupt();
			}
			jdbc.update("update inquiry set stage = 'ANSWERED' where id = ?", inquiryId);
		}));
	}

	@Test
	void sendingAnAnswerWaitsForAConcurrentCloseThenCorrectlySeesTheThreadAsClosed() throws Exception {
		ConfirmedInquiry confirmed = urgentInquiryConfirmedByTeacher();
		String teacherToken = confirmed.scenario().teacher().accessToken();
		UUID inquiryId = confirmed.inquiryId();
		CountDownLatch lockAcquired = new CountDownLatch(1);
		ExecutorService pool = Executors.newSingleThreadExecutor();
		try {
			Future<?> closer = closeRootAfterDelay(inquiryId, lockAcquired, 500, pool);
			assertThat(lockAcquired.await(5, TimeUnit.SECONDS)).as("종료 트랜잭션이 잠금을 쥐지 못했다").isTrue();

			long start = System.nanoTime();
			MockHttpServletResponse response = sendAnswer(teacherToken, inquiryId, "동시에 보낸 답변");
			long waitedMillis = Duration.ofNanos(System.nanoTime() - start).toMillis();
			closer.get(5, TimeUnit.SECONDS);

			assertThat(waitedMillis).as("종료 트랜잭션이 커밋할 때까지 기다려야 한다(잠금)").isGreaterThanOrEqualTo(400);
			assertThat(response.getStatus()).isEqualTo(409);
			assertSafeErrorResponse(response.getContentAsString(), "THREAD_CLOSED");
			assertThat(jdbc.queryForObject("select count(*) from answer", Integer.class)).isZero();
		}
		finally {
			pool.shutdownNow();
		}
	}

	@Test
	void submittingAFollowUpWaitsForAConcurrentCloseThenCorrectlySeesTheThreadAsClosed() throws Exception {
		ConfirmedInquiry confirmed = urgentInquiryConfirmedByTeacher();
		String parentToken = confirmed.scenario().parent().accessToken();
		UUID inquiryId = confirmed.inquiryId();
		CountDownLatch lockAcquired = new CountDownLatch(1);
		ExecutorService pool = Executors.newSingleThreadExecutor();
		try {
			Future<?> closer = closeRootAfterDelay(inquiryId, lockAcquired, 500, pool);
			assertThat(lockAcquired.await(5, TimeUnit.SECONDS)).as("종료 트랜잭션이 잠금을 쥐지 못했다").isTrue();

			long start = System.nanoTime();
			MockHttpServletResponse response = submitFollowUp(parentToken, inquiryId, "동시에 보낸 재문의");
			long waitedMillis = Duration.ofNanos(System.nanoTime() - start).toMillis();
			closer.get(5, TimeUnit.SECONDS);

			assertThat(waitedMillis).as("종료 트랜잭션이 커밋할 때까지 기다려야 한다(잠금)").isGreaterThanOrEqualTo(400);
			assertThat(response.getStatus()).isEqualTo(409);
			assertSafeErrorResponse(response.getContentAsString(), "THREAD_CLOSED");
			assertThat(jdbc.queryForObject("select count(*) from inquiry where thread_root_id is not null",
					Integer.class)).isZero();
		}
		finally {
			pool.shutdownNow();
		}
	}

	/**
	 * 다른 트랜잭션이 이미 루트 행에 쓰기 잠금(FOR UPDATE)을 쥔 채 "답변을 쓰는 중"이라고 흉내 낸다. 답변 전송은
	 * 루트 행 자체를 바꾸지 않으므로(Answer 테이블에만 insert), 잠금을 쥔 뒤 {@code holdMillis}만큼 기다렸다가
	 * answer 한 건을 넣고 커밋한다 — 루트의 버전은 그대로다.
	 */
	private Future<?> holdRootLockThenInsertAnswerAfterDelay(UUID inquiryId, UUID teacherId,
			CountDownLatch lockAcquired, long holdMillis, ExecutorService pool) {
		TransactionTemplate answeringTransaction = new TransactionTemplate(transactionManager);
		return pool.submit(() -> answeringTransaction.executeWithoutResult(status -> {
			jdbc.queryForList("select id from inquiry where id = ? for update", inquiryId);
			lockAcquired.countDown();
			try {
				Thread.sleep(holdMillis);
			}
			catch (InterruptedException ex) {
				Thread.currentThread().interrupt();
			}
			jdbc.update("insert into answer (id, created_at, updated_at, version, thread_root_id, teacher_id, "
					+ "content, reviewed, sent_at) values (?, now(), now(), 0, ?, ?, 'lock-test-content', false, now())",
					UUID.randomUUID(), inquiryId, teacherId);
		}));
	}

	/** 같은 행 잠금이지만 아무것도 쓰지 않고 풀린다 — 재문의 제출이 루트 행을 바꾸지 않는 것과 같다. */
	private Future<?> holdRootLockThenReleaseAfterDelay(UUID inquiryId, CountDownLatch lockAcquired, long holdMillis,
			ExecutorService pool) {
		TransactionTemplate holdingTransaction = new TransactionTemplate(transactionManager);
		return pool.submit(() -> holdingTransaction.executeWithoutResult(status -> {
			jdbc.queryForList("select id from inquiry where id = ? for update", inquiryId);
			lockAcquired.countDown();
			try {
				Thread.sleep(holdMillis);
			}
			catch (InterruptedException ex) {
				Thread.currentThread().interrupt();
			}
		}));
	}

	@Test
	void closingWaitsForAConcurrentAnswerThenSucceeds() throws Exception {
		ConfirmedInquiry confirmed = urgentInquiryConfirmedByTeacher();
		String teacherToken = confirmed.scenario().teacher().accessToken();
		UUID teacherId = confirmed.scenario().teacher().userId();
		UUID inquiryId = confirmed.inquiryId();
		CountDownLatch lockAcquired = new CountDownLatch(1);
		ExecutorService pool = Executors.newSingleThreadExecutor();
		try {
			Future<?> answerer = holdRootLockThenInsertAnswerAfterDelay(inquiryId, teacherId, lockAcquired, 500, pool);
			assertThat(lockAcquired.await(5, TimeUnit.SECONDS)).as("답변 전송 트랜잭션이 잠금을 쥐지 못했다").isTrue();

			long start = System.nanoTime();
			MockHttpServletResponse response = close(teacherToken, inquiryId);
			long waitedMillis = Duration.ofNanos(System.nanoTime() - start).toMillis();
			answerer.get(5, TimeUnit.SECONDS);

			assertThat(waitedMillis).as("답변 전송 트랜잭션이 끝날 때까지 기다려야 한다(잠금)").isGreaterThanOrEqualTo(400);
			assertThat(response.getStatus()).isEqualTo(200);
			assertThat(jdbc.queryForObject("select stage from inquiry where id = ?", String.class, inquiryId))
					.isEqualTo("ANSWERED");
			assertThat(jdbc.queryForObject("select count(*) from answer where thread_root_id = ?", Integer.class,
					inquiryId)).as("잠금이 풀리기 전에 들어간 답변은 그대로 유효해야 한다").isEqualTo(1);
		}
		finally {
			pool.shutdownNow();
		}
	}

	@Test
	void closingWaitsForAConcurrentFollowUpSubmissionThenSucceeds() throws Exception {
		ConfirmedInquiry confirmed = urgentInquiryConfirmedByTeacher();
		String teacherToken = confirmed.scenario().teacher().accessToken();
		UUID inquiryId = confirmed.inquiryId();
		CountDownLatch lockAcquired = new CountDownLatch(1);
		ExecutorService pool = Executors.newSingleThreadExecutor();
		try {
			Future<?> follower = holdRootLockThenReleaseAfterDelay(inquiryId, lockAcquired, 500, pool);
			assertThat(lockAcquired.await(5, TimeUnit.SECONDS)).as("재문의 제출 트랜잭션이 잠금을 쥐지 못했다").isTrue();

			long start = System.nanoTime();
			MockHttpServletResponse response = close(teacherToken, inquiryId);
			long waitedMillis = Duration.ofNanos(System.nanoTime() - start).toMillis();
			follower.get(5, TimeUnit.SECONDS);

			assertThat(waitedMillis).as("재문의 제출 트랜잭션이 끝날 때까지 기다려야 한다(잠금)").isGreaterThanOrEqualTo(400);
			assertThat(response.getStatus()).isEqualTo(200);
			assertThat(jdbc.queryForObject("select stage from inquiry where id = ?", String.class, inquiryId))
					.isEqualTo("ANSWERED");
		}
		finally {
			pool.shutdownNow();
		}
	}

}
