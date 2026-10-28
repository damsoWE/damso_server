package damso.inquiry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.lang.reflect.Constructor;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletResponse;

import damso.inquiry.application.port.in.CrisisClassificationUseCase;
import damso.inquiry.application.service.CrisisClassificationService;
import damso.support.IntegrationTestSupport;

/**
 * 위기 종류 분류 결과를 받는 연결 지점은 허용값만 받고,
 * 학부모 안내는 위기 종류에 맞는 번호 코드뿐이다.
 */
class CrisisClassificationTest extends IntegrationTestSupport {

	@Autowired
	private CrisisClassificationUseCase classification;

	private UUID submit(Scenario s, String content) throws Exception {
		clock.advance(Duration.ofSeconds(61));
		MockHttpServletResponse response = submitInquiry(s.parent().accessToken(), s.student().getId(), content);
		assertThat(response.getStatus()).isEqualTo(201);
		return UUID.fromString(read(response, "$.inquiryId"));
	}

	private List<String> hotlines(Scenario s, UUID inquiryId) throws Exception {
		MockHttpServletResponse detail = send(get("/api/v1/parent/inquiries/" + inquiryId), s.parent().accessToken());
		assertThat(detail.getStatus()).isEqualTo(200);
		return read(detail, "$.hotlines");
	}

	private String crisisType(UUID inquiryId) {
		return jdbc.queryForObject("select crisis_type from inquiry where id = ?", String.class, inquiryId);
	}

	@Test
	void selfHarmShowsOnlyTheSuicidePreventionNumber() throws Exception {
		Scenario s = teacherWithClassroom();
		UUID id = submit(s, URGENT_SAMPLE);

		classification.apply(id, "SELF_HARM");

		assertThat(hotlines(s, id)).containsExactly("SUICIDE_PREVENTION_109");
		assertThat(crisisType(id)).isEqualTo("SELF_HARM");
	}

	@Test
	void abuseShowsOnlyTheChildAbuseNumber() throws Exception {
		Scenario s = teacherWithClassroom();
		UUID id = submit(s, URGENT_SAMPLE);

		classification.apply(id, "ABUSE");

		assertThat(hotlines(s, id)).containsExactly("CHILD_ABUSE_112");
	}

	@Test
	void otherSafetyShowsNoNumberButStillTheDeliveredNotice() throws Exception {
		Scenario s = teacherWithClassroom();
		UUID id = submit(s, URGENT_SAMPLE);

		classification.apply(id, "OTHER_SAFETY");

		MockHttpServletResponse detail = send(get("/api/v1/parent/inquiries/" + id), s.parent().accessToken());
		List<String> hotlines = read(detail, "$.hotlines");
		List<String> notices = read(detail, "$.notices");
		assertThat(hotlines).isEmpty();
		assertThat(notices).containsExactly("URGENT_DELIVERED");
	}

	@Test
	void unclassifiedAndInvalidResultsKeepTheDefaultNumbers() throws Exception {
		Scenario s = teacherWithClassroom();
		UUID id = submit(s, URGENT_SAMPLE);
		assertThat(hotlines(s, id)).containsExactly("CHILD_ABUSE_112", "SUICIDE_PREVENTION_109");

		for (String invalid : new String[] { "BOGUS", "self_harm", "Abuse", "", " ", "SELF_HARM ", "UNCLASSIFIED", null }) {
			classification.apply(id, invalid);
			assertThat(hotlines(s, id)).as("[%s]", invalid).containsExactly("CHILD_ABUSE_112", "SUICIDE_PREVENTION_109");
			assertThat(crisisType(id)).isEqualTo("UNCLASSIFIED");
		}
	}

	@Test
	void generalInquiriesAreNotChanged() throws Exception {
		Scenario s = teacherWithClassroom();
		UUID id = submit(s, NORMAL_SAMPLE);

		classification.apply(id, "SELF_HARM");

		assertThat(crisisType(id)).isEqualTo("UNCLASSIFIED");
		assertThat(hotlines(s, id)).isEmpty();
	}

	@Test
	void unknownInquiryIsIgnored() {
		classification.apply(UUID.randomUUID(), "ABUSE");
	}

	@Test
	void theFirstClassificationIsKept() throws Exception {
		Scenario s = teacherWithClassroom();
		UUID id = submit(s, URGENT_SAMPLE);

		classification.apply(id, "ABUSE");
		classification.apply(id, "SELF_HARM");

		assertThat(crisisType(id)).isEqualTo("ABUSE");
		assertThat(hotlines(s, id)).containsExactly("CHILD_ABUSE_112");
	}

	@Test
	void onlyTheCrisisTypeChanges() throws Exception {
		Scenario s = teacherWithClassroom();
		UUID id = submit(s, URGENT_SAMPLE);
		String contentBefore = jdbc.queryForObject("select content from inquiry where id = ?", String.class, id);

		classification.apply(id, "SELF_HARM");

		assertThat(jdbc.queryForObject("select content from inquiry where id = ?", String.class, id))
				.isEqualTo(contentBefore);
		assertThat(jdbc.queryForObject("select urgent from inquiry where id = ?", Boolean.class, id)).isTrue();
		assertThat(jdbc.queryForObject("select stage from inquiry where id = ?", String.class, id))
				.isEqualTo("DELIVERED");
	}

	@Test
	void parentResponsesContainOnlyKnownFieldsWithCodeValues() throws Exception {
		Scenario s = teacherWithClassroom();
		UUID id = submit(s, URGENT_SAMPLE);
		classification.apply(id, "SELF_HARM");

		MockHttpServletResponse detail = send(get("/api/v1/parent/inquiries/" + id), s.parent().accessToken());
		MockHttpServletResponse list = send(get("/api/v1/parent/inquiries"), s.parent().accessToken());

		Set<String> allowed = Set.of("inquiryId", "studentId", "studentName", "content", "urgent", "stage",
				"submittedAt", "timeline", "notices", "hotlines", "followUps", "answers");
		com.jayway.jsonpath.DocumentContext json = com.jayway.jsonpath.JsonPath.parse(detail.getContentAsString());
		java.util.Map<String, Object> top = json.read("$");
		assertThat(allowed).containsAll(top.keySet());
		List<String> notices = read(detail, "$.notices");
		List<String> hotlines = read(detail, "$.hotlines");
		assertThat(Set.of("RECEIVED", "URGENT_DELIVERED", "DEFERRED_TO_AVAILABLE_HOURS")).containsAll(notices);
		assertThat(Set.of("SUICIDE_PREVENTION_109", "CHILD_ABUSE_112")).containsAll(hotlines);
		assertThat(list.getContentAsString()).doesNotContain("guidance").doesNotContain("message");
	}

	@Test
	void classificationServiceHasNoExternalCallDependencies() {
		Constructor<?>[] constructors = CrisisClassificationService.class.getConstructors();
		assertThat(constructors).hasSize(1);
		List<String> parameterTypes = Arrays.stream(constructors[0].getParameterTypes()).map(Class::getSimpleName)
				.toList();
		assertThat(parameterTypes).containsExactly("InquiryPort");
		assertThat(parameterTypes).noneMatch(name -> name.contains("Push") || name.contains("Ai")
				|| name.contains("OpenAi") || name.contains("Gateway") || name.contains("Llm"));
	}

}
