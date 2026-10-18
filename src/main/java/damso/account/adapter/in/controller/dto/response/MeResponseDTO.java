package damso.account.adapter.in.controller.dto.response;

import java.util.UUID;

import damso.account.application.port.in.MyAccountUseCase.MeView;
import damso.account.domain.Role;

/** 마이페이지 응답. 본인 이메일만 담는다. */
public record MeResponseDTO(UUID userId, String name, String email, Role role) {

	public static MeResponseDTO from(MeView view) {
		return new MeResponseDTO(view.userId(), view.name(), view.email(), view.role());
	}

	@Override
	public String toString() {
		return "MeResponseDTO[userId=" + userId + "]";
	}

}
