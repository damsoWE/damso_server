package damso.account.application.port.in;

/**
 * 비밀번호 재설정: 가입 이메일로 6자리 재설정 코드를 보내고, 앱에서 코드와 새 비밀번호를 입력한다 (FR-029~031).
 * 가입 여부와 관계없이 요청 응답은 같다(가입 여부 추측 차단).
 */
public interface PasswordResetUseCase {

	/** 가입된 활성 계정일 때만 코드를 보낸다. 발송 제한에 걸려도, 미가입이어도 예외 없이 끝난다. */
	void request(String email);

	/**
	 * 코드가 맞는지만 확인한다(코드를 소모하지 않는다). 없음·만료·사용됨·미가입은 모두
	 * INVALID_VERIFICATION_CODE이고 실패 횟수를 센다(5회 실패 시 10분 잠금).
	 */
	void verify(String email, String code);

	/** 코드를 소모하고 비밀번호를 바꾼다. 해당 계정의 모든 기존 세션을 폐기한다. */
	void confirm(String email, String code, String newPassword);

}
