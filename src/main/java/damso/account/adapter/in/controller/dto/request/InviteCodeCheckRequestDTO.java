package damso.account.adapter.in.controller.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 가입 전 초대 코드 확인 요청. 실패 횟수를 가입과 같은 이메일 기준으로 세기 위해 가입하려는 이메일을 함께 받는다(FR-011a).
 * 형식 규칙은 가입 1단계(SignupRequestDTO)와 같다.
 */
public record InviteCodeCheckRequestDTO(
		@NotBlank @Pattern(regexp = "^[A-HJKMNP-Za-hjkmnp-z2-9]{10}$") String inviteCode,
		@NotBlank @Email @Size(max = 254) String email) {

	@Override
	public String toString() {
		return "InviteCodeCheckRequestDTO[]";
	}

}
