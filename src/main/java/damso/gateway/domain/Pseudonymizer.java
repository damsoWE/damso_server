package damso.gateway.domain;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 문의 본문의 식별정보를 토큰으로 바꾼다 (원칙 II, research R2·R3). 외부 의존 없는 순수 함수다.
 * <ol>
 * <li>입력에 이미 있는 토큰 모양 문자열([STUDENT_1] 등)은 대괄호를 전각(［ ］)으로 바꿔 무력화한다 — 학부모가 쓴 글이 복원 단계에서
 * 다른 사람 이름으로 바뀌지 않게.</li>
 * <li>1차 사전: 학생·보호자·교사 이름(성+이름, 3자 이름은 이름 2자도), 학급(학년·반 표기 변형), 학교 용어. 한 사람(한 대상)은 표기가
 * 달라도 같은 토큰이고, 긴 표기부터 한 번에 바꾼다(겹침 없음).</li>
 * <li>2차 패턴: 주민등록번호 형태, 전화번호, 이메일, 주소(아파트 동·호, 도로명, 지번). 같은 값은 같은 토큰.</li>
 * </ol>
 * 사전에 없는 이름(다른 반 친구 등)과 패턴에 맞지 않는 식별정보는 가려지지 않는다 — 알려진 한계(research R2).
 * 과잉 가림은 안전한 쪽이라 받아들인다(예: 이름 "하나"가 일반 낱말 "하나"도 가린다).
 */
public final class Pseudonymizer {

	/** 숫자 뒤에 오면 시간·수량·학년 같은 말이라 주소로 보지 않는다. */
	private static final String NOT_A_QUANTITY = "(?!\\s?(?:시|분|초|일|월|년|명|개|번(?!지)|번째|학년|반|살|등|교시|층|원|권|장|회|차))";

	/** 2차 패턴. 순서가 중요하다(주민번호를 전화번호보다 먼저). */
	static final List<SecondaryPattern> SECONDARY = List.of(
			new SecondaryPattern(TokenKind.ID_NUMBER, Pattern.compile("(?<!\\d)\\d{6}[- ]?[1-8]\\d{6}(?!\\d)")),
			new SecondaryPattern(TokenKind.PHONE,
					Pattern.compile("(?<!\\d)(?:01[016789]|0[2-6]\\d?)[-. ]?\\d{3,4}[-. ]?\\d{4}(?!\\d)")),
			new SecondaryPattern(TokenKind.EMAIL,
					Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}")),
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

	/** 사전의 한 항목: 문의에 나타날 수 있는 표기(surface)와 그것이 가리키는 대상(entity). */
	record Entry(String surface, String entity, TokenKind kind, String canonical) {
	}

	private Pseudonymizer() {
	}

	public static Masked mask(String text, Roster roster) {
		String neutralized = neutralizeTokens(text);
		Map<String, String> originalByToken = new LinkedHashMap<>();
		Map<TokenKind, Integer> counters = new EnumMap<>(TokenKind.class);

		String primary = replacePrimary(neutralized, dictionary(roster), originalByToken, counters);
		String masked = primary;
		for (SecondaryPattern secondary : SECONDARY) {
			masked = replaceSecondary(masked, secondary, originalByToken, counters);
		}
		return new Masked(new MaskedText(masked), new PseudonymMap(originalByToken));
	}

	/** 사전의 모든 표기. 가린 뒤 자기 점검(MaskingVerifier)이 같은 목록으로 남은 값을 찾는다. */
	static List<String> surfaces(Roster roster) {
		return dictionary(roster).stream().map(Entry::surface).distinct().toList();
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

	static List<Entry> dictionary(Roster roster) {
		List<Entry> entries = new ArrayList<>();
		addPeople(entries, roster.studentNames(), TokenKind.STUDENT);
		addPeople(entries, roster.guardianNames(), TokenKind.GUARDIAN);
		addPeople(entries, roster.teacherNames(), TokenKind.TEACHER);
		if (roster.grade() > 0 && roster.classNumber() > 0) {
			int grade = roster.grade();
			int number = roster.classNumber();
			String canonical = grade + "학년 " + number + "반";
			for (String surface : List.of(canonical, grade + "학년" + number + "반", grade + "-" + number + "반",
					grade + "-" + number, grade + "학년", number + "반")) {
				entries.add(new Entry(surface, "CLASS", TokenKind.CLASS, canonical));
			}
		}
		if (!roster.schoolTerms().isEmpty()) {
			String canonical = roster.schoolTerms().get(0);
			for (String term : roster.schoolTerms()) {
				entries.add(new Entry(term, "SCHOOL", TokenKind.SCHOOL, canonical));
			}
		}
		return entries;
	}

	private static void addPeople(List<Entry> entries, List<String> names, TokenKind kind) {
		for (String raw : names) {
			if (raw == null) {
				continue;
			}
			String name = raw.strip();
			if (name.length() < 2) {
				continue;
			}
			String entity = kind + ":" + name;
			entries.add(new Entry(name, entity, kind, name));
			if (name.length() == 3) {
				entries.add(new Entry(name.substring(1), entity, kind, name));
			}
		}
	}

	private static String replacePrimary(String text, List<Entry> entries, Map<String, String> originalByToken,
			Map<TokenKind, Integer> counters) {
		if (entries.isEmpty()) {
			return text;
		}
		// 같은 표기가 여러 대상에 속하면(동명이인) 먼저 나온 대상 하나로 묶는다 — 어느 쪽이든 가려진다
		Map<String, Entry> bySurface = new LinkedHashMap<>();
		for (Entry entry : entries) {
			bySurface.putIfAbsent(entry.surface(), entry);
		}
		String alternation = bySurface.keySet().stream()
				.sorted(Comparator.comparingInt(String::length).reversed())
				.map(Pattern::quote)
				.collect(Collectors.joining("|"));
		Matcher matcher = Pattern.compile(alternation).matcher(text);
		Map<String, String> tokenByEntity = new LinkedHashMap<>();
		StringBuilder out = new StringBuilder();
		while (matcher.find()) {
			Entry entry = bySurface.get(matcher.group());
			String token = tokenByEntity.computeIfAbsent(entry.entity(), entity -> {
				String created = next(entry.kind(), counters);
				originalByToken.put(created, entry.canonical());
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
