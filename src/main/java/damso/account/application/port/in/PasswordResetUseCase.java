package damso.account.application.port.in;

/**
 * 비밀번호 재설정: 가입 이메일로 6자리 재설정 코드를 보내고, 앱에서 코드와 새 비밀번호를 입력한다.
 * 가입 여부와 관계없이 요청 응답은 같다(가입 여부 추측 차단).
 */
public interface PasswordResetUseCase {

	/**
	 * 가입된 활성 계정일 때만 코드를 보내고, 미가입이면 예외 없이 끝난다. 발송 제한(요청 위치 → 이메일+요청 위치 → 이메일)에
	 * 걸리면 가입 여부와 관계없이 TooManyAttemptsException(429 + Retry-After) — 제한은 계정 조회 전에 판단하고, 계정 조회·코드
	 * 발급은 응답 뒤에 해 가입 여부가 응답 내용·시간에 드러나지 않는다(이슈 #22). clientIp는 제한을 세는 데만 쓴다.
	 */
	void request(String email, String clientIp);

	/**
	 * 코드가 맞는지만 확인한다(코드를 소모하지 않는다). 없음·만료·사용됨·미가입은 모두
	 * INVALID_VERIFICATION_CODE이고 실패 횟수를 센다(5회 실패 시 10분 잠금).
	 */
	void verify(String email, String code);

	/** 코드를 소모하고 비밀번호를 바꾼다. 해당 계정의 모든 기존 세션을 폐기한다. */
	void confirm(String email, String code, String newPassword);

}
