package damso.inquiry.adapter.in.controller.dto.response;

import java.util.List;

import damso.inquiry.domain.AnswerReviewResult;
import damso.inquiry.domain.AnswerReviewResult.Highlight;

public record AnswerReviewResultResponseDTO(boolean available, List<Highlight> highlights) {

	public static AnswerReviewResultResponseDTO from(AnswerReviewResult result) {
		return new AnswerReviewResultResponseDTO(result.available(), result.highlights());
	}

}
