package damso.account.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import damso.common.crypto.Digests;
import damso.common.crypto.SensitiveText;
import damso.common.entity.BaseEntity;

/**
 * 가입 인증 대기. 이메일 인증이 끝나야 계정으로 확정되고 초대 코드가 소모된다.
 * 비밀번호는 BCrypt 해시, 이름은 암호화해 저장한다. 6자리 인증 코드는 HMAC만 저장한다(10분 유효).
 * 이메일당 미완료 건은 1개다 — 다시 시작하면 이전 건을 지운다.
 */
@Entity
@Table(name = "pending_signup", indexes = @Index(name = "idx_pending_signup_email", columnList = "email"))
public class PendingSignup extends BaseEntity {

	public static final Duration CODE_TTL = Duration.ofMinutes(10);

	@Column(nullable = false, updatable = false, length = 254)
	private String email;

	@Column(columnDefinition = "TEXT", nullable = false, updatable = false)
	private SensitiveText name;

	@Column(name = "password_hash", nullable = false, updatable = false, length = 100)
	private String passwordHash;

	@Column(name = "invite_code_id", nullable = false, updatable = false)
	private UUID inviteCodeId;

	@Column(name = "code_hash", nullable = false, length = 64, columnDefinition = "char(64)")
	private String codeHash;

	@Column(name = "code_expires_at", nullable = false)
	private Instant codeExpiresAt;

	@Column(name = "completed_at")
	private Instant completedAt;

	protected PendingSignup() {
	}

	public PendingSignup(String email, String name, String passwordHash, UUID inviteCodeId, String codeHash,
			Instant now) {
		this.email = email;
		this.name = SensitiveText.of(name);
		this.passwordHash = passwordHash;
		this.inviteCodeId = inviteCodeId;
		this.codeHash = codeHash;
		this.codeExpiresAt = now.plus(CODE_TTL);
	}

	/** 아직 확정되지 않았고, 코드가 일치하며, 만료 전인가. */
	public boolean matches(String candidateCodeHash, Instant now) {
		return completedAt == null && codeExpiresAt.isAfter(now) && Digests.constantTimeEquals(codeHash, candidateCodeHash);
	}

	/** 인증 코드를 다시 발급한다. 이전 코드는 즉시 무효가 된다. */
	public void reissueCode(String newCodeHash, Instant now) {
		this.codeHash = newCodeHash;
		this.codeExpiresAt = now.plus(CODE_TTL);
	}

	public void complete(Instant now) {
		this.completedAt = now;
	}

	public String getEmail() {
		return email;
	}

	public SensitiveText getName() {
		return name;
	}

	public String getPasswordHash() {
		return passwordHash;
	}

	public UUID getInviteCodeId() {
		return inviteCodeId;
	}

	public Instant getCodeExpiresAt() {
		return codeExpiresAt;
	}

	public Instant getCompletedAt() {
		return completedAt;
	}

}
