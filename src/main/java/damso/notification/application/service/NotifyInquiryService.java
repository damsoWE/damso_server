package damso.notification.application.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import damso.notification.application.port.in.NotifyInquiryUseCase;
import damso.notification.application.port.out.NotificationRequestPort;
import damso.notification.application.port.out.NotificationSettingPort;
import damso.notification.domain.AvailabilityCalendar;
import damso.notification.domain.AvailabilityWindow;
import damso.notification.domain.NotificationRequest;
import damso.notification.domain.TeacherNotificationSetting;

@Service
@Transactional
public class NotifyInquiryService implements NotifyInquiryUseCase {

	private final NotificationRequestPort requests;

	private final NotificationSettingPort settings;

	private final ApplicationEventPublisher events;

	private final Clock clock;

	public NotifyInquiryService(NotificationRequestPort requests, NotificationSettingPort settings,
			ApplicationEventPublisher events, Clock clock) {
		this.requests = requests;
		this.settings = settings;
		this.events = events;
		this.clock = clock;
	}

	@Override
	public UUID requestUrgent(UUID inquiryId, UUID teacherId) {
		NotificationRequest request = requests.save(NotificationRequest.urgent(teacherId, inquiryId, clock.instant()));
		// 커밋된 뒤에만 UrgentDispatchTrigger가 받는다. 커밋이 실패하면 요청도 문의도 남지 않는다.
		events.publishEvent(new UrgentNotificationRequested(request.getId()));
		return request.getId();
	}

	@Override
	public NotificationDecision requestGeneral(UUID inquiryId, UUID teacherId) {
		Instant now = clock.instant();
		NotificationDecision decision = decide(teacherId, now);
		if (decision == NotificationDecision.IMMEDIATE) {
			requests.save(NotificationRequest.general(teacherId, inquiryId, now));
		}
		if (decision == NotificationDecision.DEFERRED) {
			// 교사 앞으로 대기 중인 묶음이 없을 때만 다음 시작 시각에 만든다. 이미 있으면 그 시각을 바꾸지 않는다.
			requests.createDigestIfAbsent(teacherId, AvailabilityCalendar.nextStart(settings.findWindows(teacherId), now),
					now);
		}
		return decision;
	}

	@Override
	@Transactional(readOnly = true)
	public NotificationDecision previewGeneral(UUID teacherId) {
		return decide(teacherId, clock.instant());
	}

	private NotificationDecision decide(UUID teacherId, Instant now) {
		boolean enabled = settings.findSetting(teacherId).map(TeacherNotificationSetting::isGeneralEnabled).orElse(true);
		if (!enabled) {
			return NotificationDecision.OFF;
		}
		List<AvailabilityWindow> windows = settings.findWindows(teacherId);
		return AvailabilityCalendar.isAvailable(windows, now) ? NotificationDecision.IMMEDIATE
				: NotificationDecision.DEFERRED;
	}

}
