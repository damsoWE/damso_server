package damso.inquiry.adapter.in.controller.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import damso.inquiry.application.port.in.TeacherInquiryQuery.InquiryDetail;
import damso.inquiry.domain.InquiryStage;
import damso.inquiry.domain.RefinementStatus;

/**
 * 교사의 문의 상세. 본문·번역본은 없다 — 원문은 /content(감사 기록), 번역본은 /refinement로 따로 연다.
 * refinementStatus는 긴급 문의면 null, retryAvailable은 원문 확인 필요이고 교사 재시도를 아직 쓰지 않았으면 true(F3).
 */
public record TeacherInquiryDetailResponseDTO(UUID inquiryId, UUID studentId, String studentName, boolean urgent,
		String title, InquiryStage stage, RefinementStatus refinementStatus, boolean retryAvailable,
		Instant submittedAt, List<StageEventResponseDTO> timeline) {

	public static TeacherInquiryDetailResponseDTO from(InquiryDetail detail) {
		return new TeacherInquiryDetailResponseDTO(detail.inquiryId(), detail.studentId(), detail.studentName(),
				detail.urgent(), detail.title(), detail.stage(), detail.refinementStatus(), detail.retryAvailable(),
				detail.submittedAt(), detail.timeline().stream().map(StageEventResponseDTO::from).toList());
	}

	@Override
	public String toString() {
		return "TeacherInquiryDetailResponseDTO[inquiryId=" + inquiryId + "]";
	}

}
