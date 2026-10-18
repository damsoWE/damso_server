package damso.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletResponse;

import damso.notification.application.port.out.NotificationSettingPort;
import damso.notification.application.service.NotificationDispatcher;
import damso.notification.domain.AvailabilityCalendar;
import damso.notification.domain.AvailabilityWindow;
import damso.notification.domain.NotificationKind;
import damso.notification.domain.TeacherNotificationSetting;
import damso.support.FakePushSender.Sent;
import damso.support.IntegrationTestSupport;

/**
 * F2 US5 시나리오 1~6 / FR-027~030, SC-007·011: 응답 가능 시간 안에서는 즉시, 밖에서는 시작 시각에 묶음 한 번.
 * 로그인 세션이 14일 슬라이딩이라 시계를 크게 옮길 수 없으므로, 구간은 "지금 기준 오늘·내일·모레 요일"로 만든다.
 */
class GeneralNotificationTest extends IntegrationTestSupport {

	private static final String TOKEN = "teacher-device-token";

	@Autowired
	private NotificationSettingPort settings;

	@Autowired
	private NotificationDispatcher dispatcher;

	private ZonedDateTime nowSeoul() {
		return clock.instant().atZone(AvailabilityCalendar.ZONE);
	}

	/** 오늘 하루 종일(00:00~24:00). 지금은 항상 안이다. */
	private AvailabilityWindow allDayToday() {
		return new AvailabilityWindow(nowSeoul().getDayOfWeek(), LocalTime.MIDNIGHT, AvailabilityWindow.END_OF_DAY);
	}

	/** daysAhead일 뒤 요일의 09:00~18:00. 오늘이 아니면 지금은 항상 밖이다. */
	private AvailabilityWindow dayHours(int daysAhead) {
		return new AvailabilityWindow(nowSeoul().plusDays(daysAhead).getDayOfWeek(), LocalTime.of(9, 0),
				LocalTime.of(18, 0));
	}

	private Instant seoulAt(int daysAhead, int hour, int minute) {
		return nowSeoul().toLocalDate().plusDays(daysAhead).atTime(hour, minute).atZone(AvailabilityCalendar.ZONE)
				.toInstant();
	}

	private void clockTo(Instant target) {
		clock.advance(Duration.between(clock.instant(), target));
	}

	private Scenario scenarioWith(List<AvailabilityWindow> windows) throws Exception {
		Scenario s = teacherWithClassroom();
		registerDevice(s.teacher().userId(), sessionIdOf(s.teacher().accessToken()), TOKEN);
		settings.saveSetting(new TeacherNotificationSetting(s.teacher().userId(), true));
		settings.replaceWindows(s.teacher().userId(), windows);
		return s;
	}

	private MockHttpServletResponse submitGeneral(Scenario s, String suffix) throws Exception {
		clock.advance(Duration.ofSeconds(61));
		return submitInquiry(s.parent().accessToken(), s.student().getId(), NORMAL_SAMPLE + suffix);
	}

	private int count(String where) {
		return jdbc.queryForObject("select count(*) from notification_request where " + where, Integer.class);
	}

	private Instant scheduledAt(String where) {
		return jdbc.queryForObject("select scheduled_at from notification_request where " + where,
				OffsetDateTime.class).toInstant();
	}

	@Test
	void insideAvailableHoursTheGeneralPushGoesOutWithTheNextDispatcherRun() throws Exception {
		Scenario s = scenarioWith(List.of(allDayToday()));

		MockHttpServletResponse response = submitGeneral(s, "");

		assertThat(response.getStatus()).isEqualTo(201);
		List<String> notices = read(response, "$.notices");
		assertThat(notices).containsExactly("RECEIVED");
		assertThat(jdbc.queryForObject("select notification_deferred from inquiry", Boolean.class)).isFalse();
		assertThat(count("kind = 'GENERAL' and status = 'PENDING'")).isEqualTo(1);
		assertThat(count("kind = 'GENERAL_DIGEST'")).isZero();

		dispatcher.dispatchDue();

		List<Sent> sent = pushes.sentOfKind(NotificationKind.GENERAL);
		assertThat(sent).hasSize(1);
		assertThat(sent.get(0).token()).isEqualTo(TOKEN);
		assertThat(count("kind = 'GENERAL' and status = 'SENT'")).isEqualTo(1);
	}

	@Test
	void outsideAvailableHoursTheInquiryIsAcceptedAndOneDigestIsScheduledForTheNextStart() throws Exception {
		Scenario s = scenarioWith(List.of(dayHours(1)));

		MockHttpServletResponse response = submitGeneral(s, "");

		assertThat(response.getStatus()).isEqualTo(201);
		List<String> notices = read(response, "$.notices");
		assertThat(notices).containsExactly("RECEIVED", "DEFERRED_TO_AVAILABLE_HOURS");
		assertThat((String) read(response, "$.stage")).isEqualTo("RECEIVED");
		assertThat(jdbc.queryForObject("select notification_deferred from inquiry", Boolean.class)).isTrue();
		assertThat(count("kind = 'GENERAL'")).as("즉시 알림 없음").isZero();
		assertThat(count("kind = 'GENERAL_DIGEST' and status = 'PENDING'")).isEqualTo(1);
		assertThat(scheduledAt("kind = 'GENERAL_DIGEST'")).isEqualTo(seoulAt(1, 9, 0));

		dispatcher.dispatchDue();
		assertThat(pushes.sent()).isEmpty();
	}

	@Test
	void threeDeferredInquiriesBecomeOneDigestWithTheCount() throws Exception {
		Scenario s = scenarioWith(List.of(dayHours(1)));
		submitGeneral(s, " 1");
		submitGeneral(s, " 2");
		submitGeneral(s, " 3");
		assertThat(count("kind = 'GENERAL_DIGEST'")).as("교사별로 대기 중 묶음은 하나").isEqualTo(1);

		clockTo(seoulAt(1, 9, 1));
		dispatcher.dispatchDue();

		List<Sent> digests = pushes.sentOfKind(NotificationKind.GENERAL_DIGEST);
		assertThat(digests).hasSize(1);
		assertThat(digests.get(0).message().digestCount()).isEqualTo(3);
		assertThat(digests.get(0).message().body()).contains("3건");
		assertThat(jdbc.queryForObject("select digest_count from notification_request where kind = 'GENERAL_DIGEST'",
				Integer.class)).isEqualTo(3);
		assertThat(jdbc.queryForObject("select count(*) from inquiry where deferred_notified_at is not null",
				Integer.class)).isEqualTo(3);

		// 같은 문의로 다시 보내지 않는다
		clock.advance(Duration.ofMinutes(5));
		dispatcher.dispatchDue();
		assertThat(pushes.sentOfKind(NotificationKind.GENERAL_DIGEST)).hasSize(1);
		assertThat(count("kind = 'GENERAL_DIGEST' and status = 'SENT'")).isEqualTo(1);
		assertThat(count("kind = 'GENERAL_DIGEST' and status = 'PENDING'")).isZero();
	}

	@Test
	void digestWithNothingLeftIsSkippedWithoutAPush() throws Exception {
		Scenario s = scenarioWith(List.of(dayHours(1)));
		submitGeneral(s, "");
		jdbc.update("update inquiry set deferred_notified_at = now()");

		clockTo(seoulAt(1, 9, 1));
		dispatcher.dispatchDue();

		assertThat(pushes.sent()).isEmpty();
		assertThat(count("kind = 'GENERAL_DIGEST' and status = 'SKIPPED'")).isEqualTo(1);
	}

	@Test
	void turningGeneralNotificationsOffAfterTheDigestWasScheduledSkipsIt() throws Exception {
		Scenario s = scenarioWith(List.of(dayHours(1)));
		submitGeneral(s, "");
		TeacherNotificationSetting setting = settings.findSetting(s.teacher().userId()).orElseThrow();
		setting.changeGeneralEnabled(false);
		settings.saveSetting(setting);

		clockTo(seoulAt(1, 9, 1));
		dispatcher.dispatchDue();

		assertThat(pushes.sent()).isEmpty();
		assertThat(count("kind = 'GENERAL_DIGEST' and status = 'SKIPPED'")).isEqualTo(1);
		assertThat(jdbc.queryForObject("select count(*) from inquiry where deferred_notified_at is not null",
				Integer.class)).as("세지 않았으므로 표시도 없다").isZero();
	}

	@Test
	void whenGeneralNotificationsAreOffNoRequestsAreCreatedButTheInquiryStaysListed() throws Exception {
		Scenario s = scenarioWith(List.of(dayHours(1)));
		TeacherNotificationSetting setting = settings.findSetting(s.teacher().userId()).orElseThrow();
		setting.changeGeneralEnabled(false);
		settings.saveSetting(setting);

		MockHttpServletResponse response = submitGeneral(s, "");

		assertThat(response.getStatus()).isEqualTo(201);
		List<String> notices = read(response, "$.notices");
		assertThat(notices).containsExactly("RECEIVED");
		assertThat(jdbc.queryForObject("select notification_deferred from inquiry", Boolean.class)).isFalse();
		assertThat(count("kind in ('GENERAL', 'GENERAL_DIGEST')")).isZero();
		MockHttpServletResponse listed = send(get("/api/v1/teacher/inquiries"), s.teacher().accessToken());
		assertThat((List<?>) read(listed, "$.items")).hasSize(1);
	}

	@Test
	void teachersWithoutSettingsAreAlwaysAvailableWithGeneralNotificationsOn() throws Exception {
		Scenario s = teacherWithClassroom();
		registerDevice(s.teacher().userId(), sessionIdOf(s.teacher().accessToken()), TOKEN);

		MockHttpServletResponse response = submitGeneral(s, "");

		List<String> notices = read(response, "$.notices");
		assertThat(notices).containsExactly("RECEIVED");
		assertThat(count("kind = 'GENERAL' and status = 'PENDING'")).isEqualTo(1);
		dispatcher.dispatchDue();
		assertThat(pushes.sentOfKind(NotificationKind.GENERAL)).hasSize(1);
	}

	@Test
	void changingSettingsAffectsOnlyLaterInquiries() throws Exception {
		Scenario s = scenarioWith(List.of(dayHours(1)));
		submitGeneral(s, " 첫째");
		Instant scheduled = scheduledAt("kind = 'GENERAL_DIGEST'");
		assertThat(scheduled).isEqualTo(seoulAt(1, 9, 0));

		// 응답 가능 시간을 모레로 바꿔도 이미 잡힌 묶음 알림 시각은 그대로다
		settings.replaceWindows(s.teacher().userId(), List.of(dayHours(2)));
		submitGeneral(s, " 둘째");

		assertThat(scheduledAt("kind = 'GENERAL_DIGEST'")).isEqualTo(scheduled);
		assertThat(count("kind = 'GENERAL_DIGEST'")).isEqualTo(1);
	}

	@Test
	void urgentIsNeverDelayedOrBlockedBySettings() throws Exception {
		Scenario s = scenarioWith(List.of(dayHours(1)));
		TeacherNotificationSetting setting = settings.findSetting(s.teacher().userId()).orElseThrow();
		setting.changeGeneralEnabled(false);
		settings.saveSetting(setting);

		MockHttpServletResponse response = submitInquiry(s.parent().accessToken(), s.student().getId(), URGENT_SAMPLE);

		assertThat(response.getStatus()).isEqualTo(201);
		List<String> notices = read(response, "$.notices");
		assertThat(notices).containsExactly("URGENT_DELIVERED");
		assertThat(jdbc.queryForObject("select notification_deferred from inquiry", Boolean.class)).isFalse();
		assertThat(pushes.sentOfKind(NotificationKind.URGENT)).hasSize(1);
		assertThat(count("kind = 'GENERAL_DIGEST'")).isZero();
	}

	@Test
	void parentsCannotChooseTheDeliveryTime() throws Exception {
		Scenario s = scenarioWith(List.of(dayHours(1)));
		String body = "{\"studentId\":\"" + s.student().getId() + "\",\"content\":\"" + NORMAL_SAMPLE
				+ "\",\"notifyAt\":\"2030-01-07T10:00:00Z\"}";

		MockHttpServletResponse response = send(
				json(post("/api/v1/parent/inquiries"), body).header("Idempotency-Key", UUID.randomUUID().toString()),
				s.parent().accessToken());

		assertThat(response.getStatus()).isEqualTo(400);
		assertThat(jdbc.queryForObject("select count(*) from inquiry", Integer.class)).isZero();
	}

}
