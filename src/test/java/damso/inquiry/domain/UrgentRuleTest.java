package damso.inquiry.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import damso.inquiry.adapter.out.resource.ClasspathUrgentExpressionSource;

/** 긴급 판정은 정규화한 본문에 표현이 포함되는지만 본다. */
class UrgentRuleTest {

	private static final ClasspathUrgentExpressionSource SOURCE = new ClasspathUrgentExpressionSource(
			new ClassPathResource("urgent/expressions.txt"));

	private final UrgentRule rule = new UrgentRule(SOURCE.expressions());

	@Test
	void everyExpressionInTheListIsUrgent() {
		assertThat(SOURCE.expressions()).isNotEmpty();
		for (String expression : SOURCE.expressions()) {
			assertThat(rule.isUrgent("오늘 " + expression + " 일이 있었어요")).as(expression).isTrue();
		}
	}

	/** 목록 회귀 테스트: 대표 문장 하나라도 놓치면 실패한다. 파일 줄 단위 문장 + 줄바꿈으로 쪼갠 문장은 한 블록으로 본다. */
	@Test
	void everyRepresentativeSentenceIsUrgent() throws IOException {
		String raw = new String(new ClassPathResource("urgent/must-be-urgent.txt").getInputStream().readAllBytes(),
				StandardCharsets.UTF_8);
		List<String> sentences = raw.lines().map(String::strip).filter(line -> !line.isEmpty() && !line.startsWith("#"))
				.toList();
		assertThat(sentences).hasSizeGreaterThanOrEqualTo(20);
		for (String sentence : sentences) {
			assertThat(rule.isUrgent(sentence)).as(sentence).isTrue();
		}
		// 줄바꿈으로 쪼개진 표현도 합쳐서 본다
		assertThat(rule.isUrgent("죽고\n싶\n다고 합니다")).isTrue();
	}

	@Test
	void spacingAndWidthVariantsGiveTheSameResult() {
		assertThat(rule.isUrgent("아이가 자 해 를 했어요")).isTrue();
		assertThat(rule.isUrgent("아이가 자　해를 했어요")).isTrue();
		assertThat(rule.isUrgent("아이가 죽고\t싶\n다고 해요")).isTrue();

		UrgentRule latin = new UrgentRule(List.of("Self Harm"));
		assertThat(latin.isUrgent("ＳＥＬＦ　ｈａｒｍ")).isTrue();
		assertThat(latin.isUrgent("self-harm")).isFalse();
		assertThat(latin.isUrgent("SELFHARM")).isTrue();
	}

	@Test
	void ordinaryQuestionsAreNotUrgent() {
		for (String sentence : List.of(
				"내일 현장체험학습 준비물이 무엇인지 알려 주세요.",
				"우리 아이가 수학 숙제를 어려워해서 상담을 받고 싶습니다.",
				"급식에 알레르기 유발 식품이 있는지 궁금합니다.",
				"다음 주 상담 시간을 조정할 수 있을까요?",
				"아이가 감기에 걸려 내일 하루 결석합니다.")) {
			assertThat(rule.isUrgent(sentence)).as(sentence).isFalse();
		}
	}

	@Test
	void blankOrMissingContentIsNotUrgent() {
		assertThat(rule.isUrgent(null)).isFalse();
		assertThat(rule.isUrgent("")).isFalse();
		assertThat(rule.isUrgent(" \n\t　")).isFalse();
	}

	@Test
	void emptyExpressionListIsRejected() {
		assertThatThrownBy(() -> new UrgentRule(List.of())).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new UrgentRule(List.of("  ", "\n"))).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void normalizeIsIdempotent() {
		String once = UrgentRule.normalize("ＡＢＣ 가 나\n다");
		assertThat(once).isEqualTo("abc가나다");
		assertThat(UrgentRule.normalize(once)).isEqualTo(once);
	}

}
