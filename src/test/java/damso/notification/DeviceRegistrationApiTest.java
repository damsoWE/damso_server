package damso.notification;

import static damso.support.ErrorResponseAssertions.assertSafeErrorResponse;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletResponse;

import damso.account.domain.UserAccount;
import damso.notification.application.service.NotificationDispatcher;
import damso.notification.domain.NotificationKind;
import damso.support.FakePushSender.Sent;
import damso.support.IntegrationTestSupport;

/** 기기는 로그인 세션에 묶이고, 세션이 끝나면 알림이 끊긴다. */
class DeviceRegistrationApiTest extends IntegrationTestSupport {

	private static final String PATH = "/api/v1/me/devices";

	@Autowired
	private NotificationDispatcher dispatcher;

	private MockHttpServletResponse register(String accessToken, String token, String platform) throws Exception {
		return send(json(put(PATH), "{\"token\":\"" + token + "\",\"platform\":\"" + platform + "\"}"), accessToken);
	}

	private int deviceCount() {
		return jdbc.queryForObject("select count(*) from device_registration", Integer.class);
	}

	@Test
	void registeringTwiceWithTheSameTokenKeepsOneRow() throws Exception {
		Scenario s = teacherWithClassroom();

		MockHttpServletResponse first = register(s.teacher().accessToken(), "token-a", "ANDROID");
		MockHttpServletResponse second = register(s.teacher().accessToken(), "token-a", "IOS");

		assertThat(first.getStatus()).isEqualTo(204);
		assertThat(first.getContentAsString()).isEmpty();
		assertThat(second.getStatus()).isEqualTo(204);
		assertThat(deviceCount()).isEqualTo(1);
		assertThat(jdbc.queryForObject("select platform from device_registration", String.class)).isEqualTo("IOS");
	}

	@Test
	void oneUserCanRegisterSeveralDevicesAndTeachersAndParentsBothCan() throws Exception {
		Scenario s = teacherWithClassroom();

		assertThat(register(s.teacher().accessToken(), "phone", "ANDROID").getStatus()).isEqualTo(204);
		assertThat(register(s.teacher().accessToken(), "tablet", "IOS").getStatus()).isEqualTo(204);
		assertThat(register(s.parent().accessToken(), "parent-phone", "ANDROID").getStatus()).isEqualTo(204);

		assertThat(deviceCount()).isEqualTo(3);
	}

	@Test
	void anotherUserRegisteringTheSameTokenTakesTheDeviceOver() throws Exception {
		Scenario s = teacherWithClassroom();
		register(s.teacher().accessToken(), "shared-device", "ANDROID");

		register(s.parent().accessToken(), "shared-device", "ANDROID");

		assertThat(deviceCount()).isEqualTo(1);
		assertThat(jdbc.queryForObject("select user_id from device_registration", UUID.class))
				.isEqualTo(s.parent().userId());
		assertThat(jdbc.queryForObject("select session_id from device_registration", UUID.class))
				.isEqualTo(sessionIdOf(s.parent().accessToken()));
	}

	@Test
	void afterLogoutOfThatSessionNoUrgentPushGoesToThatDevice() throws Exception {
		Scenario s = teacherWithClassroom();
		register(s.teacher().accessToken(), "phone", "ANDROID");

		assertThat(send(post("/api/v1/auth/logout"), s.teacher().accessToken()).getStatus()).isEqualTo(204);
		submitInquiry(s.parent().accessToken(), s.student().getId(), URGENT_SAMPLE);

		assertThat(pushes.sent()).isEmpty();
		assertThat(jdbc.queryForObject("select status from notification_request where kind = 'URGENT'", String.class))
				.isEqualTo("PENDING");
	}

	@Test
	void otherSessionsOfTheSameTeacherKeepReceiving() throws Exception {
		Scenario s = teacherWithClassroom();
		UserAccount teacher = accountPort.findById(s.teacher().userId()).orElseThrow();
		String secondDevice = tokenFor(teacher);
		register(s.teacher().accessToken(), "phone", "ANDROID");
		register(secondDevice, "tablet", "ANDROID");

		send(post("/api/v1/auth/logout"), s.teacher().accessToken());
		submitInquiry(s.parent().accessToken(), s.student().getId(), URGENT_SAMPLE);

		assertThat(pushes.sent()).extracting(Sent::token).containsExactly("tablet");
	}

	@Test
	void unregisteringTheCurrentDeviceStopsPushesToIt() throws Exception {
		Scenario s = teacherWithClassroom();
		register(s.teacher().accessToken(), "phone", "ANDROID");

		MockHttpServletResponse removed = send(delete(PATH + "/current"), s.teacher().accessToken());
		submitInquiry(s.parent().accessToken(), s.student().getId(), URGENT_SAMPLE);

		assertThat(removed.getStatus()).isEqualTo(204);
		assertThat(jdbc.queryForObject("select active from device_registration", Boolean.class)).isFalse();
		assertThat(pushes.sent()).isEmpty();
	}

	@Test
	void unregisteringWithoutAnyRegistrationIsStillNoContent() throws Exception {
		Scenario s = teacherWithClassroom();

		assertThat(send(delete(PATH + "/current"), s.teacher().accessToken()).getStatus()).isEqualTo(204);
		assertThat(send(delete(PATH + "/current"), null).getStatus()).isEqualTo(401);
	}

	@Test
	void unregisteringOnlyAffectsThisSessionsDevice() throws Exception {
		Scenario s = teacherWithClassroom();
		UserAccount teacher = accountPort.findById(s.teacher().userId()).orElseThrow();
		String secondDevice = tokenFor(teacher);
		register(s.teacher().accessToken(), "phone", "ANDROID");
		register(secondDevice, "tablet", "ANDROID");

		send(delete(PATH + "/current"), s.teacher().accessToken());

		assertThat(jdbc.queryForObject("select active from device_registration where token = 'phone'", Boolean.class))
				.isFalse();
		assertThat(jdbc.queryForObject("select active from device_registration where token = 'tablet'", Boolean.class))
				.isTrue();
	}

	@Test
	void pendingUrgentRequestGoesOutAfterADeviceIsRegistered() throws Exception {
		Scenario s = teacherWithClassroom();
		submitInquiry(s.parent().accessToken(), s.student().getId(), URGENT_SAMPLE);
		assertThat(pushes.sent()).isEmpty();

		assertThat(register(s.teacher().accessToken(), "phone", "ANDROID").getStatus()).isEqualTo(204);
		dispatcher.dispatchDue();

		assertThat(pushes.sentOfKind(NotificationKind.URGENT)).hasSize(1);
	}

	@Test
	void invalidRegistrationsAreRejected() throws Exception {
		Scenario s = teacherWithClassroom();
		String token = s.teacher().accessToken();

		List<MockHttpServletResponse> responses = List.of(
				register(token, "", "ANDROID"),
				register(token, "   ", "ANDROID"),
				register(token, "t".repeat(513), "ANDROID"),
				register(token, "ok-token", "WINDOWS"),
				send(json(put(PATH), "{\"token\":\"ok-token\"}"), token),
				send(json(put(PATH), "{\"platform\":\"ANDROID\"}"), token),
				send(json(put(PATH), "{\"token\":\"ok-token\",\"platform\":\"ANDROID\",\"userId\":\"x\"}"), token));

		for (MockHttpServletResponse response : responses) {
			assertThat(response.getStatus()).isEqualTo(400);
			assertSafeErrorResponse(response.getContentAsString(), "INVALID_INPUT");
			assertThat(response.getContentAsString()).doesNotContain("ok-token");
		}
		assertThat(deviceCount()).isZero();
		assertThat(register(token, "t".repeat(512), "ANDROID").getStatus()).isEqualTo(204);
		assertThat(register(null, "ok-token", "ANDROID").getStatus()).isEqualTo(401);
	}

}
