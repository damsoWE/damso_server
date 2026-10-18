package damso.notification.adapter.in.controller;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import damso.common.security.CurrentUser;
import damso.notification.adapter.in.controller.dto.request.AvailabilityWindowRequestDTO;
import damso.notification.adapter.in.controller.dto.request.NotificationSettingsRequestDTO;
import damso.notification.adapter.in.controller.dto.response.NotificationSettingsResponseDTO;
import damso.notification.application.port.in.NotificationSettingsUseCase;

/** 교사 알림 설정 API (경로 단위 역할 검사는 SecurityConfig의 /api/v1/teacher/**). 교사 ID는 토큰에서만 꺼낸다. */
@RestController
@RequestMapping("/api/v1/teacher/notification-settings")
class NotificationSettingsController {

	private final NotificationSettingsUseCase settings;

	NotificationSettingsController(NotificationSettingsUseCase settings) {
		this.settings = settings;
	}

	@GetMapping
	NotificationSettingsResponseDTO get(CurrentUser teacher) {
		return NotificationSettingsResponseDTO.from(settings.get(teacher.userId()));
	}

	@PutMapping
	NotificationSettingsResponseDTO update(CurrentUser teacher, @Valid @RequestBody NotificationSettingsRequestDTO request) {
		return NotificationSettingsResponseDTO.from(settings.save(teacher.userId(), request.generalEnabled(),
				request.availability().stream().map(AvailabilityWindowRequestDTO::toInput).toList()));
	}

}
