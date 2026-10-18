package damso.inquiry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.mock.web.MockHttpServletResponse;

import damso.audit.adapter.out.persistence.AuditLogRepository;
import damso.audit.application.port.out.AuditLogPort;
import damso.audit.domain.AuditLog;
import damso.audit.domain.AuditTargetType;
import damso.support.IntegrationTestSupport;

/**
 * F2 US3 / 원칙 III, FR-022·025: 감사 기록 없이는 원문이 나가지 않고, 동시에 처음 열람해도 단계 이력은 한 줄이다.
 */
class ViewInquiryContentAtomicityTest extends IntegrationTestSupport {

	/** 감사 기록 저장을 실패시킬 수 있는 포트. 이 테스트 클래스에서만 쓴다. */
	@TestConfiguration
	static class FailingAuditConfig {

		static volatile boolean failing;

		@Bean
		@Primary
		AuditLogPort failingAuditLogPort(AuditLogRepository repository) {
			return new AuditLogPort() {

				@Override
				public AuditLog append(AuditLog log) {
					if (failing) {
						throw new IllegalStateException("audit store is down");
					}
					return repository.save(log);
				}

				@Override
				public List<AuditLog> findByActor(UUID actorId) {
					return repository.findByActorIdOrderByOccurredAt(actorId);
				}

				@Override
				public List<AuditLog> findByTarget(AuditTargetType targetType, UUID targetId) {
					return repository.findByTargetTypeAndTargetIdOrderByOccurredAt(targetType, targetId);
				}

			};
		}

	}

	private UUID urgentInquiry(Scenario s) throws Exception {
		MockHttpServletResponse created = submitInquiry(s.parent().accessToken(), s.student().getId(), URGENT_SAMPLE);
		assertThat(created.getStatus()).isEqualTo(201);
		return UUID.fromString(read(created, "$.inquiryId"));
	}

	@Test
	void contentIsNotReturnedAndNothingChangesWhenTheAuditRecordFails() throws Exception {
		Scenario s = teacherWithClassroom();
		UUID id = urgentInquiry(s);
		FailingAuditConfig.failing = true;
		try {
			MockHttpServletResponse response = send(get("/api/v1/teacher/inquiries/" + id + "/content"),
					s.teacher().accessToken());

			assertThat(response.getStatus()).isEqualTo(500);
			assertThat(response.getContentAsString()).doesNotContain(URGENT_SAMPLE).doesNotContain("content");
			assertThat(jdbc.queryForObject("select stage from inquiry where id = ?", String.class, id))
					.isEqualTo("DELIVERED");
			assertThat(jdbc.queryForObject("select count(*) from inquiry_stage_history where inquiry_id = ?",
					Integer.class, id)).isEqualTo(2);
			assertThat(jdbc.queryForObject("select count(*) from audit_log", Integer.class)).isZero();
		}
		finally {
			FailingAuditConfig.failing = false;
		}
	}

	@Test
	void concurrentFirstViewsConfirmOnceAndAuditBoth() throws Exception {
		Scenario s = teacherWithClassroom();
		UUID id = urgentInquiry(s);
		int callers = 2;
		ExecutorService pool = Executors.newFixedThreadPool(callers);
		CountDownLatch start = new CountDownLatch(1);
		try {
			List<Future<MockHttpServletResponse>> futures = new ArrayList<>();
			for (int i = 0; i < callers; i++) {
				futures.add(pool.submit(() -> {
					start.await();
					return send(get("/api/v1/teacher/inquiries/" + id + "/content"), s.teacher().accessToken());
				}));
			}
			start.countDown();
			for (Future<MockHttpServletResponse> future : futures) {
				MockHttpServletResponse response = future.get();
				assertThat(response.getStatus()).isEqualTo(200);
				assertThat((String) read(response, "$.content")).isEqualTo(URGENT_SAMPLE);
			}
		}
		finally {
			pool.shutdownNow();
		}

		assertThat(jdbc.queryForObject(
				"select count(*) from inquiry_stage_history where inquiry_id = ? and stage = 'TEACHER_CONFIRMED'",
				Integer.class, id)).isEqualTo(1);
		assertThat(jdbc.queryForObject("select count(*) from audit_log", Integer.class)).isEqualTo(2);
		assertThat(jdbc.queryForObject("select stage from inquiry where id = ?", String.class, id))
				.isEqualTo("TEACHER_CONFIRMED");
	}

}
