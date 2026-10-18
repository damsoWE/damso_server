package damso.account.adapter.in.controller;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import damso.account.adapter.in.controller.dto.response.MeResponseDTO;
import damso.account.adapter.in.controller.dto.request.RenameRequestDTO;
import damso.account.adapter.in.controller.dto.request.WithdrawalRequestDTO;
import damso.account.application.port.in.MyAccountUseCase;
import damso.account.application.port.in.WithdrawUseCase;
import damso.common.security.CurrentUser;

/**
 * 마이페이지: 모든 역할이 본인 정보만 다룬다. 연결된 학급은 역할별 API(교사 /teacher/classroom, 학부모 /parent/children)로 본다.
 * 응답에는 본인 이메일만 있다.
 */
@RestController
@RequestMapping("/api/v1/me")
class MeController {

	private final MyAccountUseCase myAccount;

	private final WithdrawUseCase withdraw;

	MeController(MyAccountUseCase myAccount, WithdrawUseCase withdraw) {
		this.myAccount = myAccount;
		this.withdraw = withdraw;
	}

	@GetMapping
	MeResponseDTO me(CurrentUser user) {
		return MeResponseDTO.from(myAccount.me(user.userId()));
	}

	@PatchMapping
	MeResponseDTO rename(CurrentUser user, @Valid @RequestBody RenameRequestDTO request) {
		return MeResponseDTO.from(myAccount.rename(user.userId(), request.name()));
	}

	@PostMapping("/withdrawal")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void withdraw(CurrentUser user, @Valid @RequestBody WithdrawalRequestDTO request) {
		withdraw.withdraw(user.userId(), request.password());
	}

}
