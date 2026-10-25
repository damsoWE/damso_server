package damso.inquiry.adapter.in.controller.dto.response;

import java.time.Instant;
import java.util.UUID;

import damso.inquiry.application.port.in.ParentInquiryQuery.FollowUpView;
import damso.inquiry.domain.InquiryStage;

/** 스레드 타임라인에 포함되는 재문의 하나 — 학부모 본인 글이라 본문이 보인다. */
public record ParentFollowUpViewResponseDTO(UUID inquiryId, String content, boolean urgent, InquiryStage stage,
		Instant submittedAt) {

	public static ParentFollowUpViewResponseDTO from(FollowUpView view) {
		return new ParentFollowUpViewResponseDTO(view.inquiryId(), view.content(), view.urgent(), view.stage(),
				view.submittedAt());
	}

	/** 본문이 로그에 섞이지 않도록 가린다. */
	@Override
	public String toString() {
		return "ParentFollowUpViewResponseDTO[inquiryId=" + inquiryId + "]";
	}

}
