package damso.account.adapter.in.controller.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 가입 1단계 요청. 역할·학급·학생 값은 받지 않는다 — 초대 코드가 정한다.
 * 초대 코드는 영문 대문자+숫자 10자리(0·O·1·I·L 제외, 소문자 입력은 서버가 대문자로 맞춘다).
 */
public record SignupRequestDTO(
		@NotBlank @Pattern(regexp = "^[A-HJKMNP-Za-hjkmnp-z2-9]{10}$") String inviteCode,
		@NotBlank @Size(max = 20) String name,
		@NotBlank @Email @Size(max = 254) String email,
		@NotBlank @Size(min = 8, max = 64) String password,
		@NotNull Boolean termsAgreed) {

	@Override
	public String toString() {
		return "SignupRequestDTO[]";
	}

}
