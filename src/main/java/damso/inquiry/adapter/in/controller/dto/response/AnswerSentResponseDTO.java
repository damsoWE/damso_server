package damso.inquiry.adapter.in.controller.dto.response;

import java.time.Instant;
import java.util.UUID;

import damso.inquiry.application.port.in.SendAnswerUseCase.AnswerSentResult;

public record AnswerSentResponseDTO(UUID answerId, UUID inquiryId, Instant sentAt) {

	public static AnswerSentResponseDTO from(AnswerSentResult result) {
		return new AnswerSentResponseDTO(result.answerId(), result.inquiryId(), result.sentAt());
	}

}
