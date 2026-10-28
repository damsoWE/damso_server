package damso.account.application.service;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import damso.account.application.port.in.SignupUseCase;
import damso.account.application.port.out.PendingSignupPort;
import damso.account.application.port.out.UserAccountPort;
import damso.account.application.port.out.VerificationMailPort;
import damso.account.domain.PasswordPolicy;
import damso.account.domain.PendingSignup;
import damso.account.domain.Role;
import damso.account.domain.UserAccount;
import damso.attempt.application.port.in.AttemptLimitUseCase;
import damso.attempt.domain.AttemptKind;
import damso.common.crypto.CodeHasher;
import damso.common.crypto.SecureCodes;
import damso.common.error.BusinessException;
import damso.common.error.ErrorCode;
import damso.common.error.InvalidInputException;
import damso.school.application.port.in.InviteRedeemUseCase;
import damso.school.application.port.in.InviteRedeemUseCase.InviteTarget;
import damso.school.domain.InviteKind;

@Service
@Transactional
public class SignupService implements SignupUseCase {

	private final UserAccountPort accounts;

	private final PendingSignupPort pendingSignups;

	private final InviteRedeemUseCase invites;

	private final AttemptLimitUseCase attempts;

	private final VerificationMailPort mail;

	private final SessionIssuer sessionIssuer;

	private final PasswordEncoder passwordEncoder;

	private final CodeHasher hasher;

	private final Clock clock;

	public SignupService(UserAccountPort accounts, PendingSignupPort pendingSignups, InviteRedeemUseCase invites,
			AttemptLimitUseCase attempts, VerificationMailPort mail, SessionIssuer sessionIssuer,
			PasswordEncoder passwordEncoder, CodeHasher hasher, Clock clock) {
		this.accounts = accounts;
		this.pendingSignups = pendingSignups;
		this.invites = invites;
		this.attempts = attempts;
		this.mail = mail;
		this.sessionIssuer = sessionIssuer;
		this.passwordEncoder = passwordEncoder;
		this.hasher = hasher;
		this.clock = clock;
	}

	@Override
	public Role checkInviteCode(String inviteCode, String email) {
		return roleOf(validateInviteCode(inviteCode, UserAccount.normalizeEmail(email)));
	}

	/** 가입 1단계와 코드 확인이 같은 규칙을 쓴다: 잠금 확인 → 검증 → 코드 무효면 이메일 기준 실패 기록. */
	private InviteTarget validateInviteCode(String inviteCode, String email) {
		attempts.ensureAllowed(AttemptKind.INVITE_CODE, email);
		try {
			return invites.validate(inviteCode);
		}
		catch (BusinessException invalidCode) {
			if (invalidCode.errorCode() == ErrorCode.INVALID_INVITE_CODE) {
				attempts.recordFailure(AttemptKind.INVITE_CODE, email);
			}
			throw invalidCode;
		}
	}

	private static Role roleOf(InviteTarget target) {
		return target.kind() == InviteKind.TEACHER ? Role.TEACHER : Role.PARENT;
	}

	@Override
	public PendingSignupResult start(StartSignupCommand command) {
		String email = UserAccount.normalizeEmail(command.email());
		String name = normalizedName(command.name());
		// 상태를 쓰지 않는 입력 검증을 먼저 한다 — 잘못된 비밀번호가 초대 코드 실패 횟수를 올리지 않게
		PasswordPolicy.validate(command.password(), "password");
		if (!command.termsAgreed()) {
			throw new InvalidInputException("termsAgreed", "동의가 필요합니다");
		}

		InviteTarget target = validateInviteCode(command.inviteCode(), email);

		// 가입된 이메일이든 아니든 처리 시간과 응답이 같도록 비밀번호 해시를 항상 먼저 계산한다
		String passwordHash = passwordEncoder.encode(command.password());
		attempts.ensureMailSendAllowed(email);

		if (accounts.existsByEmail(email)) {
			mail.sendAlreadyRegistered(email);
			return new PendingSignupResult(UUID.randomUUID(), PendingSignup.CODE_TTL.toSeconds());
		}

		Instant now = clock.instant();
		String code = SecureCodes.sixDigits();
		pendingSignups.deleteIncompleteByEmail(email);
		PendingSignup pending = pendingSignups.save(
				new PendingSignup(email, name, passwordHash, target.inviteCodeId(), hasher.hash(code), now));
		mail.sendSignupCode(email, code);
		return new PendingSignupResult(pending.getId(), PendingSignup.CODE_TTL.toSeconds());
	}

	@Override
	public void resend(UUID pendingSignupId) {
		PendingSignup pending = pendingSignups.findPendingById(pendingSignupId).orElse(null);
		if (pending == null || pending.getCompletedAt() != null) {
			return;
		}
		attempts.ensureMailSendAllowed(pending.getEmail());
		String code = SecureCodes.sixDigits();
		pending.reissueCode(hasher.hash(code), clock.instant());
		pendingSignups.save(pending);
		mail.sendSignupCode(pending.getEmail(), code);
	}

	@Override
	public TokenResult verify(UUID pendingSignupId, String code) {
		PendingSignup pending = pendingSignups.findPendingById(pendingSignupId)
				.orElseThrow(() -> new BusinessException(ErrorCode.INVALID_VERIFICATION_CODE));
		String email = pending.getEmail();

		attempts.ensureAllowed(AttemptKind.SIGNUP_CODE, email);
		Instant now = clock.instant();
		if (!pending.matches(hasher.hash(code), now)) {
			attempts.recordFailure(AttemptKind.SIGNUP_CODE, email);
			throw new BusinessException(ErrorCode.INVALID_VERIFICATION_CODE);
		}
		if (accounts.existsByEmail(email)) {
			throw new BusinessException(ErrorCode.INVALID_VERIFICATION_CODE);
		}

		// 여기부터는 한 트랜잭션: 어느 단계든 실패하면 계정·연결·코드 소모가 모두 되돌려진다
		String name = pending.getName().reveal();
		InviteTarget target = invites.resolve(pending.getInviteCodeId());
		Role role = roleOf(target);
		UserAccount account = accounts.save(
				UserAccount.signUp(email, pending.getPasswordHash(), name, role, now));
		invites.redeem(target.inviteCodeId(), account.getId(), name);
		pending.complete(now);
		pendingSignups.save(pending);

		attempts.reset(AttemptKind.SIGNUP_CODE, email);
		attempts.reset(AttemptKind.INVITE_CODE, email);
		return sessionIssuer.issueNewSession(account);
	}

	private static String normalizedName(String name) {
		try {
			return UserAccount.normalizeName(name);
		}
		catch (IllegalArgumentException invalid) {
			throw new InvalidInputException("name", "1~20자여야 합니다");
		}
	}

}
