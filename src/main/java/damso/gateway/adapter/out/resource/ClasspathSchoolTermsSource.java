package damso.gateway.adapter.out.resource;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import damso.gateway.application.port.out.SchoolTermsSource;

/**
 * 서버 리소스(pseudonymization/school-terms.txt)에서 학교 용어를 읽는다. 한 줄에 하나, 빈 줄과 '#' 주석은 무시한다.
 * 용어가 하나도 없으면 기동을 거부한다 — 학교 이름이 가려지지 않은 채 외부로 나가지 않게 (원칙 II·VIII).
 */
@Component
final class ClasspathSchoolTermsSource implements SchoolTermsSource {

	private final List<String> terms;

	ClasspathSchoolTermsSource(
			@Value("${damso.gateway.school-terms:classpath:pseudonymization/school-terms.txt}") Resource resource) {
		List<String> loaded = ResourceLines.read(resource, "학교 용어 목록").lines()
				.map(String::strip)
				.filter(line -> !line.isEmpty() && !line.startsWith("#"))
				.distinct()
				.toList();
		if (loaded.isEmpty()) {
			throw new IllegalStateException("학교 용어 목록이(가) 비어 있습니다");
		}
		this.terms = List.copyOf(loaded);
	}

	@Override
	public List<String> terms() {
		return terms;
	}

}
