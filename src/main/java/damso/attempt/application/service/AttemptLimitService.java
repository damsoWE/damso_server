package damso.attempt.application.service;

import java.time.Clock;
import java.time.Instant;

import org.springframework.stereotype.Service;

import damso.attempt.application.port.in.AttemptLimitUseCase;
import damso.attempt.application.port.out.AttemptLimitPort;
import damso.attempt.domain.AttemptKind;
import damso.attempt.domain.AttemptPolicy;
import damso.attempt.domain.AttemptState;
import damso.common.crypto.CodeHasher;
import damso.common.error.TooManyAttemptsException;

@Service
public class AttemptLimitService implements AttemptLimitUseCase {

	private final AttemptLimitPort port;

	private final CodeHasher hasher;

	private final Clock clock;

	public AttemptLimitService(AttemptLimitPort port, CodeHasher hasher, Clock clock) {
		this.port = port;
		this.hasher = hasher;
		this.clock = clock;
	}

	@Override
	public void ensureAllowed(AttemptKind kind, String subject) {
		Instant now = clock.instant();
		AttemptState state = port.get(kind, hasher.hash(subject));
		if (state.isBlocked(now)) {
			throw new TooManyAttemptsException(ceilSeconds(state.remaining(now).toMillis()));
		}
	}

	@Override
	public void recordFailure(AttemptKind kind, String subject) {
		port.recordFailure(kind, hasher.hash(subject), clock.instant(), AttemptPolicy.ruleFor(kind));
	}

	@Override
	public void reset(AttemptKind kind, String subject) {
		port.reset(kind, hasher.hash(subject));
	}

	@Override
	public long reserveMailSend(String subject) {
		long waitMillis = port.reserveMailSend(hasher.hash(subject), clock.instant());
		return waitMillis <= 0 ? 0 : ceilSeconds(waitMillis);
	}

	@Override
	public void ensureMailSendAllowed(String subject) {
		long waitSeconds = reserveMailSend(subject);
		if (waitSeconds > 0) {
			throw new TooManyAttemptsException(waitSeconds);
		}
	}

	private static long ceilSeconds(long millis) {
		return (millis + 999) / 1000;
	}

}
