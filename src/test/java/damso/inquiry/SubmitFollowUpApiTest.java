package damso.inquiry;

import static damso.support.ErrorResponseAssertions.assertSafeErrorResponse;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.time.Duration;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletResponse;

import damso.notification.application.port.out.NotificationSettingPort;
import damso.notification.domain.AvailabilityCalendar;
import damso.notification.domain.AvailabilityWindow;
import damso.notification.domain.TeacherNotificationSetting;
import damso.school.domain.Classroom;
import damso.support.IntegrationTestSupport;

/**
 * 재문의 제출 —
 * 본문 규칙·중복 확인·대상 재확인(현재 담임)·1분 제한(최초 문의와 같은 버킷 공유)·긴급 재문의의 즉시 접수.
 */
class SubmitFollowUpApiTest extends IntegrationTestSupport {

	@Autowired
	private NotificationSettingPort settings;

	/** daysAhead일 뒤 요일의 09:00~18:00. 오늘이 아니면 지금은 항상 밖이다. */
	private AvailabilityWindow dayHours(int daysAhead) {
		ZonedDateTime now = clock.instant().atZone(AvailabilityCalendar.ZONE);
		return new AvailabilityWindow(now.plusDays(daysAhead).getDayOfWeek(), LocalTime.of(9, 0), LocalTime.of(18, 0));
	}

	private UUID submitRoot(Scenario s, String content) throws Exception {
		MockHttpServletResponse response = submitInquiry(s.parent().accessToken(), s.student().getId(), content);
		return UUID.fromString(read(response, "$.inquiryId"));
	}

	private int followUpCount() {
		return jdbc.queryForObject("select count(*) from inquiry where thread_root_id is not null", Integer.class);
	}

	@Test
	void followUpIsAddedToTheSameThread() throws Exception {
		Scenario s = teacherWithClassroom();
		UUID rootId = submitRoot(s, NORMAL_SAMPLE);
		clock.advance(Duration.ofSeconds(61));

		MockHttpServletResponse response = submitFollowUp(s.parent().accessToken(), rootId,
				"추가로 궁금한 점이 있어요.");

		assertThat(response.getStatus()).isEqualTo(201);
		assertThat((String) read(response, "$.threadRootId")).isEqualTo(rootId.toString());
		String followUpId = read(response, "$.inquiryId");
		assertThat(followUpId).isNotEqualTo(rootId.toString());
		assertThat((Boolean) read(response, "$.urgent")).isFalse();
		assertThat((String) read(response, "$.submittedAt")).isNotBlank();
		Map<String, Object> row = jdbc.queryForMap("select thread_root_id from inquiry where id = ?",
				UUID.fromString(followUpId));
		assertThat(row.get("thread_root_id")).isEqualTo(rootId);
	}

	@Test
	void contentRulesMatchTheInitialInquiry() throws Exception {
		Scenario s = teacherWithClassroom();
		UUID rootId = submitRoot(s, NORMAL_SAMPLE);
		clock.advance(Duration.ofSeconds(61));
		String token = s.parent().accessToken();

		for (String bad : List.of("", "   ", "가".repeat(2001))) {
			MockHttpServletResponse response = submitFollowUp(token, rootId, bad);
			assertThat(response.getStatus()).as("[%d chars]", bad.length()).isEqualTo(400);
			assertSafeErrorResponse(response.getContentAsString(), "INVALID_INPUT");
		}
		assertThat(followUpCount()).isZero();
	}

	@Test
	void sameIdempotencyKeyRetryIsTheSameResultButDifferentContentIsAConflict() throws Exception {
		Scenario s = teacherWithClassroom();
		UUID rootId = submitRoot(s, NORMAL_SAMPLE);
		clock.advance(Duration.ofSeconds(61));
		String token = s.parent().accessToken();
		UUID key = UUID.randomUUID();

		MockHttpServletResponse first = submitFollowUp(token, rootId, "같은 내용", key);
		MockHttpServletResponse retry = submitFollowUp(token, rootId, "같은 내용", key);
		MockHttpServletResponse conflict = submitFollowUp(token, rootId, "다른 내용", key);

		assertThat(first.getStatus()).isEqualTo(201);
		assertThat(retry.getStatus()).isEqualTo(201);
		assertThat((String) read(retry, "$.inquiryId")).isEqualTo(read(first, "$.inquiryId"));
		assertThat(conflict.getStatus()).isEqualTo(409);
		assertSafeErrorResponse(conflict.getContentAsString(), "CONFLICT");
		assertThat(followUpCount()).isEqualTo(1);
	}

	@Test
	void secondGeneralFollowUpWithinAMinuteSharesTheInitialInquiryBucket() throws Exception {
		Scenario s = teacherWithClassroom();
		UUID rootId = submitRoot(s, NORMAL_SAMPLE);
		String token = s.parent().accessToken();

		MockHttpServletResponse limited = submitFollowUp(token, rootId, "바로 이어 보냄");

		assertThat(limited.getStatus()).isEqualTo(429);
		assertSafeErrorResponse(limited.getContentAsString(), "TOO_MANY_ATTEMPTS");
		assertThat(followUpCount()).isZero();

		clock.advance(Duration.ofSeconds(61));
		assertThat(submitFollowUp(token, rootId, "다시 보냄").getStatus()).isEqualTo(201);
	}

	@Test
	void urgentFollowUpIsAcceptedWithinAMinuteAndDeliveredImmediately() throws Exception {
		Scenario s = teacherWithClassroom();
		UUID rootId = submitRoot(s, NORMAL_SAMPLE);
		String token = s.parent().accessToken();

		long start = System.nanoTime();
		MockHttpServletResponse response = submitFollowUp(token, rootId, URGENT_SAMPLE);
		long elapsedMillis = Duration.ofNanos(System.nanoTime() - start).toMillis();

		assertThat(response.getStatus()).isEqualTo(201);
		assertThat((Boolean) read(response, "$.urgent")).isTrue();
		assertThat(elapsedMillis).as("3초 이내").isLessThan(3_000);
		String followUpId = read(response, "$.inquiryId");
		assertThat(jdbc.queryForObject("select stage from inquiry where id = ?", String.class,
				UUID.fromString(followUpId))).isEqualTo("DELIVERED");
	}

	@Test
	void notOwnParentOrDifferentClassroomIsNotFound() throws Exception {
		Scenario s = teacherWithClassroom();
		UUID rootId = submitRoot(s, NORMAL_SAMPLE);
		Scenario other = teacherWithClassroom(2, 1);

		MockHttpServletResponse byOtherParent = submitFollowUp(other.parent().accessToken(), rootId, "제 문의 아님");

		assertThat(byOtherParent.getStatus()).isEqualTo(404);
		assertThat(followUpCount()).isZero();
	}

	@Test
	void classroomWithoutHomeroomTeacherIsRejectedWithoutStoring() throws Exception {
		Scenario s = teacherWithClassroom();
		UUID rootId = submitRoot(s, NORMAL_SAMPLE);
		clock.advance(Duration.ofSeconds(61));
		Classroom stored = classroomPort.findClassroomById(s.classroom().getId()).orElseThrow();
		stored.releaseTeacher();
		classroomPort.saveClassroom(stored);

		MockHttpServletResponse response = submitFollowUp(s.parent().accessToken(), rootId, "담임이 없어요");

		assertThat(response.getStatus()).isEqualTo(409);
		assertSafeErrorResponse(response.getContentAsString(), "NO_HOMEROOM_TEACHER");
		assertThat(followUpCount()).isZero();
	}

	@Test
	void followUpGoesToTheNewHomeroomTeacherAfterReplacement() throws Exception {
		Scenario s = teacherWithClassroom();
		UUID rootId = submitRoot(s, NORMAL_SAMPLE);
		clock.advance(Duration.ofSeconds(61));
		Classroom stored = classroomPort.findClassroomById(s.classroom().getId()).orElseThrow();
		stored.releaseTeacher();
		classroomPort.saveClassroom(stored);
		SignedUp newTeacher = signUpWith(issueTeacherCode(s.classroom()), "새선생");

		MockHttpServletResponse response = submitFollowUp(s.parent().accessToken(), rootId, "새 담임에게 전달되나요");

		assertThat(response.getStatus()).isEqualTo(201);
		String followUpId = read(response, "$.inquiryId");
		assertThat(jdbc.queryForObject("select teacher_id from inquiry where id = ?", UUID.class,
				UUID.fromString(followUpId))).isEqualTo(newTeacher.userId());
	}

	/**
	 * 긴급 재문의도 최초 긴급 문의와 같은 안내(notices·hotlines)를 받아야 한다
	 * ("그대로 따른다"). 응답과 스레드 상세의 ParentFollowUpView 둘 다 확인한다.
	 */
	@Test
	void urgentFollowUpResponseCarriesTheSameGuidanceAsTheInitialUrgentInquiry() throws Exception {
		Scenario s = teacherWithClassroom();
		UUID rootId = submitRoot(s, NORMAL_SAMPLE);
		String token = s.parent().accessToken();

		MockHttpServletResponse response = submitFollowUp(token, rootId, URGENT_SAMPLE);

		assertThat(response.getStatus()).isEqualTo(201);
		List<String> notices = read(response, "$.notices");
		List<String> hotlines = read(response, "$.hotlines");
		assertThat(notices).containsExactly("URGENT_DELIVERED");
		assertThat(hotlines).containsExactlyInAnyOrder("CHILD_ABUSE_112", "SUICIDE_PREVENTION_109");

		MockHttpServletResponse detail = send(get("/api/v1/parent/inquiries/" + rootId), token);
		List<String> followUpNotices = read(detail, "$.followUps[0].notices");
		List<String> followUpHotlines = read(detail, "$.followUps[0].hotlines");
		assertThat(followUpNotices).containsExactly("URGENT_DELIVERED");
		assertThat(followUpHotlines).containsExactlyInAnyOrder("CHILD_ABUSE_112", "SUICIDE_PREVENTION_109");
	}

	/**
	 * 근무 시간 밖에 제출된 일반 재문의도 최초 문의와 같은 지연 안내(DEFERRED_TO_AVAILABLE_HOURS)를
	 * 받아야 한다. 상담 번호는 긴급이 아니므로 없다.
	 */
	@Test
	void generalFollowUpOutsideAvailableHoursCarriesTheDeferredNotice() throws Exception {
		Scenario s = teacherWithClassroom();
		UUID rootId = submitRoot(s, NORMAL_SAMPLE);
		clock.advance(Duration.ofSeconds(61));
		String parentToken = s.parent().accessToken();
		settings.saveSetting(new TeacherNotificationSetting(s.teacher().userId(), true));
		settings.replaceWindows(s.teacher().userId(), List.of(dayHours(1)));

		MockHttpServletResponse response = submitFollowUp(parentToken, rootId, "근무 시간 밖에 보낸 재문의");

		assertThat(response.getStatus()).isEqualTo(201);
		List<String> notices = read(response, "$.notices");
		List<String> hotlines = read(response, "$.hotlines");
		assertThat(notices).containsExactly("RECEIVED", "DEFERRED_TO_AVAILABLE_HOURS");
		assertThat(hotlines).isEmpty();

		MockHttpServletResponse detail = send(get("/api/v1/parent/inquiries/" + rootId), parentToken);
		List<String> followUpNotices = read(detail, "$.followUps[0].notices");
		assertThat(followUpNotices).containsExactly("RECEIVED", "DEFERRED_TO_AVAILABLE_HOURS");
	}

}
