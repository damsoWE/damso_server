package damso.notification.adapter.in.controller.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import damso.notification.domain.DeviceRegistration;
import damso.notification.domain.DeviceRegistration.Platform;

/** 알림을 받을 기기 등록 요청. FCM 토큰은 로그에 남기지 않으므로 toString에서 가린다. */
public record RegisterDeviceRequestDTO(
		@NotBlank @Size(max = DeviceRegistration.MAX_TOKEN_LENGTH) String token,
		@NotNull Platform platform) {

	@Override
	public String toString() {
		return "RegisterDeviceRequestDTO[platform=" + platform + "]";
	}

}
