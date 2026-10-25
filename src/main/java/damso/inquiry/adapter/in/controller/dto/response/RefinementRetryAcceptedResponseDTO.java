package damso.inquiry.adapter.in.controller.dto.response;

import java.util.UUID;

import damso.inquiry.application.port.in.RetryRefinementUseCase.RetryAccepted;
import damso.inquiry.domain.RefinementStatus;

/** 교사 재시도를 받았다. 결과는 상세의 refinementStatus로 확인한다. */
public record RefinementRetryAcceptedResponseDTO(UUID inquiryId, RefinementStatus refinementStatus) {

	public static RefinementRetryAcceptedResponseDTO from(RetryAccepted accepted) {
		return new RefinementRetryAcceptedResponseDTO(accepted.inquiryId(), accepted.refinementStatus());
	}

}
