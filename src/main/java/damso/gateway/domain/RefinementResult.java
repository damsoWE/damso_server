package damso.gateway.domain;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;

/**
 * 외부 AI 정리 결과의 서버 재검증과 복원 (research R6, FR-007·008·011). 하나라도 어기면 결과 전체를 버린다(fail-closed).
 * <ul>
 * <li>결과 안의 토큰은 이번 가명화가 만든 것만 — 모르는 토큰이 있으면 위반</li>
 * <li>복원한 뒤 길이: title 1~30자, facts 1~1,000자, requests 0~1,000자(코드 포인트)</li>
 * <li>emotionLevel은 LOW·MEDIUM·HIGH, emotionLabels는 고정 6개 중 0~2개, 중복 없음</li>
 * </ul>
 * 필드 구성(스키마 밖 필드 금지)은 JSON을 읽는 쪽이 먼저 확인한다.
 */
public final class RefinementResult {

	public static final int TITLE_MAX = 30;

	public static final int TEXT_MAX = 1000;

	public static final int LABELS_MAX = 2;

	/** 검증·복원이 끝난 값. 문의 내용이므로 toString은 가린다. */
	public record Valid(String title, String facts, String requests, EmotionLevel level, List<EmotionLabel> labels) {

		@Override
		public String toString() {
			return "Valid[level=" + level + ", labels=" + labels + "]";
		}

	}

	private RefinementResult() {
	}

	public static Optional<Valid> validate(String title, String facts, String requests, String level,
			List<String> labels, PseudonymMap map) {
		if (title == null || facts == null || requests == null || level == null || labels == null) {
			return Optional.empty();
		}
		if (!onlyKnownTokens(title, map) || !onlyKnownTokens(facts, map) || !onlyKnownTokens(requests, map)) {
			return Optional.empty();
		}
		Optional<EmotionLevel> emotionLevel = parse(EmotionLevel.class, level);
		if (emotionLevel.isEmpty() || labels.size() > LABELS_MAX || new HashSet<>(labels).size() != labels.size()) {
			return Optional.empty();
		}
		List<EmotionLabel> emotionLabels = labels.stream().map(label -> parse(EmotionLabel.class, label))
				.flatMap(Optional::stream).toList();
		if (emotionLabels.size() != labels.size()) {
			return Optional.empty();
		}
		String restoredTitle = map.restore(title).strip();
		String restoredFacts = map.restore(facts).strip();
		String restoredRequests = map.restore(requests).strip();
		if (!within(restoredTitle, 1, TITLE_MAX) || !within(restoredFacts, 1, TEXT_MAX)
				|| !within(restoredRequests, 0, TEXT_MAX)) {
			return Optional.empty();
		}
		return Optional.of(new Valid(restoredTitle, restoredFacts, restoredRequests, emotionLevel.get(), emotionLabels));
	}

	/** 결과 안의 토큰 모양 문자열이 모두 이번 대응표에 있는지. */
	static boolean onlyKnownTokens(String text, PseudonymMap map) {
		Matcher matcher = TokenKind.TOKEN.matcher(text);
		Set<String> known = map.tokens();
		while (matcher.find()) {
			if (!known.contains(matcher.group())) {
				return false;
			}
		}
		return true;
	}

	private static boolean within(String text, int min, int max) {
		int length = text.codePointCount(0, text.length());
		return length >= min && length <= max;
	}

	/** 대소문자까지 정확히 같은 이름만 받는다(소문자 'low'는 위반). */
	private static <E extends Enum<E>> Optional<E> parse(Class<E> type, String value) {
		return Arrays.stream(type.getEnumConstants()).filter(constant -> constant.name().equals(value)).findFirst();
	}

}
