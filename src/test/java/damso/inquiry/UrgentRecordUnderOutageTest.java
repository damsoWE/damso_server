package damso.inquiry;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.mock.web.MockHttpServletResponse;

import damso.inquiry.adapter.out.persistence.InquiryStageHistoryRepository;
import damso.inquiry.application.port.out.InquiryStageHistoryPort;
import damso.inquiry.domain.InquiryStageHistory;
import damso.support.IntegrationTestSupport;
import damso.support.OperatorQueries;

/**
 * F5 T013 / FR-006, SC-006: 긴급 경로의 처리 기록은 외부 AI·알림 장애와 상관없이 남고, 기록이 실패하면 접수도 남지 않는다.
 * 운영 코드는 바꾸지 않는다 — F2의 접수 트랜잭션(문의 저장 + 단계 이력)과 커밋 뒤 외부 호출 구조를 증명하는 테스트다.
 */
class UrgentRecordUnderOutageTest extends IntegrationTestSupport {

	/** 단계 이력 저장을 실패시킬 수 있는 포트. 이 테스트 클래스에서만 쓴다. */
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

	private List<String> stages(UUID inquiryId) {
		return jdbc.queryForList("select stage from inquiry_stage_history where inquiry_id = ?", String.class,
				inquiryId);
	}

	@Test
	void urgentRecordsSurviveAnAiAndPushOutageTogether() throws Exception {
		Scenario s = teacherWithClassroom();
		registerDevice(s.teacher().userId(), sessionIdOf(s.teacher().accessToken()), "teacher-device-token");
		pushes.throwAll(true);
		for (int i = 0; i < 4; i++) {
			// 위기 종류 분류(자동 재시도 포함)가 모두 실패하게 한다
			fakeAi.throwNext();
		}

		MockHttpServletResponse root = submitInquiry(s.parent().accessToken(), s.student().getId(), URGENT_SAMPLE);
		assertThat(root.getStatus()).isEqualTo(201);
		UUID rootId = UUID.fromString(read(root, "$.inquiryId"));
		clock.advance(Duration.ofSeconds(61));
		MockHttpServletResponse followUp = submitFollowUp(s.parent().accessToken(), rootId, URGENT_SAMPLE);
		assertThat(followUp.getStatus()).isEqualTo(201);
		UUID followUpId = UUID.fromString(read(followUp, "$.inquiryId"));

		assertThat(pushes.sent()).isEmpty();
		assertThat(stages(rootId)).containsExactlyInAnyOrder("RECEIVED", "DELIVERED");
		assertThat(stages(followUpId)).containsExactlyInAnyOrder("RECEIVED", "DELIVERED");
		assertThat(jdbc.queryForList("select status from notification_request where kind = 'URGENT'", String.class))
				.hasSize(2).containsOnly("PENDING");
		// 운영자 조회문으로도 두 메시지의 접수·긴급 전달이 그대로 나온다
		List<String> events = OperatorQueries.threadEvents(jdbc, rootId);
		assertThat(events).containsSubsequence("INQUIRY_SUBMITTED", "STAGE_RECEIVED", "STAGE_DELIVERED",
				"FOLLOW_UP_SUBMITTED", "STAGE_RECEIVED", "STAGE_DELIVERED");
	}

	@Test
	void anUrgentSubmissionWithoutItsStageRecordIsNotKept() throws Exception {
		Scenario s = teacherWithClassroom();
		FailingStageHistoryConfig.failing = true;
		try {
			MockHttpServletResponse response = submitInquiry(s.parent().accessToken(), s.student().getId(),
					URGENT_SAMPLE);

			assertThat(response.getStatus()).isEqualTo(500);
		}
		finally {
			FailingStageHistoryConfig.failing = false;
		}
		// 처리는 됐는데 기록이 없는 상태가 없다 — 문의·알림 요청도 함께 롤백된다
		assertThat(jdbc.queryForObject("select count(*) from inquiry", Integer.class)).isZero();
		assertThat(jdbc.queryForObject("select count(*) from notification_request", Integer.class)).isZero();
		assertThat(jdbc.queryForObject("select count(*) from inquiry_stage_history", Integer.class)).isZero();
	}

}
