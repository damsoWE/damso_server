package damso.inquiry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.jayway.jsonpath.JsonPath;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

import damso.school.domain.Student;
import damso.support.IntegrationTestSupport;

/**
 * F2 / SC-005·009, FR-026, 원칙 I·VII: 학부모·교사 응답 어디에도 다른 사용자의 이메일·이름·연락처가 없고,
 * 남의 문의는 존재 여부조차 알 수 없다(404뿐).
 */
class InquiryExposureTest extends IntegrationTestSupport {

	private static final Set<String> FORBIDDEN_KEYS = Set.of("email", "phone", "mobile", "tel", "contact", "address",
			"parentName", "teacherName", "teacherEmail", "parentEmail");

	private static void collectKeys(Object node, Set<String> keys) {
		if (node instanceof Map<?, ?> map) {
			map.forEach((key, value) -> {
				keys.add(String.valueOf(key));
				collectKeys(value, keys);
			});
		}
		else if (node instanceof List<?> list) {
			list.forEach(item -> collectKeys(item, keys));
		}
	}

	private static Set<String> keysOf(MockHttpServletResponse response) throws Exception {
		Set<String> keys = new java.util.HashSet<>();
		collectKeys(JsonPath.parse(response.getContentAsString()).read("$"), keys);
		return keys;
	}

	private UUID submit(Scenario s, String content) throws Exception {
		clock.advance(Duration.ofSeconds(61));
		MockHttpServletResponse response = submitInquiry(s.parent().accessToken(), s.student().getId(), content);
		assertThat(response.getStatus()).isEqualTo(201);
		return UUID.fromString(read(response, "$.inquiryId"));
	}

	@Test
	void noResponseCarriesAnotherUsersEmailNameOrContactField() throws Exception {
		Scenario s = teacherWithClassroom();
		Student others = newStudent(s.classroom(), "이둘");
		SignedUp otherParent = signUpWith(issueParentCode(others), "정부모");
		UUID general = submit(s, NORMAL_SAMPLE);
		UUID urgent = submit(s, URGENT_SAMPLE);
		clock.advance(Duration.ofSeconds(61));
		submitInquiry(otherParent.accessToken(), others.getId(), NORMAL_SAMPLE + " 다른");

		List<MockHttpServletResponse> parentResponses = new ArrayList<>(List.of(
				submitInquiry(s.parent().accessToken(), s.student().getId(), NORMAL_SAMPLE + " 한번 더"),
				send(get("/api/v1/parent/inquiries"), s.parent().accessToken()),
				send(get("/api/v1/parent/inquiries/" + general), s.parent().accessToken()),
				send(get("/api/v1/parent/inquiries/" + urgent), s.parent().accessToken())));
		List<MockHttpServletResponse> teacherResponses = List.of(
				send(get("/api/v1/teacher/inquiries"), s.teacher().accessToken()),
				send(get("/api/v1/teacher/inquiries/" + general), s.teacher().accessToken()),
				send(get("/api/v1/teacher/inquiries/" + urgent), s.teacher().accessToken()),
				send(get("/api/v1/teacher/inquiries/" + urgent + "/content"), s.teacher().accessToken()));

		for (MockHttpServletResponse response : parentResponses) {
			assertThat(response.getStatus()).isIn(200, 201, 429);
			String body = response.getContentAsString();
			assertThat(keysOf(response)).as(body).doesNotContainAnyElementsOf(FORBIDDEN_KEYS);
			// 학부모 응답에는 교사의 이메일·이름, 다른 학부모의 이메일·이름·문의가 없다
			assertThat(body).doesNotContain(s.teacher().email()).doesNotContain("최선생").doesNotContain("@")
					.doesNotContain(otherParent.email()).doesNotContain("정부모").doesNotContain("이둘");
		}
		for (MockHttpServletResponse response : teacherResponses) {
			assertThat(response.getStatus()).isEqualTo(200);
			String body = response.getContentAsString();
			assertThat(keysOf(response)).as(body).doesNotContainAnyElementsOf(FORBIDDEN_KEYS);
			// 교사 응답에는 학부모의 이메일·이름이 없다
			assertThat(body.toLowerCase(Locale.ROOT)).doesNotContain(s.parent().email().toLowerCase(Locale.ROOT))
					.doesNotContain("박부모").doesNotContain("정부모").doesNotContain("@");
		}
	}

	/**
	 * spec 005 T024 / US3 시나리오 1·2, FR-012, SC-004: 위협·폭언 표시는 학부모에게 나가지 않는다. 표시된 문의와 표시되지 않은 문의의
	 * 학부모 응답은 키와 안내 코드가 같아 표시 여부를 짐작할 수 없다.
	 */
	@Test
	void parentResponsesLookTheSameWhetherOrNotTheInquiryIsThreatFlagged() throws Exception {
		Scenario s = teacherWithClassroom();
		clock.advance(Duration.ofSeconds(61));
		MockHttpServletResponse flaggedSubmit = submitInquiry(s.parent().accessToken(), s.student().getId(),
				"이런 식이면 선생님 가만두지 않겠습니다.");
		clock.advance(Duration.ofSeconds(61));
		MockHttpServletResponse plainSubmit = submitInquiry(s.parent().accessToken(), s.student().getId(),
				NORMAL_SAMPLE);
		assertThat(flaggedSubmit.getStatus()).isEqualTo(201);
		assertThat(plainSubmit.getStatus()).isEqualTo(201);
		String flagged = read(flaggedSubmit, "$.inquiryId");
		String plain = read(plainSubmit, "$.inquiryId");
		assertThat((Boolean) read(send(get("/api/v1/teacher/inquiries/" + flagged), s.teacher().accessToken()),
				"$.threatFlagged")).isTrue();

		MockHttpServletResponse flaggedDetail = send(get("/api/v1/parent/inquiries/" + flagged),
				s.parent().accessToken());
		MockHttpServletResponse plainDetail = send(get("/api/v1/parent/inquiries/" + plain), s.parent().accessToken());
		MockHttpServletResponse list = send(get("/api/v1/parent/inquiries"), s.parent().accessToken());

		assertThat(keysOf(flaggedSubmit)).isEqualTo(keysOf(plainSubmit));
		assertThat(keysOf(flaggedDetail)).isEqualTo(keysOf(plainDetail));
		List<Map<String, Object>> items = read(list, "$");
		assertThat(items).hasSize(2);
		assertThat(items.get(0).keySet()).isEqualTo(items.get(1).keySet());
		for (MockHttpServletResponse response : List.of(flaggedSubmit, plainSubmit, flaggedDetail, plainDetail, list)) {
			assertThat(keysOf(response)).noneMatch(key -> key.toLowerCase(Locale.ROOT).contains("threat"));
		}
		assertThat((List<?>) read(flaggedSubmit, "$.notices")).isEqualTo(read(plainSubmit, "$.notices"));
		assertThat((List<?>) read(flaggedSubmit, "$.hotlines")).isEqualTo(read(plainSubmit, "$.hotlines"));
		assertThat((List<?>) read(flaggedDetail, "$.notices")).isEqualTo(read(plainDetail, "$.notices"));
		assertThat((List<?>) read(flaggedDetail, "$.hotlines")).isEqualTo(read(plainDetail, "$.hotlines"));
	}

	@Test
	void guidanceFieldsOnlyHoldKnownCodes() throws Exception {
		Scenario s = teacherWithClassroom();
		UUID urgent = submit(s, URGENT_SAMPLE);

		MockHttpServletResponse detail = send(get("/api/v1/parent/inquiries/" + urgent), s.parent().accessToken());

		List<String> notices = read(detail, "$.notices");
		List<String> hotlines = read(detail, "$.hotlines");
		assertThat(Set.of("RECEIVED", "URGENT_DELIVERED", "DEFERRED_TO_AVAILABLE_HOURS")).containsAll(notices);
		assertThat(Set.of("SUICIDE_PREVENTION_109", "CHILD_ABUSE_112")).containsAll(hotlines);
	}

	@Test
	void otherTeachersAndParentsCannotTellWhetherAnInquiryExists() throws Exception {
		Scenario s = teacherWithClassroom(1, 1);
		Scenario other = teacherWithClassroom(1, 2);
		UUID mine = submit(s, URGENT_SAMPLE);
		UUID unknown = UUID.randomUUID();

		for (String suffix : List.of("", "/content")) {
			MockHttpServletResponse real = send(get("/api/v1/teacher/inquiries/" + mine + suffix),
					other.teacher().accessToken());
			MockHttpServletResponse none = send(get("/api/v1/teacher/inquiries/" + unknown + suffix),
					other.teacher().accessToken());
			assertThat(real.getStatus()).as("teacher %s", suffix).isEqualTo(404);
			assertThat(real.getContentAsString()).isEqualTo(none.getContentAsString());
		}
		MockHttpServletResponse real = send(get("/api/v1/parent/inquiries/" + mine), other.parent().accessToken());
		MockHttpServletResponse none = send(get("/api/v1/parent/inquiries/" + unknown), other.parent().accessToken());
		assertThat(real.getStatus()).isEqualTo(404);
		assertThat(real.getContentAsString()).isEqualTo(none.getContentAsString());
	}

}
