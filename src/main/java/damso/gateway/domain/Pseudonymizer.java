package damso.gateway.domain;

import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 문의 본문의 식별정보를 토큰으로 바꾼다. 외부 의존 없는 순수 함수다.
 * <ol>
 * <li>입력에 이미 있는 토큰 모양 문자열([STUDENT_1] 등)은 대괄호를 전각(［ ］)으로 바꿔 무력화한다 — 학부모가 쓴 글이 복원 단계에서
 * 다른 값으로 바뀌지 않게.</li>
 * <li>2차 패턴을 먼저: 주민등록번호 형태, 전화번호, 이메일, 주소(행정구역, 공동주택 이름, 아파트 동·호, 도로명, 지번). 사전 표기("1-1" 같은 학급 표기)가
 * 번호 안을 먼저 바꿔 번호 패턴이 깨지는 일을 막는다.</li>
 * <li>1차 사전: 학생·보호자·교사 이름(성+이름, 3자 이름은 이름 2자도), 학급(학년·반 표기 변형), 학교 용어. 이미 들어간 토큰은 건너뛰고,
 * 숫자로 시작·끝나는 표기는 숫자 경계, 영문으로 시작·끝나는 표기는 영문·숫자 경계를 건다("2반"이 "12반" 안에서, "EN"이 토큰 안에서
 * 매치되지 않게). 긴 표기부터 한 번에 바꾼다.</li>
 * </ol>
 * 토큰은 표기마다 따로 매기고(같은 표기는 같은 토큰), 복원하면 본 표기 그대로 돌아온다 — "하나만"이 "이하나만"으로, "2학년"이
 * "2학년 3반"으로 바뀌어 번역본의 뜻이 달라지지 않게(자체 코드 리뷰, 방향 A).
 * 사전에 없는 이름(다른 반 친구 등)과 패턴에 맞지 않는 식별정보는 가려지지 않는다 — 알려진 한계.
 */
public final class Pseudonymizer {

	/** 숫자 뒤에 오면 시간·수량·학년 같은 말이라 주소로 보지 않는다. */
	private static final String NOT_A_QUANTITY = "(?!\\s?(?:시|분|초|일|월|년|명|개|번(?!지)|번째|학년|반|살|등|교시|층|원|권|장|회|차))";

	/** 광역시·도 이름(줄임·정식 표기). 뒤에 시·군·구가 하나 이상 와야 주소로 본다("대구 친구"·"경기 결과"는 주소가 아니다). */
	private static final String PROVINCE = "(?:서울|부산|대구|인천|광주|대전|울산|세종|경기|강원|충북|충남|전북|전남|경북|경남|제주"
			+ "|충청북|충청남|전라북|전라남|경상북|경상남)(?:특별자치시|특별자치도|특별시|광역시|시|도)?";

	/**
	 * 시·군·구. 앞 글자가 두 자 이상이어야 하고("친구"·"도구"·"다시"는 주소가 아니다), 한 글자 구는 중·동·서·남·북구만 받는다.
	 * "남자친구"·"동네축구" 같은 합성어와 시각("다섯시")은 뺀다.
	 */
	private static final String CITY_DISTRICT = "(?:(?![가-힣]{0,3}(?:친구|도구|가구|입구|출구|연구|요구|야구|축구|농구|배구|탁구|족구)"
			+ "(?![가-힣]))(?![가-힣]{0,2}(?:다섯|여섯|일곱|여덟|아홉|열한|열두)시)[가-힣]{2,4}(?:시|군|구)|[중동서남북]구)";

	/** 읍·면·동·리(동 뒤 "1가" 같은 표기 포함). 뒤에 지번이 붙으면 함께 가린다("역삼동 123-4"). */
	private static final String TOWN = "[가-힣]{1,5}(?:동|읍|면|리)(?:\\d{1,2}가)?(?:\\s?\\d{1,5}(?:-\\d{1,5})?(?:번지)?(?!\\d)"
			+ NOT_A_QUANTITY + ")?";

	/** 공동주택 이름: 이름에 붙은 종류("한빛아파트")나 브랜드("래미안"), 뒤의 동·호수까지 한 덩어리로 가린다. */
	private static final String APARTMENT = "(?:[가-힣A-Za-z0-9]{1,15}(?:아파트|빌라|맨션|오피스텔|하이츠|빌리지|캐슬|팰리스)"
			+ "|[가-힣A-Za-z0-9]{0,10}(?:래미안|푸르지오|힐스테이트|아이파크|e편한세상|더샵))"
			+ "(?:\\s?\\d{1,4}동)?(?:\\s?\\d{1,4}호)?(?!\\d)";

	/** 2차 패턴. 순서가 중요하다(주민번호를 전화번호보다 먼저, 공동주택 이름을 동·호수보다 먼저). */
	static final List<SecondaryPattern> SECONDARY = List.of(
			new SecondaryPattern(TokenKind.ID_NUMBER, Pattern.compile("(?<!\\d)\\d{6}[- ]?[1-8]\\d{6}(?!\\d)")),
			new SecondaryPattern(TokenKind.PHONE,
					Pattern.compile("(?<!\\d)(?:01[016789]|0[2-6]\\d?)[-. ]?\\d{3,4}[-. ]?\\d{4}(?!\\d)")),
			new SecondaryPattern(TokenKind.EMAIL,
					Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}")),
			// 행정구역: "서울 강남구 역삼동", "경기도 성남시 분당구", 광역시·도 없이 "강남구 역삼동"·"성남시 분당구"
			new SecondaryPattern(TokenKind.ADDRESS, Pattern.compile("(?<![가-힣])" + PROVINCE + "(?:\\s?"
					+ CITY_DISTRICT + "){1,2}(?:\\s?" + TOWN + ")?")),
			new SecondaryPattern(TokenKind.ADDRESS, Pattern.compile("(?<![가-힣])" + CITY_DISTRICT + "\\s(?:"
					+ CITY_DISTRICT + "(?:\\s" + TOWN + ")?|" + TOWN + ")")),
			new SecondaryPattern(TokenKind.ADDRESS, Pattern.compile(APARTMENT)),
			new SecondaryPattern(TokenKind.ADDRESS, Pattern.compile("(?<!\\d)\\d{1,4}동\\s?\\d{1,4}호")),
			new SecondaryPattern(TokenKind.ADDRESS,
					Pattern.compile("[가-힣A-Za-z0-9]*[가-힣](?:로|길)\\s?\\d{1,5}(?:-\\d{1,5})?(?:번길\\s?\\d{1,5})?(?!\\d)"
							+ NOT_A_QUANTITY)),
			new SecondaryPattern(TokenKind.ADDRESS,
					Pattern.compile("[가-힣]+(?:동|리)\\s\\d{1,5}(?:-\\d{1,5})?(?:번지)?(?!\\d)" + NOT_A_QUANTITY)));

	record SecondaryPattern(TokenKind kind, Pattern pattern) {
	}

	/** 가린 결과와 대응표. toString은 대응표처럼 건수만 보여 준다. */
	public record Masked(MaskedText text, PseudonymMap map) {

		@Override
		public String toString() {
			return "Masked[" + text + ", " + map + "]";
		}

	}

	/**
	 * 한 명단으로 만든 1차 사전. 표기 → 종류와, 경계를 건 표기 정규식을 한 번만 만들어 가명화와 자기 점검(MaskingVerifier)에 함께 쓴다.
	 * 이름을 담고 있으므로 toString은 표기 수만 보여 준다.
	 */
	public static final class Dictionary {

		private final Map<String, TokenKind> kindBySurface;

		/** 표기만 찾는 정규식. 사전이 비면 null. */
		private final Pattern surfaces;

		/** 토큰을 먼저 알아보고(건너뛰기) 표기를 찾는 정규식. 사전이 비면 null. */
		private final Pattern tokensOrSurfaces;

		private Dictionary(Map<String, TokenKind> kindBySurface) {
			this.kindBySurface = kindBySurface;
			if (kindBySurface.isEmpty()) {
				this.surfaces = null;
				this.tokensOrSurfaces = null;
				return;
			}
			String alternation = kindBySurface.keySet().stream()
					.sorted(Comparator.comparingInt(String::length).reversed())
					.map(Dictionary::bounded)
					.collect(Collectors.joining("|"));
			this.surfaces = Pattern.compile(alternation);
			this.tokensOrSurfaces = Pattern.compile("(" + TokenKind.TOKEN.pattern() + ")|(?:" + alternation + ")");
		}

		public static Dictionary of(Roster roster) {
			Map<String, TokenKind> kinds = new LinkedHashMap<>();
			addPeople(kinds, roster.studentNames(), TokenKind.STUDENT);
			addPeople(kinds, roster.guardianNames(), TokenKind.GUARDIAN);
			addPeople(kinds, roster.teacherNames(), TokenKind.TEACHER);
			if (roster.grade() > 0 && roster.classNumber() > 0) {
				int grade = roster.grade();
				int number = roster.classNumber();
				for (String surface : List.of(grade + "학년 " + number + "반", grade + "학년" + number + "반",
						grade + "-" + number + "반", grade + "-" + number, grade + "학년", number + "반")) {
					kinds.putIfAbsent(surface, TokenKind.CLASS);
				}
			}
			for (String term : roster.schoolTerms()) {
				if (term != null && !term.isBlank()) {
					kinds.putIfAbsent(term.strip(), TokenKind.SCHOOL);
				}
			}
			return new Dictionary(kinds);
		}

		/** 같은 표기가 여러 사람에 속하면(동명이인) 먼저 나온 종류로 묶는다 — 어느 쪽이든 가려진다. */
		private static void addPeople(Map<String, TokenKind> kinds, List<String> names, TokenKind kind) {
			for (String raw : names) {
				if (raw == null) {
					continue;
				}
				String name = raw.strip();
				if (name.length() < 2) {
					continue;
				}
				kinds.putIfAbsent(name, kind);
				if (name.length() == 3) {
					kinds.putIfAbsent(name.substring(1), kind);
				}
			}
		}

		/** 숫자로 시작·끝나면 숫자 경계, 영문으로 시작·끝나면 영문·숫자 경계를 건다. */
		private static String bounded(String surface) {
			char first = surface.charAt(0);
			char last = surface.charAt(surface.length() - 1);
			String before = Character.isDigit(first) ? "(?<!\\d)" : isAsciiLetter(first) ? "(?<![A-Za-z0-9])" : "";
			String after = Character.isDigit(last) ? "(?!\\d)" : isAsciiLetter(last) ? "(?![A-Za-z0-9])" : "";
			return before + Pattern.quote(surface) + after;
		}

		private static boolean isAsciiLetter(char c) {
			return (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z');
		}

		/** 표기만 찾는 정규식. 사전이 비면 null. */
		Pattern surfacePattern() {
			return surfaces;
		}

		@Override
		public String toString() {
			return "Dictionary[surfaces=" + kindBySurface.size() + "]";
		}

	}

	private Pseudonymizer() {
	}

	public static Masked mask(String text, Roster roster) {
		return mask(text, Dictionary.of(roster));
	}

	public static Masked mask(String text, Dictionary dictionary) {
		String masked = neutralizeTokens(text);
		Map<String, String> originalByToken = new LinkedHashMap<>();
		Map<TokenKind, Integer> counters = new EnumMap<>(TokenKind.class);
		for (SecondaryPattern secondary : SECONDARY) {
			masked = replaceSecondary(masked, secondary, originalByToken, counters);
		}
		masked = replacePrimary(masked, dictionary, originalByToken, counters);
		return new Masked(new MaskedText(masked), new PseudonymMap(originalByToken));
	}

	static String neutralizeTokens(String text) {
		Matcher matcher = TokenKind.TOKEN.matcher(text);
		StringBuilder out = new StringBuilder();
		while (matcher.find()) {
			String token = matcher.group();
			matcher.appendReplacement(out,
					Matcher.quoteReplacement("［" + token.substring(1, token.length() - 1) + "］"));
		}
		matcher.appendTail(out);
		return out.toString();
	}

	private static String replacePrimary(String text, Dictionary dictionary, Map<String, String> originalByToken,
			Map<TokenKind, Integer> counters) {
		if (dictionary.tokensOrSurfaces == null) {
			return text;
		}
		Matcher matcher = dictionary.tokensOrSurfaces.matcher(text);
		Map<String, String> tokenBySurface = new LinkedHashMap<>();
		StringBuilder out = new StringBuilder();
		while (matcher.find()) {
			if (matcher.group(1) != null) {
				// 2차 패턴이 넣은 토큰은 그대로 둔다
				matcher.appendReplacement(out, Matcher.quoteReplacement(matcher.group()));
				continue;
			}
			String surface = matcher.group();
			String token = tokenBySurface.computeIfAbsent(surface, found -> {
				String created = next(dictionary.kindBySurface.get(found), counters);
				originalByToken.put(created, found);
				return created;
			});
			matcher.appendReplacement(out, Matcher.quoteReplacement(token));
		}
		matcher.appendTail(out);
		return out.toString();
	}

	private static String replaceSecondary(String text, SecondaryPattern secondary, Map<String, String> originalByToken,
			Map<TokenKind, Integer> counters) {
		Matcher matcher = secondary.pattern().matcher(text);
		Map<String, String> tokenByValue = new LinkedHashMap<>();
		StringBuilder out = new StringBuilder();
		while (matcher.find()) {
			String value = matcher.group();
			String token = tokenByValue.computeIfAbsent(value, found -> {
				String created = next(secondary.kind(), counters);
				originalByToken.put(created, found);
				return created;
			});
			matcher.appendReplacement(out, Matcher.quoteReplacement(token));
		}
		matcher.appendTail(out);
		return out.toString();
	}

	private static String next(TokenKind kind, Map<TokenKind, Integer> counters) {
		return kind.token(counters.merge(kind, 1, Integer::sum));
	}

}
