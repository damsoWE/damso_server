package damso.account.adapter.in.controller;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import damso.account.adapter.in.controller.dto.request.InviteCodeCheckRequestDTO;
import damso.account.adapter.in.controller.dto.request.LoginRequestDTO;
import damso.account.adapter.in.controller.dto.response.InviteCodeCheckResponseDTO;
import damso.account.adapter.in.controller.dto.request.PasswordResetRequestDTO;
import damso.account.adapter.in.controller.dto.response.PendingSignupResponseDTO;
import damso.account.adapter.in.controller.dto.request.RefreshRequestDTO;
import damso.account.adapter.in.controller.dto.request.ResendSignupRequestDTO;
import damso.account.adapter.in.controller.dto.request.ResetCodeRequestDTO;
import damso.account.adapter.in.controller.dto.request.ResetConfirmRequestDTO;
import damso.account.adapter.in.controller.dto.request.SignupRequestDTO;
import damso.account.adapter.in.controller.dto.response.TokenResponseDTO;
import damso.account.adapter.in.controller.dto.request.VerifySignupRequestDTO;
import damso.account.application.port.in.LoginUseCase;
import damso.account.application.port.in.LogoutUseCase;
import damso.account.application.port.in.PasswordResetUseCase;
import damso.account.application.port.in.RefreshUseCase;
import damso.account.application.port.in.SignupUseCase;
import damso.account.application.port.in.SignupUseCase.PendingSignupResult;
import damso.account.application.port.in.SignupUseCase.StartSignupCommand;
import damso.common.security.CurrentUser;

/**
 * 로그인 전 단계(가입·로그인·갱신·비밀번호 재설정)와 로그아웃. 공개 경로는 SecurityConfig.PUBLIC_PATHS에 하나씩 명시돼 있다.
 * 요청 본문에 역할·학급·학생 값을 받지 않는다 — 초대 코드가 정한다 (FR-015).
 */
@RestController
@RequestMapping("/api/v1/auth")
class AuthController {

	private final SignupUseCase signup;

	private final LoginUseCase login;

	private final RefreshUseCase refresh;

	private final LogoutUseCase logout;

	private final PasswordResetUseCase passwordReset;

	AuthController(SignupUseCase signup, LoginUseCase login, RefreshUseCase refresh, LogoutUseCase logout,
			PasswordResetUseCase passwordReset) {
		this.signup = signup;
		this.login = login;
		this.refresh = refresh;
		this.logout = logout;
		this.passwordReset = passwordReset;
	}

	/** 가입 전 초대 코드 확인 — 역할만 알려 주고 코드를 소모하지 않는다. 가입 1단계가 코드를 다시 검증한다. */
	@PostMapping("/invite-codes/verify")
	InviteCodeCheckResponseDTO checkInviteCode(@Valid @RequestBody InviteCodeCheckRequestDTO request) {
		return new InviteCodeCheckResponseDTO(signup.checkInviteCode(request.inviteCode(), request.email()));
	}

	@PostMapping("/signup")
	@ResponseStatus(HttpStatus.ACCEPTED)
	PendingSignupResponseDTO startSignup(@Valid @RequestBody SignupRequestDTO request) {
		PendingSignupResult result = signup.start(new StartSignupCommand(request.inviteCode(), request.name(),
				request.email(), request.password(), request.termsAgreed()));
		return new PendingSignupResponseDTO(result.pendingSignupId(), result.codeExpiresInSeconds());
	}

	@PostMapping("/signup/verify")
	@ResponseStatus(HttpStatus.CREATED)
	TokenResponseDTO verifySignup(@Valid @RequestBody VerifySignupRequestDTO request) {
		return TokenResponseDTO.from(signup.verify(request.pendingSignupId(), request.code()));
	}

	@PostMapping("/signup/resend")
	@ResponseStatus(HttpStatus.ACCEPTED)
	void resendSignupCode(@Valid @RequestBody ResendSignupRequestDTO request) {
		signup.resend(request.pendingSignupId());
	}

	@PostMapping("/login")
	TokenResponseDTO login(@Valid @RequestBody LoginRequestDTO request) {
		return TokenResponseDTO.from(login.login(request.email(), request.password()));
	}

	@PostMapping("/token/refresh")
	TokenResponseDTO refresh(@Valid @RequestBody RefreshRequestDTO request) {
		return TokenResponseDTO.from(refresh.refresh(request.refreshToken()));
	}

	/** 이 기기의 세션만 폐기한다. 다른 기기의 세션은 유지된다. */
	@PostMapping("/logout")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void logout(CurrentUser user) {
		logout.logout(user.sessionId());
	}

	/** 가입 여부와 관계없이 같은 202를 돌려준다. */
	@PostMapping("/password-reset")
	@ResponseStatus(HttpStatus.ACCEPTED)
	void requestPasswordReset(@Valid @RequestBody PasswordResetRequestDTO request) {
		passwordReset.request(request.email());
	}

	@PostMapping("/password-reset/verify")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void verifyPasswordResetCode(@Valid @RequestBody ResetCodeRequestDTO request) {
		passwordReset.verify(request.email(), request.code());
	}

	@PostMapping("/password-reset/confirm")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void confirmPasswordReset(@Valid @RequestBody ResetConfirmRequestDTO request) {
		passwordReset.confirm(request.email(), request.code(), request.newPassword());
	}

}
