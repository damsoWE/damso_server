package damso.account.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import damso.common.entity.BaseEntity;

/**
 * 세션 = 갱신 토큰 계열. 로그인 1회(기기 1대)마다 하나가 생긴다 (FR-025a).
 * id가 접근 토큰의 sid 클레임이다. 마지막 갱신 후 14일이 지나면 만료되고, 갱신할 때마다 연장된다 (FR-025b).
 * 폐기(revoked)와 만료는 되돌릴 수 없다.
 */
@Entity
@Table(name = "auth_session", indexes = @Index(name = "idx_auth_session_user", columnList = "user_id"))
public class AuthSession extends BaseEntity {

	@Column(name = "user_id", nullable = false, updatable = false)
	private UUID userId;

	@Column(name = "expires_at", nullable = false)
	private Instant expiresAt;

	@Column(name = "revoked_at")
	private Instant revokedAt;

	@Enumerated(EnumType.STRING)
	@Column(name = "revoke_reason", length = 20)
	private RevokeReason revokeReason;

	protected AuthSession() {
	}

	public AuthSession(UUID userId, Instant now, Duration ttl) {
		this.userId = userId;
		this.expiresAt = now.plus(ttl);
	}

	public boolean isActive(Instant now) {
		return revokedAt == null && expiresAt.isAfter(now);
	}

	/** 갱신할 때마다 만료를 now + ttl로 미룬다. */
	public void extend(Instant now, Duration ttl) {
		this.expiresAt = now.plus(ttl);
	}

	public void revoke(RevokeReason reason, Instant now) {
		if (revokedAt == null) {
			this.revokedAt = now;
			this.revokeReason = reason;
		}
	}

	public UUID getUserId() {
		return userId;
	}

	public Instant getExpiresAt() {
		return expiresAt;
	}

	public Instant getRevokedAt() {
		return revokedAt;
	}

	public RevokeReason getRevokeReason() {
		return revokeReason;
	}

	public enum RevokeReason {
		LOGOUT,
		REUSE_DETECTED,
		PASSWORD_RESET,
		WITHDRAWAL
	}

}
