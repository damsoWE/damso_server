package damso.gateway.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** 가린 결과에 원래 값이나 2차 패턴이 남으면 실패(외부 AI를 호출하지 않는다). */
class MaskingVerifierTest {

	private static final Roster ROSTER = PseudonymizerTest.ROSTER;

	@Test
	void properlyMaskedTextPasses() {
		MaskedText masked = Pseudonymizer.mask("민준이 엄마 박지영입니다. 010-1234-5678", ROSTER).text();

		assertThat(MaskingVerifier.verify(masked, ROSTER)).isEmpty();
	}

	@Test
	void remainingRosterValueFails() {
		assertThat(MaskingVerifier.verify(new MaskedText("[STUDENT_1] 엄마 박지영입니다."), ROSTER))
				.contains(GatewayFailure.MASKING_FAILED);
		assertThat(MaskingVerifier.verify(new MaskedText("한양초 앞에서"), ROSTER))
				.contains(GatewayFailure.MASKING_FAILED);
	}

	@Test
	void remainingSecondaryPatternFails() {
		assertThat(MaskingVerifier.verify(new MaskedText("연락처 010-9999-8888"), ROSTER))
				.contains(GatewayFailure.MASKING_FAILED);
		assertThat(MaskingVerifier.verify(new MaskedText("a@b.co"), ROSTER)).contains(GatewayFailure.MASKING_FAILED);
	}

}
