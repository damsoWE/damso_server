package damso.common.crypto;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HexFormat;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 초대 코드·인증 코드·실패 횟수 대상(이메일 등)을 서버 키가 섞인 HMAC-SHA256으로 해시한다 (research R3).
 * 코드 공간이 작아 단순 해시는 DB 유출 시 전수 대입으로 복원되므로 키를 섞는다.
 * 결과는 결정적이라 조회 인덱스로 쓸 수 있다. 입력값은 예외 메시지·로그에 넣지 않는다.
 */
@Component
public class CodeHasher {

	private static final String ALGORITHM = "HmacSHA256";

	private final SecretKeySpec key;

	public CodeHasher(@Value("${damso.crypto.code-hash-key}") String base64Key) {
		this.key = new SecretKeySpec(Base64.getDecoder().decode(base64Key.trim()), ALGORITHM);
	}

	/** 정규화된 입력의 HMAC-SHA256을 소문자 hex 64자로 반환한다. */
	public String hash(String normalized) {
		try {
			Mac mac = Mac.getInstance(ALGORITHM);
			mac.init(key);
			return HexFormat.of().formatHex(mac.doFinal(normalized.getBytes(StandardCharsets.UTF_8)));
		}
		catch (java.security.GeneralSecurityException ex) {
			// 메시지에 입력을 담지 않는다
			throw new ColumnCryptoException("코드 해시 계산에 실패했습니다.");
		}
	}

}
