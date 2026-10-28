package damso.notification.adapter.in.controller.dto.response;

import java.util.List;

import damso.notification.application.port.in.NotificationSettingsUseCase.Settings;

/** 교사 알림 설정. 긴급 알림 항목은 없다(항상 켜짐). */
public record NotificationSettingsResponseDTO(boolean generalEnabled, List<AvailabilityWindowResponseDTO> availability) {

	/** 받은 목록을 복사해 둔다(불변) — 만든 쪽이 나중에 목록을 바꿔도 이 값은 바뀌지 않는다. */
	public NotificationSettingsResponseDTO {
		availability = availability == null ? null : List.copyOf(availability);
	}

	public static NotificationSettingsResponseDTO from(Settings settings) {
		return new NotificationSettingsResponseDTO(settings.generalEnabled(),
				settings.availability().stream().map(AvailabilityWindowResponseDTO::from).toList());
	}

}
