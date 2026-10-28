package damso.account.domain;

import java.time.Instant;
import java.util.Locale;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import damso.common.crypto.SensitiveText;
import damso.common.entity.BaseEntity;

/**
 * 계정. 연락처 컬럼은 없다. 이메일은 로그인 식별용이며 소문자로 정규화한다.
 * 이름은 SensitiveText로 암호화 저장하고 로그에 [MASKED]로 찍힌다.
 * 탈퇴하면 이메일·비밀번호는 NULL, 이름은 "탈퇴한 사용자"가 된다 — NULL은 유니크 제약에서 중복이 아니라 재가입이 가능하다.
 */
@Entity
@Table(name = "user_account", uniqueConstraints = @UniqueConstraint(name = "uk_user_account_email", columnNames = "email"))
public class UserAccount extends BaseEntity {

	public static final String WITHDRAWN_NAME = "탈퇴한 사용자";

	public static final int MAX_NAME_LENGTH = 20;

	@Column(length = 254)
	private String email;

	@Column(name = "password_hash", length = 100)
	private String passwordHash;

	@Column(columnDefinition = "TEXT", nullable = false)
	private SensitiveText name;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, updatable = false, length = 20)
	private Role role;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private AccountStatus status;

	@Column(name = "terms_agreed_at")
	private Instant termsAgreedAt;

	@Column(name = "withdrawn_at")
	private Instant withdrawnAt;

	protected UserAccount() {
	}

	private UserAccount(String email, String passwordHash, String name, Role role, Instant termsAgreedAt) {
		this.email = normalizeEmail(email);
		this.passwordHash = passwordHash;
		this.name = SensitiveText.of(name);
		this.role = role;
		this.status = AccountStatus.ACTIVE;
		this.termsAgreedAt = termsAgreedAt;
	}

	public static UserAccount signUp(String email, String passwordHash, String name, Role role, Instant agreedAt) {
		if (role == Role.SUPER_ADMIN) {
			throw new IllegalArgumentException("super admin is not created through sign-up");
		}
		return new UserAccount(email, passwordHash, name, role, agreedAt);
	}

	/** 서버가 직접 만드는 슈퍼 관리자 (환경변수로 주입). 약관 동의 시각은 없다. */
	public static UserAccount superAdmin(String email, String passwordHash, String name) {
		return new UserAccount(email, passwordHash, name, Role.SUPER_ADMIN, null);
	}

	public static String normalizeEmail(String email) {
		return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
	}

	/** 이름은 공백을 제거한 뒤 1~20자여야 한다. */
	public static String normalizeName(String name) {
		String trimmed = name == null ? "" : name.strip();
		if (trimmed.isEmpty() || trimmed.length() > MAX_NAME_LENGTH) {
			throw new IllegalArgumentException("name length out of range");
		}
		return trimmed;
	}

	public void changePassword(String newPasswordHash) {
		this.passwordHash = newPasswordHash;
	}

	public void rename(String newName) {
		this.name = SensitiveText.of(normalizeName(newName));
	}

	/** 비식별화하고 탈퇴 상태로 바꾼다. ACTIVE → WITHDRAWN 단방향. */
	public void withdraw(Instant now) {
		if (status == AccountStatus.WITHDRAWN) {
			return;
		}
		this.email = null;
		this.passwordHash = null;
		this.name = SensitiveText.of(WITHDRAWN_NAME);
		this.status = AccountStatus.WITHDRAWN;
		this.withdrawnAt = now;
	}

	public boolean isActive() {
		return status == AccountStatus.ACTIVE;
	}

	public String getEmail() {
		return email;
	}

	public String getPasswordHash() {
		return passwordHash;
	}

	public SensitiveText getName() {
		return name;
	}

	public Role getRole() {
		return role;
	}

	public AccountStatus getStatus() {
		return status;
	}

	public Instant getTermsAgreedAt() {
		return termsAgreedAt;
	}

	public Instant getWithdrawnAt() {
		return withdrawnAt;
	}

}
