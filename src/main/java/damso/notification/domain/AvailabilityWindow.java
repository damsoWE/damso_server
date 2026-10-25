package damso.notification.domain;

import java.time.DayOfWeek;
import java.time.LocalTime;

/**
 * 교사의 하루 응답 가능 구간. 한국 시간 기준, 시작은 포함하고 종료는 포함하지 않는다.
 * 하루 끝(24:00)은 END_OF_DAY로 나타낸다. 이 값은 그날 마지막 순간까지 포함한다.
 */
public record AvailabilityWindow(DayOfWeek day, LocalTime start, LocalTime end) {

	/** 24:00. time 컬럼에 정밀도 손실 없이 저장되는 표식 값(23:59:59)이다. HH:mm으로는 만들 수 없는 값이라 구분된다. */
	public static final LocalTime END_OF_DAY = LocalTime.of(23, 59, 59);

	public AvailabilityWindow {
		if (day == null || start == null || end == null) {
			throw new IllegalArgumentException("day, start and end are required");
		}
		if (!start.isBefore(end)) {
			throw new IllegalArgumentException("start must be before end");
		}
	}

	boolean contains(LocalTime time) {
		return !time.isBefore(start) && (time.isBefore(end) || end.equals(END_OF_DAY));
	}

}
