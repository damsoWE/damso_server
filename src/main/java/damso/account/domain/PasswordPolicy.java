package damso.account.domain;

import damso.common.error.InvalidInputException;

/**
 * 비밀번호 정책: 8~64자, 영문 1자 이상 + 숫자 1자 이상 (FR-028, 체크리스트 보안기능 9).
 * 64자 상한은 BCrypt 입력 한계(72바이트) 안쪽이다. 사유는 고정 문구이며 입력값을 담지 않는다.
 */
public final class PasswordPolicy {

	public static final int MIN_LENGTH = 8;

	public static final int MAX_LENGTH = 64;

	static final String REASON = "영문과 숫자를 포함한 8~64자여야 합니다";

	private PasswordPolicy() {
	}

	public static boolean isValid(String password) {
		if (password == null || password.length() < MIN_LENGTH || password.length() > MAX_LENGTH) {
			return false;
		}
		boolean hasLetter = false;
		boolean hasDigit = false;
		for (int i = 0; i < password.length(); i++) {
			char ch = password.charAt(i);
			if (ch >= '0' && ch <= '9') {
				hasDigit = true;
			}
			else if ((ch >= 'a' && ch <= 'z') || (ch >= 'A' && ch <= 'Z')) {
				hasLetter = true;
			}
		}
		return hasLetter && hasDigit;
	}

	/** 정책을 어기면 400 INVALID_INPUT(fieldErrors: field). */
	public static void validate(String password, String field) {
		if (!isValid(password)) {
			throw new InvalidInputException(field, REASON);
		}
	}

}
