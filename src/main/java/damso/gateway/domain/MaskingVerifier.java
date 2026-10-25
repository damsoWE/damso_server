package damso.gateway.domain;

import java.util.Optional;

/**
 * 가린 뒤 자기 점검 (research R4, FR-005). 가린 결과에 사전의 원래 표기가 하나라도 남았거나 2차 패턴이 다시 매치되면 실패다 —
 * 그러면 외부 AI를 호출하지 않는다. 가명화 코드의 실수가 곧 유출이 되지 않게 한 번 더 막는다(fail-closed).
 */
public final class MaskingVerifier {

	private MaskingVerifier() {
	}

	public static Optional<GatewayFailure> verify(MaskedText masked, Roster roster) {
		String text = masked.value();
		for (String surface : Pseudonymizer.surfaces(roster)) {
			if (text.contains(surface)) {
				return Optional.of(GatewayFailure.MASKING_FAILED);
			}
		}
		for (Pseudonymizer.SecondaryPattern secondary : Pseudonymizer.SECONDARY) {
			if (secondary.pattern().matcher(text).find()) {
				return Optional.of(GatewayFailure.MASKING_FAILED);
			}
		}
		return Optional.empty();
	}

}
