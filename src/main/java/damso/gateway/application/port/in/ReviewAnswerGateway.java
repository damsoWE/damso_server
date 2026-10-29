package damso.gateway.application.port.in;

import java.util.List;

import damso.gateway.domain.GatewayFailure;
import damso.gateway.domain.Roster;

/**
 * 교사 답변 점검의 입구. 답변 초안과 맥락을 한 사전으로 한 번에 가명화 → 가린 뒤
 * 자기 점검 → 외부 AI → 서버 재검증 → 복원 순서를 이 입구가 강제한다. 호출하는 쪽은 원래 글과 명단만 넘기고, 외부로 나가는 글은 볼 수 없다.
 */
public interface ReviewAnswerGateway {

	/**
	 * 점검 맥락: 교사가 답하려는 문의 하나의 정리 결과(복원된 값). 원문이 아니다. 문의 내용이므로 toString은 가린다.
	 * 정서강도·라벨은 허용값 코드다.
	 */
	record Context(String facts, String requests, String emotionLevel, List<String> emotionLabels) {

		public Context {
			emotionLabels = List.copyOf(emotionLabels);
		}

		@Override
		public String toString() {
			return "Context[]";
		}

	}

	/** 검증과 복원이 끝난 지적 하나. excerpt는 답변 초안에 그대로 있는 문자열이다. toString은 가린다. */
	record Finding(String excerpt, String reason, String suggestion) {

		@Override
		public String toString() {
			return "Finding[]";
		}

	}

	/** 성공이면 findings(빈 목록 가능), 실패면 failure(내용 없는 코드) 하나만 있다. */
	record ReviewOutcome(List<Finding> findings, GatewayFailure failure) {

		public ReviewOutcome {
			findings = List.copyOf(findings);
		}

		public static ReviewOutcome success(List<Finding> findings) {
			return new ReviewOutcome(findings, null);
		}

		public static ReviewOutcome failed(GatewayFailure failure) {
			return new ReviewOutcome(List.of(), failure);
		}

		public boolean succeeded() {
			return failure == null;
		}

		@Override
		public String toString() {
			return "ReviewOutcome[" + (succeeded() ? "SUCCESS, findings=" + findings.size() : failure) + "]";
		}

	}

	/**
	 * @param context 맥락. 없으면 null — 답변만 점검하고 근거 누락은 판단하지 않는다
	 */
	ReviewOutcome review(String draft, Context context, Roster roster);

}
