package damso.notification.application.service;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import damso.notification.application.port.in.ParentNotificationSettingsUseCase;
import damso.notification.application.port.out.ParentNotificationSettingPort;
import damso.notification.domain.ParentNotificationSetting;

@Service
@Transactional
public class ParentNotificationSettingsService implements ParentNotificationSettingsUseCase {

	private final ParentNotificationSettingPort settings;

	public ParentNotificationSettingsService(ParentNotificationSettingPort settings) {
		this.settings = settings;
	}

	@Override
	@Transactional(readOnly = true)
	public boolean get(UUID parentId) {
		return settings.findByParentId(parentId).map(ParentNotificationSetting::isAnswerNotifyEnabled).orElse(true);
	}

	@Override
	public boolean save(UUID parentId, boolean answerNotifyEnabled) {
		ParentNotificationSetting setting = settings.findByParentId(parentId)
				.orElseGet(() -> new ParentNotificationSetting(parentId, answerNotifyEnabled));
		setting.changeAnswerNotifyEnabled(answerNotifyEnabled);
		return settings.save(setting).isAnswerNotifyEnabled();
	}

}
