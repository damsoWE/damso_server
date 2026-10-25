package damso.notification;

import static damso.support.ErrorResponseAssertions.assertSafeErrorResponse;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

import damso.account.domain.Role;
import damso.notification.domain.AvailabilityCalendar;
import damso.support.IntegrationTestSupport;

/** F2 US7 시나리오 1~3·6~7 / FR-031, SC-011: 교사의 응답 가능 시간·일반 알림 설정. */
class NotificationSettingsApiTest extends IntegrationTestSupport {

	private static final String PATH = "/api/v1/teacher/notification-settings";

	private MockHttpServletResponse save(String token, String body) throws Exception {
		return send(json(put(PATH), body), token);
	}

	private static String window(String day, String start, String end) {
		return "{\"dayOfWeek\":\"" + day + "\",\"start\":\"" + start + "\",\"end\":\"" + end + "\"}";
	}

	private static String body(boolean enabled, String... windows) {
		return "{\"generalEnabled\":" + enabled + ",\"availability\":[" + String.join(",", windows) + "]}";
	}

	private String tomorrowDay() {
		return clock.instant().atZone(AvailabilityCalendar.ZONE).plusDays(1).getDayOfWeek().name();
	}

	@Test
	void beforeAnySettingsGeneralIsOnAndThereAreNoWindows() throws Exception {
		Scenario s = teacherWithClassroom();

		MockHttpServletResponse response = send(get(PATH), s.teacher().accessToken());

		assertThat(response.getStatus()).isEqualTo(200);
		assertThat((Boolean) read(response, "$.generalEnabled")).isTrue();
		assertThat((List<?>) read(response, "$.availability")).isEmpty();
	}

	@Test
	void savedSettingsComeBackTheSameWayOrderedByDay() throws Exception {
		Scenario s = teacherWithClassroom();
		String token = s.teacher().accessToken();

		MockHttpServletResponse saved = save(token,
				body(true, window("WEDNESDAY", "13:00", "17:30"), window("MONDAY", "09:00", "18:00")));
		MockHttpServletResponse loaded = send(get(PATH), token);

		assertThat(saved.getStatus()).isEqualTo(200);
		for (MockHttpServletResponse response : List.of(saved, loaded)) {
			List<String> days = read(response, "$.availability[*].dayOfWeek");
			assertThat(days).containsExactly("MONDAY", "WEDNESDAY");
			assertThat((String) read(response, "$.availability[0].start")).isEqualTo("09:00");
			assertThat((String) read(response, "$.availability[0].end")).isEqualTo("18:00");
			assertThat((String) read(response, "$.availability[1].start")).isEqualTo("13:00");
			assertThat((String) read(response, "$.availability[1].end")).isEqualTo("17:30");
		}
		// 다시 저장하면 통째로 바뀐다
		save(token, body(true, window("FRIDAY", "08:00", "12:00")));
		List<String> days = read(send(get(PATH), token), "$.availability[*].dayOfWeek");
		assertThat(days).containsExactly("FRIDAY");
		assertThat(jdbc.queryForObject("select count(*) from teacher_availability", Integer.class)).isEqualTo(1);
	}

	@Test
	void endOfDayCanBeWrittenAs2400AndTurnsBackInto2400() throws Exception {
		Scenario s = teacherWithClassroom();

		MockHttpServletResponse saved = save(s.teacher().accessToken(), body(true, window("FRIDAY", "18:00", "24:00")));

		assertThat(saved.getStatus()).isEqualTo(200);
		assertThat((String) read(saved, "$.availability[0].end")).isEqualTo("24:00");
		assertThat((String) read(send(get(PATH), s.teacher().accessToken()), "$.availability[0].end")).isEqualTo("24:00");
	}

	@Test
	void invalidWindowsAreRejected() throws Exception {
		Scenario s = teacherWithClassroom();
		String token = s.teacher().accessToken();
		List<String> bodies = List.of(
				body(true, window("MONDAY", "18:00", "09:00")),
				body(true, window("MONDAY", "09:00", "09:00")),
				body(true, window("MONDAY", "09:00", "12:00"), window("MONDAY", "13:00", "17:00")),
				body(true, window("MONDAY", "25:00", "26:00")),
				body(true, window("MONDAY", "24:00", "24:00")),
				body(true, window("MONDAY", "9:00", "12:00")),
				body(true, window("MONDAY", "09:00", "12:60")),
				body(true, window("FUNDAY", "09:00", "12:00")),
				body(true, window("MONDAY", "09:00", "12:00"), window("TUESDAY", "09:00", "12:00"),
						window("WEDNESDAY", "09:00", "12:00"), window("THURSDAY", "09:00", "12:00"),
						window("FRIDAY", "09:00", "12:00"), window("SATURDAY", "09:00", "12:00"),
						window("SUNDAY", "09:00", "12:00"), window("MONDAY", "13:00", "14:00")),
				"{\"availability\":[]}",
				"{\"generalEnabled\":true}",
				"{\"generalEnabled\":true,\"availability\":[],\"urgentEnabled\":false}",
				"{\"generalEnabled\":true,\"availability\":[{\"dayOfWeek\":\"MONDAY\",\"start\":\"09:00\"}]}");

		for (String bad : bodies) {
			MockHttpServletResponse response = save(token, bad);
			assertThat(response.getStatus()).as(bad).isEqualTo(400);
			assertSafeErrorResponse(response.getContentAsString(), "INVALID_INPUT");
		}
		assertThat(jdbc.queryForObject("select count(*) from teacher_availability", Integer.class)).isZero();
	}

	@Test
	void emptyAvailabilityMeansAlwaysAvailableAndGeneralCanBeTurnedOff() throws Exception {
		Scenario s = teacherWithClassroom();
		String token = s.teacher().accessToken();
		save(token, body(true, window("MONDAY", "09:00", "18:00")));

		MockHttpServletResponse saved = save(token, body(false));

		assertThat((Boolean) read(saved, "$.generalEnabled")).isFalse();
		assertThat((List<?>) read(saved, "$.availability")).isEmpty();
		assertThat((Boolean) read(send(get(PATH), token), "$.generalEnabled")).isFalse();
	}

	@Test
	void thereIsNoUrgentSwitchInTheResponse() throws Exception {
		Scenario s = teacherWithClassroom();

		MockHttpServletResponse response = send(get(PATH), s.teacher().accessToken());

		Map<String, Object> top = com.jayway.jsonpath.JsonPath.parse(response.getContentAsString()).read("$");
		assertThat(top.keySet()).isEqualTo(Set.of("generalEnabled", "availability"));
		assertThat(response.getContentAsString().toLowerCase()).doesNotContain("urgent");
	}

	@Test
	void onlyTeachersCanUseTheSettings() throws Exception {
		Scenario s = teacherWithClassroom();
		String admin = tokenFor(createAccount(Role.SUPER_ADMIN));

		assertThat(send(get(PATH), s.parent().accessToken()).getStatus()).isEqualTo(403);
		assertThat(send(get(PATH), admin).getStatus()).isEqualTo(403);
		assertThat(send(get(PATH), null).getStatus()).isEqualTo(401);
		assertThat(save(s.parent().accessToken(), body(true)).getStatus()).isEqualTo(403);
		assertThat(save(admin, body(true)).getStatus()).isEqualTo(403);
		assertThat(save(null, body(true)).getStatus()).isEqualTo(401);
		assertThat(jdbc.queryForObject("select count(*) from teacher_notification_setting", Integer.class)).isZero();
	}

	@Test
	void newHoursApplyFromTheNextInquiryAndAlreadyScheduledDigestsStay() throws Exception {
		Scenario s = teacherWithClassroom();
		String teacherToken = s.teacher().accessToken();
		// 내일만 응답 가능으로 저장하면 지금 접수하는 일반 문의는 지연된다
		save(teacherToken, body(true, window(tomorrowDay(), "09:00", "18:00")));
		clock.advance(Duration.ofSeconds(61));
		MockHttpServletResponse deferred = submitInquiry(s.parent().accessToken(), s.student().getId(), NORMAL_SAMPLE);
		List<String> deferredNotices = read(deferred, "$.notices");
		assertThat(deferredNotices).containsExactly("RECEIVED", "DEFERRED_TO_AVAILABLE_HOURS");
		Instant scheduled = jdbc.queryForObject(
				"select scheduled_at from notification_request where kind = 'GENERAL_DIGEST'", OffsetDateTime.class)
				.toInstant();

		// 저장 직후 접수하는 문의부터 새 설정이 적용된다(항상 응답 가능), 이미 잡힌 묶음 알림 시각은 그대로
		save(teacherToken, body(true));
		clock.advance(Duration.ofSeconds(61));
		MockHttpServletResponse immediate = submitInquiry(s.parent().accessToken(), s.student().getId(),
				NORMAL_SAMPLE + " 다시");

		List<String> immediateNotices = read(immediate, "$.notices");
		assertThat(immediateNotices).containsExactly("RECEIVED");
		assertThat(jdbc.queryForObject("select count(*) from notification_request where kind = 'GENERAL'",
				Integer.class)).isEqualTo(1);
		assertThat(jdbc.queryForObject("select scheduled_at from notification_request where kind = 'GENERAL_DIGEST'",
				OffsetDateTime.class).toInstant()).isEqualTo(scheduled);
	}

}
