package damso.account.application.port.out;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import damso.account.domain.AuthSession;
import damso.account.domain.RefreshToken;

public interface SessionPort {

	AuthSession saveSession(AuthSession session);

	Optional<AuthSession> findSession(UUID sessionId);

	RefreshToken saveRefreshToken(RefreshToken token);

	Optional<RefreshToken> findRefreshTokenByHash(String tokenHash);

	/**
	 * 갱신 토큰을 사용 처리한다. 조건부 갱신(used_at IS NULL)이라 동시에 두 번 호출해도 한 번만 true다.
	 * false면 이미 사용된 토큰이다(재사용).
	 */
	boolean markRefreshTokenUsed(UUID tokenId, Instant now);

	/** 그 사용자의 살아 있는 모든 세션을 폐기한다. 폐기한 세션 수를 돌려준다. */
	int revokeAllByUser(UUID userId, AuthSession.RevokeReason reason, Instant now);

	/** 세션이 폐기·만료되지 않았고 사용자가 ACTIVE일 때만 true. */
	boolean isSessionActive(UUID sessionId, Instant now);

}
