package damso.gateway.application.port.in;

import java.util.List;

import damso.gateway.domain.GatewayFailure;
import damso.gateway.domain.Roster;

/**
 * 일반 문의 3요소 정리의 입구. 가명화 → 가린 뒤 자기 점검 → 외부 AI → 서버 재검증 → 복원 순서를 이 입구가
 * 강제한다. 호출하는 쪽은 원문과 명단만 넘기고, 외부로 나가는 글은 볼 수 없다. 학교 용어는 게이트웨이가 직접 채운다.
 */
public interface RefineInquiryGateway {

	/**
	 * 검증과 복원이 끝난 정리 결과. 이름·학급은 원래 값으로 되돌려져 있다. 정서강도·라벨은 허용값 코드(EmotionLevel·EmotionLabel 이름)다.
	 * 문의 내용이므로 toString은 가린다.
	 */
	record Refined(String title, String facts, String requests, String emotionLevel, List<String> emotionLabels) {

		public Refined {
			emotionLabels = List.copyOf(emotionLabels);
		}

		@Override
		public String toString() {
			return "Refined[emotionLevel=" + emotionLevel + ", labels=" + emotionLabels.size() + "]";
		}

	}

	/** 성공이면 result, 실패면 failure(내용 없는 코드) 하나만 있다. */
	record RefineOutcome(Refined result, GatewayFailure failure) {

		public static RefineOutcome success(Refined result) {
			return new RefineOutcome(result, null);
		}

		public static RefineOutcome failed(GatewayFailure failure) {
			return new RefineOutcome(null, failure);
		}

		public boolean succeeded() {
			return result != null;
		}

		@Override
		public String toString() {
			return "RefineOutcome[" + (succeeded() ? "SUCCESS" : failure) + "]";
		}

	}

	RefineOutcome refine(String content, Roster roster);

}
