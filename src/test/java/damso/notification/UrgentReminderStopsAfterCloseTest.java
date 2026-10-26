package damso.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.time.Duration;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletResponse;

import damso.notification.application.service.NotificationDispatcher;
import damso.notification.domain.NotificationKind;
import damso.support.IntegrationTestSupport;

/**
 * F4 PR #10 리뷰: 종료(CloseInquiryService)는 스레드 루트만 ANSWERED로 바꾸고, 아직 교사가 원문을 열람하지
 * 않은 긴급 재문의는 자기 자신의 단계가 DELIVERED에 머문다. UrgentReminderPolicy(F2)가 스레드가 닫힌 것도
 * 함께 보지 않으면, 종료 뒤에도 그 재문의의 재알림이 계속 나간다.
 */
class UrgentReminderStopsAfterCloseTest extends IntegrationTestSupport {

	private static final String TOKEN = "teacher-device-token-reminder";

	@Autowired
	private NotificationDispatcher dispatcher;

	private int count(String where) {
		return jdbc.queryForObject("select count(*) from notification_request where " + where, Integer.class);
	}

	@Test
	void closingTheThreadStopsRemindersForAnUnconfirmedUrgentFollowUp() throws Exception {
		Scenario s = teacherWithClassroom();
		String teacherToken = s.teacher().accessToken();
		String parentToken = s.parent().accessToken();
		// 기기를 미리 등록해 둔다 — 루트의 최초 긴급 알림도 즉시 성공시켜, 뒤늦게 기기가 생겨 그 알림이
		// 다시 깨어나면서 루트 자신의 재알림이 새로 잡히는 것을(이 테스트와 무관한 경로) 피한다.
		registerDevice(s.teacher().userId(), sessionIdOf(teacherToken), TOKEN);

		MockHttpServletResponse rootSubmitted = submitInquiry(parentToken, s.student().getId(), URGENT_SAMPLE);
		UUID rootId = UUID.fromString(read(rootSubmitted, "$.inquiryId"));
		send(get("/api/v1/teacher/inquiries/" + rootId + "/content"), teacherToken);

		// 교사가 원문을 열람하지 않을 긴급 재문의 — 자기 자신은 DELIVERED에 계속 머문다
		MockHttpServletResponse followUp = submitFollowUp(parentToken, rootId, URGENT_SAMPLE);
		assertThat(followUp.getStatus()).isEqualTo(201);
		assertThat((Boolean) read(followUp, "$.urgent")).isTrue();
		UUID followUpId = UUID.fromString(read(followUp, "$.inquiryId"));
		assertThat(count("kind = 'URGENT_REMINDER' and inquiry_id = '" + followUpId + "'"))
				.as("첫 알림 발송 성공 시 그 재문의의 재알림이 1건 잡힌다").isEqualTo(1);

		// 교사가 "답변 완료"로 스레드를 종료한다 — 재문의 자신은 아직 DELIVERED
		assertThat(send(post("/api/v1/teacher/inquiries/" + rootId + "/close"), teacherToken).getStatus())
				.isEqualTo(200);

		clock.advance(Duration.ofHours(1).plusSeconds(1));
		dispatcher.dispatchDue();

		assertThat(pushes.sentOfKind(NotificationKind.URGENT_REMINDER)).as("종료된 스레드의 재문의에는 재알림이 가면 안 된다")
				.isEmpty();
		assertThat(count("kind = 'URGENT_REMINDER' and inquiry_id = '" + followUpId + "' and status = 'CANCELLED'"))
				.isEqualTo(1);
		assertThat(count("kind = 'URGENT_REMINDER' and inquiry_id = '" + followUpId + "' and status = 'PENDING'"))
				.isZero();

		// 한 번 더 돌려도 다시 잡히지 않는다
		clock.advance(Duration.ofHours(1));
		dispatcher.dispatchDue();
		assertThat(pushes.sentOfKind(NotificationKind.URGENT_REMINDER)).isEmpty();
	}

}
