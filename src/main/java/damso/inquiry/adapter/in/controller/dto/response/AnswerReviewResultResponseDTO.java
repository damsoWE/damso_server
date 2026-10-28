package damso.inquiry.adapter.in.controller.dto.response;

import java.util.List;

import damso.inquiry.domain.AnswerReviewResult;
import damso.inquiry.domain.AnswerReviewResult.Highlight;

public record AnswerReviewResultResponseDTO(boolean available, List<Highlight> highlights) {

	/** 받은 목록을 복사해 둔다(불변) — 만든 쪽이 나중에 목록을 바꿔도 이 값은 바뀌지 않는다. */
	public AnswerReviewResultResponseDTO {
		highlights = highlights == null ? null : List.copyOf(highlights);
	}

	public static AnswerReviewResultResponseDTO from(AnswerReviewResult result) {
		return new AnswerReviewResultResponseDTO(result.available(), result.highlights());
	}

}
