package damso.account.application.service;

import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import damso.account.application.port.in.LoginUseCase;
import damso.account.application.port.out.UserAccountPort;
import damso.account.domain.UserAccount;
import damso.attempt.application.port.in.AttemptLimitUseCase;
import damso.attempt.domain.AttemptKind;
import damso.common.error.BusinessException;
import damso.common.error.ErrorCode;

@Service
@Transactional
public class LoginService implements LoginUseCase {

	private final UserAccountPort accounts;

	private final PasswordEncoder passwordEncoder;

	private final AttemptLimitUseCase attempts;

	private final SessionIssuer sessionIssuer;

	/** 없는 이메일에도 같은 비용으로 비밀번호를 비교하기 위한 고정 해시. 어떤 계정의 비밀번호도 아니다 (research R8). */
	private final String dummyHash;

	public LoginService(UserAccountPort accounts, PasswordEncoder passwordEncoder, AttemptLimitUseCase attempts,
			SessionIssuer sessionIssuer) {
		this.accounts = accounts;
		this.passwordEncoder = passwordEncoder;
		this.attempts = attempts;
		this.sessionIssuer = sessionIssuer;
		this.dummyHash = passwordEncoder.encode(UUID.randomUUID().toString());
	}

	@Override
	public TokenResult login(String email, String password, String clientIp) {
		String normalized = UserAccount.normalizeEmail(email);
		attempts.ensureAllowed(AttemptKind.LOGIN, normalized);
		// 위 대기 확인은 읽기만 하므로 순서가 이래야 거부된 요청이 서로의 횟수를 쓰지 않는다(이슈 #35)
		attempts.ensureLoginClientAllowed(clientIp);

		UserAccount account = accounts.findByEmail(normalized).filter(UserAccount::isActive).orElse(null);
		// 계정이 없어도 같은 비용으로 비교해 처리 시간으로 가입 여부가 드러나지 않게 한다
		boolean matches = passwordEncoder.matches(password, account == null ? dummyHash : account.getPasswordHash());
		if (account == null || !matches) {
			attempts.recordFailure(AttemptKind.LOGIN, normalized);
			throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
		}
		attempts.reset(AttemptKind.LOGIN, normalized);
		return sessionIssuer.issueNewSession(account);
	}

}
