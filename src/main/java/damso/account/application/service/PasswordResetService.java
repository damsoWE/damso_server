package damso.account.application.service;

import java.time.Clock;
import java.time.Instant;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import damso.account.application.port.in.PasswordResetUseCase;
import damso.account.application.port.out.PasswordResetPort;
import damso.account.application.port.out.SessionPort;
import damso.account.application.port.out.UserAccountPort;
import damso.account.application.port.out.VerificationMailPort;
import damso.account.domain.AuthSession;
import damso.account.domain.PasswordPolicy;
import damso.account.domain.PasswordReset;
import damso.account.domain.UserAccount;
import damso.attempt.application.port.in.AttemptLimitUseCase;
import damso.attempt.domain.AttemptKind;
import damso.common.crypto.CodeHasher;
import damso.common.crypto.SecureCodes;
import damso.common.error.BusinessException;
import damso.common.error.ErrorCode;

@Service
@Transactional
public class PasswordResetService implements PasswordResetUseCase {

	private final UserAccountPort accounts;

	private final PasswordResetPort resets;

	private final SessionPort sessions;

	private final AttemptLimitUseCase attempts;

	private final VerificationMailPort mail;

	private final PasswordEncoder passwordEncoder;

	private final CodeHasher hasher;

	private final Clock clock;

	public PasswordResetService(UserAccountPort accounts, PasswordResetPort resets, SessionPort sessions,
			AttemptLimitUseCase attempts, VerificationMailPort mail, PasswordEncoder passwordEncoder,
			CodeHasher hasher, Clock clock) {
		this.accounts = accounts;
		this.resets = resets;
		this.sessions = sessions;
		this.attempts = attempts;
		this.mail = mail;
		this.passwordEncoder = passwordEncoder;
		this.hasher = hasher;
		this.clock = clock;
	}

	@Override
	public void request(String email) {
		String normalized = UserAccount.normalizeEmail(email);
		// 가입 여부와 관계없이 같은 호출을 하고(발송 제한 확인), 가입된 활성 계정일 때만 코드를 만든다
		long waitSeconds = attempts.reserveMailSend(normalized);
		UserAccount account = accounts.findByEmail(normalized).filter(UserAccount::isActive).orElse(null);
		if (account == null || waitSeconds > 0) {
			return;
		}
		String code = SecureCodes.sixDigits();
		resets.deleteByUserId(account.getId());
		resets.save(new PasswordReset(account.getId(), hasher.hash(code), clock.instant()));
		mail.sendPasswordResetCode(normalized, code);
	}

	@Override
	public void verify(String email, String code) {
		requireValidCode(UserAccount.normalizeEmail(email), code);
	}

	@Override
	public void confirm(String email, String code, String newPassword) {
		String normalized = UserAccount.normalizeEmail(email);
		Instant now = clock.instant();
		ValidReset valid = requireValidCode(normalized, code);
		PasswordPolicy.validate(newPassword, "newPassword");

		valid.account().changePassword(passwordEncoder.encode(newPassword));
		accounts.save(valid.account());
		valid.reset().markUsed(now);
		resets.save(valid.reset());
		sessions.revokeAllByUser(valid.account().getId(), AuthSession.RevokeReason.PASSWORD_RESET, now);
		attempts.reset(AttemptKind.RESET_CODE, normalized);
	}

	/** 잠금 확인 → 코드 확인. 어떤 이유로든 코드가 맞지 않으면 실패 횟수를 세고 같은 INVALID_VERIFICATION_CODE로 응답한다. */
	private ValidReset requireValidCode(String email, String code) {
		attempts.ensureAllowed(AttemptKind.RESET_CODE, email);
		Instant now = clock.instant();
		UserAccount account = accounts.findByEmail(email).filter(UserAccount::isActive).orElse(null);
		PasswordReset reset = account == null ? null : resets.findByUserId(account.getId()).orElse(null);
		if (reset == null || !reset.matches(hasher.hash(code), now)) {
			attempts.recordFailure(AttemptKind.RESET_CODE, email);
			throw new BusinessException(ErrorCode.INVALID_VERIFICATION_CODE);
		}
		return new ValidReset(account, reset);
	}

	private record ValidReset(UserAccount account, PasswordReset reset) {
	}

}
