package damso.account.adapter.in.controller.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 회원 탈퇴 요청: 현재 비밀번호를 다시 확인한다. */
public record WithdrawalRequestDTO(@NotBlank @Size(max = 64) String password) {

	@Override
	public String toString() {
		return "WithdrawalRequestDTO[]";
	}

}
