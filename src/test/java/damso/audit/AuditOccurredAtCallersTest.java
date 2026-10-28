package damso.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.TestPropertySource;

import damso.support.IntegrationTestSupport;

/**
 * PR #21 리뷰: 기록 시각에 허용 범위(5분)를 둔 뒤에도 시각을 직접 넘기는 두 호출부(재시도·종료, research R9)는 그대로 기록된다.
 * 앱 시계를 앞으로 옮긴 상태에서도 처리 시각이 그대로 남는지 본다.
 */
@TestPropertySource(properties = "damso.inquiry.close-enabled=true")
class AuditOccurredAtCallersTest extends IntegrationTestSupport {

	private int recordsBetween(String action, UUID targetId, Instant from, Instant to) {
		return jdbc.queryForObject("select count(*) from audit_log where action = ? and target_id = ? "
				+ "and occurred_at between ? and ?", Integer.class, action, targetId, Timestamp.from(from),
				Timestamp.from(to));
	}

	@Test
	void teacherRetryIsRecordedAtTheRequestTime() throws Exception {
		Scenario s = teacherWithClassroom();
		fakeAi.noResponseNext();
		fakeAi.noResponseNext();
		clock.advance(Duration.ofHours(1));
		MockHttpServletResponse submitted = submitInquiry(s.parent().accessToken(), s.student().getId(),
				NORMAL_SAMPLE);
		UUID id = UUID.fromString(read(submitted, "$.inquiryId"));

		Instant before = clock.instant().minusMillis(1);
		MockHttpServletResponse accepted = send(post("/api/v1/teacher/inquiries/" + id + "/refinement/retry"),
				s.teacher().accessToken());
		Instant after = clock.instant().plusMillis(1);

		assertThat(accepted.getStatus()).isEqualTo(202);
		assertThat(recordsBetween("REFINEMENT_RETRY_REQUESTED", id, before, after)).isEqualTo(1);
	}

	@Test
	void closeIsRecordedAtTheClosedTime() throws Exception {
		ConfirmedInquiry confirmed = urgentInquiryConfirmedByTeacher();
		clock.advance(Duration.ofHours(1));

		MockHttpServletResponse closed = send(post("/api/v1/teacher/inquiries/" + confirmed.inquiryId() + "/close"),
				confirmed.scenario().teacher().accessToken());

		assertThat(closed.getStatus()).isEqualTo(200);
		assertThat(jdbc.queryForObject("select count(*) from audit_log a join inquiry_stage_history h "
				+ "on h.inquiry_id = a.target_id and h.stage = 'ANSWERED' "
				+ "where a.action = 'INQUIRY_CLOSED' and a.occurred_at = h.occurred_at", Integer.class)).isEqualTo(1);
	}

}
