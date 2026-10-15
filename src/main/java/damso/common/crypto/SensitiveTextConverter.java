package damso.common.crypto;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * SensitiveText 타입 필드에 자동 적용되는 컬럼 암호화 컨버터 (autoApply).
 * 개발자는 필드 타입만 SensitiveText로 정하면 된다 — 암호화 코드를 직접 쓰지 않는다 (SC-009).
 * Hibernate가 Spring 빈 컨테이너로 생성하므로 AesGcmCipher를 생성자로 주입받는다.
 */
@Converter(autoApply = true)
public class SensitiveTextConverter implements AttributeConverter<SensitiveText, String> {

	private final AesGcmCipher cipher;

	public SensitiveTextConverter(AesGcmCipher cipher) {
		this.cipher = cipher;
	}

	@Override
	public String convertToDatabaseColumn(SensitiveText attribute) {
		return attribute == null ? null : cipher.encrypt(attribute.reveal());
	}

	@Override
	public SensitiveText convertToEntityAttribute(String dbData) {
		return dbData == null ? null : SensitiveText.of(cipher.decrypt(dbData));
	}

}
