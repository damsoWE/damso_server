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
	void samePersonInDifferentFormsGetsTheSameTokenAndRestoresToTheFullName() {
		Masked masked = Pseudonymizer.mask("김민준 엄마예요. 민준이가 아파요.", ROSTER);

		assertThat(masked.text().value()).isEqualTo("[STUDENT_1] 엄마예요. [STUDENT_1]이가 아파요.");
		assertThat(masked.map().restore("[STUDENT_1]이가 아파요")).isEqualTo("김민준이가 아파요");
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
	void classAndSchoolVariantsShareOneToken() {
		Masked masked = Pseudonymizer.mask("한양초등학교 3학년 2반, 한양초 3-2반", ROSTER);

		assertThat(masked.text().value()).isEqualTo("[SCHOOL_1] [CLASS_1], [SCHOOL_1] [CLASS_1]");
		assertThat(masked.map().restore("[SCHOOL_1] [CLASS_1]")).isEqualTo("한양초등학교 3학년 2반");
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
		assertThat(masked.map().restore(masked.text().value())).isEqualTo("［STUDENT_1］이라고 써 봤어요. 김민준이");
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
