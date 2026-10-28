package damso.common.crypto;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** 키 없는 해시. 256비트 무작위 값(갱신 토큰)처럼 추측할 수 없는 입력에만 쓴다. 코드 공간이 작은 값은 CodeHasher를 쓴다. */
public final class Digests {

	private Digests() {
	}

	public static String sha256Hex(String value) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
		}
		catch (NoSuchAlgorithmException ex) {
			throw new ColumnCryptoException("해시 계산에 실패했습니다.");
		}
	}

	/**
	 * 해시 문자열을 상수 시간으로 비교한다 — 앞에서부터 몇 글자가 맞았는지가 처리 시간에 드러나지 않는다.
	 * 저장된 해시와 요청에서 만든 해시를 비교할 때는 String.equals 대신 이것을 쓴다.
	 */
	public static boolean constantTimeEquals(String expected, String actual) {
		if (expected == null || actual == null) {
			return false;
		}
		return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
				actual.getBytes(StandardCharsets.UTF_8));
	}

}
