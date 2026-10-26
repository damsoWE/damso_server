package damso.inquiry.adapter.in.controller.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import damso.inquiry.application.port.in.SubmitFollowUpUseCase.FollowUpSubmittedResult;
import damso.inquiry.domain.HotlineCode;
import damso.inquiry.domain.NoticeCode;

/** 재문의 자신의 접수 결과(F2 InquirySubmittedResponseDTO와 같은 모양). */
public record FollowUpSubmittedResponseDTO(UUID inquiryId, UUID threadRootId, boolean urgent, Instant submittedAt,
		List<NoticeCode> notices, List<HotlineCode> hotlines) {

	public static FollowUpSubmittedResponseDTO from(FollowUpSubmittedResult result) {
		return new FollowUpSubmittedResponseDTO(result.inquiryId(), result.threadRootId(), result.urgent(),
				result.submittedAt(), result.notices(), result.hotlines());
	}

}
