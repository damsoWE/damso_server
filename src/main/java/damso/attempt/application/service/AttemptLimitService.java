package damso.attempt.application.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
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

	private final Duration inquirySubmitInterval;

	public AttemptLimitService(AttemptLimitPort port, CodeHasher hasher, Clock clock,
			@Value("${damso.inquiry.submit-interval:60s}") Duration inquirySubmitInterval) {
		this.port = port;
		this.hasher = hasher;
		this.clock = clock;
		this.inquirySubmitInterval = inquirySubmitInterval;
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

	@Override
	public long reserveInquirySubmit(UUID parentId) {
		long waitMillis = port.reserveRate(AttemptKind.INQUIRY_SUBMIT, hasher.hash(parentId.toString()),
				clock.instant(), inquirySubmitInterval, inquirySubmitInterval, AttemptPolicy.INQUIRY_MAX_PER_WINDOW);
		return waitMillis <= 0 ? 0 : ceilSeconds(waitMillis);
	}

	private static long ceilSeconds(long millis) {
		return (millis + 999) / 1000;
	}

}
