package damso.common.crypto;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * 컬럼 암호화: AES-256-GCM (research R5).
 * 저장 형식: Base64( version(1B)=0x01 ‖ IV(12B) ‖ ciphertext ‖ tag(16B) )
 * - IV는 값마다 SecureRandom으로 새로 만든다 → 같은 평문도 다른 암호문
 * - GCM 인증 태그로 변조를 감지한다
 * - 빈 문자열은 암호화하지 않는다
 */
public final class AesGcmCipher {

	static final byte VERSION = 0x01;
	static final int KEY_BYTES = 32;
	static final int IV_BYTES = 12;
	static final int TAG_BITS = 128;
	private static final String TRANSFORMATION = "AES/GCM/NoPadding";

	private final SecretKey key;
	private final SecureRandom random = new SecureRandom();

	public AesGcmCipher(byte[] keyBytes) {
		if (keyBytes == null || keyBytes.length < KEY_BYTES) {
			throw new IllegalArgumentException("column encryption key must be at least 256 bits");
		}
		this.key = new SecretKeySpec(keyBytes, 0, KEY_BYTES, "AES");
	}

	public String encrypt(String plaintext) {
		if (plaintext.isEmpty()) {
			return "";
		}
		try {
			byte[] iv = new byte[IV_BYTES];
			random.nextBytes(iv);
			Cipher cipher = Cipher.getInstance(TRANSFORMATION);
			cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
			byte[] sealed = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
			ByteBuffer out = ByteBuffer.allocate(1 + IV_BYTES + sealed.length);
			out.put(VERSION).put(iv).put(sealed);
			return Base64.getEncoder().encodeToString(out.array());
		}
		catch (GeneralSecurityException ex) {
			throw new ColumnCryptoException("column encryption failed", ex);
		}
	}

	public String decrypt(String stored) {
		if (stored.isEmpty()) {
			return "";
		}
		byte[] raw;
		try {
			raw = Base64.getDecoder().decode(stored);
		}
		catch (IllegalArgumentException ex) {
			throw new ColumnCryptoException("column value is not valid ciphertext");
		}
		if (raw.length < 1 + IV_BYTES + TAG_BITS / 8 || raw[0] != VERSION) {
			throw new ColumnCryptoException("column value has unsupported format");
		}
		try {
			Cipher cipher = Cipher.getInstance(TRANSFORMATION);
			cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, raw, 1, IV_BYTES));
			byte[] plain = cipher.doFinal(raw, 1 + IV_BYTES, raw.length - 1 - IV_BYTES);
			return new String(plain, StandardCharsets.UTF_8);
		}
		catch (GeneralSecurityException ex) {
			throw new ColumnCryptoException("column decryption failed");
		}
	}

}
