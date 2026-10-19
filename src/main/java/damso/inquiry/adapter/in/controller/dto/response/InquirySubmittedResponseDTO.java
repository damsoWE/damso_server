package damso.inquiry.adapter.in.controller.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import damso.inquiry.application.port.in.SubmitInquiryUseCase.InquirySubmittedResult;
import damso.inquiry.domain.HotlineCode;
import damso.inquiry.domain.InquiryStage;
import damso.inquiry.domain.NoticeCode;

/** 문의 접수 응답. 안내는 코드뿐이고 고정 문구는 API 계약에 있다. */
public record InquirySubmittedResponseDTO(UUID inquiryId, boolean urgent, InquiryStage stage, Instant submittedAt,
		List<NoticeCode> notices, List<HotlineCode> hotlines) {

	public static InquirySubmittedResponseDTO from(InquirySubmittedResult result) {
		return new InquirySubmittedResponseDTO(result.inquiryId(), result.urgent(), result.stage(),
				result.submittedAt(), result.notices(), result.hotlines());
	}

}
