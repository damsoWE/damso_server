package damso.notification.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import org.junit.jupiter.api.Test;

/** 응답 가능 시간 판정은 Asia/Seoul 기준이고 시작은 포함·종료는 미포함이다. */
class AvailabilityCalendarTest {

	private static Instant seoul(int year, int month, int day, int hour, int minute) {
		return LocalDateTime.of(year, month, day, hour, minute).atZone(AvailabilityCalendar.ZONE).toInstant();
	}

	// 2030-01-07 은 월요일
	private static final List<AvailabilityWindow> MON_WED = List.of(
			new AvailabilityWindow(DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(18, 0)),
			new AvailabilityWindow(DayOfWeek.WEDNESDAY, LocalTime.of(13, 0), LocalTime.of(17, 30)));

	@Test
	void noWindowsMeansAlwaysAvailable() {
		Instant any = seoul(2030, 1, 6, 3, 0);
		assertThat(AvailabilityCalendar.isAvailable(List.of(), any)).isTrue();
		assertThat(AvailabilityCalendar.nextStart(List.of(), any)).isEqualTo(any);
	}

	@Test
	void startIsInclusiveAndEndIsExclusive() {
		assertThat(AvailabilityCalendar.isAvailable(MON_WED, seoul(2030, 1, 7, 9, 0))).as("시작 정각은 안").isTrue();
		assertThat(AvailabilityCalendar.isAvailable(MON_WED, seoul(2030, 1, 7, 17, 59))).isTrue();
		assertThat(AvailabilityCalendar.isAvailable(MON_WED, seoul(2030, 1, 7, 18, 0))).as("종료 정각은 밖").isFalse();
		assertThat(AvailabilityCalendar.isAvailable(MON_WED, seoul(2030, 1, 7, 8, 59))).isFalse();
	}

	@Test
	void daysWithoutAWindowAreOutside() {
		assertThat(AvailabilityCalendar.isAvailable(MON_WED, seoul(2030, 1, 8, 10, 0))).as("화요일").isFalse();
		assertThat(AvailabilityCalendar.isAvailable(MON_WED, seoul(2030, 1, 13, 10, 0))).as("일요일").isFalse();
	}

	@Test
	void evaluationUsesSeoulTimeNotUtc() {
		// UTC 2030-01-07 00:30 = 서울 월요일 09:30 → 안
		assertThat(AvailabilityCalendar.isAvailable(MON_WED, Instant.parse("2030-01-07T00:30:00Z"))).isTrue();
		// UTC 2030-01-07 09:30 = 서울 월요일 18:30 → 밖
		assertThat(AvailabilityCalendar.isAvailable(MON_WED, Instant.parse("2030-01-07T09:30:00Z"))).isFalse();
	}

	@Test
	void nextStartIsNowWhenAvailable() {
		Instant inside = seoul(2030, 1, 7, 10, 0);
		assertThat(AvailabilityCalendar.nextStart(MON_WED, inside)).isEqualTo(inside);
	}

	@Test
	void nextStartIsLaterTodayWhenBeforeTodaysWindow() {
		assertThat(AvailabilityCalendar.nextStart(MON_WED, seoul(2030, 1, 7, 7, 0))).isEqualTo(seoul(2030, 1, 7, 9, 0));
	}

	@Test
	void nextStartSkipsToTheNearestFollowingWindow() {
		// 월요일 18시 이후 → 수요일 13시
		assertThat(AvailabilityCalendar.nextStart(MON_WED, seoul(2030, 1, 7, 20, 0))).isEqualTo(seoul(2030, 1, 9, 13, 0));
		// 화요일 → 수요일 13시
		assertThat(AvailabilityCalendar.nextStart(MON_WED, seoul(2030, 1, 8, 9, 0))).isEqualTo(seoul(2030, 1, 9, 13, 0));
		// 수요일 17:30 이후 → 다음 주 월요일 9시
		assertThat(AvailabilityCalendar.nextStart(MON_WED, seoul(2030, 1, 9, 18, 0))).isEqualTo(seoul(2030, 1, 14, 9, 0));
	}

	@Test
	void sameDayWindowThatAlreadyStartedAndEndedWrapsToNextWeekWhenItIsTheOnlyOne() {
		List<AvailabilityWindow> mondayOnly = List.of(
				new AvailabilityWindow(DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(10, 0)));
		// 같은 월요일 10시 이후면 7일 뒤 월요일 9시
		assertThat(AvailabilityCalendar.nextStart(mondayOnly, seoul(2030, 1, 7, 11, 0))).isEqualTo(seoul(2030, 1, 14, 9, 0));
	}

	@Test
	void endOfDayWindowIncludesTheLastMinuteOfTheDay() {
		List<AvailabilityWindow> evening = List.of(
				new AvailabilityWindow(DayOfWeek.FRIDAY, LocalTime.of(18, 0), AvailabilityWindow.END_OF_DAY));
		assertThat(AvailabilityCalendar.isAvailable(evening, seoul(2030, 1, 11, 23, 59))).isTrue();
		assertThat(AvailabilityCalendar.isAvailable(evening, seoul(2030, 1, 11, 17, 59))).isFalse();
		assertThat(AvailabilityCalendar.isAvailable(evening, seoul(2030, 1, 12, 0, 0))).isFalse();
	}

	@Test
	void windowRequiresStartBeforeEnd() {
		assertThatThrownBy(() -> new AvailabilityWindow(DayOfWeek.MONDAY, LocalTime.of(10, 0), LocalTime.of(10, 0)))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new AvailabilityWindow(DayOfWeek.MONDAY, LocalTime.of(11, 0), LocalTime.of(10, 0)))
				.isInstanceOf(IllegalArgumentException.class);
	}

}
