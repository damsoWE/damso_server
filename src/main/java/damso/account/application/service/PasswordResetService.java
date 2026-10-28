package damso.account.application.service;

import java.time.Clock;
import java.time.Instant;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import damso.account.application.port.in.PasswordResetUseCase;
import damso.account.application.port.out.PasswordResetPort;
import damso.account.application.port.out.SessionPort;
import damso.account.application.port.out.UserAccountPort;
import damso.account.domain.AuthSession;
import damso.account.domain.PasswordPolicy;
import damso.account.domain.PasswordReset;
import damso.account.domain.UserAccount;
import damso.attempt.application.port.in.AttemptLimitUseCase;
import damso.attempt.domain.AttemptKind;
import damso.common.crypto.CodeHasher;
import damso.common.error.BusinessException;
import damso.common.error.ErrorCode;

@Service
@Transactional
public class PasswordResetService implements PasswordResetUseCase {

	private final UserAccountPort accounts;

	private final PasswordResetPort resets;

	private final SessionPort sessions;

	private final AttemptLimitUseCase attempts;

	private final ApplicationEventPublisher events;

	private final PasswordEncoder passwordEncoder;

	private final CodeHasher hasher;

	private final Clock clock;

	public PasswordResetService(UserAccountPort accounts, PasswordResetPort resets, SessionPort sessions,
			AttemptLimitUseCase attempts, ApplicationEventPublisher events, PasswordEncoder passwordEncoder,
			CodeHasher hasher, Clock clock) {
		this.accounts = accounts;
		this.resets = resets;
		this.sessions = sessions;
		this.attempts = attempts;
		this.events = events;
		this.passwordEncoder = passwordEncoder;
		this.hasher = hasher;
		this.clock = clock;
	}

	@Override
	public void request(String email, String clientIp) {
		String normalized = UserAccount.normalizeEmail(email);
		// 발송 제한은 계정 조회 전에 판단한다 — 걸리면 가입 여부와 관계없이 같은 429(이슈 #22).
		// 이메일만이 아니라 요청 위치까지 묶어 세므로 남이 내 이메일로 요청해도 내 재설정이 막히지 않는다
		attempts.ensureResetMailAllowed(normalized, clientIp);
		// 계정 조회·코드 발급은 응답 뒤에 한다(PasswordResetIssuer) — 가입 여부가 응답 시간에 드러나지 않는다
		events.publishEvent(new PasswordResetRequested(normalized));
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
