package damso.common.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Base64;

import org.junit.jupiter.api.Test;

import damso.support.TestSecrets;

/** AES-256-GCM 왕복, 매번 다른 암호문, 변조·키 불일치·버전 오류 감지. */
class AesGcmCipherTest {

	private final AesGcmCipher cipher = new AesGcmCipher(TestSecrets.randomBytes(32));

	@Test
	void roundTrip() {
		String plain = "아이가 어제 또 다쳐서 왔어요.";
		assertThat(cipher.decrypt(cipher.encrypt(plain))).isEqualTo(plain);
	}

	@Test
	void samePlaintextProducesDifferentCiphertext() {
		assertThat(cipher.encrypt("same")).isNotEqualTo(cipher.encrypt("same"));
	}

	@Test
	void ciphertextHasVersionIvAndTag() {
		byte[] raw = Base64.getDecoder().decode(cipher.encrypt("x"));
		assertThat(raw[0]).isEqualTo((byte) 0x01);
		assertThat(raw.length).isEqualTo(1 + 12 + 1 + 16);
	}

	@Test
	void tamperedCiphertextIsRejected() {
		byte[] raw = Base64.getDecoder().decode(cipher.encrypt("original"));
		raw[raw.length - 1] ^= 0x01;
		String tampered = Base64.getEncoder().encodeToString(raw);
		assertThatThrownBy(() -> cipher.decrypt(tampered)).isInstanceOf(ColumnCryptoException.class);
	}

	@Test
	void unknownVersionIsRejected() {
		byte[] raw = Base64.getDecoder().decode(cipher.encrypt("original"));
		raw[0] = 0x02;
		String other = Base64.getEncoder().encodeToString(raw);
		assertThatThrownBy(() -> cipher.decrypt(other)).isInstanceOf(ColumnCryptoException.class);
	}

	@Test
	void differentKeyCannotDecrypt() {
		String encrypted = cipher.encrypt("original");
		AesGcmCipher other = new AesGcmCipher(TestSecrets.randomBytes(32));
		assertThatThrownBy(() -> other.decrypt(encrypted)).isInstanceOf(ColumnCryptoException.class);
	}

	@Test
	void malformedInputIsRejectedWithoutEchoingIt() {
		assertThatThrownBy(() -> cipher.decrypt("not-base64 secret-input"))
				.isInstanceOf(ColumnCryptoException.class)
				.message().doesNotContain("secret-input");
	}

	@Test
	void emptyStringStaysEmpty() {
		assertThat(cipher.encrypt("")).isEmpty();
		assertThat(cipher.decrypt("")).isEmpty();
	}

	@Test
	void rejectsKeysShorterThan256Bits() {
		assertThatThrownBy(() -> new AesGcmCipher(TestSecrets.randomBytes(16)))
				.isInstanceOf(IllegalArgumentException.class);
	}

}
