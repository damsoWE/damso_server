package damso.inquiry.adapter.in.controller.dto.response;

import java.time.Instant;
import java.util.UUID;

import damso.inquiry.application.port.in.CloseInquiryUseCase.InquiryClosedResult;

/** 문의 스레드 종료 응답. */
public record InquiryClosedResponseDTO(UUID inquiryId, Instant closedAt) {

	public static InquiryClosedResponseDTO from(InquiryClosedResult result) {
		return new InquiryClosedResponseDTO(result.inquiryId(), result.closedAt());
	}

}
