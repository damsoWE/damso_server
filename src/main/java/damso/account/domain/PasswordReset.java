package damso.account.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import damso.common.crypto.Digests;
import damso.common.entity.BaseEntity;

/**
 * 비밀번호 재설정 코드(6자리, 10분 유효, 1회용). 코드는 HMAC만 저장한다.
 * 사용자당 유효한 건은 1개다 — 다시 요청하면 이전 건을 지워 이전 코드가 무효가 된다 (FR-030).
 * user_id 유니크 인덱스로 DB에서도 1건을 보장한다. 기존 DB는 docs/db/2026-10-28-password-reset-unique-user.sql로 바꾼다.
 */
@Entity
@Table(name = "password_reset",
		indexes = @Index(name = "idx_password_reset_user", columnList = "user_id", unique = true))
public class PasswordReset extends BaseEntity {

	public static final Duration CODE_TTL = Duration.ofMinutes(10);

	@Column(name = "user_id", nullable = false, updatable = false)
	private UUID userId;

	@Column(name = "code_hash", nullable = false, updatable = false, length = 64, columnDefinition = "char(64)")
	private String codeHash;

	@Column(name = "expires_at", nullable = false, updatable = false)
	private Instant expiresAt;

	@Column(name = "used_at")
	private Instant usedAt;

	protected PasswordReset() {
	}

	public PasswordReset(UUID userId, String codeHash, Instant now) {
		this.userId = userId;
		this.codeHash = codeHash;
		this.expiresAt = now.plus(CODE_TTL);
	}

	public boolean matches(String candidateCodeHash, Instant now) {
		return usedAt == null && expiresAt.isAfter(now) && Digests.constantTimeEquals(codeHash, candidateCodeHash);
	}

	public void markUsed(Instant now) {
		this.usedAt = now;
	}

	public UUID getUserId() {
		return userId;
	}

	public Instant getExpiresAt() {
		return expiresAt;
	}

	public Instant getUsedAt() {
		return usedAt;
	}

}
