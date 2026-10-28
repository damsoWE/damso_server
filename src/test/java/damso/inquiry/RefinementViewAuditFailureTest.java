package damso.inquiry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.mock.web.MockHttpServletResponse;

import damso.audit.adapter.out.persistence.AuditLogRepository;
import damso.audit.application.port.out.AuditLogPort;
import damso.audit.domain.AuditLog;
import damso.audit.domain.AuditTargetType;
import damso.inquiry.adapter.out.persistence.InquiryStageHistoryRepository;
import damso.inquiry.application.port.out.InquiryStageHistoryPort;
import damso.inquiry.domain.InquiryStageHistory;
import damso.support.FakeExternalAi;
import damso.support.IntegrationTestSupport;

/**
 * [팀 결정 2026-10-27] 번역본은 감사 기록이 실패해도 정상으로 나가고, 실패는 서버 로그에
 * 문의 식별자·행위 종류·오류 종류만 남는다. 원문은 기록이 실패하면 나가지 않는다.
 * 확인 전이가 충돌이 아닌 이유로 실패하면(감사 실패가 아님) 시도·실패가 기록되고 번역본은 나가지 않는다.
 */
@ExtendWith(OutputCaptureExtension.class)
class RefinementViewAuditFailureTest extends IntegrationTestSupport {

	private static final String TITLE = "번역본제목검사ZXQ" + "8101";

	private static final String FACTS = "번역본사실관계검사ZXQ" + "8102";

	private static final String REQUESTS = "번역본요청사항검사ZXQ" + "8103";

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
						throw new IllegalStateException("audit store is down " + FACTS);
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

	/** 단계 이력 저장을 낙관적 락이 아닌 데이터 접근 오류로 실패시킬 수 있는 포트. 이 테스트 클래스에서만 쓴다. */
	@TestConfiguration
	static class FailingStageHistoryConfig {

		static volatile boolean failing;

		@Bean
		@Primary
		InquiryStageHistoryPort failingInquiryStageHistoryPort(InquiryStageHistoryRepository repository) {
			return new InquiryStageHistoryPort() {

				@Override
				public InquiryStageHistory append(InquiryStageHistory history) {
					if (failing) {
						throw new DataAccessResourceFailureException("stage history store is down");
					}
					return repository.save(history);
				}

				@Override
				public List<InquiryStageHistory> findByInquiry(UUID inquiryId) {
					return repository.findByInquiryIdOrderByOccurredAt(inquiryId);
				}

			};
		}

	}

	private UUID refinedInquiry(Scenario s) throws Exception {
		fakeAi.replyNext(FakeExternalAi.refinementJson(TITLE, FACTS, REQUESTS, "LOW", List.of()));
		UUID id = UUID.fromString(read(submitInquiry(s.parent().accessToken(), s.student().getId(), NORMAL_SAMPLE),
				"$.inquiryId"));
		assertThat(stage(id)).isEqualTo("REFINING");
		return id;
	}

	private MockHttpServletResponse refinement(String token, UUID id) throws Exception {
		return send(get("/api/v1/teacher/inquiries/" + id + "/refinement"), token);
	}

	private String stage(UUID id) {
		return jdbc.queryForObject("select stage from inquiry where id = ?", String.class, id);
	}

	@Test
	void translationIsReturnedAndOnlyIdentifiersAreLoggedWhenTheAuditRecordFails(CapturedOutput output)
			throws Exception {
		Scenario s = teacherWithClassroom();
		UUID id = refinedInquiry(s);
		FailingAuditConfig.failing = true;
		try {
			MockHttpServletResponse response = refinement(s.teacher().accessToken(), id);

			assertThat(response.getStatus()).isEqualTo(200);
			assertThat((String) read(response, "$.title")).isEqualTo(TITLE);
			assertThat((String) read(response, "$.facts")).isEqualTo(FACTS);
			assertThat((String) read(response, "$.requests")).isEqualTo(REQUESTS);
			// 기록 실패와 상관없이 첫 열람의 확인 전이는 일어난다
			assertThat(stage(id)).isEqualTo("TEACHER_CONFIRMED");
			assertThat(jdbc.queryForObject("select count(*) from audit_log", Integer.class)).isZero();
		}
		finally {
			FailingAuditConfig.failing = false;
		}

		String logs = output.getAll();
		assertThat(logs).contains("inquiry=" + id).contains("action=INQUIRY_REFINEMENT_VIEW_ATTEMPTED")
				.contains("action=INQUIRY_REFINEMENT_VIEWED").contains("type=IllegalStateException");
		// 예외 메시지에 섞인 값까지 포함해 본문·번역본·제목·학생 이름이 로그에 없다
		assertThat(logs).doesNotContain(TITLE).doesNotContain(FACTS).doesNotContain(REQUESTS)
				.doesNotContain(NORMAL_SAMPLE).doesNotContain(STUDENT_NAME);
	}

	@Test
	void originalContentIsStillWithheldWhenTheAuditRecordFails() throws Exception {
		Scenario s = teacherWithClassroom();
		UUID id = refinedInquiry(s);
		FailingAuditConfig.failing = true;
		try {
			MockHttpServletResponse response = send(get("/api/v1/teacher/inquiries/" + id + "/content"),
					s.teacher().accessToken());

			assertThat(response.getStatus()).isEqualTo(500);
			assertThat(response.getContentAsString()).doesNotContain(NORMAL_SAMPLE);
		}
		finally {
			FailingAuditConfig.failing = false;
		}
	}

	@Test
	void translationIsWithheldWhenTheConfirmationFailsForAReasonOtherThanAConflict() throws Exception {
		Scenario s = teacherWithClassroom();
		UUID id = refinedInquiry(s);
		FailingStageHistoryConfig.failing = true;
		try {
			MockHttpServletResponse response = refinement(s.teacher().accessToken(), id);

			assertThat(response.getStatus()).isEqualTo(500);
			assertThat(response.getContentAsString()).doesNotContain(FACTS).doesNotContain(REQUESTS)
					.doesNotContain(TITLE);
			assertThat(stage(id)).isEqualTo("REFINING");
			assertThat(jdbc.queryForList("select action from audit_log", String.class))
					.containsExactlyInAnyOrder("INQUIRY_REFINEMENT_VIEW_ATTEMPTED", "INQUIRY_REFINEMENT_VIEW_FAILED");
		}
		finally {
			FailingStageHistoryConfig.failing = false;
		}
	}

}
