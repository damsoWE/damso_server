package damso.account.application.service;

import java.time.Clock;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import damso.account.application.port.in.LogoutUseCase;
import damso.account.application.port.out.SessionPort;
import damso.account.domain.AuthSession;

@Service
@Transactional
public class LogoutService implements LogoutUseCase {

	private final SessionPort sessions;

	private final Clock clock;

	public LogoutService(SessionPort sessions, Clock clock) {
		this.sessions = sessions;
		this.clock = clock;
	}

	@Override
	public void logout(UUID sessionId) {
		sessions.findSession(sessionId).ifPresent(session -> {
			session.revoke(AuthSession.RevokeReason.LOGOUT, clock.instant());
			sessions.saveSession(session);
		});
	}

}
