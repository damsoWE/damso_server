package damso.gateway.domain;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;

/**
 * 토큰 ↔ 원래 값의 대응표(복원표). 정리 한 번 동안만 메모리에 있고, 복원이 끝나면 버린다 — DB·캐시·파일·로그 어디에도 남기지 않는다
 * (원칙 II). 그래서 직렬화를 구현하지 않고, toString은 토큰 종류별 건수만 보여 준다.
 */
public final class PseudonymMap {

	private final Map<String, String> originalByToken;

	PseudonymMap(Map<String, String> originalByToken) {
		this.originalByToken = Collections.unmodifiableMap(new LinkedHashMap<>(originalByToken));
	}

	public static PseudonymMap empty() {
		return new PseudonymMap(Map.of());
	}

	public boolean contains(String token) {
		return originalByToken.containsKey(token);
	}

	public Set<String> tokens() {
		return originalByToken.keySet();
	}

	/** 원래 값 목록. 가린 뒤 자기 점검(MaskingVerifier)이 남은 값이 없는지 볼 때만 쓴다. */
	Iterable<String> originals() {
		return originalByToken.values();
	}

	/** 글 안의 토큰을 원래 값으로 바꾼다. 이 대응표에 없는 토큰 모양 문자열은 그대로 둔다. */
	public String restore(String text) {
		Matcher matcher = TokenKind.TOKEN.matcher(text);
		StringBuilder restored = new StringBuilder();
		while (matcher.find()) {
			String original = originalByToken.get(matcher.group());
			matcher.appendReplacement(restored, Matcher.quoteReplacement(original != null ? original : matcher.group()));
		}
		matcher.appendTail(restored);
		return restored.toString();
	}

	@Override
	public String toString() {
		Map<String, Integer> counts = new LinkedHashMap<>();
		for (String token : originalByToken.keySet()) {
			Matcher matcher = TokenKind.TOKEN.matcher(token);
			String kind = matcher.matches() ? matcher.group(1) : "?";
			counts.merge(kind, 1, Integer::sum);
		}
		return "PseudonymMap" + counts;
	}

}
