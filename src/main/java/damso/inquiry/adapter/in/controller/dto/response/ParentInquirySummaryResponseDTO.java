package damso.inquiry.adapter.in.controller.dto.response;

import java.time.Instant;
import java.util.UUID;

import damso.inquiry.application.port.in.ParentInquiryQuery.InquirySummary;
import damso.inquiry.domain.InquiryStage;

/**
 * 학부모 목록의 문의 한 건. 본문은 없고 상세에서만 본다.
 * hasAnswer·lastAnsweredAt은 스레드 전체의 답변 기준이며, 앱은 stage가 아니라 이 값으로 답변한 문의를 판단한다.
 */
public record ParentInquirySummaryResponseDTO(UUID inquiryId, UUID studentId, String studentName, boolean urgent,
		InquiryStage stage, Instant submittedAt, boolean hasAnswer, Instant lastAnsweredAt) {

	public static ParentInquirySummaryResponseDTO from(InquirySummary summary) {
		return new ParentInquirySummaryResponseDTO(summary.inquiryId(), summary.studentId(), summary.studentName(),
				summary.urgent(), summary.stage(), summary.submittedAt(), summary.hasAnswer(),
				summary.lastAnsweredAt());
	}

	/** 학생 이름이 로그에 섞이지 않도록 가린다. */
	@Override
	public String toString() {
		return "ParentInquirySummaryResponseDTO[inquiryId=" + inquiryId + "]";
	}

}
