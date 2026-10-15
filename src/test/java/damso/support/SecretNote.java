package damso.support;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import damso.common.crypto.SensitiveText;
import damso.common.entity.BaseEntity;

/** 테스트 전용 엔티티: BaseEntity 상속 + SensitiveText 필드. 운영 코드에는 없다. */
@Entity
@Table(name = "test_secret_note")
public class SecretNote extends BaseEntity {

	@Column(columnDefinition = "TEXT", nullable = false)
	private SensitiveText content;

	protected SecretNote() {
	}

	public SecretNote(SensitiveText content) {
		this.content = content;
	}

	public SensitiveText getContent() {
		return content;
	}

	public void changeContent(SensitiveText content) {
		this.content = content;
	}

}
