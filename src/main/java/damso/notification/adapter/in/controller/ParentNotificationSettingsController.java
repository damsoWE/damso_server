package damso.notification.adapter.in.controller;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import damso.common.security.CurrentUser;
import damso.notification.adapter.in.controller.dto.request.ParentNotificationSettingsRequestDTO;
import damso.notification.adapter.in.controller.dto.response.ParentNotificationSettingsResponseDTO;
import damso.notification.application.port.in.ParentNotificationSettingsUseCase;

/** 학부모 답변 도착 알림 설정 API (경로 단위 역할 검사는 SecurityConfig의 /api/v1/parent/**). 학부모 ID는 토큰에서만 꺼낸다. */
@RestController
@RequestMapping("/api/v1/parent/notification-settings")
class ParentNotificationSettingsController {

	private final ParentNotificationSettingsUseCase settings;

	ParentNotificationSettingsController(ParentNotificationSettingsUseCase settings) {
		this.settings = settings;
	}

	@GetMapping
	ParentNotificationSettingsResponseDTO get(CurrentUser parent) {
		return new ParentNotificationSettingsResponseDTO(settings.get(parent.userId()));
	}

	@PutMapping
	ParentNotificationSettingsResponseDTO update(CurrentUser parent,
			@Valid @RequestBody ParentNotificationSettingsRequestDTO request) {
		return new ParentNotificationSettingsResponseDTO(
				settings.save(parent.userId(), request.answerNotifyEnabled()));
	}

}
