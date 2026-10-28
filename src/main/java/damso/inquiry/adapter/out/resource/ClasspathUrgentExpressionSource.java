package damso.inquiry.adapter.out.resource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import damso.inquiry.application.port.out.UrgentExpressionSource;
import damso.inquiry.domain.UrgentRule;

/**
 * 서버 리소스 파일(urgent/expressions.txt)에서 표현 목록을 읽는다. 한 줄에 표현 하나, 빈 줄과 '#' 주석은 무시한다.
 * 파일을 못 읽거나 표현이 하나도 없으면 기동을 거부한다 — 목록이 없는 채로 뜨면 긴급을 조용히 놓친다.
 */
@Component
public final class ClasspathUrgentExpressionSource implements UrgentExpressionSource {

	private final List<String> expressions;

	public ClasspathUrgentExpressionSource(
			@Value("${damso.inquiry.urgent-expressions:classpath:urgent/expressions.txt}") Resource resource) {
		this.expressions = List.copyOf(load(resource));
	}

	@Override
	public List<String> expressions() {
		return expressions;
	}

	private static List<String> load(Resource resource) {
		List<String> lines;
		try (var in = resource.getInputStream()) {
			lines = new String(in.readAllBytes(), StandardCharsets.UTF_8).lines().toList();
		}
		catch (IOException | UncheckedIOException ex) {
			throw new IllegalStateException("긴급 표현 목록을 읽을 수 없습니다", ex);
		}
		List<String> loaded = lines.stream()
				.map(String::strip)
				.filter(line -> !line.isEmpty() && !line.startsWith("#"))
				.map(UrgentRule::normalize)
				.filter(expression -> !expression.isEmpty())
				.distinct()
				.toList();
		if (loaded.isEmpty()) {
			throw new IllegalStateException("긴급 표현 목록이 비어 있습니다");
		}
		return loaded;
	}

}
