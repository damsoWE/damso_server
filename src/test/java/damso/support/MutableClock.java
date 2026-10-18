package damso.support;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/**
 * 테스트에서 시간을 앞으로 옮기는 Clock. 실제 시간은 계속 흐르고, advance()로 쌓은 오프셋만큼 앞서 간다.
 * 만료·대기(Redis의 앱 시계 기준 시각 포함) 테스트에서 실제로 기다리지 않게 한다.
 */
public class MutableClock extends Clock {

	private volatile Duration offset = Duration.ZERO;

	public void advance(Duration duration) {
		offset = offset.plus(duration);
	}

	public void reset() {
		offset = Duration.ZERO;
	}

	@Override
	public ZoneId getZone() {
		return ZoneOffset.UTC;
	}

	@Override
	public Clock withZone(ZoneId zone) {
		return this;
	}

	@Override
	public Instant instant() {
		return Instant.now().plus(offset);
	}

}
