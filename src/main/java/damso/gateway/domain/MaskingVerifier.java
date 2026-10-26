package damso.gateway.domain;

import java.util.Optional;
import java.util.regex.Pattern;

import damso.gateway.domain.Pseudonymizer.Dictionary;

/**
 * 가린 뒤 자기 점검 (research R4, FR-005). 가린 결과에 사전 표기가 하나라도 남았거나 2차 패턴이 다시 매치되면 실패다 —
 * 그러면 외부 AI를 호출하지 않는다. 가명화 코드의 실수가 곧 유출이 되지 않게 한 번 더 막는다(fail-closed).
 * 점검 전에 토큰([STUDENT_1] 등)을 지운다 — 영문 이름("EN" 등)이 토큰 글자 안에서 매치돼 멀쩡한 결과를 실패로 보지 않게.
 * 표기는 가명화와 같은 경계를 건 정규식으로 찾는다.
 */
public final class MaskingVerifier {

	private MaskingVerifier() {
	}

	public static Optional<GatewayFailure> verify(MaskedText masked, Roster roster) {
		return verify(masked, Dictionary.of(roster));
	}

	public static Optional<GatewayFailure> verify(MaskedText masked, Dictionary dictionary) {
		String text = TokenKind.TOKEN.matcher(masked.value()).replaceAll(" ");
		Pattern surfaces = dictionary.surfacePattern();
		if (surfaces != null && surfaces.matcher(text).find()) {
			return Optional.of(GatewayFailure.MASKING_FAILED);
		}
		for (Pseudonymizer.SecondaryPattern secondary : Pseudonymizer.SECONDARY) {
			if (secondary.pattern().matcher(text).find()) {
				return Optional.of(GatewayFailure.MASKING_FAILED);
			}
		}
		return Optional.empty();
	}

}
