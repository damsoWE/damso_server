package damso.gateway.application.port.in;

import damso.gateway.domain.GatewayFailure;
import damso.gateway.domain.Roster;

/**
 * 긴급 문의 위기 종류 분류의 입구. 일반 정리와 같이 가명화 → 가린 뒤 자기 점검 → 외부 AI → 허용값 검증을 거친다.
 * 결과는 코드 하나(SELF_HARM·ABUSE·OTHER_SAFETY)뿐이라 복원할 내용이 없다.
 */
public interface ClassifyCrisisGateway {

	/** 성공이면 crisisType(허용값 코드), 실패면 failure 하나만 있다. */
	record CrisisOutcome(String crisisType, GatewayFailure failure) {

		public static CrisisOutcome success(String crisisType) {
			return new CrisisOutcome(crisisType, null);
		}

		public static CrisisOutcome failed(GatewayFailure failure) {
			return new CrisisOutcome(null, failure);
		}

		public boolean succeeded() {
			return crisisType != null;
		}

	}

	CrisisOutcome classify(String content, Roster roster);

}
