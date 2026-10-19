package damso.notification.adapter.in.controller;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import damso.common.security.CurrentUser;
import damso.notification.adapter.in.controller.dto.request.RegisterDeviceRequestDTO;
import damso.notification.application.port.in.DeviceRegistrationUseCase;

/**
 * 알림 수신 기기 등록 API. 로그인한 누구나 쓸 수 있고(교사·학부모), 등록은 접근 토큰의 세션에 묶인다.
 * 사용자·세션은 토큰에서만 꺼내고 토큰 값은 응답·로그에 남기지 않는다.
 */
@RestController
@RequestMapping("/api/v1/me/devices")
class DeviceController {

	private final DeviceRegistrationUseCase devices;

	DeviceController(DeviceRegistrationUseCase devices) {
		this.devices = devices;
	}

	@PutMapping
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void register(CurrentUser user, @Valid @RequestBody RegisterDeviceRequestDTO request) {
		devices.register(user.userId(), user.sessionId(), request.token(), request.platform());
	}

	@DeleteMapping("/current")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void unregisterCurrent(CurrentUser user) {
		devices.unregisterCurrent(user.userId(), user.sessionId());
	}

}
