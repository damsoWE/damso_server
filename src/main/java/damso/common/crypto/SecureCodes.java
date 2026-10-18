package damso.common.crypto;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * 초대 코드·인증 코드·갱신 토큰 생성. 모두 SecureRandom이다 (원칙 백엔드 요건, 체크리스트 보안기능 8).
 */
public final class SecureCodes {

	/** 영문 대문자+숫자에서 헷갈리는 문자(0, O, 1, I, L)를 뺀 31자. */
	static final String INVITE_ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";

	static final int INVITE_LENGTH = 10;

	private static final SecureRandom RANDOM = new SecureRandom();

	private SecureCodes() {
	}

	public static String inviteCode() {
		StringBuilder code = new StringBuilder(INVITE_LENGTH);
		for (int i = 0; i < INVITE_LENGTH; i++) {
			code.append(INVITE_ALPHABET.charAt(RANDOM.nextInt(INVITE_ALPHABET.length())));
		}
		return code.toString();
	}

	/** 000000~999999, 앞은 0으로 채운다. */
	public static String sixDigits() {
		return String.format("%06d", RANDOM.nextInt(1_000_000));
	}

	/** 256비트 무작위 값(Base64URL, 패딩 없음). 갱신 토큰 원문. */
	public static String refreshToken() {
		byte[] bytes = new byte[32];
		RANDOM.nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

}
