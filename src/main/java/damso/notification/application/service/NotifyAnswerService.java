package damso.notification.application.service;

import java.time.Clock;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import damso.notification.application.port.in.NotifyAnswerUseCase;
import damso.notification.application.port.in.ParentNotificationSettingsUseCase;
import damso.notification.application.port.out.NotificationRequestPort;
import damso.notification.domain.NotificationRequest;

@Service
@Transactional
public class NotifyAnswerService implements NotifyAnswerUseCase {

	private final ParentNotificationSettingsUseCase settings;

	private final NotificationRequestPort requests;

	private final Clock clock;

	public NotifyAnswerService(ParentNotificationSettingsUseCase settings, NotificationRequestPort requests,
			Clock clock) {
		this.settings = settings;
		this.requests = requests;
		this.clock = clock;
	}

	@Override
	public void requestAnswerNotification(UUID inquiryId, UUID parentId) {
		if (!settings.get(parentId)) {
			return;
		}
		requests.save(NotificationRequest.answer(parentId, inquiryId, clock.instant()));
	}

}
