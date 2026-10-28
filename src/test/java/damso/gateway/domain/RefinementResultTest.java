package damso.gateway.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import damso.gateway.application.service.RefineInquiryGatewayServiceParseProbe;

/**
 * 외부 AI 결과의 서버 재검증과 복원. 하나라도 어기면 결과 전체를 버린다.
 */
class RefinementResultTest {

	private static final PseudonymMap MAP = Pseudonymizer.mask("김민준 3학년 2반",
			Roster.of(List.of("김민준"), List.of(), List.of(), 3, 2)).map();

	private static final String NO_CONDUCT = ",\"threatToTeacher\":false,\"threatToStudent\":false,"
			+ "\"abuseTowardTeacher\":false,\"abuseTowardStudent\":false";

	private static String json(String title, String facts, String requests, String level, String labels) {
		return json(title, facts, requests, level, labels, NO_CONDUCT);
	}

	private static String json(String title, String facts, String requests, String level, String labels,
			String conduct) {
		return "{\"title\":" + title + ",\"facts\":" + facts + ",\"requests\":" + requests + ",\"emotionLevel\":" + level
				+ ",\"emotionLabels\":" + labels + conduct + "}";
	}

	private static String conduct(boolean threatToTeacher, boolean threatToStudent, boolean abuseTowardTeacher,
			boolean abuseTowardStudent) {
		return ",\"threatToTeacher\":" + threatToTeacher + ",\"threatToStudent\":" + threatToStudent
				+ ",\"abuseTowardTeacher\":" + abuseTowardTeacher + ",\"abuseTowardStudent\":" + abuseTowardStudent;
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
				+ "\"emotionLabels\":[]" + NO_CONDUCT + ",\"advice\":\"이렇게 답하세요\"}")).isFalse();
		assertThat(valid("{\"title\":\"제목\",\"facts\":\"사실\",\"requests\":\"\",\"emotionLevel\":\"LOW\"}")).isFalse();
		assertThat(valid(json("\"제목\"", "\"사실\"", "\"\"", "\"LOW\"", "[]"))).isTrue();
		assertThat(valid(json("\"제목\"", "\"사실\"", "\"\"", "\"LOW\"", "[]", ""))).isFalse();
		assertThat(valid(json("\"제목\"", "\"사실\"", "\"\"", "\"LOW\"", "[]",
				NO_CONDUCT.replace("\"threatToTeacher\":false", "\"threatToTeacher\":\"false\"")))).isFalse();
		assertThat(valid(json("1", "\"사실\"", "\"\"", "\"LOW\"", "[]"))).isFalse();
		assertThat(valid(json("\"제목\"", "\"사실\"", "\"\"", "\"LOW\"", "[1]"))).isFalse();
		assertThat(valid("not json")).isFalse();
		assertThat(valid("[]")).isFalse();
	}

	@Test
	void conductFlagsAppendServerFixedSentencesInOrder() {
		// 위협·폭언·비하 문장은 모델이 쓰지 않고 서버가 고정 문장으로 붙인다 — 오귀속·문구 편차 방지(실호출 점검 2026-10-25)
		var all = RefineInquiryGatewayServiceParseProbe.parse(json("\"[STUDENT_1] 문의\"",
				"\"[STUDENT_1]이 다쳤다고 전했다.\"", "\"\"", "\"HIGH\"", "[]", conduct(true, true, true, true)), MAP);

		assertThat(all).isPresent();
		assertThat(all.get().facts()).isEqualTo("김민준이 다쳤다고 전했다. 교사에게 위협성 발언을 했다. 학생에게 위협성 발언을 했다. "
				+ "교사를 향한 폭언이 있었다. 다른 학생을 향한 비하 표현이 있었다.");

		var none = RefineInquiryGatewayServiceParseProbe.parse(json("\"문의\"", "\"사실.\"", "\"\"", "\"LOW\"", "[]"),
				MAP);
		assertThat(none.get().facts()).isEqualTo("사실.");
	}

	@Test
	void threatOnlyInquiryMayHaveEmptyModelFactsButNotWithoutAnyFlag() {
		var threatOnly = RefineInquiryGatewayServiceParseProbe.parse(
				json("\"문의\"", "\"\"", "\"\"", "\"HIGH\"", "[]", conduct(true, false, false, false)), MAP);

		assertThat(threatOnly).isPresent();
		assertThat(threatOnly.get().facts()).isEqualTo("교사에게 위협성 발언을 했다.");
		assertThat(valid(json("\"문의\"", "\"  \"", "\"\"", "\"HIGH\"", "[]"))).isFalse();
	}

	@Test
	void appendedSentencesCountTowardTheFactsLimit() {
		String facts990 = "\"" + "가".repeat(990) + "\"";

		assertThat(valid(json("\"제목\"", facts990, "\"\"", "\"LOW\"", "[]"))).isTrue();
		assertThat(valid(json("\"제목\"", facts990, "\"\"", "\"LOW\"", "[]", conduct(false, false, true, false))))
				.isFalse();
	}

	@Test
	void contactAndIdentifierTokensAreNeverRestoredIntoTheResult() {
		// 정리 결과가 연락처를 새로 저장하지 않는다 — 실호출 점검(2026-10-25)에서 전화번호가 사실관계로 복원되던 문제
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
