package damso.inquiry.domain;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 위협·폭언 판정 규칙. 긴급이 아닌 문의에만 쓴다. 오탐 최소화가 목표라 긴급 판정(공백을 지우고 포함 검사)과
 * 달리 낱말 경계를 본다.
 * - 표현은 어절의 시작에서 시작할 때만 맞는다("아이 고모가"의 '모가지', "질병 신고"의 '병신'은 맞지 않는다).
 * - 띄어쓰기 차이는 표현에 공백이 있는 자리에서만 허용한다("가만 두지 않겠"은 "가만두지 않겠"·"가만\n두지 않겠"과 맞고,
 *   공백이 없는 "애새끼"는 "애 새끼손가락"과 맞지 않는다).
 * - 전각→반각(NFKC)과 대소문자는 구분하지 않는다.
 * - 표현 바로 뒤에 인용·전언 표지가 오면 남이 한 말을 전하는 것으로 보고 표시하지 않는다(isReportedSpeech).
 * 순수 함수다: 외부 호출·AI·Redis·알림 서비스에 의존하지 않으므로 어떤 장애에서도 같은 결과를 낸다.
 */
public final class ThreatRule {

	/** 앞 글자가 글자·숫자가 아니어야 한다 = 어절(또는 문장부호 뒤)의 시작이다. */
	private static final String WORD_START = "(?<![\\p{L}\\p{N}])";

	/** 표현 조각 사이에 허용하는 공백(줄바꿈·탭·전각 공백 포함, 0개 이상). */
	private static final String OPTIONAL_SPACE = "[\\s\\p{Z}]*";

	private static final Pattern SPACES = Pattern.compile("[\\s\\p{Z}]+");

	/** 문장의 끝. 인용·전언 표지는 같은 문장 안에서만 찾는다(NFKC가 '…'를 '...'로 바꾼다). */
	private static final Pattern SENTENCE_END = Pattern.compile("[.?!\\n]");

	/** 표현이 든 어절에 바로 붙는 전언 어미: "개새끼래요", "병신이란", "입 닥치래요", "가만 안 두겠대요". */
	private static final Pattern REPORTED_TAIL = Pattern.compile("^(?:이?(?:래|랬|란|라던|라더)|대요|댔|다던|다더)");

	/**
	 * 표현 뒤 두 어절 안의 인용·전언 표지: "~라고 했/하셨/들었", "~라는 말/소리", "~ 소리를 들었", "~했대요".
	 * 학부모 자신의 강조("입 닥치라고요!")는 말하는 동사가 없어 걸리지 않는다.
	 */
	private static final Pattern REPORTED_NEARBY = Pattern.compile(
			"(?:라|다|냐|자)고" + OPTIONAL_SPACE
					+ "(?:했|하셨|하시|하더|하던|하며|하면서|하길|말했|말씀하|들었|그랬|그러셨|그러더|욕했|욕하)"
					+ "|(?:라|다)는" + OPTIONAL_SPACE + "(?:말|소리|얘기|이야기|욕)"
					+ "|(?:소리|말|욕|얘기)(?:을|를)?" + OPTIONAL_SPACE + "(?:들었|듣고|들어|들은)"
					+ "|(?:했|하셨|그랬|그러셨)(?:대|다던|다더)");

	private final List<Pattern> patterns;

	/** @param expressions 표현 목록. 정규화되어 있지 않아도 된다. 비어 있으면 거부한다(목록 없이 표시가 조용히 꺼지지 않게). */
	public ThreatRule(Collection<String> expressions) {
		List<Pattern> compiled = expressions.stream()
				.map(ThreatRule::normalizeExpression)
				.filter(expression -> !expression.isEmpty())
				.distinct()
				.map(ThreatRule::compile)
				.collect(Collectors.toUnmodifiableList());
		if (compiled.isEmpty()) {
			throw new IllegalArgumentException("threat expressions must not be empty");
		}
		this.patterns = compiled;
	}

	public boolean isFlagged(String content) {
		if (content == null) {
			return false;
		}
		String normalized = Normalizer.normalize(content, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
		if (normalized.isBlank()) {
			return false;
		}
		for (Pattern pattern : patterns) {
			Matcher matcher = pattern.matcher(normalized);
			while (matcher.find()) {
				if (!isReportedSpeech(normalized.substring(matcher.end()))) {
					return true;
				}
			}
		}
		return false;
	}

	/**
	 * 표현 뒤에 인용·전언 표지가 오면 남이 한 말을 전하는 문장으로 보고 그 자리는 표시하지 않는다(PR #13 리뷰 결정).
	 * "친구가 우리 애한테 개새끼라고 했대요"는 학부모의 폭언이 아니다. 같은 문장, 표현이 든 어절의 나머지와 뒤 두 어절까지만 본다.
	 * 알려진 한계: 학부모가 자기 말을 "~다고 했잖아요"처럼 다시 전하는 문장도 표시되지 않는다.
	 */
	private static boolean isReportedSpeech(String rest) {
		Matcher end = SENTENCE_END.matcher(rest);
		String sentence = end.find() ? rest.substring(0, end.start()) : rest;
		String[] words = SPACES.split(sentence, 4);
		if (REPORTED_TAIL.matcher(words[0]).find()) {
			return true;
		}
		String nearby = String.join(" ", Arrays.asList(words).subList(0, Math.min(words.length, 3)));
		return REPORTED_NEARBY.matcher(nearby).find();
	}

	/** 전각→반각(NFKC), 소문자, 공백은 한 칸으로 모으고 앞뒤 공백을 뗀다. 표현 안의 공백 자리는 남긴다(띄어쓰기를 허용할 자리). */
	public static String normalizeExpression(String expression) {
		String folded = Normalizer.normalize(expression, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
		return Arrays.stream(folded.split("[\\s\\p{Z}]+"))
				.filter(part -> !part.isEmpty())
				.collect(Collectors.joining(" "));
	}

	/** 어절 시작 + 표현의 각 조각을 글자 그대로(정규식 문자 무력화) 잇되, 조각 사이에만 공백을 허용한다. */
	private static Pattern compile(String expression) {
		String body = Arrays.stream(expression.split(" "))
				.map(Pattern::quote)
				.collect(Collectors.joining(OPTIONAL_SPACE));
		return Pattern.compile(WORD_START + body);
	}

}
