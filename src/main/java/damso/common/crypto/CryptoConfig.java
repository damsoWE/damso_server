package damso.common.crypto;

import java.util.Base64;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CryptoConfig {

	/** 키 존재·길이는 기동 전에 SecretsValidator가 이미 검사했다. */
	@Bean
	AesGcmCipher columnCipher(@Value("${damso.crypto.column-key}") String base64Key) {
		return new AesGcmCipher(Base64.getDecoder().decode(base64Key.trim()));
	}

}
