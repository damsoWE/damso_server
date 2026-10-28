package damso.gateway.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import damso.gateway.application.port.in.RefineInquiryGateway.RefineOutcome;
import damso.gateway.domain.GatewayFailure;
import damso.gateway.domain.Roster;
import damso.support.FakeExternalAi;

/**
 * 게이트웨이는 가명화를 먼저 하고, 못 가렸으면 외부 AI를 부르지 않는다.
 * 학교 용어는 호출하는 쪽이 넘기지 않아도 게이트웨이가 채운다. 외부 오류는 코드로만 돌려준다.
 */
class RefineInquiryGatewayServiceTest {

	private static final Roster ROSTER = Roster.of(List.of("김민준"), List.of("박지영"), List.of("최유진"), 3, 2);

	private FakeExternalAi ai;

	private RefineInquiryGatewayService gateway;

	@BeforeEach
	void setUp() {
		ai = new FakeExternalAi();
		gateway = new RefineInquiryGatewayService(
				new MaskedAiCall(ai, task -> "고정 지시", () -> List.of("한양초등학교", "한양초")));
	}

	@Test
	void masksBeforeCallingAndAddsSchoolTermsItself() {
		RefineOutcome outcome = gateway.refine("한양초 3학년 2반 민준이 엄마 박지영입니다.", ROSTER);

		assertThat(ai.calls()).hasSize(1);
		assertThat(ai.calls().get(0).userText()).isEqualTo("[SCHOOL_1] [CLASS_1] [STUDENT_1]이 엄마 [GUARDIAN_1]입니다.");
		assertThat(outcome.succeeded()).isTrue();
		// 기본 가짜 응답은 가린 본문을 facts로 돌려준다 → 본 표기 그대로 복원된다(정식 이름으로 바꾸지 않는다)
		assertThat(outcome.result().facts()).isEqualTo("한양초 3학년 2반 민준이 엄마 박지영입니다.");
	}

	@Test
	void maskingErrorNeverCallsTheExternalAi() {
		RefineOutcome outcome = gateway.refine(null, ROSTER);

		assertThat(outcome.failure()).isEqualTo(GatewayFailure.MASKING_FAILED);
		assertThat(ai.calls()).isEmpty();
	}

	@Test
	void externalFailuresBecomeCodes() {
		ai.noResponseNext();
		ai.throwNext();
		ai.replyNext("{\"title\":\"제목\"}");

		assertThat(gateway.refine("민준이 숙제", ROSTER).failure()).isEqualTo(GatewayFailure.NO_RESPONSE);
		assertThat(gateway.refine("민준이 숙제", ROSTER).failure()).isEqualTo(GatewayFailure.NO_RESPONSE);
		assertThat(gateway.refine("민준이 숙제", ROSTER).failure()).isEqualTo(GatewayFailure.INVALID_OUTPUT);
	}

	@Test
	void outcomeToStringHasNoContent() {
		RefineOutcome outcome = gateway.refine("김민준 숙제 문의", ROSTER);

		assertThat(outcome.toString()).doesNotContain("김민준").doesNotContain("숙제");
		assertThat(outcome.result().toString()).doesNotContain("김민준").doesNotContain("숙제");
	}

}
