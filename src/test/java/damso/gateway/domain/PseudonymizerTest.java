package damso.gateway.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.core.io.ClassPathResource;

import damso.gateway.domain.Pseudonymizer.Masked;

/**
 * F3 T016 / FR-002~004, SC-001, research R2·R3: 1차 사전·2차 패턴 가명화. 대표 사례 파일(cases.txt)은 가려져야 할 값과
 * 가려지지 않는 값(알려진 한계)을 함께 고정한다.
 */
class PseudonymizerTest {

	static final Roster ROSTER = Roster.of(List.of("김민준", "이서연"), List.of("박지영"), List.of("최유진"), 3, 2)
			.withSchoolTerms(List.of("한양초등학교", "한양 초등학교", "한양초교", "한양초"));

	static Stream<Arguments> cases() throws IOException {
		String file;
		try (var in = new ClassPathResource("pseudonymization/cases.txt").getInputStream()) {
			file = new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
		return file.lines()
				.filter(line -> !line.isBlank() && !line.startsWith("#"))
				.map(line -> line.split("\t"))
				.map(cols -> Arguments.of(cols[0], values(cols[1]), values(cols[2])));
	}

	private static List<String> values(String column) {
		return column.equals("-") ? List.of() : Arrays.asList(column.split("\\|"));
	}

	@ParameterizedTest
	@MethodSource("cases")
	void representativeCases(String input, List<String> masked, List<String> knownLimits) {
		String output = Pseudonymizer.mask(input, ROSTER).text().value();

		for (String value : masked) {
			assertThat(output).as("가려져야 함: %s", value).doesNotContain(value);
		}
		for (String value : knownLimits) {
			assertThat(output).as("알려진 한계로 남음: %s", value).contains(value);
		}
	}

	@Test
	void eachSurfaceGetsItsOwnTokenAndRestoresExactlyAsWritten() {
		// 방향 A(자체 코드 리뷰): 표기마다 토큰을 따로 두고 본 표기 그대로 되돌린다 — 정식 이름으로 바꿔 뜻이 달라지지 않게
		Masked masked = Pseudonymizer.mask("김민준 엄마예요. 민준이가 아파요. 김민준 약을 챙겨 주세요.", ROSTER);

		assertThat(masked.text().value())
				.isEqualTo("[STUDENT_1] 엄마예요. [STUDENT_2]이가 아파요. [STUDENT_1] 약을 챙겨 주세요.");
		assertThat(masked.map().restore("[STUDENT_2]이가 아파요")).isEqualTo("민준이가 아파요");
		assertThat(masked.map().restore("[STUDENT_1] 엄마")).isEqualTo("김민준 엄마");
	}

	@Test
	void anOrdinaryWordEqualToAGivenNameIsRestoredUnchanged() {
		Roster roster = Roster.of(List.of("이하나"), List.of(), List.of(), 0, 0);
		Masked masked = Pseudonymizer.mask("하나만 부탁드려요", roster);

		assertThat(masked.text().value()).isEqualTo("[STUDENT_1]만 부탁드려요");
		assertThat(masked.map().restore(masked.text().value())).isEqualTo("하나만 부탁드려요");
	}

	@Test
	void classSurfacesInsideNumbersNeverBreakPhoneOrIdPatterns() {
		// "1-1"·"4-5" 같은 학급 표기가 번호 안을 먼저 바꾸면 번호 패턴이 깨져 그대로 나간다(자체 코드 리뷰 1번)
		Roster firstOne = Roster.of(List.of(), List.of(), List.of(), 1, 1);
		Roster fourFive = Roster.of(List.of(), List.of(), List.of(), 4, 5);

		assertThat(Pseudonymizer.mask("주민번호 900101-1234567", firstOne).text().value())
				.isEqualTo("주민번호 [ID_NUMBER_1]");
		assertThat(Pseudonymizer.mask("전화 010-1234-5678", fourFive).text().value()).isEqualTo("전화 [PHONE_1]");
		assertThat(Pseudonymizer.mask("1-1 반이고 010-1111-1234", firstOne).text().value())
				.isEqualTo("[CLASS_1] 반이고 [PHONE_1]");
	}

	@Test
	void classSurfacesRespectDigitBoundariesAndRestoreAsWritten() {
		Roster twoThree = Roster.of(List.of(), List.of(), List.of(), 2, 3);
		Masked masked = Pseudonymizer.mask("13반 친구가 작년 2학년 때 3반이었어요", twoThree);

		assertThat(masked.text().value()).isEqualTo("13반 친구가 작년 [CLASS_1] 때 [CLASS_2]이었어요");
		assertThat(masked.map().restore(masked.text().value())).isEqualTo("13반 친구가 작년 2학년 때 3반이었어요");
	}

	@Test
	void asciiNamesMatchOnlyAsWholeWordsAndNeverInsideTokens() {
		Roster roster = Roster.of(List.of("김민준"), List.of(), List.of("EN"), 0, 0);
		Masked masked = Pseudonymizer.mask("EN 선생님, OPEN 시간, 김민준, en@x.co", roster);

		assertThat(masked.text().value()).isEqualTo("[TEACHER_1] 선생님, OPEN 시간, [STUDENT_1], [EMAIL_1]");
		assertThat(MaskingVerifier.verify(masked.text(), roster)).isEmpty();
	}

	@Test
	void differentPeopleGetDifferentTokensNumberedByFirstAppearance() {
		Masked masked = Pseudonymizer.mask("서연이와 민준이, 그리고 박지영 씨와 최유진 선생님", ROSTER);

		assertThat(masked.text().value())
				.isEqualTo("[STUDENT_1]이와 [STUDENT_2]이, 그리고 [GUARDIAN_1] 씨와 [TEACHER_1] 선생님");
		assertThat(masked.map().tokens())
				.containsExactly("[STUDENT_1]", "[STUDENT_2]", "[GUARDIAN_1]", "[TEACHER_1]");
	}

	@Test
	void classAndSchoolVariantsAreMaskedAndRestoredAsWritten() {
		Masked masked = Pseudonymizer.mask("한양초등학교 3학년 2반, 한양초 3-2반", ROSTER);

		assertThat(masked.text().value()).isEqualTo("[SCHOOL_1] [CLASS_1], [SCHOOL_2] [CLASS_2]");
		assertThat(masked.map().restore("[SCHOOL_2] [CLASS_2]")).isEqualTo("한양초 3-2반");
	}

	@Test
	void sameSecondaryValueGetsTheSameToken() {
		Masked masked = Pseudonymizer.mask("010-1111-2222, 다시 010-1111-2222, 그리고 010-3333-4444", ROSTER);

		assertThat(masked.text().value()).isEqualTo("[PHONE_1], 다시 [PHONE_1], 그리고 [PHONE_2]");
	}

	@Test
	void tokenShapedInputIsNeutralizedAndNeverRestoredToAName() {
		Masked masked = Pseudonymizer.mask("[STUDENT_1]이라고 써 봤어요. 민준이", ROSTER);

		assertThat(masked.text().value()).isEqualTo("［STUDENT_1］이라고 써 봤어요. [STUDENT_1]이");
		assertThat(masked.map().restore(masked.text().value())).isEqualTo("［STUDENT_1］이라고 써 봤어요. 민준이");
	}

	@Test
	void toStringNeverShowsOriginalValues() {
		Masked masked = Pseudonymizer.mask("김민준, 010-1234-5678, 한양초", ROSTER);

		assertThat(masked.toString()).doesNotContain("김민준").doesNotContain("010-1234-5678").doesNotContain("한양초");
		assertThat(masked.map().toString()).doesNotContain("김민준").contains("STUDENT");
		assertThat(masked.text().toString()).doesNotContain("[STUDENT_1]");
		assertThat(ROSTER.toString()).doesNotContain("김민준").doesNotContain("한양초");
	}

	@Test
	void shortOrBlankNamesAreSkippedAndEmptyRosterOnlyAppliesPatterns() {
		Roster empty = Roster.of(List.of("", " ", "김"), List.of(), List.of(), 0, 0);

		assertThat(Pseudonymizer.mask("김 선생님 010-1234-5678", empty).text().value()).isEqualTo("김 선생님 [PHONE_1]");
	}

}
