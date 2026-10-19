package damso.notification.adapter.in.controller.dto.request;

import java.time.DayOfWeek;
import java.time.LocalTime;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import damso.notification.application.port.in.NotificationSettingsUseCase.WindowInput;
import damso.notification.domain.AvailabilityWindow;

/** 요일별 응답 가능 구간 하나(한국 시간, HH:mm). 끝은 24:00도 허용한다. */
public record AvailabilityWindowRequestDTO(
		@NotNull DayOfWeek dayOfWeek,
		@NotNull @Pattern(regexp = "^([01][0-9]|2[0-3]):[0-5][0-9]$") String start,
		@NotNull @Pattern(regexp = "^([01][0-9]|2[0-3]):[0-5][0-9]$|^24:00$") String end) {

	public WindowInput toInput() {
		LocalTime endTime = "24:00".equals(end) ? AvailabilityWindow.END_OF_DAY : LocalTime.parse(end);
		return new WindowInput(dayOfWeek, LocalTime.parse(start), endTime);
	}

}
