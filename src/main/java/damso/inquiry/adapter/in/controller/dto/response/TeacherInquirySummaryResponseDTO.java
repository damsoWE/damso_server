package damso.inquiry.adapter.in.controller.dto.response;

import java.time.Instant;
import java.util.UUID;

import damso.inquiry.application.port.in.TeacherInquiryQuery.InquirySummary;
import damso.inquiry.domain.InquiryStage;
import damso.inquiry.domain.RefinementStatus;

/**
 * 교사 목록의 문의 한 건. 본문·학부모 이메일·이름은 없다. 제목은 고정 문구이거나 정리 결과 제목이다.
 * refinementStatus는 일반 문의의 정리 상태, 긴급 문의는 null.
 * threatFlagged는 교사에게만 보이는 위협·폭언 표현 표시, threadThreatFlagged는 재문의까지 포함한 스레드 단위 표시.
 * hasAnswer·lastAnsweredAt은 스레드 전체의 답변 기준, 답변이 없으면 lastAnsweredAt은 null.
 */
public record TeacherInquirySummaryResponseDTO(UUID inquiryId, UUID studentId, String studentName, boolean urgent,
		String title, InquiryStage stage, RefinementStatus refinementStatus, boolean threatFlagged,
		boolean threadThreatFlagged, Instant submittedAt, boolean hasAnswer, Instant lastAnsweredAt) {

	public static TeacherInquirySummaryResponseDTO from(InquirySummary summary) {
		return new TeacherInquirySummaryResponseDTO(summary.inquiryId(), summary.studentId(), summary.studentName(),
				summary.urgent(), summary.title(), summary.stage(), summary.refinementStatus(), summary.threatFlagged(),
				summary.threadThreatFlagged(), summary.submittedAt(), summary.hasAnswer(), summary.lastAnsweredAt());
	}

	/** 학생 이름이 로그에 섞이지 않도록 가린다. */
	@Override
	public String toString() {
		return "TeacherInquirySummaryResponseDTO[inquiryId=" + inquiryId + "]";
	}

}
