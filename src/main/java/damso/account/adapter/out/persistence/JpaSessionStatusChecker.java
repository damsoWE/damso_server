package damso.account.adapter.out.persistence;

import java.time.Clock;
import java.util.UUID;

import org.springframework.stereotype.Component;

import damso.account.application.port.out.SessionPort;
import damso.common.security.SessionStatusChecker;

/** 접근 토큰 검증 때 세션이 살아 있는지(폐기·만료·탈퇴 여부)를 DB에서 확인한다 (research R4). */
@Component
class JpaSessionStatusChecker implements SessionStatusChecker {

	private final SessionPort sessions;

	private final Clock clock;

	JpaSessionStatusChecker(SessionPort sessions, Clock clock) {
		this.sessions = sessions;
		this.clock = clock;
	}

	@Override
	public boolean isActive(UUID sessionId) {
		return sessions.isSessionActive(sessionId, clock.instant());
	}

}
