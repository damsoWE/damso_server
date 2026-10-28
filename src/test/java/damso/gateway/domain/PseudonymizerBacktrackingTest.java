package damso.gateway.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import damso.inquiry.domain.InquiryContentPolicy;

/**
 * 이슈 #26(SpotBugs REDOS): 가명화 정규식은 수량자에 모두 상한이 있어 역추적이 다항 시간에 머문다. 본문 상한(2,000자)을 꽉 채운
 * 역추적 유도 입력에서도 가명화와 토큰 검사가 짧게 끝나는지 확인한다. config/spotbugs/exclude.xml의 REDOS 예외 근거다.
 */
class PseudonymizerBacktrackingTest {

	private static final Roster ROSTER = Roster.of(List.of("김민준"), List.of("박지영"), List.of("최유진"), 3, 2);

	@ParameterizedTest
	@ValueSource(strings = { "서울", "서울 강남", "강남구 ", "가", "가나다라마바", "a@", "a.", "1동 ", "[A_", "로1-", "동 1" })
	void adversarialInputOfMaximumLengthIsMaskedQuickly(String unit) {
		String input = repeatToLimit(unit);

		long started = System.nanoTime();
		String masked = Pseudonymizer.mask(input, ROSTER).text().value();
		TokenKind.TOKEN.matcher(input + masked).find();
		Duration took = Duration.ofNanos(System.nanoTime() - started);

		assertThat(took).isLessThan(Duration.ofSeconds(2));
	}

	private static String repeatToLimit(String unit) {
		StringBuilder builder = new StringBuilder();
		while (builder.codePointCount(0, builder.length()) + unit.codePointCount(0, unit.length())
				<= InquiryContentPolicy.MAX_CODE_POINTS) {
			builder.append(unit);
		}
		return builder.toString();
	}

}
