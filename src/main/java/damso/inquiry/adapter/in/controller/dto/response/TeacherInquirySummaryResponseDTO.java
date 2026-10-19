package damso.inquiry.adapter.in.controller.dto.response;

import java.time.Instant;
import java.util.UUID;

import damso.inquiry.application.port.in.TeacherInquiryQuery.InquirySummary;
import damso.inquiry.domain.InquiryStage;

/** 교사 목록의 문의 한 건. 본문·학부모 이메일·이름은 없다. 제목은 고정 문구이거나 정제 결과(F3)다. */
public record TeacherInquirySummaryResponseDTO(UUID inquiryId, UUID studentId, String studentName, boolean urgent,
		String title, InquiryStage stage, Instant submittedAt) {

	public static TeacherInquirySummaryResponseDTO from(InquirySummary summary) {
		return new TeacherInquirySummaryResponseDTO(summary.inquiryId(), summary.studentId(), summary.studentName(),
				summary.urgent(), summary.title(), summary.stage(), summary.submittedAt());
	}

	/** 학생 이름이 로그에 섞이지 않도록 가린다. */
	@Override
	public String toString() {
		return "TeacherInquirySummaryResponseDTO[inquiryId=" + inquiryId + "]";
	}

}
