package damso.inquiry.domain;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 위협·폭언 판정 규칙 (spec 005). 긴급이 아닌 문의에만 쓴다. 본문을 긴급 판정과 같은 방식으로 정규화한 뒤
 * 표현 목록의 항목이 하나라도 포함되면 표시한다. 오탐 최소화 — 목록에는 분명한 표현만 둔다.
 * 순수 함수다: 외부 호출·AI·Redis·알림 서비스에 의존하지 않으므로 어떤 장애에서도 같은 결과를 낸다 (원칙 IV).
 */
public final class ThreatRule {

	private final List<String> expressions;

	/** @param expressions 표현 목록. 정규화되어 있지 않아도 된다. 비어 있으면 거부한다(목록 없이 표시가 조용히 꺼지지 않게). */
	public ThreatRule(Collection<String> expressions) {
		List<String> normalized = expressions.stream()
				.map(UrgentRule::normalize)
				.filter(expression -> !expression.isEmpty())
				.distinct()
				.collect(Collectors.toUnmodifiableList());
		if (normalized.isEmpty()) {
			throw new IllegalArgumentException("threat expressions must not be empty");
		}
		this.expressions = normalized;
	}

	public boolean isFlagged(String content) {
		if (content == null) {
			return false;
		}
		String normalized = UrgentRule.normalize(content);
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

}
