package damso.account.adapter.in.controller.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 재설정 코드 확인 요청(코드를 소모하지 않는다). */
public record ResetCodeRequestDTO(
		@NotBlank @Email @Size(max = 254) String email,
		@NotBlank @Pattern(regexp = "^[0-9]{6}$") String code) {

	@Override
	public String toString() {
		return "ResetCodeRequestDTO[]";
	}

}
