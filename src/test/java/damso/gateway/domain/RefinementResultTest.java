package damso.gateway.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import damso.gateway.application.service.RefineInquiryGatewayServiceParseProbe;

/**
 * F3 T028 / FR-007·008·011, SC-003·011, research R6: 외부 AI 결과의 서버 재검증과 복원. 하나라도 어기면 결과 전체를 버린다.
 */
class RefinementResultTest {

	private static final PseudonymMap MAP = Pseudonymizer.mask("김민준 3학년 2반",
			Roster.of(List.of("김민준"), List.of(), List.of(), 3, 2)).map();

	private static String json(String title, String facts, String requests, String level, String labels) {
		return "{\"title\":" + title + ",\"facts\":" + facts + ",\"requests\":" + requests + ",\"emotionLevel\":" + level
				+ ",\"emotionLabels\":" + labels + "}";
	}

	private static boolean valid(String json) {
		return RefineInquiryGatewayServiceParseProbe.parse(json, MAP).isPresent();
	}

	@Test
	void validResultIsRestoredAndTokensDisappear() {
		var result = RefineInquiryGatewayServiceParseProbe.parse(json("\"[STUDENT_1] 숙제 문의\"",
				"\"[CLASS_1] [STUDENT_1]이 숙제를 잊음\"", "\"\"", "\"MEDIUM\"", "[\"WORRY\",\"ANXIETY\"]"), MAP);

		assertThat(result).isPresent();
		assertThat(result.get().title()).isEqualTo("김민준 숙제 문의");
		assertThat(result.get().facts()).isEqualTo("3학년 2반 김민준이 숙제를 잊음").doesNotContain("[");
		assertThat(result.get().requests()).isEmpty();
		assertThat(result.get().level()).isEqualTo(EmotionLevel.MEDIUM);
		assertThat(result.get().labels()).containsExactly(EmotionLabel.WORRY, EmotionLabel.ANXIETY);
	}

	@Test
	void titleAndTextLengthsAreEnforced() {
		String title31 = "\"" + "가".repeat(31) + "\"";
		String facts1001 = "\"" + "가".repeat(1001) + "\"";

		assertThat(valid(json("\"\"", "\"사실\"", "\"\"", "\"LOW\"", "[]"))).isFalse();
		assertThat(valid(json(title31, "\"사실\"", "\"\"", "\"LOW\"", "[]"))).isFalse();
		assertThat(valid(json("\"제목\"", "\"\"", "\"\"", "\"LOW\"", "[]"))).isFalse();
		assertThat(valid(json("\"제목\"", facts1001, "\"\"", "\"LOW\"", "[]"))).isFalse();
		assertThat(valid(json("\"제목\"", "\"사실\"", facts1001, "\"LOW\"", "[]"))).isFalse();
		assertThat(valid(json("\"" + "가".repeat(30) + "\"", "\"" + "가".repeat(1000) + "\"",
				"\"" + "가".repeat(1000) + "\"", "\"LOW\"", "[]"))).isTrue();
	}

	@Test
	void emotionValuesMustBeFromTheFixedLists() {
		assertThat(valid(json("\"제목\"", "\"사실\"", "\"\"", "\"VERY_HIGH\"", "[]"))).isFalse();
		assertThat(valid(json("\"제목\"", "\"사실\"", "\"\"", "\"low\"", "[]"))).isFalse();
		assertThat(valid(json("\"제목\"", "\"사실\"", "\"\"", "\"LOW\"", "[\"WORRY\",\"ANGER\",\"SADNESS\"]")))
				.isFalse();
		assertThat(valid(json("\"제목\"", "\"사실\"", "\"\"", "\"LOW\"", "[\"WORRY\",\"WORRY\"]"))).isFalse();
		assertThat(valid(json("\"제목\"", "\"사실\"", "\"\"", "\"LOW\"", "[\"HAPPY\"]"))).isFalse();
		assertThat(valid(json("\"제목\"", "\"사실\"", "\"\"", "\"HIGH\"", "[\"ANGER\",\"FRUSTRATION\"]"))).isTrue();
	}

	@Test
	void unknownTokensExtraFieldsAndWrongTypesAreRejected() {
		assertThat(valid(json("\"[STUDENT_9] 문의\"", "\"사실\"", "\"\"", "\"LOW\"", "[]"))).isFalse();
		assertThat(valid(json("\"제목\"", "\"[PHONE_1]로 연락\"", "\"\"", "\"LOW\"", "[]"))).isFalse();
		assertThat(valid("{\"title\":\"제목\",\"facts\":\"사실\",\"requests\":\"\",\"emotionLevel\":\"LOW\","
				+ "\"emotionLabels\":[],\"advice\":\"이렇게 답하세요\"}")).isFalse();
		assertThat(valid("{\"title\":\"제목\",\"facts\":\"사실\",\"requests\":\"\",\"emotionLevel\":\"LOW\"}")).isFalse();
		assertThat(valid(json("1", "\"사실\"", "\"\"", "\"LOW\"", "[]"))).isFalse();
		assertThat(valid(json("\"제목\"", "\"사실\"", "\"\"", "\"LOW\"", "[1]"))).isFalse();
		assertThat(valid("not json")).isFalse();
		assertThat(valid("[]")).isFalse();
	}

	@Test
	void contactAndIdentifierTokensAreNeverRestoredIntoTheResult() {
		// 원칙 I: 정리 결과가 연락처를 새로 저장하지 않는다 — 실호출 점검(2026-10-25)에서 전화번호가 사실관계로 복원되던 문제
		Pseudonymizer.Masked masked = Pseudonymizer.mask(
				"김민준 엄마예요. 010-1234-5678, a@b.co, 행복로 12, 900101-1234567",
				Roster.of(List.of("김민준"), List.of(), List.of(), 0, 0));

		var result = RefineInquiryGatewayServiceParseProbe.parse(json("\"[STUDENT_1] 연락 문의\"",
				"\"[STUDENT_1] 보호자가 [PHONE_1], [EMAIL_1], [ADDRESS_1], [ID_NUMBER_1]을 남겼다\"", "\"\"", "\"LOW\"",
				"[]"), masked.map());

		assertThat(result).isPresent();
		assertThat(result.get().title()).isEqualTo("김민준 연락 문의");
		assertThat(result.get().facts()).isEqualTo(
				"김민준 보호자가 (연락처 생략), (연락처 생략), (연락처 생략), (주민등록번호 생략)을 남겼다")
				.doesNotContain("010-1234-5678").doesNotContain("a@b.co").doesNotContain("행복로")
				.doesNotContain("900101");
	}

	@Test
	void neutralizedParentInputIsNeverRestored() {
		Pseudonymizer.Masked masked = Pseudonymizer.mask("[STUDENT_1] 라고 썼어요 민준이",
				Roster.of(List.of("김민준"), List.of(), List.of(), 0, 0));

		var result = RefineInquiryGatewayServiceParseProbe.parse(json("\"문의\"",
				"\"" + masked.text().value() + "\"", "\"\"", "\"LOW\"", "[]"), masked.map());

		assertThat(result).isPresent();
		assertThat(result.get().facts()).isEqualTo("［STUDENT_1］ 라고 썼어요 민준이");
	}

}
