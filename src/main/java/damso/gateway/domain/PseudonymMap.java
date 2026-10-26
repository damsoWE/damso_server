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
		return restore(text, false);
	}

	/**
	 * 정리 결과를 사람이 읽을 글로 되돌린다. 이름·학교·학급은 원래 표기로 되돌리고, 연락처·식별번호(전화·이메일·주소·주민번호)는
	 * 되돌리지 않고 고정 문구("(연락처 생략)" 등)로 남긴다 — 정리 결과에 연락처가 새로 저장되지 않게(원칙 I).
	 */
	public String restoreWithoutContacts(String text) {
		return restore(text, true);
	}

	private String restore(String text, boolean withholdContacts) {
		Matcher matcher = TokenKind.TOKEN.matcher(text);
		StringBuilder restored = new StringBuilder();
		while (matcher.find()) {
			String token = matcher.group();
			String original = originalByToken.get(token);
			String replacement = original == null ? token : original;
			if (original != null && withholdContacts) {
				TokenKind kind = kindOf(matcher.group(1));
				if (kind != null && kind.isContactOrIdentifier()) {
					replacement = kind.withheldLabel();
				}
			}
			matcher.appendReplacement(restored, Matcher.quoteReplacement(replacement));
		}
		matcher.appendTail(restored);
		return restored.toString();
	}

	private static TokenKind kindOf(String name) {
		for (TokenKind kind : TokenKind.values()) {
			if (kind.name().equals(name)) {
				return kind;
			}
		}
		return null;
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
