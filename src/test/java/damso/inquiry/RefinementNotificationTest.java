package damso.inquiry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.TestPropertySource;

import damso.notification.application.port.out.NotificationSettingPort;
import damso.notification.application.service.NotificationDispatcher;
import damso.notification.domain.AvailabilityCalendar;
import damso.notification.domain.AvailabilityWindow;
import damso.notification.domain.NotificationKind;
import damso.notification.domain.TeacherNotificationSetting;
import damso.support.IntegrationTestSupport;

/**
 * F3 T040 / US7 시나리오 1~8, FR-032~034, SC-013·014: 일반 문의 알림은 접수 때가 아니라 정리가 확정될 때(성공·원문 확인 필요)
 * 한 번만 요청된다. 응답 가능 시간·알림 끔·묶음 규칙은 그대로이고 기준 시각만 정리 확정 시각이다. 학부모 안내는 접수 때 값으로 고정된다.
 * 정리 확정 전 상태를 보려고 커밋 직후 정리를 끄고, 실행기를 직접 돌린다.
 */
@TestPropertySource(properties = "damso.refinement.dispatch-on-commit=false")
class RefinementNotificationTest extends IntegrationTestSupport {

	private static final String TOKEN = "teacher-device-token";

	@Autowired
	private NotificationSettingPort settings;

	@Autowired
	private NotificationDispatcher dispatcher;

	private ZonedDateTime nowSeoul() {
		return clock.instant().atZone(AvailabilityCalendar.ZONE);
	}

	/** 지금부터 minutes분 뒤(자정을 넘기면 하루 끝)에 끝나는 오늘의 구간. 지금은 안이다. */
	private AvailabilityWindow todayUntilMinutesFromNow(int minutes) {
		ZonedDateTime now = nowSeoul();
		ZonedDateTime end = now.plusMinutes(minutes);
		LocalTime endTime = end.toLocalDate().equals(now.toLocalDate()) ? end.toLocalTime().withNano(0)
				: AvailabilityWindow.END_OF_DAY;
		return new AvailabilityWindow(now.getDayOfWeek(), LocalTime.MIDNIGHT, endTime);
	}

	/** daysAhead일 뒤 요일의 09:00~18:00. 오늘이 아니면 지금은 밖이다. */
	private AvailabilityWindow dayHours(int daysAhead) {
		return new AvailabilityWindow(nowSeoul().plusDays(daysAhead).getDayOfWeek(), LocalTime.of(9, 0),
				LocalTime.of(18, 0));
	}

	private Scenario scenario(boolean generalEnabled, List<AvailabilityWindow> windows) throws Exception {
		Scenario s = teacherWithClassroom();
		registerDevice(s.teacher().userId(), sessionIdOf(s.teacher().accessToken()), TOKEN);
		settings.saveSetting(new TeacherNotificationSetting(s.teacher().userId(), generalEnabled));
		settings.replaceWindows(s.teacher().userId(), windows);
		return s;
	}

	private int requests(String where) {
		return jdbc.queryForObject("select count(*) from notification_request where " + where, Integer.class);
	}

	private UUID submit(Scenario s, String content) throws Exception {
		clock.advance(Duration.ofSeconds(61));
		return UUID.fromString(read(submitInquiry(s.parent().accessToken(), s.student().getId(), content),
				"$.inquiryId"));
	}

	@Test
	void noGeneralNotificationUntilTheRefinementIsConfirmedThenExactlyOne() throws Exception {
		Scenario s = scenario(true, List.of());
		UUID id = submit(s, NORMAL_SAMPLE);

		assertThat(requests("kind in ('GENERAL', 'GENERAL_DIGEST')")).as("정리 확정 전").isZero();

		runRefinement();

		assertThat(requests("kind = 'GENERAL' and inquiry_id = '" + id + "'")).isEqualTo(1);
		dispatcher.dispatchDue();
		assertThat(pushes.sentOfKind(NotificationKind.GENERAL)).hasSize(1);
		// 알림을 받고 연 교사는 번역본이 있는 상태를 본다
		assertThat((String) read(send(get("/api/v1/teacher/inquiries/" + id), s.teacher().accessToken()),
				"$.refinementStatus")).isEqualTo("REFINED");
	}

	@Test
	void aConfirmedFailureAlsoNotifiesOnceAndATeacherRetryDoesNotNotifyAgain() throws Exception {
		Scenario s = scenario(true, List.of());
		fakeAi.noResponseNext();
		fakeAi.noResponseNext();
		UUID id = submit(s, NORMAL_SAMPLE);
		runRefinement();

		assertThat(requests("kind = 'GENERAL'")).isEqualTo(1);
		assertThat((String) read(send(get("/api/v1/teacher/inquiries/" + id), s.teacher().accessToken()),
				"$.refinementStatus")).isEqualTo("NEEDS_ORIGINAL");

		assertThat(send(post("/api/v1/teacher/inquiries/" + id + "/refinement/retry"), s.teacher().accessToken())
				.getStatus()).isEqualTo(202);
		runRefinement();

		assertThat(jdbc.queryForObject("select refinement_status from inquiry where id = ?", String.class, id))
				.isEqualTo("REFINED");
		assertThat(requests("kind in ('GENERAL', 'GENERAL_DIGEST')")).as("교사 재시도 성공으로 추가 알림 없음")
				.isEqualTo(1);
	}

	@Test
	void availabilityIsJudgedAtConfirmationTimeButTheParentNoticeKeepsTheSubmissionTimeValue() throws Exception {
		// 지금은 응답 가능 시간 안(오늘 지금+10분까지)이고, 내일 09:00에 다시 시작한다
		// (접근 토큰 수명이 15분이라 시계는 그 안에서만 옮긴다)
		Scenario s = scenario(true, List.of(todayUntilMinutesFromNow(10), dayHours(1)));
		UUID key = UUID.randomUUID();
		clock.advance(Duration.ofSeconds(61));
		MockHttpServletResponse submitted = submitInquiry(s.parent().accessToken(), s.student().getId(), NORMAL_SAMPLE,
				key);
		UUID id = UUID.fromString(read(submitted, "$.inquiryId"));
		assertThat(IntegrationTestSupport.<List<String>>read(submitted, "$.notices")).containsExactly("RECEIVED");

		// 정리가 응답 가능 시간이 끝난 뒤에 확정된다 → 알림은 다음 시작 시각의 묶음으로 미뤄진다
		clock.advance(Duration.ofMinutes(11));
		runRefinement();

		assertThat(requests("kind = 'GENERAL'")).isZero();
		assertThat(requests("kind = 'GENERAL_DIGEST' and status = 'PENDING'")).isEqualTo(1);
		assertThat(jdbc.queryForObject("select notification_deferred from inquiry where id = ?", Boolean.class, id))
				.isTrue();
		// 학부모에게는 접수 때 판정한 안내가 그대로다 — 재전송·상세 모두
		MockHttpServletResponse replay = submitInquiry(s.parent().accessToken(), s.student().getId(), NORMAL_SAMPLE,
				key);
		assertThat(IntegrationTestSupport.<List<String>>read(replay, "$.notices")).containsExactly("RECEIVED");
		assertThat(IntegrationTestSupport.<List<String>>read(send(get("/api/v1/parent/inquiries/" + id), s.parent().accessToken()),
				"$.notices")).containsExactly("RECEIVED");
	}

	@Test
	void outsideHoursAtSubmissionTheNoticeIsDeferredBeforeAndAfterConfirmation() throws Exception {
		Scenario s = scenario(true, List.of(dayHours(1)));
		UUID key = UUID.randomUUID();
		clock.advance(Duration.ofSeconds(61));
		MockHttpServletResponse submitted = submitInquiry(s.parent().accessToken(), s.student().getId(), NORMAL_SAMPLE,
				key);
		UUID id = UUID.fromString(read(submitted, "$.inquiryId"));

		List<String> before = read(send(get("/api/v1/parent/inquiries/" + id), s.parent().accessToken()), "$.notices");
		runRefinement();
		List<String> replay = read(submitInquiry(s.parent().accessToken(), s.student().getId(), NORMAL_SAMPLE, key),
				"$.notices");
		List<String> after = read(send(get("/api/v1/parent/inquiries/" + id), s.parent().accessToken()), "$.notices");

		assertThat(IntegrationTestSupport.<List<String>>read(submitted, "$.notices")).containsExactly("RECEIVED",
				"DEFERRED_TO_AVAILABLE_HOURS");
		assertThat(before).isEqualTo(after).isEqualTo(replay).containsExactly("RECEIVED",
				"DEFERRED_TO_AVAILABLE_HOURS");
		assertThat(requests("kind = 'GENERAL_DIGEST' and status = 'PENDING'")).isEqualTo(1);
	}

	@Test
	void digestCountsInquiriesWhoseRefinementWasConfirmedOutsideHours() throws Exception {
		Scenario s = scenario(true, List.of(dayHours(1)));
		submit(s, NORMAL_SAMPLE + " 1");
		submit(s, NORMAL_SAMPLE + " 2");
		runRefinement();
		Instant start = nowSeoul().toLocalDate().plusDays(1).atTime(9, 0).atZone(AvailabilityCalendar.ZONE)
				.toInstant();
		clock.advance(Duration.between(clock.instant(), start));

		dispatcher.dispatchDue();

		assertThat(pushes.sentOfKind(NotificationKind.GENERAL_DIGEST)).hasSize(1);
		assertThat(pushes.sentOfKind(NotificationKind.GENERAL_DIGEST).get(0).message().digestCount()).isEqualTo(2);
	}

	@Test
	void generalNotificationsOffMeansNoRequestEvenAfterConfirmation() throws Exception {
		Scenario s = scenario(false, List.of());
		submit(s, NORMAL_SAMPLE);
		runRefinement();

		assertThat(requests("kind in ('GENERAL', 'GENERAL_DIGEST')")).isZero();
	}

	@Test
	void urgentNotificationIsImmediateAndDoesNotWaitForAnything() throws Exception {
		Scenario s = scenario(true, List.of(dayHours(1)));
		submit(s, URGENT_SAMPLE);

		assertThat(requests("kind = 'URGENT'")).isEqualTo(1);
		assertThat(pushes.sentOfKind(NotificationKind.URGENT)).hasSize(1);
	}

}
