package damso.account.application.port.in;

import damso.account.application.service.TokenResult;

/**
 * 이메일·비밀번호 로그인. 기기마다 새 세션이 생긴다.
 * 없는 이메일·틀린 비밀번호·탈퇴 계정은 같은 INVALID_CREDENTIALS이고 처리 시간도 비슷하다.
 * 연속 실패 3회부터 대기하며, 대기 중에는 맞는 비밀번호여도 TooManyAttemptsException이다.
 */
public interface LoginUseCase {

	TokenResult login(String email, String password);

}
