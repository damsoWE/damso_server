package damso.notification.adapter.in.controller.dto.response;

import java.time.DayOfWeek;
import java.time.format.DateTimeFormatter;

import damso.notification.domain.AvailabilityWindow;

/** 요일별 응답 가능 구간 하나(한국 시간, HH:mm). 하루 끝은 24:00으로 내려 준다. */
public record AvailabilityWindowResponseDTO(DayOfWeek dayOfWeek, String start, String end) {

	private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");

	public static AvailabilityWindowResponseDTO from(AvailabilityWindow window) {
		String end = AvailabilityWindow.END_OF_DAY.equals(window.end()) ? "24:00" : window.end().format(HH_MM);
		return new AvailabilityWindowResponseDTO(window.day(), window.start().format(HH_MM), end);
	}

}
