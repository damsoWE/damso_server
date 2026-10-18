package damso.account.adapter.in.controller.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 새 비밀번호 설정 요청: 재설정 코드를 소모하고 비밀번호를 바꾼다. */
public record ResetConfirmRequestDTO(
		@NotBlank @Email @Size(max = 254) String email,
		@NotBlank @Pattern(regexp = "^[0-9]{6}$") String code,
		@NotBlank @Size(min = 8, max = 64) String newPassword) {

	@Override
	public String toString() {
		return "ResetConfirmRequestDTO[]";
	}

}
