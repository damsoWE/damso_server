package damso.notification;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletResponse;

import damso.notification.application.service.NotificationDispatcher;
import damso.notification.domain.NotificationKind;
import damso.school.domain.Classroom;
import damso.support.FakePushSender.Sent;
import damso.support.IntegrationTestSupport;

/**
 * 긴급 알림의 즉시 발송·재시도·재알림·중복 방지.
 * 통합 테스트에서는 @Async가 동기 실행기라 "커밋 직후 즉시 발송"이 제출 응답 전에 끝난다.
 */
class UrgentNotificationTest extends IntegrationTestSupport {

	private static final String TOKEN = "teacher-device-token-1";

	@Autowired
	private NotificationDispatcher dispatcher;

	private Instant scheduledAt(String where) {
		return jdbc.queryForObject("select scheduled_at from notification_request where " + where,
				OffsetDateTime.class).toInstant();
	}

	/** 테스트 시계는 실제 시간과 함께 흐르므로(MutableClock은 앞으로 옮기기만 한다) 몇 초 오차는 허용한다. */
	private static void assertNear(Instant actual, Instant expected) {
		assertThat(actual).isBetween(expected.minusSeconds(5), expected.plusSeconds(5));
	}

	private int count(String where) {
		return jdbc.queryForObject("select count(*) from notification_request where " + where, Integer.class);
	}

	private Scenario scenarioWithTeacherDevice() throws Exception {
		Scenario s = teacherWithClassroom();
		registerDevice(s.teacher().userId(), sessionIdOf(s.teacher().accessToken()), TOKEN);
		return s;
	}

	@Test
	void deviceReceivesTheUrgentPushRightAfterSubmissionWithFixedTextOnly() throws Exception {
		Scenario s = scenarioWithTeacherDevice();

		MockHttpServletResponse response = submitInquiry(s.parent().accessToken(), s.student().getId(), URGENT_SAMPLE);

		assertThat(response.getStatus()).isEqualTo(201);
		List<Sent> sent = pushes.sent();
		assertThat(sent).hasSize(1);
		assertThat(sent.get(0).token()).isEqualTo(TOKEN);
		assertThat(sent.get(0).message().kind()).isEqualTo(NotificationKind.URGENT);
		assertThat(sent.get(0).message().inquiryId().toString()).isEqualTo(read(response, "$.inquiryId"));
		String text = sent.get(0).message().title() + sent.get(0).message().body() + sent.get(0).message();
		assertThat(text).doesNotContain("죽고").doesNotContain("김하나").doesNotContain("박부모");
		assertThat(count("kind = 'URGENT' and status = 'SENT'")).isEqualTo(1);
	}

	@Test
	void failedImmediateSendIsSentExactlyOnceByTheNextDispatcherRun() throws Exception {
		Scenario s = scenarioWithTeacherDevice();
		pushes.failAll(true);

		assertThat(submitInquiry(s.parent().accessToken(), s.student().getId(), URGENT_SAMPLE).getStatus())
				.isEqualTo(201);

		assertThat(pushes.sent()).isEmpty();
		assertThat(count("kind = 'URGENT' and status = 'PENDING' and attempts = 1")).isEqualTo(1);

		pushes.failAll(false);
		dispatcher.dispatchDue();
		assertThat(pushes.sent()).as("재시도 시각 전에는 보내지 않는다").isEmpty();

		clock.advance(Duration.ofSeconds(61));
		dispatcher.dispatchDue();
		dispatcher.dispatchDue();

		assertThat(pushes.sentOfKind(NotificationKind.URGENT)).hasSize(1);
		assertThat(count("kind = 'URGENT' and status = 'SENT'")).isEqualTo(1);
	}

	@Test
	void concurrentDispatchersSendAnUnsentRequestOnlyOnce() throws Exception {
		Scenario s = scenarioWithTeacherDevice();
		pushes.failAll(true);
		submitInquiry(s.parent().accessToken(), s.student().getId(), URGENT_SAMPLE);
		pushes.failAll(false);
		clock.advance(Duration.ofSeconds(61));

		int callers = 4;
		ExecutorService pool = Executors.newFixedThreadPool(callers);
		CountDownLatch start = new CountDownLatch(1);
		try {
			List<Future<Integer>> futures = new ArrayList<>();
			for (int i = 0; i < callers; i++) {
				futures.add(pool.submit(() -> {
					start.await();
					return dispatcher.dispatchDue();
				}));
			}
			start.countDown();
			int claimedInTotal = 0;
			for (Future<Integer> future : futures) {
				claimedInTotal += future.get();
			}
			assertThat(claimedInTotal).as("SKIP LOCKED + 임대: 같은 요청을 한 곳만 잡는다").isEqualTo(1);
		}
		finally {
			pool.shutdownNow();
		}

		assertThat(pushes.sentOfKind(NotificationKind.URGENT)).hasSize(1);
	}

	@Test
	void requestStuckInSendingIsTakenOverAfterTheLeaseExpires() throws Exception {
		Scenario s = scenarioWithTeacherDevice();
		pushes.failAll(true);
		submitInquiry(s.parent().accessToken(), s.student().getId(), URGENT_SAMPLE);
		pushes.failAll(false);
		clock.advance(Duration.ofMinutes(2));
		// 발송기가 임대한 채 멈춘 상황: SENDING + 60초 뒤까지 잠금
		Instant lockedUntil = clock.instant().plusSeconds(60);
		jdbc.update("update notification_request set status = 'SENDING', locked_until = ? where kind = 'URGENT'",
				java.sql.Timestamp.from(lockedUntil));

		dispatcher.dispatchDue();
		assertThat(pushes.sent()).as("임대 중에는 건드리지 않는다").isEmpty();

		clock.advance(Duration.ofSeconds(61));
		dispatcher.dispatchDue();

		assertThat(pushes.sentOfKind(NotificationKind.URGENT)).hasSize(1);
		assertThat(count("kind = 'URGENT' and status = 'SENT'")).isEqualTo(1);
	}

	@Test
	void retriesAfterOneFiveFifteenMinutesThenEveryHourForever() throws Exception {
		Scenario s = scenarioWithTeacherDevice();
		pushes.failAll(true);
		submitInquiry(s.parent().accessToken(), s.student().getId(), URGENT_SAMPLE);

		List<Duration> expected = List.of(Duration.ofMinutes(1), Duration.ofMinutes(5), Duration.ofMinutes(15),
				Duration.ofHours(1), Duration.ofHours(1), Duration.ofHours(1));
		for (int i = 0; i < expected.size(); i++) {
			assertNear(scheduledAt("kind = 'URGENT'"), clock.instant().plus(expected.get(i)));
			assertThat(count("kind = 'URGENT' and status = 'PENDING' and attempts = " + (i + 1))).isEqualTo(1);
			clock.advance(expected.get(i).plusSeconds(1));
			dispatcher.dispatchDue();
		}
		assertThat(pushes.sent()).isEmpty();

		// 복구되면 다음 시도에서 나간다
		pushes.failAll(false);
		clock.advance(Duration.ofHours(1).plusSeconds(1));
		dispatcher.dispatchDue();
		assertThat(pushes.sentOfKind(NotificationKind.URGENT)).hasSize(1);
	}

	@Test
	void everyActiveDeviceWithALiveSessionReceivesItAndOthersDoNot() throws Exception {
		Scenario s = teacherWithClassroom();
		registerDevice(s.teacher().userId(), sessionIdOf(s.teacher().accessToken()), "phone-token");
		registerDevice(s.teacher().userId(), sessionIdOf(s.teacher().accessToken()), "tablet-token");
		// 세션이 끝난(살아 있지 않은) 기기
		registerDevice(s.teacher().userId(), UUID.randomUUID(), "old-session-token");
		// 다른 사용자의 기기
		registerDevice(s.parent().userId(), sessionIdOf(s.parent().accessToken()), "parent-token");

		submitInquiry(s.parent().accessToken(), s.student().getId(), URGENT_SAMPLE);

		assertThat(pushes.sent()).extracting(Sent::token).containsExactlyInAnyOrder("phone-token", "tablet-token");
	}

	@Test
	void invalidTokenDeactivatesThatDeviceAndTheRequestWaitsForANewOne() throws Exception {
		Scenario s = scenarioWithTeacherDevice();
		pushes.markInvalid(TOKEN);

		submitInquiry(s.parent().accessToken(), s.student().getId(), URGENT_SAMPLE);

		assertThat(jdbc.queryForObject("select active from device_registration where token = ?", Boolean.class,
				TOKEN)).isFalse();
		assertThat(pushes.sent()).isEmpty();
		assertThat(count("kind = 'URGENT' and status = 'PENDING'")).isEqualTo(1);
	}

	@Test
	void remindsEveryHourWhileUnconfirmedAndStopsWhenConfirmed() throws Exception {
		Scenario s = scenarioWithTeacherDevice();
		MockHttpServletResponse created = submitInquiry(s.parent().accessToken(), s.student().getId(), URGENT_SAMPLE);
		UUID inquiryId = UUID.fromString(read(created, "$.inquiryId"));
		assertNear(scheduledAt("kind = 'URGENT_REMINDER'"), clock.instant().plus(Duration.ofHours(1)));

		clock.advance(Duration.ofMinutes(59));
		dispatcher.dispatchDue();
		assertThat(pushes.sentOfKind(NotificationKind.URGENT_REMINDER)).isEmpty();

		clock.advance(Duration.ofMinutes(2));
		dispatcher.dispatchDue();
		assertThat(pushes.sentOfKind(NotificationKind.URGENT_REMINDER)).hasSize(1);
		assertThat(count("kind = 'URGENT_REMINDER' and status = 'PENDING'")).as("다음 재알림이 한 건 잡힌다").isEqualTo(1);

		clock.advance(Duration.ofHours(1));
		dispatcher.dispatchDue();
		assertThat(pushes.sentOfKind(NotificationKind.URGENT_REMINDER)).hasSize(2);

		// 교사가 확인하면 다음 재알림은 보내지 않고 취소된다
		jdbc.update("update inquiry set stage = 'TEACHER_CONFIRMED' where id = ?", inquiryId);
		clock.advance(Duration.ofHours(1).plusSeconds(1));
		dispatcher.dispatchDue();
		assertThat(pushes.sentOfKind(NotificationKind.URGENT_REMINDER)).hasSize(2);
		assertThat(count("kind = 'URGENT_REMINDER' and status = 'CANCELLED'")).isEqualTo(1);
		assertThat(count("kind = 'URGENT_REMINDER' and status = 'PENDING'")).isZero();
	}

	@Test
	void reminderIsCancelledWhenTheHomeroomTeacherIsReleased() throws Exception {
		Scenario s = scenarioWithTeacherDevice();
		submitInquiry(s.parent().accessToken(), s.student().getId(), URGENT_SAMPLE);
		Classroom stored = classroomPort.findClassroomById(s.classroom().getId()).orElseThrow();
		stored.releaseTeacher();
		classroomPort.saveClassroom(stored);

		clock.advance(Duration.ofHours(1).plusSeconds(1));
		dispatcher.dispatchDue();

		assertThat(pushes.sentOfKind(NotificationKind.URGENT_REMINDER)).isEmpty();
		assertThat(count("kind = 'URGENT_REMINDER' and status = 'CANCELLED'")).isEqualTo(1);
	}

	@Test
	void sendsToTheCurrentHomeroomTeacherEvenIfItChangedAfterSubmission() throws Exception {
		Scenario s = scenarioWithTeacherDevice();
		pushes.failAll(true);
		submitInquiry(s.parent().accessToken(), s.student().getId(), URGENT_SAMPLE);
		pushes.failAll(false);

		// 담임이 바뀐다: 이전 담임이 해제되고 새 교사가 가입해 담임이 된다
		Classroom stored = classroomPort.findClassroomById(s.classroom().getId()).orElseThrow();
		stored.releaseTeacher();
		classroomPort.saveClassroom(stored);
		SignedUp newTeacher = signUpWith(issueTeacherCode(s.classroom()), "새선생");
		registerDevice(newTeacher.userId(), sessionIdOf(newTeacher.accessToken()), "new-teacher-token");

		clock.advance(Duration.ofSeconds(61));
		dispatcher.dispatchDue();

		assertThat(pushes.sent()).extracting(Sent::token).containsExactly("new-teacher-token");
	}

	@Test
	void pendingUrgentIsSentOnTheFirstRunAfterADeviceIsRegistered() throws Exception {
		Scenario s = teacherWithClassroom();
		submitInquiry(s.parent().accessToken(), s.student().getId(), URGENT_SAMPLE);
		assertThat(pushes.sent()).isEmpty();
		assertThat(count("kind = 'URGENT' and status = 'PENDING'")).isEqualTo(1);

		registerDevice(s.teacher().userId(), sessionIdOf(s.teacher().accessToken()), TOKEN);
		dispatcher.dispatchDue();

		assertThat(pushes.sentOfKind(NotificationKind.URGENT)).hasSize(1);
	}

}
