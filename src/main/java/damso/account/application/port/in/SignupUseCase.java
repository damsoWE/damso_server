package damso.account.application.port.in;

import java.util.UUID;

import damso.account.application.service.TokenResult;

/**
 * 가입 2단계 (research R5).
 * 1단계: 초대 코드를 검증하고 이메일로 6자리 인증 코드를 보낸다. 2단계: 인증 코드가 맞으면 한 트랜잭션에서
 * 계정 생성 → 초대 코드 소모 → 담임/보호자 연결 → 연결 이력 → 로그인 토큰 발급.
 */
public interface SignupUseCase {

	/** 이미 가입된 이메일이어도 같은 결과를 돌려준다(그 주소로 안내 메일만 보낸다). */
	PendingSignupResult start(StartSignupCommand command);

	/** 대기 건이 없어도 예외 없이 끝난다. */
	void resend(UUID pendingSignupId);

	TokenResult verify(UUID pendingSignupId, String code);

	record StartSignupCommand(String inviteCode, String name, String email, String password, boolean termsAgreed) {

		@Override
		public String toString() {
			return "StartSignupCommand[]";
		}

	}

	record PendingSignupResult(UUID pendingSignupId, long codeExpiresInSeconds) {
	}

}
