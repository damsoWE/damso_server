package damso.school.adapter.in.controller.dto.response;

import java.time.Instant;
import java.util.UUID;

import damso.school.application.port.in.InviteIssueUseCase.InviteStatusView;
import damso.school.domain.InviteCode;
import damso.school.domain.InviteKind;

/** 초대 코드 발행 현황 응답. 코드 원문은 담지 않는다. */
public record InviteCodeStatusResponseDTO(UUID inviteCodeId, InviteKind kind, UUID studentId, String studentName,
		InviteCode.DisplayStatus status, Instant issuedAt, Instant expiresAt, Instant usedAt) {

	public static InviteCodeStatusResponseDTO from(InviteStatusView view) {
		return new InviteCodeStatusResponseDTO(view.inviteCodeId(), view.kind(), view.studentId(),
				view.studentName(), view.status(), view.issuedAt(), view.expiresAt(), view.usedAt());
	}

}
