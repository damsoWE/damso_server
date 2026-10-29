package damso.inquiry.adapter.in.controller.dto.response;

import java.util.List;

import damso.inquiry.domain.AnswerReviewResult;
import damso.inquiry.domain.AnswerReviewResult.Highlight;

public record AnswerReviewResultResponseDTO(boolean available, boolean contextUsed, List<Highlight> highlights) {

	/** 받은 목록을 복사해 둔다(불변) — 만든 쪽이 나중에 목록을 바꿔도 이 값은 바뀌지 않는다. */
	public AnswerReviewResultResponseDTO {
		highlights = highlights == null ? null : List.copyOf(highlights);
	}

	public static AnswerReviewResultResponseDTO from(AnswerReviewResult result) {
		return new AnswerReviewResultResponseDTO(result.available(), result.contextUsed(), result.highlights());
	}

	/** 하이라이트는 답변 초안·점검 결과 내용이라 로그에 섞이지 않도록 건수만 보인다. */
	@Override
	public String toString() {
		return "AnswerReviewResultResponseDTO[available=" + available + ", contextUsed=" + contextUsed
				+ ", highlights=" + (highlights == null ? 0 : highlights.size()) + "]";
	}

}
