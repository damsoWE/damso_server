package damso.inquiry.adapter.in.controller.dto.response;

import java.time.Instant;
import java.util.UUID;

import damso.inquiry.application.port.in.TeacherInquiryQuery.FollowUpView;
import damso.inquiry.domain.InquiryStage;

/**
 * 스레드 타임라인에 포함되는 재문의 하나 — 본문 없음(원문은 /content로만, 긴급만 열람 가능).
 * threatFlagged는 교사에게만 보이는 위협·폭언 표현 표시.
 */
public record TeacherFollowUpViewResponseDTO(UUID inquiryId, boolean urgent, String title, InquiryStage stage,
		boolean threatFlagged, Instant submittedAt) {

	public static TeacherFollowUpViewResponseDTO from(FollowUpView view) {
		return new TeacherFollowUpViewResponseDTO(view.inquiryId(), view.urgent(), view.title(), view.stage(),
				view.threatFlagged(), view.submittedAt());
	}

}
