package damso.account.application.service;

import java.time.Clock;
import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import damso.account.application.port.in.RefreshUseCase;
import damso.account.application.port.out.SessionPort;
import damso.account.application.port.out.UserAccountPort;
import damso.account.domain.AuthSession;
import damso.account.domain.RefreshToken;
import damso.account.domain.UserAccount;
import damso.common.crypto.Digests;
import damso.common.error.BusinessException;
import damso.common.error.ErrorCode;

@Service
public class RefreshService implements RefreshUseCase {

	private final SessionPort sessions;

	private final UserAccountPort accounts;

	private final SessionIssuer sessionIssuer;

	private final Clock clock;

	public RefreshService(SessionPort sessions, UserAccountPort accounts, SessionIssuer sessionIssuer, Clock clock) {
		this.sessions = sessions;
		this.accounts = accounts;
		this.sessionIssuer = sessionIssuer;
		this.clock = clock;
	}

	/**
	 * 재사용을 탐지하면 세션 폐기를 남겨야 하므로, 401을 던져도 트랜잭션은 커밋한다(noRollbackFor).
	 * 이 메서드에서 던지는 BusinessException은 모두 UNAUTHORIZED이고 쓰기는 세션 폐기뿐이다.
	 */
	@Override
	@Transactional(noRollbackFor = BusinessException.class)
	public TokenResult refresh(String refreshToken) {
		Instant now = clock.instant();
		RefreshToken token = sessions.findRefreshTokenByHash(Digests.sha256Hex(refreshToken))
				.orElseThrow(RefreshService::unauthorized);
		AuthSession session = sessions.findSession(token.getSessionId()).orElseThrow(RefreshService::unauthorized);
		if (!session.isActive(now)) {
			throw unauthorized();
		}
		UserAccount account = accounts.findById(session.getUserId()).filter(UserAccount::isActive)
				.orElseThrow(RefreshService::unauthorized);

		// 조건부 갱신(used_at IS NULL) — 이미 쓰인 토큰이면 재사용(탈취 의심)으로 보고 이 세션만 폐기한다
		if (!sessions.markRefreshTokenUsed(token.getId(), now)) {
			session.revoke(AuthSession.RevokeReason.REUSE_DETECTED, now);
			sessions.saveSession(session);
			throw unauthorized();
		}
		return sessionIssuer.issueForSession(account, session);
	}

	private static BusinessException unauthorized() {
		return new BusinessException(ErrorCode.UNAUTHORIZED);
	}

}
