package damso.account.adapter.in.controller.dto.request;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/** 가입 2단계 요청: 대기 건 UUID와 이메일로 받은 6자리 인증 코드. */
public record VerifySignupRequestDTO(
		@NotNull UUID pendingSignupId,
		@NotBlank @Pattern(regexp = "^[0-9]{6}$") String code) {

	@Override
	public String toString() {
		return "VerifySignupRequestDTO[]";
	}

}
