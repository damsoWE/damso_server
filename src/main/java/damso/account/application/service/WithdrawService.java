package damso.account.application.service;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import damso.account.application.port.in.WithdrawUseCase;
import damso.account.application.port.out.SessionPort;
import damso.account.application.port.out.UserAccountPort;
import damso.account.domain.AuthSession;
import damso.account.domain.Role;
import damso.account.domain.UserAccount;
import damso.attempt.application.port.in.AttemptLimitUseCase;
import damso.attempt.domain.AttemptKind;
import damso.common.error.BusinessException;
import damso.common.error.ErrorCode;
import damso.school.application.port.in.MembershipReleaseUseCase;

@Service
@Transactional
public class WithdrawService implements WithdrawUseCase {

	private final UserAccountPort accounts;

	private final SessionPort sessions;

	private final MembershipReleaseUseCase memberships;

	private final AttemptLimitUseCase attempts;

	private final PasswordEncoder passwordEncoder;

	private final Clock clock;

	public WithdrawService(UserAccountPort accounts, SessionPort sessions, MembershipReleaseUseCase memberships,
			AttemptLimitUseCase attempts, PasswordEncoder passwordEncoder, Clock clock) {
		this.accounts = accounts;
		this.sessions = sessions;
		this.memberships = memberships;
		this.attempts = attempts;
		this.passwordEncoder = passwordEncoder;
		this.clock = clock;
	}

	@Override
	public void withdraw(UUID userId, String password) {
		UserAccount account = accounts.findById(userId).filter(UserAccount::isActive)
				.orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
		if (account.getRole() == Role.SUPER_ADMIN) {
			throw new BusinessException(ErrorCode.FORBIDDEN);
		}

		// 탈취된 접근 토큰으로 비밀번호를 대입하지 못하게 로그인과 같은 실패 횟수 규칙을 쓴다
		String email = account.getEmail();
		attempts.ensureAllowed(AttemptKind.LOGIN, email);
		if (!passwordEncoder.matches(password, account.getPasswordHash())) {
			attempts.recordFailure(AttemptKind.LOGIN, email);
			throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
		}
		attempts.reset(AttemptKind.LOGIN, email);

		Instant now = clock.instant();
		memberships.releaseAll(userId);
		sessions.revokeAllByUser(userId, AuthSession.RevokeReason.WITHDRAWAL, now);
		// 위 작업의 벌크 UPDATE·DELETE가 영속성 컨텍스트를 비우므로 계정은 다시 읽어서 바꾼다
		UserAccount fresh = accounts.findById(userId).orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
		fresh.withdraw(now);
		accounts.save(fresh);
	}

}
