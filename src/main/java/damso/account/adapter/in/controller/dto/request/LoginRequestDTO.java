package damso.account.adapter.in.controller.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequestDTO(
		@NotBlank @Email @Size(max = 254) String email,
		@NotBlank @Size(max = 64) String password) {

	@Override
	public String toString() {
		return "LoginRequestDTO[]";
	}

}
