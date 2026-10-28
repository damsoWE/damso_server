package damso.inquiry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.TestPropertySource;

import damso.audit.adapter.out.persistence.AuditLogRepository;
import damso.audit.application.port.out.AuditLogPort;
import damso.audit.domain.AuditLog;
import damso.audit.domain.AuditTargetType;
import damso.support.IntegrationTestSupport;

/**
 * F5 FR-005·006, research R9 [팀 결정 2026-10-28]: 재시도·종료는 처리가 커밋된 뒤 감사 기록을 남긴다. 기록이 실패해도
 * 재시도는 202, 종료는 200이고 상태는 정상으로 바뀌며, 실패는 문의 식별자·행위 종류·오류 종류만 로그에 남는다.
 */
@ExtendWith(OutputCaptureExtension.class)
@TestPropertySource(properties = "damso.inquiry.close-enabled=true")
class ActionAuditFailureTest extends IntegrationTestSupport {

	private static final String BODY = "처리감사검사본문ZXQ" + "9401";

	/** 감사 기록 저장을 실패시킬 수 있는 포트. 예외 메시지에 본문을 섞어 로그에 새지 않는지도 본다. */
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
						throw new IllegalStateException("audit store is down " + BODY);
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

	private int auditCount(String action) {
		return jdbc.queryForObject("select count(*) from audit_log where action = ?", Integer.class, action);
	}

	@Test
	void retryCompletesAndOnlyIdentifiersAreLoggedWhenTheAuditRecordFails(CapturedOutput output) throws Exception {
		Scenario s = teacherWithClassroom();
		fakeAi.noResponseNext();
		fakeAi.noResponseNext();
		UUID id = UUID.fromString(read(submitInquiry(s.parent().accessToken(), s.student().getId(), BODY),
				"$.inquiryId"));
		assertThat(jdbc.queryForObject("select refinement_status from inquiry where id = ?", String.class, id))
				.isEqualTo("NEEDS_ORIGINAL");
		FailingAuditConfig.failing = true;
		MockHttpServletResponse response;
		try {
			response = send(post("/api/v1/teacher/inquiries/" + id + "/refinement/retry"), s.teacher().accessToken());
		}
		finally {
			FailingAuditConfig.failing = false;
		}

		assertThat(response.getStatus()).isEqualTo(202);
		// 재시도는 커밋되어 실행됐다 — 기회를 썼고 정리가 끝났다
		assertThat(jdbc.queryForObject("select refinement_status from inquiry where id = ?", String.class, id))
				.isEqualTo("REFINED");
		assertThat(jdbc.queryForObject("select teacher_retry_used from refinement_job where inquiry_id = ? "
				+ "and kind = 'REFINE'", Boolean.class, id)).isTrue();
		assertThat(auditCount("REFINEMENT_RETRY_REQUESTED")).isZero();
		String logs = output.getAll();
		assertThat(logs).contains("재시도 요청 감사 기록 실패 (inquiry=" + id + ", action=REFINEMENT_RETRY_REQUESTED, "
				+ "type=IllegalStateException)");
		assertThat(logs).doesNotContain(BODY).doesNotContain(STUDENT_NAME);
	}

	@Test
	void closeCompletesAndOnlyIdentifiersAreLoggedWhenTheAuditRecordFails(CapturedOutput output) throws Exception {
		ConfirmedInquiry confirmed = urgentInquiryConfirmedByTeacher();
		UUID id = confirmed.inquiryId();
		FailingAuditConfig.failing = true;
		MockHttpServletResponse response;
		try {
			response = send(post("/api/v1/teacher/inquiries/" + id + "/close"),
					confirmed.scenario().teacher().accessToken());
		}
		finally {
			FailingAuditConfig.failing = false;
		}

		assertThat(response.getStatus()).isEqualTo(200);
		assertThat((String) read(response, "$.inquiryId")).isEqualTo(id.toString());
		assertThat(jdbc.queryForObject("select stage from inquiry where id = ?", String.class, id))
				.isEqualTo("ANSWERED");
		assertThat(jdbc.queryForObject(
				"select count(*) from inquiry_stage_history where inquiry_id = ? and stage = 'ANSWERED'",
				Integer.class, id)).isEqualTo(1);
		assertThat(auditCount("INQUIRY_CLOSED")).isZero();
		String logs = output.getAll();
		assertThat(logs).contains(
				"종료 감사 기록 실패 (inquiry=" + id + ", action=INQUIRY_CLOSED, type=IllegalStateException)");
		assertThat(logs).doesNotContain(BODY).doesNotContain(URGENT_SAMPLE);
	}

	@Test
	void theCloseRecordCarriesTheCloseTimeEvenThoughItIsWrittenAfterTheCommit() throws Exception {
		ConfirmedInquiry confirmed = urgentInquiryConfirmedByTeacher();
		UUID id = confirmed.inquiryId();

		MockHttpServletResponse response = send(post("/api/v1/teacher/inquiries/" + id + "/close"),
				confirmed.scenario().teacher().accessToken());

		assertThat(response.getStatus()).isEqualTo(200);
		assertThat(jdbc.queryForObject("select occurred_at from audit_log where action = 'INQUIRY_CLOSED'",
				java.sql.Timestamp.class))
				.isEqualTo(jdbc.queryForObject("select occurred_at from inquiry_stage_history where inquiry_id = ? "
						+ "and stage = 'ANSWERED'", java.sql.Timestamp.class, id));
	}

}
