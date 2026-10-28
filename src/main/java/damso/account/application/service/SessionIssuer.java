package damso.account.application.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import damso.account.application.port.out.AccessTokenIssuerPort;
import damso.account.application.port.out.SessionPort;
import damso.account.domain.AuthSession;
import damso.account.domain.RefreshToken;
import damso.account.domain.UserAccount;
import damso.common.crypto.Digests;
import damso.common.crypto.SecureCodes;

/**
 * 로그인·가입 확정 공용: 기기마다 새 세션을 만들고 접근 토큰 + 갱신 토큰을 발급한다.
 * 갱신 토큰 원문은 반환값에만 있고 저장되는 것은 SHA-256 해시뿐이다.
 */
@Component
public class SessionIssuer {

	private final SessionPort sessions;

	private final AccessTokenIssuerPort accessTokens;

	private final Clock clock;

	private final Duration sessionTtl;

	SessionIssuer(SessionPort sessions, AccessTokenIssuerPort accessTokens, Clock clock,
			@Value("${damso.auth.session-ttl}") Duration sessionTtl) {
		this.sessions = sessions;
		this.accessTokens = accessTokens;
		this.clock = clock;
		this.sessionTtl = sessionTtl;
	}

	/** 새 세션(기기 1대)을 만들고 토큰을 발급한다. */
	public TokenResult issueNewSession(UserAccount account) {
		Instant now = clock.instant();
		AuthSession session = sessions.saveSession(new AuthSession(account.getId(), now, sessionTtl));
		return issueRefreshAndAccess(account, session, now);
	}

	/** 기존 세션 안에서 새 갱신 토큰과 접근 토큰을 발급한다(갱신 시 회전). */
	TokenResult issueForSession(UserAccount account, AuthSession session) {
		Instant now = clock.instant();
		session.extend(now, sessionTtl);
		sessions.saveSession(session);
		return issueRefreshAndAccess(account, session, now);
	}

	private TokenResult issueRefreshAndAccess(UserAccount account, AuthSession session, Instant now) {
		String refreshToken = SecureCodes.refreshToken();
		sessions.saveRefreshToken(new RefreshToken(session.getId(), Digests.sha256Hex(refreshToken)));
		String accessToken = accessTokens.issue(account.getId(), account.getRole(), session.getId(), now);
		return new TokenResult(accessToken, accessTokens.ttlSeconds(), refreshToken, sessionTtl.toSeconds(),
				account.getRole());
	}

}
