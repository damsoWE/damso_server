package damso.inquiry.application.port.out;

import java.util.List;
import java.util.Optional;

import damso.gateway.domain.Roster;
import damso.inquiry.domain.AnswerReviewResult;

/**
 * 답변 점검(가명화 → 점검 → 복원) 게이트웨이. 구현은 반드시 가명화 게이트웨이를 거쳐야 한다.
 * 호출하는 쪽은 가리기 전 답변 초안, 맥락(정리 결과, 없으면 null), 가명화 사전 재료(명단)만 넘긴다 — 가명화는 구현이 게이트웨이에서 한다.
 * 결과를 낼 수 없으면(장애·형식 오류·시간 초과 등) 예외 없이 {@link Optional#empty()}를 반환한다
 * (fail-open).
 */
public interface AnswerReviewPort {

	/**
	 * 점검 맥락: 교사가 답하려는 문의 하나의 정리 결과. 원문이 아니다. 문의 내용이므로 toString은 가린다.
	 */
	record ReviewContext(String facts, String requests, String emotionLevel, List<String> emotionLabels) {

		public ReviewContext {
			emotionLabels = List.copyOf(emotionLabels);
		}

		@Override
		public String toString() {
			return "ReviewContext[]";
		}

	}

	Optional<AnswerReviewResult> review(String draftContent, ReviewContext context, Roster roster);

}
