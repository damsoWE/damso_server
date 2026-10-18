package damso.account.adapter.out.persistence;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;

import damso.account.application.port.out.PasswordResetPort;
import damso.account.application.port.out.PendingSignupPort;
import damso.account.application.port.out.SessionPort;
import damso.account.application.port.out.UserAccountPort;
import damso.account.domain.AccountStatus;
import damso.account.domain.AuthSession;
import damso.account.domain.PasswordReset;
import damso.account.domain.PendingSignup;
import damso.account.domain.RefreshToken;
import damso.account.domain.UserAccount;

/** account 도메인의 출력 포트를 Spring Data JPA로 구현한다. */
@Component
class AccountPersistenceAdapter implements UserAccountPort, SessionPort, PendingSignupPort, PasswordResetPort {

	private final SpringDataUserAccountRepository accounts;

	private final SpringDataAuthSessionRepository sessions;

	private final SpringDataRefreshTokenRepository refreshTokens;

	private final SpringDataPendingSignupRepository pendingSignups;

	private final SpringDataPasswordResetRepository passwordResets;

	AccountPersistenceAdapter(SpringDataUserAccountRepository accounts, SpringDataAuthSessionRepository sessions,
			SpringDataRefreshTokenRepository refreshTokens, SpringDataPendingSignupRepository pendingSignups,
			SpringDataPasswordResetRepository passwordResets) {
		this.accounts = accounts;
		this.sessions = sessions;
		this.refreshTokens = refreshTokens;
		this.pendingSignups = pendingSignups;
		this.passwordResets = passwordResets;
	}

	// UserAccountPort

	@Override
	public UserAccount save(UserAccount account) {
		return accounts.save(account);
	}

	@Override
	public Optional<UserAccount> findById(UUID id) {
		return accounts.findById(id);
	}

	@Override
	public Optional<UserAccount> findByEmail(String email) {
		return accounts.findByEmail(email);
	}

	@Override
	public boolean existsByEmail(String email) {
		return accounts.existsByEmail(email);
	}

	// SessionPort

	@Override
	public AuthSession saveSession(AuthSession session) {
		return sessions.save(session);
	}

	@Override
	public Optional<AuthSession> findSession(UUID sessionId) {
		return sessions.findById(sessionId);
	}

	@Override
	public RefreshToken saveRefreshToken(RefreshToken token) {
		return refreshTokens.save(token);
	}

	@Override
	public Optional<RefreshToken> findRefreshTokenByHash(String tokenHash) {
		return refreshTokens.findByTokenHash(tokenHash);
	}

	@Override
	public boolean markRefreshTokenUsed(UUID tokenId, Instant now) {
		return refreshTokens.markUsed(tokenId, now) == 1;
	}

	@Override
	public int revokeAllByUser(UUID userId, AuthSession.RevokeReason reason, Instant now) {
		return sessions.revokeAllByUser(userId, reason, now);
	}

	@Override
	public boolean isSessionActive(UUID sessionId, Instant now) {
		return sessions.isActive(sessionId, now, AccountStatus.ACTIVE);
	}

	// PendingSignupPort

	@Override
	public PendingSignup save(PendingSignup pending) {
		return pendingSignups.save(pending);
	}

	@Override
	public Optional<PendingSignup> findPendingById(UUID id) {
		return pendingSignups.findById(id);
	}

	@Override
	public void deleteIncompleteByEmail(String email) {
		pendingSignups.deleteIncompleteByEmail(email);
	}

	@Override
	public int deleteStalePending(Instant before) {
		return pendingSignups.deleteStale(before);
	}

	// PasswordResetPort

	@Override
	public PasswordReset save(PasswordReset reset) {
		return passwordResets.save(reset);
	}

	@Override
	public Optional<PasswordReset> findByUserId(UUID userId) {
		return passwordResets.findByUserId(userId);
	}

	@Override
	public void deleteByUserId(UUID userId) {
		passwordResets.deleteByUserId(userId);
	}

	@Override
	public int deleteStaleResets(Instant before) {
		return passwordResets.deleteStale(before);
	}

}
