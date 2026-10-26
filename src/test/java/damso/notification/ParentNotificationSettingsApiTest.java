package damso.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

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

	private Long versionOf(UUID parentId) {
		return jdbc.queryForObject("select version from parent_notification_setting where parent_id = ?", Long.class,
				parentId);
	}

	private OffsetDateTime updatedAtOf(UUID parentId) {
		return jdbc.queryForObject("select updated_at from parent_notification_setting where parent_id = ?",
				OffsetDateTime.class, parentId);
	}

	private OffsetDateTime createdAtOf(UUID parentId) {
		return jdbc.queryForObject("select created_at from parent_notification_setting where parent_id = ?",
				OffsetDateTime.class, parentId);
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

	/**
	 * PR #10 리뷰: find-or-create 방식이라 같은 학부모의 첫 저장 요청 두 건이 동시에 오면 각자 insert를
	 * 시도해 parent_id 유니크 제약에서 하나가 409로 실패했다. upsert(INSERT ... ON CONFLICT DO UPDATE)로
	 * 바꿔 둘 다 성공하고 행이 하나만 남아야 한다.
	 */
	@Test
	void concurrentFirstSavesForTheSameParentBothSucceedAndLeaveOneRow() throws Exception {
		Scenario s = teacherWithClassroom();
		String token = s.parent().accessToken();
		ExecutorService pool = Executors.newFixedThreadPool(2);
		CountDownLatch start = new CountDownLatch(2);
		try {
			Callable<MockHttpServletResponse> saveFalse = () -> {
				start.countDown();
				start.await();
				return save(token, "{\"answerNotifyEnabled\":false}");
			};
			Callable<MockHttpServletResponse> saveTrue = () -> {
				start.countDown();
				start.await();
				return save(token, "{\"answerNotifyEnabled\":true}");
			};
			Future<MockHttpServletResponse> first = pool.submit(saveFalse);
			Future<MockHttpServletResponse> second = pool.submit(saveTrue);

			int status1 = first.get(5, TimeUnit.SECONDS).getStatus();
			int status2 = second.get(5, TimeUnit.SECONDS).getStatus();

			assertThat(status1).as("첫 저장 두 건이 동시에 와도 둘 다 성공해야 한다").isEqualTo(200);
			assertThat(status2).as("첫 저장 두 건이 동시에 와도 둘 다 성공해야 한다").isEqualTo(200);
			assertThat(jdbc.queryForObject("select count(*) from parent_notification_setting where parent_id = ?",
					Integer.class, s.parent().userId())).isEqualTo(1);
		}
		finally {
			pool.shutdownNow();
		}
	}

	/** 네이티브 upsert 쿼리에서도 @Version·updated_at이 저장 때마다 올바르게 갱신되는지 확인한다. */
	@Test
	void upsertStillBumpsVersionAndUpdatedAtOnEverySave() throws Exception {
		Scenario s = teacherWithClassroom();
		String token = s.parent().accessToken();
		UUID parentId = s.parent().userId();

		assertThat(save(token, "{\"answerNotifyEnabled\":false}").getStatus()).isEqualTo(200);
		Long versionAfterInsert = versionOf(parentId);
		OffsetDateTime createdAt = createdAtOf(parentId);
		OffsetDateTime updatedAfterInsert = updatedAtOf(parentId);
		assertThat(versionAfterInsert).isEqualTo(0L);

		assertThat(save(token, "{\"answerNotifyEnabled\":true}").getStatus()).isEqualTo(200);
		Long versionAfterFirstUpdate = versionOf(parentId);
		OffsetDateTime updatedAfterFirstUpdate = updatedAtOf(parentId);
		assertThat(versionAfterFirstUpdate).isEqualTo(1L);
		assertThat(createdAtOf(parentId)).as("최초 insert 시각은 바뀌지 않는다").isEqualTo(createdAt);
		assertThat(updatedAfterFirstUpdate).isAfter(updatedAfterInsert);

		assertThat(save(token, "{\"answerNotifyEnabled\":false}").getStatus()).isEqualTo(200);
		assertThat(versionOf(parentId)).isEqualTo(2L);
		assertThat(updatedAtOf(parentId)).isAfter(updatedAfterFirstUpdate);
	}

}
