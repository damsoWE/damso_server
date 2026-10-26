package damso.gateway.adapter.out.resource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

import org.springframework.core.io.Resource;

/** 게이트웨이 리소스 파일 읽기. 못 읽거나 비어 있으면 기동을 거부한다 — 메시지에는 파일 이름만 넣고 내용은 넣지 않는다. */
final class ResourceLines {

	private ResourceLines() {
	}

	static String read(Resource resource, String name) {
		String text;
		try (var in = resource.getInputStream()) {
			text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
		catch (IOException | UncheckedIOException ex) {
			throw new IllegalStateException(name + "을(를) 읽을 수 없습니다", ex);
		}
		if (text.isBlank()) {
			throw new IllegalStateException(name + "이(가) 비어 있습니다");
		}
		return text;
	}

}
