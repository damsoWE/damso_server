package damso.inquiry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import org.yaml.snakeyaml.Yaml;

import damso.account.domain.Role;
import damso.support.IntegrationTestSupport;

/**
 * F2 / FR-007, SC-005 (F1 tasks 메모의 FR-004 검증): 슈퍼 관리자는 학부모·교사 문의 API를 쓸 수 없고,
 * 관리자 경로에는 문의 내용·제목·처리 내용을 돌려주는 API가 없다.
 */
class SuperAdminInquiryAccessTest extends IntegrationTestSupport {

	private static final Set<String> CONTENT_PROPERTIES = Set.of("content", "title", "refinedContent", "answer",
			"originalContent", "timeline", "stage");

	@Test
	void adminTokenIsForbiddenOnEveryInquiryPath() throws Exception {
		Scenario s = teacherWithClassroom();
		UUID id = UUID.fromString(read(submitInquiry(s.parent().accessToken(), s.student().getId(), URGENT_SAMPLE),
				"$.inquiryId"));
		String admin = tokenFor(createAccount(Role.SUPER_ADMIN));

		List<String> getPaths = List.of("/api/v1/parent/inquiries", "/api/v1/parent/inquiries/" + id,
				"/api/v1/teacher/inquiries", "/api/v1/teacher/inquiries/" + id,
				"/api/v1/teacher/inquiries/" + id + "/content", "/api/v1/teacher/notification-settings");
		for (String path : getPaths) {
			assertThat(send(get(path), admin).getStatus()).as(path).isEqualTo(403);
		}
		MockHttpServletResponse submit = send(
				json(post("/api/v1/parent/inquiries"),
						"{\"studentId\":\"" + s.student().getId() + "\",\"content\":\"" + NORMAL_SAMPLE + "\"}")
						.header("Idempotency-Key", UUID.randomUUID().toString()),
				admin);
		assertThat(submit.getStatus()).isEqualTo(403);
		assertThat(jdbc.queryForObject("select count(*) from audit_log", Integer.class)).isZero();
		assertThat(jdbc.queryForObject("select count(*) from inquiry", Integer.class)).isEqualTo(1);
	}

	@Test
	void adminApiSpecHasNoInquiryContentOrTitleFields() throws IOException {
		Map<String, Object> spec = new Yaml().load(Files.readString(Path.of("docs/api/openapi.yaml")));
		@SuppressWarnings("unchecked")
		Map<String, Object> paths = (Map<String, Object>) spec.get("paths");

		Set<String> adminPaths = new HashSet<>();
		Set<String> properties = new HashSet<>();
		for (Map.Entry<String, Object> path : paths.entrySet()) {
			if (path.getKey().startsWith("/api/v1/admin")) {
				adminPaths.add(path.getKey());
				collectProperties(path.getValue(), spec, properties, new HashSet<>());
			}
		}

		assertThat(adminPaths).as("관리자 경로가 있어야 검사가 의미 있다").isNotEmpty();
		assertThat(adminPaths).noneMatch(path -> path.contains("inquir"));
		assertThat(properties).as("관리자 경로가 돌려주는 필드").doesNotContainAnyElementsOf(CONTENT_PROPERTIES);
	}

	@SuppressWarnings("unchecked")
	private static void collectProperties(Object node, Map<String, Object> spec, Set<String> properties,
			Set<String> visitedRefs) {
		if (node instanceof Map<?, ?> map) {
			Object ref = map.get("$ref");
			if (ref instanceof String reference && reference.startsWith("#/") && visitedRefs.add(reference)) {
				Object target = spec;
				for (String part : reference.substring(2).split("/")) {
					target = ((Map<String, Object>) target).get(part);
				}
				collectProperties(target, spec, properties, visitedRefs);
			}
			Object props = map.get("properties");
			if (props instanceof Map<?, ?> propertyMap) {
				propertyMap.keySet().forEach(key -> properties.add(String.valueOf(key)));
			}
			map.values().forEach(value -> collectProperties(value, spec, properties, visitedRefs));
		}
		else if (node instanceof List<?> list) {
			list.forEach(item -> collectProperties(item, spec, properties, visitedRefs));
		}
	}

}
