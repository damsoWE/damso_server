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
	public ParentNotificationSetting save(ParentNotificationSetting setting) {
		return settings.saveAndFlush(setting);
	}

}
