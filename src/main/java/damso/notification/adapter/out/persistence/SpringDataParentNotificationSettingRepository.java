package damso.notification.adapter.out.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import damso.notification.domain.ParentNotificationSetting;

interface SpringDataParentNotificationSettingRepository extends JpaRepository<ParentNotificationSetting, UUID> {

	Optional<ParentNotificationSetting> findByParentId(UUID parentId);

}
