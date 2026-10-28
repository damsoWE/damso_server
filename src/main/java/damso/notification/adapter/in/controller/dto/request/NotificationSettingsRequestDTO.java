package damso.notification.adapter.in.controller.dto.request;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 교사 알림 설정 저장 요청. 긴급 알림 항목은 없다(끌 수 없다). availability를 빈 배열로 보내면 항상 응답 가능이다.
 * 요일당 한 구간·start &lt; end는 서비스가 한 번 더 검증한다.
 */
public record NotificationSettingsRequestDTO(
		@NotNull Boolean generalEnabled,
		@NotNull @Size(max = 7) List<@NotNull @Valid AvailabilityWindowRequestDTO> availability) {

	/** 받은 목록을 복사해 둔다(불변). 검증 전에 만들어지므로 null·null 원소는 그대로 두고 검증(@NotNull 등)이 거른다. */
	public NotificationSettingsRequestDTO {
		availability = availability == null ? null : Collections.unmodifiableList(new ArrayList<>(availability));
	}

}
