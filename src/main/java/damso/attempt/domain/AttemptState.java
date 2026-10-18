package damso.attempt.domain;

import java.time.Duration;
import java.time.Instant;

/**
 * 연속 실패 횟수와 다음 시도 가능 시각.
 *
 * @param nextAllowedAt 이 시각 전의 시도는 거부한다. 대기·잠금이 없으면 null
 */
public record AttemptState(int failedCount, Instant nextAllowedAt) {

	public static final AttemptState NONE = new AttemptState(0, null);

	public boolean isBlocked(Instant now) {
		return nextAllowedAt != null && nextAllowedAt.isAfter(now);
	}

	/** 남은 대기 시간. 대기 중이 아니면 0. */
	public Duration remaining(Instant now) {
		return isBlocked(now) ? Duration.between(now, nextAllowedAt) : Duration.ZERO;
	}

}
