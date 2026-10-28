package damso.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** 모든 오류 응답이 공통 형식이며 내부 정보를 노출하지 않는지 검사한다. */
public final class ErrorResponseAssertions {

	private static final JsonMapper MAPPER = JsonMapper.builder().build();
	private static final List<String> FORBIDDEN = List.of(
			"Exception", "at damso.", ".java", "SELECT", "org.springframework", "trace", "/home/");
	private static final Set<String> ALLOWED_KEYS = Set.of("code", "message", "fieldErrors");

	private ErrorResponseAssertions() {
	}

	public static JsonNode assertSafeErrorResponse(String body, String expectedCode) {
		for (String word : FORBIDDEN) {
			assertThat(body).as("error body must not contain '%s'", word).doesNotContain(word);
		}
		JsonNode node = MAPPER.readTree(body);
		Set<String> keys = new HashSet<>(node.propertyNames());
		assertThat(ALLOWED_KEYS).containsAll(keys);
		assertThat(node.get("code").asString()).isEqualTo(expectedCode);
		assertThat(node.get("message").asString()).isNotBlank();
		return node;
	}

}
