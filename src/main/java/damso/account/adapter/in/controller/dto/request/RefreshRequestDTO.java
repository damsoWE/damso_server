package damso.account.adapter.in.controller.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RefreshRequestDTO(@NotBlank @Size(max = 64) String refreshToken) {

	@Override
	public String toString() {
		return "RefreshRequestDTO[]";
	}

}
