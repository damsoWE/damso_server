package damso.inquiry;

import static damso.support.ErrorResponseAssertions.assertSafeErrorResponse;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
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
import damso.school.domain.Classroom;
import damso.school.domain.Student;
import damso.support.IntegrationTestSupport;

/**
 * F2 US1 시나리오 7~8·10~11 / FR-002a·002b, SC-014: 중복 제출(같은 결과)·1분 제한·같은 키 다른 내용(409)·
 * 제한 저장소 장애 시 일반 문의도 접수(fail-open).
 */
@ExtendWith(OutputCaptureExtension.class)
class SubmitInquiryRateLimitTest extends IntegrationTestSupport {

	/** 문의 제출 제한(INQUIRY_SUBMIT)만 Redis 장애처럼 실패시키는 포트. 이 테스트 클래스에서만 쓴다. */
	@TestConfiguration
	static class FailingSubmitLimitConfig {

		static volatile boolean down;

		@Bean
		@Primary
		AttemptLimitPort toggleableAttemptLimitPort(RedisAttemptLimitAdapter real) {
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
					if (down && kind == AttemptKind.INQUIRY_SUBMIT) {
						throw new BusinessException(ErrorCode.INTERNAL_ERROR);
					}
					return real.reserveRate(kind, subjectHash, now, minGap, window, max);
				}

			};
		}

	}

	private int inquiryCount() {
		return jdbc.queryForObject("select count(*) from inquiry", Integer.class);
	}

	@Test
	void sameKeyTwiceCreatesOneInquiryAndReturnsTheSameResultWithoutHittingTheLimit() throws Exception {
		Scenario s = teacherWithClassroom();
		UUID key = UUID.randomUUID();

		MockHttpServletResponse first = submitInquiry(s.parent().accessToken(), s.student().getId(), NORMAL_SAMPLE, key);
		MockHttpServletResponse second = submitInquiry(s.parent().accessToken(), s.student().getId(), NORMAL_SAMPLE, key);

		assertThat(first.getStatus()).isEqualTo(201);
		assertThat(second.getStatus()).isEqualTo(201);
		assertThat((String) read(second, "$.inquiryId")).isEqualTo(read(first, "$.inquiryId"));
		assertThat((String) read(second, "$.submittedAt")).isEqualTo(read(first, "$.submittedAt"));
		assertThat(inquiryCount()).isEqualTo(1);
		assertThat(jdbc.queryForObject("select count(*) from inquiry_stage_history", Integer.class)).isEqualTo(1);
	}

	@Test
	void concurrentSubmissionsWithTheSameKeyCreateOneInquiry() throws Exception {
		Scenario s = teacherWithClassroom();
		UUID key = UUID.randomUUID();
		int callers = 4;
		ExecutorService pool = Executors.newFixedThreadPool(callers);
		CountDownLatch start = new CountDownLatch(1);
		try {
			List<Future<MockHttpServletResponse>> futures = new ArrayList<>();
			for (int i = 0; i < callers; i++) {
				Callable<MockHttpServletResponse> call = () -> {
					start.await();
					return submitInquiry(s.parent().accessToken(), s.student().getId(), NORMAL_SAMPLE, key);
				};
				futures.add(pool.submit(call));
			}
			start.countDown();

			// 동시 재시도도 제한에 걸리지 않고 모두 같은 접수 결과를 받는다 (FR-002a)
			Set<String> ids = new HashSet<>();
			Set<String> submittedAts = new HashSet<>();
			for (Future<MockHttpServletResponse> future : futures) {
				MockHttpServletResponse response = future.get();
				assertThat(response.getStatus()).isEqualTo(201);
				ids.add(read(response, "$.inquiryId"));
				submittedAts.add(read(response, "$.submittedAt"));
			}
			assertThat(ids).hasSize(1);
			assertThat(submittedAts).hasSize(1);
			assertThat(inquiryCount()).isEqualTo(1);
		}
		finally {
			pool.shutdownNow();
		}
	}

	@Test
	void missingOrMalformedIdempotencyKeyIsRejected() throws Exception {
		Scenario s = teacherWithClassroom();
		String body = "{\"studentId\":\"" + s.student().getId() + "\",\"content\":\"" + NORMAL_SAMPLE + "\"}";

		MockHttpServletResponse missing = send(json(post("/api/v1/parent/inquiries"), body), s.parent().accessToken());
		assertThat(missing.getStatus()).isEqualTo(400);
		assertSafeErrorResponse(missing.getContentAsString(), "INVALID_INPUT");

		for (String bad : List.of("not-a-uuid", "12345", " ", "1-1-1-1-1")) {
			MockHttpServletResponse response = send(
					json(post("/api/v1/parent/inquiries"), body).header("Idempotency-Key", bad),
					s.parent().accessToken());
			assertThat(response.getStatus()).as(bad).isEqualTo(400);
			assertSafeErrorResponse(response.getContentAsString(), "INVALID_INPUT");
		}
		assertThat(inquiryCount()).isZero();
	}

	@Test
	void secondGeneralInquiryWithinAMinuteIsRejectedWithRetryAfter() throws Exception {
		Scenario s = teacherWithClassroom();
		String token = s.parent().accessToken();

		assertThat(submitInquiry(token, s.student().getId(), NORMAL_SAMPLE).getStatus()).isEqualTo(201);
		MockHttpServletResponse limited = submitInquiry(token, s.student().getId(), NORMAL_SAMPLE + " 추가");

		assertThat(limited.getStatus()).isEqualTo(429);
		assertSafeErrorResponse(limited.getContentAsString(), "TOO_MANY_ATTEMPTS");
		long retryAfter = Long.parseLong(limited.getHeader("Retry-After"));
		assertThat(retryAfter).isBetween(1L, 60L);
		assertThat(inquiryCount()).isEqualTo(1);

		clock.advance(Duration.ofSeconds(61));
		assertThat(submitInquiry(token, s.student().getId(), NORMAL_SAMPLE + " 추가").getStatus()).isEqualTo(201);
		assertThat(inquiryCount()).isEqualTo(2);
	}

	@Test
	void otherParentsAreNotAffectedAndTheLimitIsPerParentNotPerChild() throws Exception {
		Scenario s = teacherWithClassroom();
		Student sibling = newStudent(s.classroom(), "김둘");
		MockHttpServletResponse linked = send(json(post("/api/v1/parent/children"),
				"{\"inviteCode\":\"" + issueParentCode(sibling) + "\"}"), s.parent().accessToken());
		assertThat(linked.getStatus()).isEqualTo(201);
		Classroom otherClassroom = newClassroom(2, 2);
		Student otherChild = newStudent(otherClassroom, "이셋");
		signUpWith(issueTeacherCode(otherClassroom), "다른선생");
		SignedUp otherParent = signUpWith(issueParentCode(otherChild), "정부모");

		assertThat(submitInquiry(s.parent().accessToken(), s.student().getId(), NORMAL_SAMPLE).getStatus())
				.isEqualTo(201);
		// 같은 학부모의 다른 자녀도 같은 제한
		assertThat(submitInquiry(s.parent().accessToken(), sibling.getId(), NORMAL_SAMPLE).getStatus()).isEqualTo(429);
		// 다른 학부모는 영향 없음
		assertThat(submitInquiry(otherParent.accessToken(), otherChild.getId(), NORMAL_SAMPLE).getStatus())
				.isEqualTo(201);
	}

	@Test
	void sameKeyWithDifferentContentOrChildIsAConflict() throws Exception {
		Scenario s = teacherWithClassroom();
		Student sibling = newStudent(s.classroom(), "김둘");
		MockHttpServletResponse linked = send(json(post("/api/v1/parent/children"),
				"{\"inviteCode\":\"" + issueParentCode(sibling) + "\"}"), s.parent().accessToken());
		assertThat(linked.getStatus()).isEqualTo(201);
		UUID key = UUID.randomUUID();
		String token = s.parent().accessToken();
		assertThat(submitInquiry(token, s.student().getId(), NORMAL_SAMPLE, key).getStatus()).isEqualTo(201);

		MockHttpServletResponse otherContent = submitInquiry(token, s.student().getId(), NORMAL_SAMPLE + " 수정", key);
		MockHttpServletResponse otherChild = submitInquiry(token, sibling.getId(), NORMAL_SAMPLE, key);

		assertThat(otherContent.getStatus()).isEqualTo(409);
		assertSafeErrorResponse(otherContent.getContentAsString(), "CONFLICT");
		assertThat(otherChild.getStatus()).isEqualTo(409);
		assertThat(inquiryCount()).isEqualTo(1);
	}

	@Test
	void generalInquiryIsAcceptedWhenTheLimitStoreIsDown(CapturedOutput output) throws Exception {
		Scenario s = teacherWithClassroom();
		FailingSubmitLimitConfig.down = true;
		try {
			MockHttpServletResponse response = submitInquiry(s.parent().accessToken(), s.student().getId(),
					NORMAL_SAMPLE);

			assertThat(response.getStatus()).isEqualTo(201);
			assertThat(inquiryCount()).isEqualTo(1);
			assertThat(output.getAll()).contains("INQUIRY_SUBMIT").doesNotContain(NORMAL_SAMPLE);
		}
		finally {
			FailingSubmitLimitConfig.down = false;
		}
	}

}
