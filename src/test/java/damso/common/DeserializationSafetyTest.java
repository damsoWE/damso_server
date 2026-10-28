package damso.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * 보안약점 체크리스트 코드오류 5(신뢰할 수 없는 데이터의 역직렬화): 운영 코드에 Java 네이티브 역직렬화와, 데이터에 적힌
 * 타입대로 객체를 만드는 JSON 설정이 없다. JSON은 고정 DTO·트리(JsonNode)로만 읽고, Redis는 문자열 템플릿만 쓴다.
 */
class DeserializationSafetyTest {

	/** 들어오면 안 되는 API. RedisTemplate&lt;…&gt;은 기본 직렬화가 Java 네이티브라 StringRedisTemplate만 허용한다. */
	private static final List<String> FORBIDDEN = List.of("ObjectInputStream", "readObject(", "readUnshared(",
			"XMLDecoder", "SerializationUtils.deserialize", "activateDefaultTyping", "enableDefaultTyping",
			"JsonTypeInfo", "JdkSerializationRedisSerializer", "RedisTemplate<");

	@Test
	void mainSourcesUseNoUnsafeDeserialization() throws IOException {
		List<String> hits = new ArrayList<>();
		try (Stream<Path> files = Files.walk(Path.of("src/main/java"))) {
			for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
				String source = Files.readString(file);
				for (String api : FORBIDDEN) {
					if (source.contains(api)) {
						hits.add(file + ": " + api);
					}
				}
			}
		}

		assertThat(hits).isEmpty();
	}

}
