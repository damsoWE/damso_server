package damso.inquiry.domain;

import java.util.List;

/**
 * 답변 점검 결과. 엔티티가 아니라 응답에만 쓰이는 값 객체이며 영구 저장하지 않는다 (FR-013).
 * F3가 가명화 게이트웨이를 완성하기 전까지는 항상 {@code available=false}다(fail-open, FR-012).
 */
public record AnswerReviewResult(boolean available, List<Highlight> highlights) {

	public static final AnswerReviewResult UNAVAILABLE = new AnswerReviewResult(false, List.of());

	/** 받은 목록을 복사해 둔다 — 넘긴 쪽이 나중에 목록을 바꿔도 결과가 바뀌지 않는다. */
	public AnswerReviewResult {
		highlights = List.copyOf(highlights);
	}

	/** 점검이 짚어준 문제 표현 한 곳. */
	public record Highlight(String excerpt, String reason, String suggestion) {

	}

}
