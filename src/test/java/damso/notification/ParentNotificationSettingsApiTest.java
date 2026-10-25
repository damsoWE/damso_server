package damso.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

import damso.account.domain.Role;
import damso.support.IntegrationTestSupport;

/** F4 US3 시나리오 5 / FR-024, SC-007: 학부모 답변 도착 알림 on/off, 기본값은 켜짐. */
class ParentNotificationSettingsApiTest extends IntegrationTestSupport {

	private static final String PATH = "/api/v1/parent/notification-settings";

	private MockHttpServletResponse save(String token, String body) throws Exception {
		return send(json(put(PATH), body), token);
	}

	@Test
	void settingsNeverChangedComeBackOn() throws Exception {
		Scenario s = teacherWithClassroom();

		MockHttpServletResponse response = send(get(PATH), s.parent().accessToken());

		assertThat(response.getStatus()).isEqualTo(200);
		assertThat((Boolean) read(response, "$.answerNotifyEnabled")).isTrue();
	}

	@Test
	void turningItOffThenOnIsSaved() throws Exception {
		Scenario s = teacherWithClassroom();
		String token = s.parent().accessToken();

		MockHttpServletResponse off = save(token, "{\"answerNotifyEnabled\":false}");
		MockHttpServletResponse loadedOff = send(get(PATH), token);
		MockHttpServletResponse on = save(token, "{\"answerNotifyEnabled\":true}");
		MockHttpServletResponse loadedOn = send(get(PATH), token);

		assertThat(off.getStatus()).isEqualTo(200);
		assertThat((Boolean) read(off, "$.answerNotifyEnabled")).isFalse();
		assertThat((Boolean) read(loadedOff, "$.answerNotifyEnabled")).isFalse();
		assertThat((Boolean) read(on, "$.answerNotifyEnabled")).isTrue();
		assertThat((Boolean) read(loadedOn, "$.answerNotifyEnabled")).isTrue();
		assertThat(jdbc.queryForObject("select count(*) from parent_notification_setting", Integer.class))
				.isEqualTo(1);
	}

	@Test
	void unknownFieldOrMissingValueIsRejected() throws Exception {
		Scenario s = teacherWithClassroom();
		String token = s.parent().accessToken();

		MockHttpServletResponse unknownField = save(token, "{\"answerNotifyEnabled\":true,\"extra\":1}");
		MockHttpServletResponse missingValue = save(token, "{}");

		assertThat(unknownField.getStatus()).isEqualTo(400);
		assertThat(missingValue.getStatus()).isEqualTo(400);
	}

	@Test
	void onlyParentsCanUseTheSettings() throws Exception {
		Scenario s = teacherWithClassroom();
		String admin = tokenFor(createAccount(Role.SUPER_ADMIN));

		assertThat(send(get(PATH), s.teacher().accessToken()).getStatus()).isEqualTo(403);
		assertThat(send(get(PATH), admin).getStatus()).isEqualTo(403);
		assertThat(send(get(PATH), null).getStatus()).isEqualTo(401);
		assertThat(save(s.teacher().accessToken(), "{\"answerNotifyEnabled\":true}").getStatus()).isEqualTo(403);
		assertThat(save(admin, "{\"answerNotifyEnabled\":true}").getStatus()).isEqualTo(403);
		assertThat(save(null, "{\"answerNotifyEnabled\":true}").getStatus()).isEqualTo(401);
		assertThat(jdbc.queryForObject("select count(*) from parent_notification_setting", Integer.class)).isZero();
	}

}
