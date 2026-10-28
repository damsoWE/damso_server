package damso.inquiry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.mock.web.MockHttpServletResponse;

import damso.attempt.adapter.out.redis.RedisAttemptLimitAdapter;
import damso.attempt.application.port.out.AttemptLimitPort;
import damso.attempt.domain.AttemptKind;
import damso.attempt.domain.AttemptRule;
import damso.attempt.domain.AttemptState;
import damso.common.error.BusinessException;
import damso.common.error.ErrorCode;
import damso.inquiry.application.port.out.InquiryStageHistoryPort;
import damso.inquiry.domain.HotlineCode;
import damso.inquiry.domain.InquiryStage;
import damso.inquiry.domain.InquiryStageHistory;
import damso.inquiry.domain.NoticeCode;
import damso.support.IntegrationTestSupport;

/**
 * F2 US2 시나리오 1~6 / FR-009~011·015·016·019·020, SC-002·003·009:
 * 긴급 표현이 든 문의는 정제 없이 담임에게 전달되고, 1분 제한·Redis·푸시 장애와 무관하게 접수된다.
 */
class UrgentInquiryApiTest extends IntegrationTestSupport {

	/** 문의 제출 제한(INQUIRY_SUBMIT)을 끄고 켤 수 있고, 몇 번 불렸는지 센다. 이 테스트 클래스에서만 쓴다. */
	@TestConfiguration
	static class CountingSubmitLimitConfig {

		static final AtomicInteger submitCalls = new AtomicInteger();

		static volatile boolean down;

		@Bean
		@Primary
		AttemptLimitPort countingAttemptLimitPort(RedisAttemptLimitAdapter real) {
			return new AttemptLimitPort() {

				@Override
				public AttemptState get(AttemptKind kind, String subjectHash) {
					return real.get(kind, subjectHash);
				}

				@Override
				public AttemptState recordFailure(AttemptKind kind, String subjectHash, Instant now, AttemptRule rule) {
					return real.recordFailure(kind, subjectHash, now, rule);
				}

				@Override
				public void reset(AttemptKind kind, String subjectHash) {
					real.reset(kind, subjectHash);
				}

				@Override
				public long reserveRate(AttemptKind kind, String subjectHash, Instant now, Duration minGap,
						Duration window, int max) {
					if (kind == AttemptKind.INQUIRY_SUBMIT) {
						submitCalls.incrementAndGet();
						if (down) {
							throw new BusinessException(ErrorCode.INTERNAL_ERROR);
						}
					}
					return real.reserveRate(kind, subjectHash, now, minGap, window, max);
				}

				@Override
				public long reserveRates(Instant now, List<RateLimit> limits) {
					return real.reserveRates(now, limits);
				}

			};
		}

	}

	@Autowired
	private InquiryStageHistoryPort histories;

	private String stageOf(UUID inquiryId) {
		return jdbc.queryForObject("select stage from inquiry where id = ?", String.class, inquiryId);
	}

	@Test
	void urgentInquiryIsDeliveredWithoutRefining() throws Exception {
		Scenario s = teacherWithClassroom();

		MockHttpServletResponse response = submitInquiry(s.parent().accessToken(), s.student().getId(), URGENT_SAMPLE);

		assertThat(response.getStatus()).isEqualTo(201);
		assertThat((Boolean) read(response, "$.urgent")).isTrue();
		assertThat((String) read(response, "$.stage")).isEqualTo("DELIVERED");
		List<String> notices = read(response, "$.notices");
		assertThat(notices).containsExactly("URGENT_DELIVERED");
		List<String> hotlines = read(response, "$.hotlines");
		assertThat(hotlines).containsExactly("CHILD_ABUSE_112", "SUICIDE_PREVENTION_109");

		UUID inquiryId = UUID.fromString(read(response, "$.inquiryId"));
		assertThat(stageOf(inquiryId)).isEqualTo("DELIVERED");
		List<InquiryStageHistory> timeline = histories.findByInquiry(inquiryId);
		assertThat(timeline).extracting(InquiryStageHistory::getStage)
				.containsExactly(InquiryStage.RECEIVED, InquiryStage.DELIVERED);
	}

	@Test
	void urgentResponseIsFast() throws Exception {
		Scenario s = teacherWithClassroom();

		long start = System.nanoTime();
		MockHttpServletResponse response = submitInquiry(s.parent().accessToken(), s.student().getId(), URGENT_SAMPLE);
		long elapsedMillis = Duration.ofNanos(System.nanoTime() - start).toMillis();

		assertThat(response.getStatus()).isEqualTo(201);
		assertThat(elapsedMillis).as("SC-002 3초 이내").isLessThan(3000);
	}

	@Test
	void ambiguousExpressionsAreTreatedAsUrgent() throws Exception {
		Scenario s = teacherWithClassroom();
		List<String> contents = List.of("아이가 요즘 사라지고 싶다는 말을 자주 해서 걱정입니다.",
				"혹시 학교폭력인지 확실하지 않은데 한번 봐 주세요.", "집에서 있었던 일인데 아 이 가   자 해 같은 걸 한 것 같기도 해요.");

		for (String content : contents) {
			clock.advance(Duration.ofSeconds(61));
			MockHttpServletResponse response = submitInquiry(s.parent().accessToken(), s.student().getId(), content);
			assertThat(response.getStatus()).isEqualTo(201);
			assertThat((Boolean) read(response, "$.urgent")).as(content).isTrue();
		}
	}

	@Test
	void urgentIsNeitherLimitedNorRecordedByTheOneMinuteLimit() throws Exception {
		Scenario s = teacherWithClassroom();
		String token = s.parent().accessToken();
		UUID child = s.student().getId();

		// 일반 문의 직후의 긴급 문의는 제한 없이 접수된다
		assertThat(submitInquiry(token, child, NORMAL_SAMPLE).getStatus()).isEqualTo(201);
		MockHttpServletResponse urgent = submitInquiry(token, child, URGENT_SAMPLE);
		assertThat(urgent.getStatus()).isEqualTo(201);
		// 긴급 문의는 제한 시계를 시작하지 않는다 — 시간을 옮긴 뒤 첫 일반 문의만 제한을 받는다
		clock.advance(Duration.ofSeconds(61));
		MockHttpServletResponse urgentAgain = submitInquiry(token, child, URGENT_SAMPLE + " 다시");
		assertThat(urgentAgain.getStatus()).isEqualTo(201);
		assertThat(submitInquiry(token, child, NORMAL_SAMPLE + " 추가").getStatus()).isEqualTo(201);
		assertThat(submitInquiry(token, child, NORMAL_SAMPLE + " 또").getStatus()).isEqualTo(429);
	}

	@Test
	void urgentSkipsTheLimitStoreAndGeneralStillWorksWhenItIsDown() throws Exception {
		Scenario s = teacherWithClassroom();
		CountingSubmitLimitConfig.submitCalls.set(0);
		CountingSubmitLimitConfig.down = true;
		try {
			MockHttpServletResponse urgent = submitInquiry(s.parent().accessToken(), s.student().getId(), URGENT_SAMPLE);

			assertThat(urgent.getStatus()).isEqualTo(201);
			assertThat(CountingSubmitLimitConfig.submitCalls.get()).as("긴급 문의는 제한 저장소를 부르지 않는다").isZero();

			clock.advance(Duration.ofSeconds(61));
			MockHttpServletResponse general = submitInquiry(s.parent().accessToken(), s.student().getId(),
					NORMAL_SAMPLE);
			assertThat(general.getStatus()).isEqualTo(201);
			assertThat(CountingSubmitLimitConfig.submitCalls.get()).isEqualTo(1);
		}
		finally {
			CountingSubmitLimitConfig.down = false;
		}
	}

	@Test
	void urgentIsAcceptedWhenThereAreNoDevicesAndTheRequestStaysPending() throws Exception {
		Scenario s = teacherWithClassroom();

		MockHttpServletResponse response = submitInquiry(s.parent().accessToken(), s.student().getId(), URGENT_SAMPLE);

		assertThat(response.getStatus()).isEqualTo(201);
		assertThat(pushes.sent()).isEmpty();
		List<String> statuses = jdbc.queryForList("select status from notification_request where kind = 'URGENT'",
				String.class);
		assertThat(statuses).containsExactly("PENDING");
	}

	@Test
	void urgentIsAcceptedWhenThePushSenderThrows() throws Exception {
		Scenario s = teacherWithClassroom();
		registerDevice(s.teacher().userId(), sessionIdOf(s.teacher().accessToken()), "teacher-device-token");
		pushes.throwAll(true);

		MockHttpServletResponse response = submitInquiry(s.parent().accessToken(), s.student().getId(), URGENT_SAMPLE);

		assertThat(response.getStatus()).isEqualTo(201);
		assertThat(pushes.sent()).isEmpty();
		assertThat(jdbc.queryForObject("select count(*) from inquiry", Integer.class)).isEqualTo(1);
		assertThat(jdbc.queryForMap("select status, attempts from notification_request where kind = 'URGENT'"))
				.containsEntry("status", "PENDING").containsEntry("attempts", 1);
	}

	@Test
	void thereIsNoWayToChangeTheUrgentFlag() throws Exception {
		Scenario s = teacherWithClassroom();
		MockHttpServletResponse created = submitInquiry(s.parent().accessToken(), s.student().getId(), NORMAL_SAMPLE);
		String id = read(created, "$.inquiryId");

		for (MockHttpServletResponse response : List.of(
				send(json(patch("/api/v1/parent/inquiries/" + id), "{\"urgent\":true}"), s.parent().accessToken()),
				send(json(put("/api/v1/parent/inquiries/" + id + "/urgent"), "{\"urgent\":true}"),
						s.parent().accessToken()),
				send(json(patch("/api/v1/teacher/inquiries/" + id), "{\"urgent\":true}"),
						s.teacher().accessToken()))) {
			assertThat(response.getStatus()).isIn(404, 405);
		}
		assertThat(jdbc.queryForObject("select urgent from inquiry", Boolean.class)).isFalse();
	}

	@Test
	void guidanceInTheResponseIsCodesOnly() throws Exception {
		Scenario s = teacherWithClassroom();

		MockHttpServletResponse general = submitInquiry(s.parent().accessToken(), s.student().getId(), NORMAL_SAMPLE);
		clock.advance(Duration.ofSeconds(61));
		MockHttpServletResponse urgent = submitInquiry(s.parent().accessToken(), s.student().getId(), URGENT_SAMPLE);

		for (MockHttpServletResponse response : List.of(general, urgent)) {
			List<String> notices = read(response, "$.notices");
			List<String> hotlines = read(response, "$.hotlines");
			notices.forEach(code -> assertThat(NoticeCode.valueOf(code)).isNotNull());
			hotlines.forEach(code -> assertThat(HotlineCode.valueOf(code)).isNotNull());
			// 응답 최상위에 자유 문장 필드가 없다
			assertThat(response.getContentAsString()).doesNotContain("message").doesNotContain("guidance");
		}
	}

}
