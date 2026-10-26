package damso.inquiry.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import damso.inquiry.adapter.out.resource.ClasspathThreatExpressionSource;
import damso.inquiry.adapter.out.resource.ClasspathUrgentExpressionSource;

/**
 * spec 005 T006 / FR-003·004a·005a·006, SC-001: 위협·폭언 판정은 정규화한 본문에 표현이 포함되는지만 본다.
 * 대표 사례(표시할 문장 / 표시하지 않을 문장)가 목록 변경의 회귀 기준이다.
 */
class ThreatRuleTest {

	private static final ClasspathThreatExpressionSource SOURCE = new ClasspathThreatExpressionSource(
			new ClassPathResource("threat/expressions.txt"));

	private static final UrgentRule URGENT = new UrgentRule(
			new ClasspathUrgentExpressionSource(new ClassPathResource("urgent/expressions.txt")).expressions());

	private final ThreatRule rule = new ThreatRule(SOURCE.expressions());

	/** 파일의 문장(빈 줄·'#' 줄 제외). 실패 메시지에 본문 대신 줄 번호를 쓰도록 번호와 함께 돌려준다. */
	private static List<Sentence> sentences(String path) throws IOException {
		List<String> lines = new String(new ClassPathResource(path).getInputStream().readAllBytes(),
				StandardCharsets.UTF_8).lines().toList();
		return IntStream.range(0, lines.size())
				.mapToObj(i -> new Sentence(path + ":" + (i + 1), lines.get(i).strip()))
				.filter(sentence -> !sentence.text().isEmpty() && !sentence.text().startsWith("#"))
				.toList();
	}

	private record Sentence(String where, String text) {
	}

	@Test
	void everyExpressionInTheListIsFlagged() {
		assertThat(SOURCE.expressions()).isNotEmpty();
		for (String expression : SOURCE.expressions()) {
			assertThat(rule.isFlagged("오늘 " + expression + " 일이 있었어요")).as(expression).isTrue();
		}
	}

	/** 목록 회귀 테스트: 대표 문장 하나라도 놓치면 실패한다. */
	@Test
	void everyMustFlagSentenceIsFlagged() throws IOException {
		List<Sentence> mustFlag = sentences("threat/must-flag.txt");
		assertThat(mustFlag).hasSizeGreaterThanOrEqualTo(15);
		for (Sentence sentence : mustFlag) {
			assertThat(rule.isFlagged(sentence.text())).as(sentence.where()).isTrue();
		}
	}

	/** 오탐 회귀 테스트: 정당한 의사 표현·글자만 겹치는 문장이 하나라도 걸리면 실패한다. */
	@Test
	void noMustNotFlagSentenceIsFlagged() throws IOException {
		List<Sentence> mustNotFlag = sentences("threat/must-not-flag.txt");
		assertThat(mustNotFlag).hasSizeGreaterThanOrEqualTo(15);
		for (Sentence sentence : mustNotFlag) {
			assertThat(rule.isFlagged(sentence.text())).as(sentence.where()).isFalse();
		}
	}

	/** research R4: 긴급 목록과 겹치는 표현은 쓰이지 않으므로 목록에 두지 않는다. 대표 문장도 긴급이 아니어야 판정 대상이다. */
	@Test
	void nothingInTheThreatListOrItsCasesIsUrgent() throws IOException {
		for (String expression : SOURCE.expressions()) {
			assertThat(URGENT.isUrgent(expression)).as(expression).isFalse();
		}
		for (String path : List.of("threat/must-flag.txt", "threat/must-not-flag.txt")) {
			for (Sentence sentence : sentences(path)) {
				assertThat(URGENT.isUrgent(sentence.text())).as(sentence.where()).isFalse();
			}
		}
	}

	@Test
	void spacingAndWidthVariantsGiveTheSameResult() {
		assertThat(rule.isFlagged("선생님 가만두지 않겠습니다")).isTrue();
		assertThat(rule.isFlagged("선생님 가만 두지 않겠습니다")).isTrue();
		assertThat(rule.isFlagged("선생님 가만\n두지\t않겠습니다")).isTrue();
		assertThat(rule.isFlagged("선생님 가만　두지　않겠습니다")).isTrue();

		ThreatRule latin = new ThreatRule(List.of("Shut Up"));
		assertThat(latin.isFlagged("ＳＨＵＴ　ｕｐ")).isTrue();
		assertThat(latin.isFlagged("SHUTUP")).isTrue();
		assertThat(latin.isFlagged("shut-up")).isFalse();
	}

	@Test
	void blankOrMissingContentIsNotFlagged() {
		assertThat(rule.isFlagged(null)).isFalse();
		assertThat(rule.isFlagged("")).isFalse();
		assertThat(rule.isFlagged(" \n\t　")).isFalse();
	}

	@Test
	void emptyExpressionListIsRejected() {
		assertThatThrownBy(() -> new ThreatRule(List.of())).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new ThreatRule(List.of("  ", "\n"))).isInstanceOf(IllegalArgumentException.class);
	}

}
