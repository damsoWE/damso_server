package damso.inquiry.domain;

import java.text.Normalizer;
import java.util.Collection;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.List;

/**
 * 긴급 판정 규칙. 본문을 정규화한 뒤 표현 목록의 항목이 하나라도 포함되면 긴급이다.
 * 순수 함수다: 외부 호출·AI·Redis·알림 서비스에 의존하지 않으므로 어떤 장애에서도 같은 결과를 낸다.
 */
public final class UrgentRule {

	private final List<String> expressions;

	/** @param expressions 표현 목록. 정규화되어 있지 않아도 된다. 비어 있으면 거부한다(목록이 없으면 긴급을 놓친다). */
	public UrgentRule(Collection<String> expressions) {
		List<String> normalized = expressions.stream()
				.map(UrgentRule::normalize)
				.filter(expression -> !expression.isEmpty())
				.distinct()
				.collect(Collectors.toUnmodifiableList());
		if (normalized.isEmpty()) {
			throw new IllegalArgumentException("urgent expressions must not be empty");
		}
		this.expressions = normalized;
	}

	public boolean isUrgent(String content) {
		if (content == null) {
			return false;
		}
		String normalized = normalize(content);
		if (normalized.isEmpty()) {
			return false;
		}
		for (String expression : expressions) {
			if (normalized.contains(expression)) {
				return true;
			}
		}
		return false;
	}

	/** 전각→반각(NFKC), 소문자, 공백·줄바꿈 제거. 표현 목록과 본문이 같은 방식으로 맞춰진다. */
	public static String normalize(String text) {
		String folded = Normalizer.normalize(text, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
		StringBuilder out = new StringBuilder(folded.length());
		folded.codePoints()
				.filter(cp -> !Character.isWhitespace(cp) && !Character.isSpaceChar(cp))
				.forEach(out::appendCodePoint);
		return out.toString();
	}

}
