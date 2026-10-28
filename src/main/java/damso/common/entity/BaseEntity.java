package damso.common.entity;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Version;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * 모든 엔티티의 공통 기반.
 * - id: 생성 시점에 UUID v4로 할당 (순번 ID 미노출)
 * - version: 낙관적 락 (동시 수정 충돌 감지)
 * - toString: 클래스명과 id만 출력해 하위 클래스의 민감 필드가 로그에 섞이지 않게 한다
 */
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseEntity {

	@Id
	@Column(nullable = false, updatable = false)
	private UUID id = UUID.randomUUID();

	@CreatedDate
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@LastModifiedDate
	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@Version
	@Column(nullable = false)
	private Long version;

	public UUID getId() {
		return id;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

	public Long getVersion() {
		return version;
	}

	@Override
	public final boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		return Objects.equals(id, ((BaseEntity) o).id);
	}

	@Override
	public final int hashCode() {
		return Objects.hashCode(id);
	}

	@Override
	public final String toString() {
		return getClass().getSimpleName() + "[id=" + id + "]";
	}

}
