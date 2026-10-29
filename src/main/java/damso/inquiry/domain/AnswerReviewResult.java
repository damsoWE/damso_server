package damso.inquiry.domain;

import java.util.List;

/**
 * 답변 점검 결과. 엔티티가 아니라 응답에만 쓰이는 값 객체이며 영구 저장하지 않는다 (FR-013).
 * 점검이 결과를 내지 못하면(장애·검증 실패·시간 초과) {@code available=false}다(fail-open, FR-012).
 * {@code contextUsed}는 문의 정리 결과를 맥락으로 넣어 점검했는지다(FR-008c). 실패면 항상 false다.
 * 하이라이트는 답변 초안·점검 결과 내용이므로 toString은 건수만 보여 준다(원칙 VI).
 */
public record AnswerReviewResult(boolean available, boolean contextUsed, List<Highlight> highlights) {

	public static final AnswerReviewResult UNAVAILABLE = new AnswerReviewResult(false, false, List.of());

	/** 받은 목록을 복사해 둔다 — 넘긴 쪽이 나중에 목록을 바꿔도 결과가 바뀌지 않는다. */
	public AnswerReviewResult {
		highlights = List.copyOf(highlights);
	}

	@Override
	public String toString() {
		return "AnswerReviewResult[available=" + available + ", contextUsed=" + contextUsed + ", highlights="
				+ highlights.size() + "]";
	}

	/** 점검이 짚어준 문제 표현 한 곳. toString은 가린다. */
	public record Highlight(String excerpt, String reason, String suggestion) {

		@Override
		public String toString() {
			return "Highlight[]";
		}

	}

}
