package damso.notification.application.port.out;

import java.util.Optional;
import java.util.UUID;

import damso.notification.domain.ParentNotificationSetting;

public interface ParentNotificationSettingPort {

	Optional<ParentNotificationSetting> findByParentId(UUID parentId);

	ParentNotificationSetting save(ParentNotificationSetting setting);

}
