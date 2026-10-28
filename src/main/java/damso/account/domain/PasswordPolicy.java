package damso.account.domain;

import java.nio.charset.StandardCharsets;

import damso.common.error.InvalidInputException;

/**
 * 비밀번호 정책: 8~64자, 영문 1자 이상 + 숫자 1자 이상 (FR-028, 체크리스트 보안기능 9).
 * BCrypt는 72바이트(UTF-8)를 넘는 입력을 거부하므로 그것도 함께 본다 — 64자 안이라도 한글처럼 여러 바이트인 글자로
 * 72바이트를 넘으면 해시 단계에서 예외(500)가 나기 전에 여기서 400으로 끝낸다. 사유는 고정 문구이며 입력값을 담지 않는다.
 */
public final class PasswordPolicy {

	public static final int MIN_LENGTH = 8;

	public static final int MAX_LENGTH = 64;

	/** BCrypt 입력 한계(UTF-8 바이트). */
	public static final int MAX_BYTES = 72;

	static final String REASON = "영문과 숫자를 포함한 8~64자여야 합니다";

	private PasswordPolicy() {
	}

	public static boolean isValid(String password) {
		if (password == null || password.length() < MIN_LENGTH || password.length() > MAX_LENGTH
				|| password.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
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
