package damso.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletResponse;

import damso.notification.application.service.NotificationDispatcher;
import damso.notification.domain.NotificationKind;
import damso.support.FakePushSender.Sent;
import damso.support.IntegrationTestSupport;

/** 답변 전송 시 학부모 알림, 고정 문구뿐, 꺼두면 저장만. */
class AnswerNotificationTest extends IntegrationTestSupport {

	private static final String DEVICE_TOKEN = "parent-device-token";

	@Autowired
	private NotificationDispatcher dispatcher;

	private MockHttpServletResponse sendAnswer(String teacherToken, UUID inquiryId, String content) throws Exception {
		String body = "{\"content\":\"" + content + "\",\"reviewed\":false}";
		return send(json(post("/api/v1/teacher/inquiries/" + inquiryId + "/answers"), body), teacherToken);
	}

	private void turnOffParentNotification(String parentToken) throws Exception {
		MockHttpServletResponse response = send(
				json(put("/api/v1/parent/notification-settings"), "{\"answerNotifyEnabled\":false}"), parentToken);
		assertThat(response.getStatus()).isEqualTo(200);
	}

	@Test
	void parentWithNotificationOnReceivesAFixedPhraseOnly() throws Exception {
		ConfirmedInquiry confirmed = urgentInquiryConfirmedByTeacher();
		String parentToken = confirmed.scenario().parent().accessToken();
		registerDevice(confirmed.scenario().parent().userId(), sessionIdOf(parentToken), DEVICE_TOKEN);

		MockHttpServletResponse sent = sendAnswer(confirmed.scenario().teacher().accessToken(), confirmed.inquiryId(),
				"가정통신문을 참고해 주세요.");
		assertThat(sent.getStatus()).isEqualTo(201);
		dispatcher.dispatchDue();

		List<Sent> delivered = pushes.sentOfKind(NotificationKind.ANSWER);
		assertThat(delivered).hasSize(1);
		assertThat(delivered.get(0).token()).isEqualTo(DEVICE_TOKEN);
		assertThat(delivered.get(0).message().title()).doesNotContain("최선생").doesNotContain("가정통신문");
		assertThat(delivered.get(0).message().body()).doesNotContain("최선생").doesNotContain("가정통신문");
	}

	@Test
	void parentWithNotificationOffReceivesNothingButTheAnswerIsStillSaved() throws Exception {
		ConfirmedInquiry confirmed = urgentInquiryConfirmedByTeacher();
		String parentToken = confirmed.scenario().parent().accessToken();
		registerDevice(confirmed.scenario().parent().userId(), sessionIdOf(parentToken), DEVICE_TOKEN);
		turnOffParentNotification(parentToken);

		MockHttpServletResponse sent = sendAnswer(confirmed.scenario().teacher().accessToken(), confirmed.inquiryId(),
				"안내 말씀 드립니다.");
		assertThat(sent.getStatus()).isEqualTo(201);
		dispatcher.dispatchDue();

		assertThat(pushes.sentOfKind(NotificationKind.ANSWER)).isEmpty();
		assertThat(jdbc.queryForObject("select count(*) from notification_request where kind = 'ANSWER'",
				Integer.class)).isZero();
		String answerId = read(sent, "$.answerId");
		String stored = jdbc.queryForObject("select content from answer where id = ?", String.class,
				UUID.fromString(answerId));
		assertThat(stored).isNotBlank();
	}

	@Test
	void parentWithNoRegisteredDeviceStillGetsTheAnswerSavedButNoPush() throws Exception {
		ConfirmedInquiry confirmed = urgentInquiryConfirmedByTeacher();

		MockHttpServletResponse sent = sendAnswer(confirmed.scenario().teacher().accessToken(), confirmed.inquiryId(),
				"기기 등록 없이도 저장됩니다.");
		assertThat(sent.getStatus()).isEqualTo(201);
		dispatcher.dispatchDue();

		assertThat(pushes.sentOfKind(NotificationKind.ANSWER)).isEmpty();
		assertThat(jdbc.queryForObject("select count(*) from notification_request where kind = 'ANSWER' and status = 'PENDING'",
				Integer.class)).isEqualTo(1);
		String answerId = read(sent, "$.answerId");
		String stored = jdbc.queryForObject("select content from answer where id = ?", String.class,
				UUID.fromString(answerId));
		assertThat(stored).isNotBlank();
	}

}
