package damso.inquiry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletResponse;

import damso.gateway.application.port.out.ExternalAiPort.AiTask;
import damso.inquiry.application.port.in.CrisisClassificationUseCase;
import damso.notification.domain.NotificationKind;
import damso.support.FakeExternalAi;
import damso.support.IntegrationTestSupport;

/**
 * 긴급 문의는 접수 뒤 위기 종류를 한 번 분류해 학부모 안내 번호를 고른다.
 * 가린 글만 나가고, 허용값이 아니거나 실패하면 기본 안내(112·109)가 그대로다. 긴급 전달·알림은 분류와 무관하다.
 */
class CrisisClassificationPipelineTest extends IntegrationTestSupport {

	private static final List<String> DEFAULT_HOTLINES = List.of("CHILD_ABUSE_112", "SUICIDE_PREVENTION_109");

	@Autowired
	private CrisisClassificationUseCase classification;

	private UUID submitUrgent(Scenario s) throws Exception {
		MockHttpServletResponse response = submitInquiry(s.parent().accessToken(), s.student().getId(),
				STUDENT_NAME + " 엄마예요. " + URGENT_SAMPLE + " 010-1234-5678");
		assertThat(response.getStatus()).isEqualTo(201);
		assertThat(IntegrationTestSupport.<List<String>>read(response, "$.hotlines"))
				.as("접수 응답은 분류를 기다리지 않는다").isEqualTo(DEFAULT_HOTLINES);
		return UUID.fromString(read(response, "$.inquiryId"));
	}

	private List<String> hotlines(Scenario s, UUID id) throws Exception {
		return read(send(get("/api/v1/parent/inquiries/" + id), s.parent().accessToken()), "$.hotlines");
	}

	@Test
	void classifiedTypesPickTheMatchingHotlines() throws Exception {
		Scenario s = teacherWithClassroom();
		fakeAi.replyNext(FakeExternalAi.crisisJson("SELF_HARM"));
		UUID selfHarm = submitUrgent(s);
		fakeAi.replyNext(FakeExternalAi.crisisJson("ABUSE"));
		UUID abuse = submitUrgent(s);
		fakeAi.replyNext(FakeExternalAi.crisisJson("OTHER_SAFETY"));
		UUID other = submitUrgent(s);

		assertThat(hotlines(s, selfHarm)).containsExactly("SUICIDE_PREVENTION_109");
		assertThat(hotlines(s, abuse)).containsExactly("CHILD_ABUSE_112");
		assertThat(hotlines(s, other)).isEmpty();
		assertThat(jdbc.queryForList("select crisis_type from inquiry order by submitted_at", String.class))
				.containsExactly("SELF_HARM", "ABUSE", "OTHER_SAFETY");
	}

	@Test
	void onlyMaskedTextIsSentAndOnlyForUrgentInquiries() throws Exception {
		Scenario s = teacherWithClassroom();
		UUID urgent = submitUrgent(s);
		clock.advance(java.time.Duration.ofSeconds(61));
		submitInquiry(s.parent().accessToken(), s.student().getId(), NORMAL_SAMPLE);

		assertThat(fakeAi.callsOf(AiTask.CLASSIFY_CRISIS)).hasSize(1);
		assertThat(fakeAi.callsOf(AiTask.CLASSIFY_CRISIS).get(0).userText()).doesNotContain(STUDENT_NAME)
				.doesNotContain("010-1234-5678").contains("[STUDENT_1]").contains("[PHONE_1]");
		assertThat(jdbc.queryForObject("select count(*) from refinement_job where kind = 'CLASSIFY_CRISIS' "
				+ "and inquiry_id = ?", Integer.class, urgent)).isEqualTo(1);
		assertThat(jdbc.queryForObject("select count(*) from refinement_job where kind = 'CLASSIFY_CRISIS'",
				Integer.class)).isEqualTo(1);
	}

	@Test
	void anythingButAnAllowedValueKeepsTheDefaultHotlinesAndIsTriedOnce() throws Exception {
		Scenario s = teacherWithClassroom();
		fakeAi.replyNext(FakeExternalAi.crisisJson("self_harm"));
		UUID lower = submitUrgent(s);
		fakeAi.replyNext(FakeExternalAi.crisisJson(""));
		UUID empty = submitUrgent(s);
		fakeAi.replyNext("{\"crisisType\":\"ABUSE\",\"note\":\"추가 설명\"}");
		UUID extra = submitUrgent(s);
		fakeAi.noResponseNext();
		UUID silent = submitUrgent(s);
		fakeAi.throwNext();
		UUID broken = submitUrgent(s);

		for (UUID id : List.of(lower, empty, extra, silent, broken)) {
			assertThat(hotlines(s, id)).isEqualTo(DEFAULT_HOTLINES);
			assertThat(jdbc.queryForObject("select crisis_type from inquiry where id = ?", String.class, id))
					.isEqualTo("UNCLASSIFIED");
			assertThat(jdbc.queryForObject("select status from refinement_job where inquiry_id = ?", String.class,
					id)).isEqualTo("FAILED");
			assertThat(jdbc.queryForObject("select count(*) from refinement_attempt a join refinement_job j "
					+ "on j.id = a.job_id where j.inquiry_id = ?", Integer.class, id)).isEqualTo(1);
		}
	}

	@Test
	void urgentDeliveryAndNotificationDoNotDependOnClassification() throws Exception {
		Scenario s = teacherWithClassroom();
		registerDevice(s.teacher().userId(), sessionIdOf(s.teacher().accessToken()), "teacher-token");
		fakeAi.throwNext();
		UUID id = submitUrgent(s);

		assertThat(jdbc.queryForObject("select stage from inquiry where id = ?", String.class, id))
				.isEqualTo("DELIVERED");
		assertThat(pushes.sentOfKind(NotificationKind.URGENT)).hasSize(1);
		assertThat(jdbc.queryForObject("select urgent from inquiry where id = ?", Boolean.class, id)).isTrue();
	}

	@Test
	void classificationChangesOnlyTheHotlinesAndIsNotShownToTheTeacher() throws Exception {
		Scenario s = teacherWithClassroom();
		fakeAi.replyNext(FakeExternalAi.crisisJson("ABUSE"));
		UUID id = submitUrgent(s);

		assertThat(jdbc.queryForObject("select stage from inquiry where id = ?", String.class, id))
				.isEqualTo("DELIVERED");
		assertThat(jdbc.queryForObject("select count(*) from notification_request where inquiry_id = ? "
				+ "and kind = 'URGENT'", Integer.class, id)).isEqualTo(1);
		MockHttpServletResponse teacherDetail = send(get("/api/v1/teacher/inquiries/" + id), s.teacher().accessToken());
		assertThat(teacherDetail.getContentAsString()).doesNotContain("ABUSE").doesNotContain("crisis")
				.doesNotContain("hotline");

		// 한 번 정해진 분류는 바뀌지 않는다
		classification.apply(id, "SELF_HARM");
		assertThat(hotlines(s, id)).containsExactly("CHILD_ABUSE_112");
	}

}
