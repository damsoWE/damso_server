package damso.inquiry.adapter.in.controller.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import damso.inquiry.application.port.in.TeacherInquiryQuery.InquiryDetail;
import damso.inquiry.domain.InquiryStage;

/** 교사의 문의 상세. 본문은 없다 — 원문은 /content로만 열람하고 그때 감사 기록이 남는다. */
public record TeacherInquiryDetailResponseDTO(UUID inquiryId, UUID studentId, String studentName, boolean urgent,
		String title, InquiryStage stage, Instant submittedAt, List<StageEventResponseDTO> timeline) {

	public static TeacherInquiryDetailResponseDTO from(InquiryDetail detail) {
		return new TeacherInquiryDetailResponseDTO(detail.inquiryId(), detail.studentId(), detail.studentName(),
				detail.urgent(), detail.title(), detail.stage(), detail.submittedAt(),
				detail.timeline().stream().map(StageEventResponseDTO::from).toList());
	}

	@Override
	public String toString() {
		return "TeacherInquiryDetailResponseDTO[inquiryId=" + inquiryId + "]";
	}

}
