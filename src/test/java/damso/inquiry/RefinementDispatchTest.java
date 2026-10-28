package damso.inquiry;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

import damso.gateway.application.port.out.ExternalAiPort.AiTask;
import damso.support.IntegrationTestSupport;

/**
 * 커밋 직후 즉시 실행을 끄고(안전망만) 정리 확정 전 상태와 실행기 동작을 본다.
 * 접수는 외부 AI를 기다리지 않고, 같은 작업은 한 번만 실행되며, 멈춘 작업은 임대가 끝나면 다시 실행된다.
 */
@TestPropertySource(properties = "damso.refinement.dispatch-on-commit=false")
class RefinementDispatchTest extends IntegrationTestSupport {

	private UUID submitGeneral(Scenario s) throws Exception {
		return UUID.fromString(read(submitInquiry(s.parent().accessToken(), s.student().getId(),
				generalInquiryNaming(s)), "$.inquiryId"));
	}

	@Test
	void submissionLeavesAPendingJobAndDoesNotWaitForTheExternalAi() throws Exception {
		Scenario s = teacherWithClassroom();
		UUID id = submitGeneral(s);

		assertThat(fakeAi.calls()).isEmpty();
		assertThat(jdbc.queryForObject("select status from refinement_job where inquiry_id = ?", String.class, id))
				.isEqualTo("PENDING");
		assertThat(jdbc.queryForObject("select refinement_status from inquiry where id = ?", String.class, id))
				.isEqualTo("PENDING");

		assertThat(runRefinement()).isEqualTo(1);
		assertThat(fakeAi.callsOf(AiTask.REFINE)).hasSize(1);
		assertThat(jdbc.queryForObject("select status from refinement_job where inquiry_id = ?", String.class, id))
				.isEqualTo("DONE");
		assertThat(runRefinement()).isZero();
	}

	@Test
	void pipelineItselfFinishesWellUnderASecond() throws Exception {
		Scenario s = teacherWithClassroom();
		UUID id = submitGeneral(s);

		long started = System.nanoTime();
		runRefinement();
		Duration elapsed = Duration.ofNanos(System.nanoTime() - started);

		// 가짜 외부 AI는 지연 없이 답한다 — 남는 시간은 가명화·검증·저장뿐이다
		assertThat(elapsed).isLessThan(Duration.ofSeconds(1));
		assertThat(jdbc.queryForObject("select count(*) from inquiry_refinement where inquiry_id = ?", Integer.class,
				id)).isEqualTo(1);
	}

	@Test
	void theSameJobRunsOnlyOnceEvenWhenTwoRunnersRace() throws Exception {
		Scenario s = teacherWithClassroom();
		UUID id = submitGeneral(s);
		UUID jobId = jdbc.queryForObject("select id from refinement_job where inquiry_id = ?", UUID.class, id);

		ExecutorService pool = Executors.newFixedThreadPool(2);
		CountDownLatch start = new CountDownLatch(1);
		try {
			List<Future<?>> futures = new ArrayList<>();
			futures.add(pool.submit(() -> {
				start.await();
				refinementRunner.dispatchOne(jobId);
				return null;
			}));
			futures.add(pool.submit(() -> {
				start.await();
				return refinementRunner.dispatchDue();
			}));
			start.countDown();
			for (Future<?> future : futures) {
				future.get();
			}
		}
		finally {
			pool.shutdownNow();
		}

		assertThat(fakeAi.callsOf(AiTask.REFINE)).hasSize(1);
		assertThat(jdbc.queryForObject("select count(*) from inquiry_stage_history where inquiry_id = ? and stage = "
				+ "'REFINING'", Integer.class, id)).isEqualTo(1);
	}

	@Test
	void theSafetyNetClaimsAndFinishesJobsOneAtATime() throws Exception {
		Scenario s = teacherWithClassroom();
		UUID first = submitGeneral(s);
		clock.advance(Duration.ofSeconds(61));
		UUID second = submitGeneral(s);
		clock.advance(Duration.ofSeconds(61));
		UUID third = submitGeneral(s);

		assertThat(runRefinement()).isEqualTo(3);

		for (UUID id : List.of(first, second, third)) {
			assertThat(jdbc.queryForObject("select status from refinement_job where inquiry_id = ?", String.class, id))
					.isEqualTo("DONE");
			assertThat(jdbc.queryForObject("select claim_count from refinement_job where inquiry_id = ?",
					Integer.class, id)).isEqualTo(1);
		}
		assertThat(fakeAi.callsOf(AiTask.REFINE)).hasSize(3);
	}

	@Test
	void aJobWhoseResultKeepsFailingToApplyIsGivenUpWithoutCallingTheAiAgain() throws Exception {
		// 반영이 거듭 실패해 같은 작업이 계속 다시 확보되는 상황: 확보 횟수가 상한을 넘으면 외부 AI 없이 실패로 끝낸다(자체 코드 리뷰 3번)
		Scenario s = teacherWithClassroom();
		UUID id = submitGeneral(s);
		jdbc.update("update refinement_job set status = 'RUNNING', locked_until = ?, claim_count = 5 "
				+ "where inquiry_id = ?", java.sql.Timestamp.from(clock.instant().minusSeconds(1)), id);

		assertThat(runRefinement()).isEqualTo(1);

		assertThat(fakeAi.calls()).isEmpty();
		assertThat(jdbc.queryForObject("select status from refinement_job where inquiry_id = ?", String.class, id))
				.isEqualTo("FAILED");
		assertThat(jdbc.queryForObject("select refinement_status from inquiry where id = ?", String.class, id))
				.isEqualTo("NEEDS_ORIGINAL");
		assertThat(jdbc.queryForObject("select count(*) from notification_request where kind = 'GENERAL'",
				Integer.class)).as("원문 확인 필요로 확정됐으니 교사에게 알린다").isEqualTo(1);
		assertThat(runRefinement()).isZero();
	}

	@Test
	void aJobStuckInRunningIsRetakenAfterTheLeaseExpires() throws Exception {
		Scenario s = teacherWithClassroom();
		UUID id = submitGeneral(s);
		// 실행 중에 서버가 멈춘 상황: RUNNING, 임대는 지금부터 60초
		jdbc.update("update refinement_job set status = 'RUNNING', locked_until = ? where inquiry_id = ?",
				java.sql.Timestamp.from(clock.instant().plusSeconds(60)), id);

		assertThat(runRefinement()).isZero();
		clock.advance(Duration.ofSeconds(61));
		assertThat(runRefinement()).isEqualTo(1);
		assertThat(jdbc.queryForObject("select status from refinement_job where inquiry_id = ?", String.class, id))
				.isEqualTo("DONE");
	}

}
