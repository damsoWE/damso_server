package damso.account.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import damso.common.entity.BaseEntity;

/**
 * 갱신 토큰. 원문은 저장하지 않고 SHA-256 해시만 둔다(토큰 자체가 256비트 무작위라 키 없는 해시로 충분).
 * 사용되면 used_at이 찍히고, 이미 쓰인 토큰이 다시 오면 재사용으로 보고 세션을 폐기한다.
 * 회전은 조건부 UPDATE(used_at IS NULL)로 원자 처리한다.
 */
@Entity
@Table(name = "refresh_token",
		uniqueConstraints = @UniqueConstraint(name = "uk_refresh_token_hash", columnNames = "token_hash"),
		indexes = @Index(name = "idx_refresh_token_session", columnList = "session_id"))
public class RefreshToken extends BaseEntity {

	@Column(name = "session_id", nullable = false, updatable = false)
	private UUID sessionId;

	@Column(name = "token_hash", nullable = false, updatable = false, length = 64, columnDefinition = "char(64)")
	private String tokenHash;

	@Column(name = "used_at")
	private Instant usedAt;

	protected RefreshToken() {
	}

	public RefreshToken(UUID sessionId, String tokenHash) {
		this.sessionId = sessionId;
		this.tokenHash = tokenHash;
	}

	public UUID getSessionId() {
		return sessionId;
	}

	public String getTokenHash() {
		return tokenHash;
	}

	public Instant getUsedAt() {
		return usedAt;
	}

}
