package damso.school.adapter.in.controller.dto.response;

import java.time.Instant;
import java.util.UUID;

import damso.school.application.port.in.InviteIssueUseCase.IssuedInvite;

/** code 원문은 이 응답에서만 보인다. toString에서 가린다. */
public record IssuedInviteCodeResponseDTO(UUID inviteCodeId, String code, Instant expiresAt) {

	public static IssuedInviteCodeResponseDTO from(IssuedInvite invite) {
		return new IssuedInviteCodeResponseDTO(invite.inviteCodeId(), invite.code(), invite.expiresAt());
	}

	@Override
	public String toString() {
		return "IssuedInviteCodeResponseDTO[inviteCodeId=" + inviteCodeId + "]";
	}

}
