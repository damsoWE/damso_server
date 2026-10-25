package damso.notification.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 응답 가능 시간 판정(순수 규칙). 시각 비교만 Asia/Seoul로 한다 (FR-027·029).
 * 구간이 하나도 없으면(설정 없음·빈 목록) 항상 응답 가능이다.
 */
public final class AvailabilityCalendar {

	public static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

	private AvailabilityCalendar() {
	}

	public static boolean isAvailable(List<AvailabilityWindow> windows, Instant at) {
		if (windows.isEmpty()) {
			return true;
		}
		ZonedDateTime local = at.atZone(ZONE);
		return windowOn(windows, local.toLocalDate()).filter(window -> window.contains(local.toLocalTime())).isPresent();
	}

	/**
	 * 지금 응답 가능하면 at 그대로, 아니면 7일 안에서 가장 가까운 다음 시작 시각.
	 * 구간이 하나라도 있으면 매주 반복되므로 7일 안에 반드시 있다. 없으면(구간 없음) at.
	 */
	public static Instant nextStart(List<AvailabilityWindow> windows, Instant at) {
		if (isAvailable(windows, at)) {
			return at;
		}
		LocalDate today = at.atZone(ZONE).toLocalDate();
		for (int offset = 0; offset <= 7; offset++) {
			LocalDate date = today.plusDays(offset);
			Optional<AvailabilityWindow> window = windowOn(windows, date);
			if (window.isPresent()) {
				Instant start = date.atTime(window.get().start()).atZone(ZONE).toInstant();
				if (start.isAfter(at)) {
					return start;
				}
			}
		}
		return at;
	}

	private static Optional<AvailabilityWindow> windowOn(List<AvailabilityWindow> windows, LocalDate date) {
		return windows.stream().filter(window -> window.day() == date.getDayOfWeek()).findFirst();
	}

}
