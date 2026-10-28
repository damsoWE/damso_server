package damso.notification.application.port.in;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import damso.notification.domain.AvailabilityWindow;

/** 교사의 알림 설정: 일반 알림 켜기/끄기와 요일별 응답 가능 시간. 긴급 알림 항목은 없다(끌 수 없다). */
public interface NotificationSettingsUseCase {

	/** 저장 요청의 구간 한 줄. 검증은 서비스가 한다(도메인 값 객체는 잘못된 값을 아예 만들 수 없어서 따로 둔다). */
	record WindowInput(DayOfWeek day, LocalTime start, LocalTime end) {
	}

	record Settings(boolean generalEnabled, List<AvailabilityWindow> availability) {

		/** 받은 목록을 복사해 둔다(불변) — 만든 쪽이 나중에 목록을 바꿔도 이 값은 바뀌지 않는다. */
		public Settings {
			availability = availability == null ? null : List.copyOf(availability);
		}

	}

	/** 설정 행이 없으면 일반 알림 켜짐 + 구간 없음(항상 응답 가능). */
	Settings get(UUID teacherId);

	/**
	 * 설정을 통째로 저장한다. 구간은 start &lt; end, 요일당 한 구간, 최대 7개여야 하며 아니면 InvalidInputException(고정 사유).
	 * 이후 접수되는 문의부터 적용되고, 이미 잡힌 묶음 알림의 시각은 바꾸지 않는다.
	 */
	Settings save(UUID teacherId, boolean generalEnabled, List<WindowInput> availability);

}
