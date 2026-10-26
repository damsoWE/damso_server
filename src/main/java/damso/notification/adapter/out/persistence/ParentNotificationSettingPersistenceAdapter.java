package damso.notification.adapter.out.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;

import damso.notification.application.port.out.ParentNotificationSettingPort;
import damso.notification.domain.ParentNotificationSetting;

@Component
class ParentNotificationSettingPersistenceAdapter implements ParentNotificationSettingPort {

	private final SpringDataParentNotificationSettingRepository settings;

	ParentNotificationSettingPersistenceAdapter(SpringDataParentNotificationSettingRepository settings) {
		this.settings = settings;
	}

	@Override
	public Optional<ParentNotificationSetting> findByParentId(UUID parentId) {
		return settings.findByParentId(parentId);
	}

	@Override
	public boolean upsertAnswerNotifyEnabled(UUID parentId, boolean answerNotifyEnabled) {
		return settings.upsertAnswerNotifyEnabled(UUID.randomUUID(), parentId, answerNotifyEnabled);
	}

}
